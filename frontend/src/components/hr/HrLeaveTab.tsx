import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { Check, Eye, Plus, X } from 'lucide-react';
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
  HrEmployee,
  HrLeaveRequest,
  LeaveRequestStatus,
  LeaveType,
} from '../../lib/hrTypes';
import {
  LEAVE_STATUS_LABELS,
  LEAVE_TYPE_LABELS,
  formatTimestamp,
  getLeaveStatusVariant,
} from './hrFormatters';

interface HrLeaveTabProps {
  token: string | null;
  canWrite: boolean;
  canReview: boolean;
  selfServiceOnly: boolean;
  linkedEmployee: HrEmployee | null;
  employees: HrEmployee[];
}

export function HrLeaveTab({
  token,
  canWrite,
  canReview,
  selfServiceOnly,
  linkedEmployee,
  employees,
}: HrLeaveTabProps) {
  const { notify } = useToast();
  const pageSize = 15;

  const [requests, setRequests] = useState<HrLeaveRequest[]>([]);
  const [totalItems, setTotalItems] = useState(0);
  const [page, setPage] = useState(1);
  const [employeeFilter, setEmployeeFilter] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [typeFilter, setTypeFilter] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Submit Leave Dialog
  const [createOpen, setCreateOpen] = useState(false);
  const [leaveEmployeeId, setLeaveEmployeeId] = useState('');
  const [leaveType, setLeaveType] = useState<LeaveType>('CASUAL_LEAVE');
  const [startDate, setStartDate] = useState(new Date().toISOString().slice(0, 10));
  const [endDate, setEndDate] = useState(new Date().toISOString().slice(0, 10));
  const [reason, setReason] = useState('');
  const [createError, setCreateError] = useState<string | null>(null);
  const [createSubmitting, setCreateSubmitting] = useState(false);

  // Detail Dialog
  const [selectedLeave, setSelectedLeave] = useState<HrLeaveRequest | null>(null);

  // Review / Cancel Dialog
  const [reviewAction, setReviewAction] = useState<{
    leave: HrLeaveRequest;
    action: 'approve' | 'reject' | 'cancel';
  } | null>(null);
  const [reviewComments, setReviewComments] = useState('');
  const [reviewError, setReviewError] = useState<string | null>(null);
  const [reviewSubmitting, setReviewSubmitting] = useState(false);

  const loadLeaveRequests = useCallback(async () => {
    if (!token) return;
    setLoading(true);
    setError(null);
    const params = new URLSearchParams({
      page: String(page),
      pageSize: String(pageSize),
    });
    if (employeeFilter) params.set('employeeId', employeeFilter);
    if (statusFilter) params.set('status', statusFilter);
    if (typeFilter) params.set('leaveType', typeFilter);

    try {
      const res = await apiRequest<PagedResponse<HrLeaveRequest>>(
        `/api/v1/hr/leave-requests?${params.toString()}`,
        { token },
      );
      setRequests(res.data);
      setTotalItems(res.totalItems);
    } catch (err) {
      setError(
        err instanceof ApiError ? err.message : 'Unable to load leave requests.',
      );
    } finally {
      setLoading(false);
    }
  }, [employeeFilter, page, statusFilter, token, typeFilter]);

  useEffect(() => {
    void loadLeaveRequests();
  }, [loadLeaveRequests]);

  const activeEmployees = employees.filter((e) => e.employmentStatus === 'ACTIVE');
  const canSubmitLeave = canWrite || Boolean(linkedEmployee);

  const openCreateDialog = () => {
    setLeaveEmployeeId(
      canWrite
        ? activeEmployees[0]?.id ?? linkedEmployee?.id ?? ''
        : linkedEmployee?.id ?? '',
    );
    const today = new Date().toISOString().slice(0, 10);
    setLeaveType('CASUAL_LEAVE');
    setStartDate(today);
    setEndDate(today);
    setReason('');
    setCreateError(null);
    setCreateOpen(true);
  };

  const openDetail = async (leave: HrLeaveRequest) => {
    setSelectedLeave(leave);
    if (!token) return;
    try {
      const full = await apiRequest<HrLeaveRequest>(
        `/api/v1/hr/leave-requests/${leave.id}`,
        { token },
      );
      setSelectedLeave(full);
    } catch {
      // Keep summary if detail fetch fails
    }
  };

  const handleSubmitLeave = async (event: FormEvent) => {
    event.preventDefault();
    if (!token) return;
    if (!leaveEmployeeId || !startDate || !endDate || !reason.trim()) {
      setCreateError('Please fill in all required leave request fields.');
      return;
    }

    setCreateSubmitting(true);
    setCreateError(null);
    try {
      await apiRequest<HrLeaveRequest>('/api/v1/hr/leave-requests', {
        method: 'POST',
        token,
        body: {
          employeeId: leaveEmployeeId,
          leaveType,
          startDate,
          endDate,
          reason: reason.trim(),
        },
      });
      notify({ variant: 'success', title: 'Leave request submitted' });
      setCreateOpen(false);
      await loadLeaveRequests();
    } catch (err) {
      setCreateError(
        err instanceof ApiError ? err.message : 'Unable to submit leave request.',
      );
    } finally {
      setCreateSubmitting(false);
    }
  };

  const handleReviewSubmit = async (event: FormEvent) => {
    event.preventDefault();
    if (!token || !reviewAction) return;
    if (reviewAction.action === 'reject' && !reviewComments.trim()) {
      setReviewError('Please provide a comment when rejecting a leave request.');
      return;
    }

    setReviewSubmitting(true);
    setReviewError(null);
    try {
      const updated = await apiRequest<HrLeaveRequest>(
        `/api/v1/hr/leave-requests/${reviewAction.leave.id}/${reviewAction.action}`,
        {
          method: 'POST',
          token,
          body: { comments: reviewComments.trim() || null },
        },
      );
      notify({
        variant: 'success',
        title:
          reviewAction.action === 'approve'
            ? 'Leave request approved'
            : reviewAction.action === 'reject'
              ? 'Leave request rejected'
              : 'Leave request cancelled',
      });
      setReviewAction(null);
      setReviewComments('');
      if (selectedLeave?.id === updated.id) {
        setSelectedLeave(updated);
      }
      await loadLeaveRequests();
    } catch (err) {
      setReviewError(
        err instanceof ApiError ? err.message : 'Unable to process leave action.',
      );
    } finally {
      setReviewSubmitting(false);
    }
  };

  const canReviewRow = (row: HrLeaveRequest) =>
    canReview &&
    row.status === 'PENDING' &&
    (canWrite || !linkedEmployee || row.employeeId !== linkedEmployee.id);

  const canCancelRow = (row: HrLeaveRequest) =>
    row.status === 'PENDING' &&
    (canWrite || (linkedEmployee && row.employeeId === linkedEmployee.id));

  const screenStatus: ScreenStatus = loading
    ? 'loading'
    : error
      ? 'error'
      : requests.length === 0
        ? 'empty'
        : 'success';

  const columns: TableColumn<HrLeaveRequest>[] = [
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
      key: 'type',
      header: 'Leave Type',
      render: (row) => LEAVE_TYPE_LABELS[row.leaveType],
    },
    {
      key: 'dates',
      header: 'Period',
      render: (row) => `${row.startDate} to ${row.endDate} (${row.totalDays}d)`,
    },
    {
      key: 'status',
      header: 'Status',
      render: (row) => (
        <Badge variant={getLeaveStatusVariant(row.status)}>
          {LEAVE_STATUS_LABELS[row.status]}
        </Badge>
      ),
    },
    {
      key: 'reviewer',
      header: 'Reviewer',
      render: (row) => row.reviewerName ?? '—',
    },
    {
      key: 'actions',
      header: 'Actions',
      align: 'right',
      render: (row) => (
        <div className="foundation-row" style={{ justifyContent: 'flex-end' }}>
          <Button
            variant="ghost"
            size="sm"
            leftIcon={<Eye size={14} aria-hidden="true" />}
            onClick={() => void openDetail(row)}
          >
            Details
          </Button>
          {canReviewRow(row) ? (
            <>
              <Button
                variant="secondary"
                size="sm"
                leftIcon={<Check size={14} aria-hidden="true" />}
                onClick={() => {
                  setReviewComments('');
                  setReviewError(null);
                  setReviewAction({ leave: row, action: 'approve' });
                }}
              >
                Approve
              </Button>
              <Button
                variant="danger"
                size="sm"
                leftIcon={<X size={14} aria-hidden="true" />}
                onClick={() => {
                  setReviewComments('');
                  setReviewError(null);
                  setReviewAction({ leave: row, action: 'reject' });
                }}
              >
                Reject
              </Button>
            </>
          ) : null}
          {canCancelRow(row) ? (
            <Button
              variant="ghost"
              size="sm"
              onClick={() => {
                setReviewComments('');
                setReviewError(null);
                setReviewAction({ leave: row, action: 'cancel' });
              }}
            >
              Cancel
            </Button>
          ) : null}
        </div>
      ),
    },
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
              <Select
                label="Status"
                value={statusFilter}
                onChange={(e) => {
                  setStatusFilter(e.target.value as LeaveRequestStatus | '');
                  setPage(1);
                }}
                options={[
                  { value: '', label: 'All statuses' },
                  { value: 'PENDING', label: 'Pending' },
                  { value: 'APPROVED', label: 'Approved' },
                  { value: 'REJECTED', label: 'Rejected' },
                  { value: 'CANCELLED', label: 'Cancelled' },
                ]}
              />
              <Select
                label="Leave Type"
                value={typeFilter}
                onChange={(e) => {
                  setTypeFilter(e.target.value as LeaveType | '');
                  setPage(1);
                }}
                options={[
                  { value: '', label: 'All types' },
                  { value: 'CASUAL_LEAVE', label: 'Casual Leave' },
                  { value: 'SICK_LEAVE', label: 'Sick Leave' },
                  { value: 'EARNED_LEAVE', label: 'Earned Leave' },
                  { value: 'UNPAID_LEAVE', label: 'Unpaid Leave' },
                ]}
              />
            </div>

            {canSubmitLeave ? (
              <Button
                variant="primary"
                leftIcon={<Plus size={16} aria-hidden="true" />}
                onClick={openCreateDialog}
              >
                Request Leave
              </Button>
            ) : null}
          </div>

          <StateBoundary
            status={screenStatus}
            errorTitle="Could not load leave requests"
            errorMessage={error ?? 'Failed to fetch leave requests.'}
            onRetry={() => void loadLeaveRequests()}
            emptyTitle="No leave requests"
            emptyDescription="Submitted leave requests and review decisions will appear here."
          >
            <Table
              caption="Leave requests"
              columns={columns}
              rows={requests}
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

      {/* Submit Leave Request Dialog */}
      <Dialog
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        title="Submit Leave Request"
        description="Overlapping pending or approved leave requests for the same employee are prevented."
      >
        <form onSubmit={handleSubmitLeave} className="foundation-stack" noValidate>
          {createError ? <Alert variant="error" title={createError} /> : null}
          {canWrite ? (
            <Select
              label="Employee"
              required
              value={leaveEmployeeId}
              onChange={(e) => setLeaveEmployeeId(e.target.value)}
              placeholder="Select employee"
              options={activeEmployees.map((e) => ({
                value: e.id,
                label: `${e.fullName} (${e.employeeCode})`,
              }))}
            />
          ) : (
            <div style={{ fontSize: 'var(--text-sm)' }}>
              Submitting for: <strong>{linkedEmployee?.fullName}</strong>
            </div>
          )}
          <Select
            label="Leave Type"
            required
            value={leaveType}
            onChange={(e) => setLeaveType(e.target.value as LeaveType)}
            options={[
              { value: 'CASUAL_LEAVE', label: 'Casual Leave' },
              { value: 'SICK_LEAVE', label: 'Sick Leave' },
              { value: 'EARNED_LEAVE', label: 'Earned Leave' },
              { value: 'UNPAID_LEAVE', label: 'Unpaid Leave' },
            ]}
          />
          <div className="foundation-grid">
            <div className="foundation-col-6">
              <FormField
                label="Start Date"
                type="date"
                required
                value={startDate}
                onChange={(e) => setStartDate(e.target.value)}
              />
            </div>
            <div className="foundation-col-6">
              <FormField
                label="End Date"
                type="date"
                required
                value={endDate}
                onChange={(e) => setEndDate(e.target.value)}
              />
            </div>
          </div>
          <TextareaField
            label="Reason"
            required
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            placeholder="Reason for leave..."
          />
          <div className="foundation-row" style={{ justifyContent: 'flex-end' }}>
            <Button
              type="button"
              variant="secondary"
              onClick={() => setCreateOpen(false)}
            >
              Cancel
            </Button>
            <Button type="submit" variant="primary" loading={createSubmitting}>
              Submit Request
            </Button>
          </div>
        </form>
      </Dialog>

      {/* Leave Request Detail + Audit Trail Dialog */}
      <Dialog
        open={Boolean(selectedLeave)}
        onClose={() => setSelectedLeave(null)}
        title={
          selectedLeave
            ? `Leave Request · ${selectedLeave.employeeName}`
            : 'Leave Request Details'
        }
        description={
          selectedLeave
            ? `${LEAVE_TYPE_LABELS[selectedLeave.leaveType]} · ${selectedLeave.startDate} to ${selectedLeave.endDate} (${selectedLeave.totalDays} day(s))`
            : undefined
        }
        size="lg"
      >
        {selectedLeave ? (
          <div className="foundation-stack">
            <div className="foundation-grid">
              <div className="foundation-col-6">
                <div style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>
                  Status
                </div>
                <Badge variant={getLeaveStatusVariant(selectedLeave.status)}>
                  {LEAVE_STATUS_LABELS[selectedLeave.status]}
                </Badge>
              </div>
              <div className="foundation-col-6">
                <div style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>
                  Submitted At
                </div>
                <div>{formatTimestamp(selectedLeave.submittedAt)}</div>
              </div>
              <div className="foundation-col-12">
                <div style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>
                  Reason
                </div>
                <div>{selectedLeave.reason}</div>
              </div>
              <div className="foundation-col-6">
                <div style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>
                  Reviewer
                </div>
                <div>{selectedLeave.reviewerName ?? 'Pending review'}</div>
              </div>
              <div className="foundation-col-6">
                <div style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>
                  Reviewed At
                </div>
                <div>{formatTimestamp(selectedLeave.reviewedAt)}</div>
              </div>
              {selectedLeave.reviewComments ? (
                <div className="foundation-col-12">
                  <div style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>
                    Review Comments
                  </div>
                  <div>{selectedLeave.reviewComments}</div>
                </div>
              ) : null}
            </div>

            <div>
              <div style={{ fontWeight: 'var(--weight-semibold)', marginBottom: 'var(--space-2)' }}>
                Audit Trail
              </div>
              {selectedLeave.auditTrail.length === 0 ? (
                <div style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>
                  No audit events recorded.
                </div>
              ) : (
                <div className="foundation-stack">
                  {selectedLeave.auditTrail.map((item) => (
                    <div
                      key={item.id}
                      style={{
                        padding: 'var(--space-2) var(--space-3)',
                        border: '1px solid var(--color-border)',
                        borderRadius: 'var(--radius-md)',
                        fontSize: 'var(--text-xs)',
                      }}
                    >
                      <strong>
                        {item.fromStatus ? `${item.fromStatus} → ` : ''}
                        {item.toStatus}
                      </strong>{' '}
                      by {item.actorName ?? 'System'} · {formatTimestamp(item.createdAt)}
                      {item.comment ? (
                        <div style={{ color: 'var(--color-text-secondary)', marginTop: '2px' }}>
                          {item.comment}
                        </div>
                      ) : null}
                    </div>
                  ))}
                </div>
              )}
            </div>

            <div className="foundation-row" style={{ justifyContent: 'flex-end' }}>
              <Button variant="primary" onClick={() => setSelectedLeave(null)}>
                Close
              </Button>
            </div>
          </div>
        ) : null}
      </Dialog>

      {/* Approve / Reject / Cancel Dialog */}
      <Dialog
        open={Boolean(reviewAction)}
        onClose={() => setReviewAction(null)}
        title={
          reviewAction?.action === 'approve'
            ? 'Approve Leave Request'
            : reviewAction?.action === 'reject'
              ? 'Reject Leave Request'
              : 'Cancel Leave Request'
        }
        description={
          reviewAction
            ? `${reviewAction.leave.employeeName} · ${reviewAction.leave.startDate} to ${reviewAction.leave.endDate}`
            : undefined
        }
      >
        <form onSubmit={handleReviewSubmit} className="foundation-stack" noValidate>
          {reviewError ? <Alert variant="error" title={reviewError} /> : null}
          <TextareaField
            label={
              reviewAction?.action === 'reject'
                ? 'Rejection Reason'
                : 'Comments (optional)'
            }
            required={reviewAction?.action === 'reject'}
            value={reviewComments}
            onChange={(e) => setReviewComments(e.target.value)}
            placeholder="Add review notes..."
          />
          <div className="foundation-row" style={{ justifyContent: 'flex-end' }}>
            <Button
              type="button"
              variant="secondary"
              onClick={() => setReviewAction(null)}
            >
              Back
            </Button>
            <Button
              type="submit"
              variant={reviewAction?.action === 'reject' ? 'danger' : 'primary'}
              loading={reviewSubmitting}
            >
              {reviewAction?.action === 'approve'
                ? 'Approve'
                : reviewAction?.action === 'reject'
                  ? 'Reject'
                  : 'Cancel Request'}
            </Button>
          </div>
        </form>
      </Dialog>
    </div>
  );
}

