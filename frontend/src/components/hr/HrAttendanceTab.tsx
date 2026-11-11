import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { Pencil, Plus } from 'lucide-react';
import {
  Alert,
  Badge,
  Button,
  Card,
  CardContent,
  Dialog,
  FormField,
  Pagination,
  Select,
  StateBoundary,
  Table,
  TextareaField,
  useToast,
  type ScreenStatus,
  type TableColumn,
} from '../ui';
import { apiRequest, ApiError } from '../../lib/apiClient';
import type { PagedResponse } from '../../lib/crmTypes';
import type {
  AttendanceStatus,
  HrAttendance,
  HrEmployee,
} from '../../lib/hrTypes';
import {
  ATTENDANCE_STATUS_LABELS,
  getAttendanceStatusVariant,
} from './hrFormatters';

interface HrAttendanceTabProps {
  token: string | null;
  canWrite: boolean;
  selfServiceOnly: boolean;
  employees: HrEmployee[];
}

export function HrAttendanceTab({
  token,
  canWrite,
  selfServiceOnly,
  employees,
}: HrAttendanceTabProps) {
  const { notify } = useToast();
  const pageSize = 15;

  const [records, setRecords] = useState<HrAttendance[]>([]);
  const [totalItems, setTotalItems] = useState(0);
  const [page, setPage] = useState(1);
  const [employeeFilter, setEmployeeFilter] = useState('');
  const [fromDate, setFromDate] = useState('');
  const [toDate, setToDate] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Mark dialog
  const [markOpen, setMarkOpen] = useState(false);
  const [markEmployeeId, setMarkEmployeeId] = useState('');
  const [markDate, setMarkDate] = useState(new Date().toISOString().slice(0, 10));
  const [markStatus, setMarkStatus] = useState<AttendanceStatus>('PRESENT');
  const [markNotes, setMarkNotes] = useState('');
  const [markError, setMarkError] = useState<string | null>(null);
  const [markSubmitting, setMarkSubmitting] = useState(false);

  // Correct dialog
  const [editTarget, setEditTarget] = useState<HrAttendance | null>(null);
  const [editStatus, setEditStatus] = useState<AttendanceStatus>('PRESENT');
  const [editNotes, setEditNotes] = useState('');
  const [editError, setEditError] = useState<string | null>(null);
  const [editSubmitting, setEditSubmitting] = useState(false);

  const loadAttendance = useCallback(async () => {
    if (!token) return;
    setLoading(true);
    setError(null);
    const params = new URLSearchParams({
      page: String(page),
      pageSize: String(pageSize),
    });
    if (employeeFilter) params.set('employeeId', employeeFilter);
    if (fromDate) params.set('fromDate', fromDate);
    if (toDate) params.set('toDate', toDate);
    if (statusFilter) params.set('status', statusFilter);

    try {
      const res = await apiRequest<PagedResponse<HrAttendance>>(
        `/api/v1/hr/attendance?${params.toString()}`,
        { token },
      );
      setRecords(res.data);
      setTotalItems(res.totalItems);
    } catch (err) {
      setError(
        err instanceof ApiError ? err.message : 'Unable to load attendance records.',
      );
    } finally {
      setLoading(false);
    }
  }, [employeeFilter, fromDate, page, statusFilter, toDate, token]);

  useEffect(() => {
    void loadAttendance();
  }, [loadAttendance]);

  const activeEmployees = employees.filter((e) => e.employmentStatus === 'ACTIVE');

  const openMarkDialog = () => {
    setMarkEmployeeId(activeEmployees[0]?.id ?? '');
    setMarkDate(new Date().toISOString().slice(0, 10));
    setMarkStatus('PRESENT');
    setMarkNotes('');
    setMarkError(null);
    setMarkOpen(true);
  };

  const openEditDialog = (record: HrAttendance) => {
    setEditTarget(record);
    setEditStatus(record.status);
    setEditNotes(record.notes ?? '');
    setEditError(null);
  };

  const handleMarkSubmit = async (event: FormEvent) => {
    event.preventDefault();
    if (!token) return;
    if (!markEmployeeId || !markDate) {
      setMarkError('Employee and attendance date are required.');
      return;
    }

    setMarkSubmitting(true);
    setMarkError(null);
    try {
      await apiRequest<HrAttendance>('/api/v1/hr/attendance', {
        method: 'POST',
        token,
        body: {
          employeeId: markEmployeeId,
          attendanceDate: markDate,
          status: markStatus,
          notes: markNotes.trim() || null,
        },
      });
      notify({ variant: 'success', title: 'Attendance recorded' });
      setMarkOpen(false);
      await loadAttendance();
    } catch (err) {
      setMarkError(
        err instanceof ApiError ? err.message : 'Unable to mark attendance.',
      );
    } finally {
      setMarkSubmitting(false);
    }
  };

  const handleEditSubmit = async (event: FormEvent) => {
    event.preventDefault();
    if (!token || !editTarget) return;
    setEditSubmitting(true);
    setEditError(null);
    try {
      await apiRequest<HrAttendance>(`/api/v1/hr/attendance/${editTarget.id}`, {
        method: 'PATCH',
        token,
        body: {
          status: editStatus,
          notes: editNotes.trim(),
        },
      });
      notify({ variant: 'success', title: 'Attendance updated' });
      setEditTarget(null);
      await loadAttendance();
    } catch (err) {
      setEditError(
        err instanceof ApiError ? err.message : 'Unable to update attendance.',
      );
    } finally {
      setEditSubmitting(false);
    }
  };

  const screenStatus: ScreenStatus = loading
    ? 'loading'
    : error
      ? 'error'
      : records.length === 0
        ? 'empty'
        : 'success';

  const columns: TableColumn<HrAttendance>[] = [
    {
      key: 'date',
      header: 'Date',
      render: (row) => <strong>{row.attendanceDate}</strong>,
    },
    {
      key: 'employee',
      header: 'Employee',
      render: (row) => (
        <div>
          <strong>{row.employeeName}</strong>
          <div style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>
            {row.employeeCode} · {row.departmentName}
          </div>
        </div>
      ),
    },
    {
      key: 'status',
      header: 'Status',
      render: (row) => (
        <Badge variant={getAttendanceStatusVariant(row.status)}>
          {ATTENDANCE_STATUS_LABELS[row.status]}
        </Badge>
      ),
    },
    {
      key: 'notes',
      header: 'Notes',
      render: (row) => row.notes || '—',
    },
    {
      key: 'recordedBy',
      header: 'Recorded / Updated By',
      render: (row) => row.updatedByName ?? row.recordedByName ?? '—',
    },
    ...(canWrite
      ? [
          {
            key: 'actions',
            header: 'Actions',
            align: 'right' as const,
            render: (row: HrAttendance) => (
              <Button
                variant="ghost"
                size="sm"
                leftIcon={<Pencil size={14} aria-hidden="true" />}
                onClick={() => openEditDialog(row)}
              >
                Correct
              </Button>
            ),
          },
        ]
      : []),
  ];

  return (
    <div className="foundation-stack">
      <Card>
        <CardContent className="foundation-stack">
          <div
            className="foundation-row"
            style={{ alignItems: 'flex-end', justifyContent: 'space-between' }}
          >
            <div className="foundation-row" style={{ alignItems: 'flex-end' }}>
              {!selfServiceOnly ? (
                <Select
                  label="Employee"
                  value={employeeFilter}
                  onChange={(e) => {
                    setEmployeeFilter(e.target.value);
                    setPage(1);
                  }}
                  options={[
                    { value: '', label: 'All employees' },
                    ...employees.map((e) => ({
                      value: e.id,
                      label: `${e.fullName} (${e.employeeCode})`,
                    })),
                  ]}
                />
              ) : null}
              <FormField
                label="From Date"
                type="date"
                value={fromDate}
                onChange={(e) => {
                  setFromDate(e.target.value);
                  setPage(1);
                }}
              />
              <FormField
                label="To Date"
                type="date"
                value={toDate}
                onChange={(e) => {
                  setToDate(e.target.value);
                  setPage(1);
                }}
              />
              <Select
                label="Status"
                value={statusFilter}
                onChange={(e) => {
                  setStatusFilter(e.target.value);
                  setPage(1);
                }}
                options={[
                  { value: '', label: 'All statuses' },
                  { value: 'PRESENT', label: 'Present' },
                  { value: 'ABSENT', label: 'Absent' },
                  { value: 'HALF_DAY', label: 'Half Day' },
                  { value: 'ON_LEAVE', label: 'On Leave' },
                ]}
              />
            </div>

            {canWrite ? (
              <Button
                variant="primary"
                leftIcon={<Plus size={16} aria-hidden="true" />}
                onClick={openMarkDialog}
              >
                Mark Attendance
              </Button>
            ) : null}
          </div>

          <StateBoundary
            status={screenStatus}
            errorTitle="Could not load attendance"
            errorMessage={error ?? 'Failed to fetch attendance records.'}
            onRetry={() => void loadAttendance()}
            emptyTitle="No attendance records"
            emptyDescription="Attendance entries for the selected filters will appear here."
          >
            <Table
              caption="Attendance history"
              columns={columns}
              rows={records}
              getRowKey={(row) => row.id}
            />
            <Pagination
              page={page}
              pageSize={pageSize}
              totalItems={totalItems}
              onPageChange={setPage}
            />
          </StateBoundary>
        </CardContent>
      </Card>

      {/* Mark Attendance Dialog */}
      <Dialog
        open={markOpen}
        onClose={() => setMarkOpen(false)}
        title="Mark Attendance"
        description="Record attendance for an active employee. Duplicate entries for the same date are prevented."
      >
        <form onSubmit={handleMarkSubmit} className="foundation-stack" noValidate>
          {markError ? <Alert variant="error" title={markError} /> : null}
          <Select
            label="Employee"
            required
            value={markEmployeeId}
            onChange={(e) => setMarkEmployeeId(e.target.value)}
            placeholder="Select employee"
            options={activeEmployees.map((e) => ({
              value: e.id,
              label: `${e.fullName} (${e.employeeCode})`,
            }))}
          />
          <FormField
            label="Date"
            type="date"
            required
            value={markDate}
            onChange={(e) => setMarkDate(e.target.value)}
          />
          <Select
            label="Status"
            required
            value={markStatus}
            onChange={(e) => setMarkStatus(e.target.value as AttendanceStatus)}
            options={[
              { value: 'PRESENT', label: 'Present' },
              { value: 'ABSENT', label: 'Absent' },
              { value: 'HALF_DAY', label: 'Half Day' },
              { value: 'ON_LEAVE', label: 'On Leave' },
            ]}
          />
          <TextareaField
            label="Notes (optional)"
            value={markNotes}
            onChange={(e) => setMarkNotes(e.target.value)}
          />
          <div className="foundation-row" style={{ justifyContent: 'flex-end' }}>
            <Button
              type="button"
              variant="secondary"
              onClick={() => setMarkOpen(false)}
            >
              Cancel
            </Button>
            <Button type="submit" variant="primary" loading={markSubmitting}>
              Save Attendance
            </Button>
          </div>
        </form>
      </Dialog>

      {/* Correct Attendance Dialog */}
      <Dialog
        open={Boolean(editTarget)}
        onClose={() => setEditTarget(null)}
        title="Correct Attendance"
        description={
          editTarget
            ? `${editTarget.employeeName} · ${editTarget.attendanceDate}`
            : undefined
        }
      >
        <form onSubmit={handleEditSubmit} className="foundation-stack" noValidate>
          {editError ? <Alert variant="error" title={editError} /> : null}
          <Select
            label="Status"
            required
            value={editStatus}
            onChange={(e) => setEditStatus(e.target.value as AttendanceStatus)}
            options={[
              { value: 'PRESENT', label: 'Present' },
              { value: 'ABSENT', label: 'Absent' },
              { value: 'HALF_DAY', label: 'Half Day' },
              { value: 'ON_LEAVE', label: 'On Leave' },
            ]}
          />
          <TextareaField
            label="Notes"
            value={editNotes}
            onChange={(e) => setEditNotes(e.target.value)}
          />
          <div className="foundation-row" style={{ justifyContent: 'flex-end' }}>
            <Button
              type="button"
              variant="secondary"
              onClick={() => setEditTarget(null)}
            >
              Cancel
            </Button>
            <Button type="submit" variant="primary" loading={editSubmitting}>
              Update Attendance
            </Button>
          </div>
        </form>
      </Dialog>
    </div>
  );
}

