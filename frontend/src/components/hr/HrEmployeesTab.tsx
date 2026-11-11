import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { Eye, Pencil, Plus, UserX } from 'lucide-react';
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
  EmploymentStatus,
  EmploymentType,
  HrDepartment,
  HrEmployee,
} from '../../lib/hrTypes';
import {
  EMPLOYMENT_STATUS_LABELS,
  EMPLOYMENT_TYPE_LABELS,
  formatIsoDate,
  formatTimestamp,
  getEmploymentStatusVariant,
} from './hrFormatters';

export interface OrgMemberOption {
  userId: string;
  displayName: string;
  email: string;
  role: string;
}

interface HrEmployeesTabProps {
  token: string | null;
  canWrite: boolean;
  selfServiceOnly: boolean;
  departments: HrDepartment[];
  allEmployees: HrEmployee[];
  orgMembers: OrgMemberOption[];
  onDirectoryChanged: () => Promise<void>;
}

const INITIAL_FORM = {
  firstName: '',
  lastName: '',
  email: '',
  phone: '',
  departmentId: '',
  designation: '',
  employmentType: 'FULL_TIME' as EmploymentType,
  dateOfJoining: new Date().toISOString().slice(0, 10),
  employmentStatus: 'ACTIVE' as EmploymentStatus,
  reportingManagerId: '',
  address: '',
  userId: '',
};

export function HrEmployeesTab({
  token,
  canWrite,
  selfServiceOnly,
  departments,
  allEmployees,
  orgMembers,
  onDirectoryChanged,
}: HrEmployeesTabProps) {
  const { notify } = useToast();
  const pageSize = 10;

  const [employees, setEmployees] = useState<HrEmployee[]>([]);
  const [totalItems, setTotalItems] = useState(0);
  const [page, setPage] = useState(1);
  const [search, setSearch] = useState('');
  const [departmentFilter, setDepartmentFilter] = useState('');
  const [designationFilter, setDesignationFilter] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [selectedEmployee, setSelectedEmployee] = useState<HrEmployee | null>(null);
  const [formOpen, setFormOpen] = useState(false);
  const [editingEmployee, setEditingEmployee] = useState<HrEmployee | null>(null);
  const [form, setForm] = useState(INITIAL_FORM);
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const [deactivateTarget, setDeactivateTarget] = useState<HrEmployee | null>(null);
  const [deactivating, setDeactivating] = useState(false);

  const loadEmployees = useCallback(async () => {
    if (!token) return;
    setLoading(true);
    setError(null);
    const params = new URLSearchParams({
      page: String(page),
      pageSize: String(pageSize),
    });
    if (search.trim()) params.set('search', search.trim());
    if (departmentFilter) params.set('departmentId', departmentFilter);
    if (designationFilter.trim()) params.set('designation', designationFilter.trim());
    if (statusFilter) params.set('status', statusFilter);

    try {
      const res = await apiRequest<PagedResponse<HrEmployee>>(
        `/api/v1/hr/employees?${params.toString()}`,
        { token },
      );
      setEmployees(res.data);
      setTotalItems(res.totalItems);
    } catch (err) {
      setError(
        err instanceof ApiError ? err.message : 'Unable to load employees.',
      );
    } finally {
      setLoading(false);
    }
  }, [departmentFilter, designationFilter, page, search, statusFilter, token]);

  useEffect(() => {
    void loadEmployees();
  }, [loadEmployees]);

  const openCreate = () => {
    setEditingEmployee(null);
    setForm({
      ...INITIAL_FORM,
      departmentId: departments[0]?.id ?? '',
    });
    setFormError(null);
    setFormOpen(true);
  };

  const openEdit = (emp: HrEmployee) => {
    setEditingEmployee(emp);
    setForm({
      firstName: emp.firstName,
      lastName: emp.lastName,
      email: emp.email,
      phone: emp.phone,
      departmentId: emp.departmentId,
      designation: emp.designation,
      employmentType: emp.employmentType,
      dateOfJoining: emp.dateOfJoining,
      employmentStatus: emp.employmentStatus,
      reportingManagerId: emp.reportingManagerId ?? '',
      address: emp.address ?? '',
      userId: emp.userId ?? '',
    });
    setFormError(null);
    setFormOpen(true);
  };

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    if (!token) return;
    if (
      !form.firstName.trim() ||
      !form.lastName.trim() ||
      !form.email.trim() ||
      !form.phone.trim() ||
      !form.departmentId ||
      !form.designation.trim() ||
      !form.dateOfJoining
    ) {
      setFormError('Please fill in all required employee fields.');
      return;
    }

    setSubmitting(true);
    setFormError(null);
    try {
      if (editingEmployee) {
        await apiRequest<HrEmployee>(`/api/v1/hr/employees/${editingEmployee.id}`, {
          method: 'PATCH',
          token,
          body: {
            firstName: form.firstName.trim(),
            lastName: form.lastName.trim(),
            email: form.email.trim(),
            phone: form.phone.trim(),
            departmentId: form.departmentId,
            designation: form.designation.trim(),
            employmentType: form.employmentType,
            dateOfJoining: form.dateOfJoining,
            employmentStatus: form.employmentStatus,
            reportingManagerId: form.reportingManagerId || null,
            clearReportingManager: !form.reportingManagerId,
            address: form.address.trim(),
            userId: form.userId || null,
            clearUserId: !form.userId,
          },
        });
        notify({ variant: 'success', title: 'Employee updated' });
      } else {
        await apiRequest<HrEmployee>('/api/v1/hr/employees', {
          method: 'POST',
          token,
          body: {
            firstName: form.firstName.trim(),
            lastName: form.lastName.trim(),
            email: form.email.trim(),
            phone: form.phone.trim(),
            departmentId: form.departmentId,
            designation: form.designation.trim(),
            employmentType: form.employmentType,
            dateOfJoining: form.dateOfJoining,
            reportingManagerId: form.reportingManagerId || null,
            address: form.address.trim() || null,
            userId: form.userId || null,
          },
        });
        notify({ variant: 'success', title: 'Employee added' });
      }
      setFormOpen(false);
      await Promise.all([loadEmployees(), onDirectoryChanged()]);
    } catch (err) {
      setFormError(
        err instanceof ApiError ? err.message : 'Unable to save employee.',
      );
    } finally {
      setSubmitting(false);
    }
  };

  const handleDeactivate = async () => {
    if (!token || !deactivateTarget) return;
    setDeactivating(true);
    try {
      await apiRequest<HrEmployee>(
        `/api/v1/hr/employees/${deactivateTarget.id}/deactivate`,
        { method: 'POST', token },
      );
      notify({
        variant: 'info',
        title: 'Employee deactivated',
        description: `${deactivateTarget.fullName} is now marked inactive.`,
      });
      setDeactivateTarget(null);
      await Promise.all([loadEmployees(), onDirectoryChanged()]);
    } catch (err) {
      notify({
        variant: 'error',
        title: 'Deactivation failed',
        description:
          err instanceof ApiError ? err.message : 'Unable to deactivate employee.',
      });
    } finally {
      setDeactivating(false);
    }
  };

  const status: ScreenStatus = loading
    ? 'loading'
    : error
      ? 'error'
      : employees.length === 0
        ? 'empty'
        : 'success';

  const columns: TableColumn<HrEmployee>[] = [
    {
      key: 'code',
      header: 'Employee ID',
      render: (row) => <strong>{row.employeeCode}</strong>,
    },
    {
      key: 'name',
      header: 'Employee',
      render: (row) => (
        <div>
          <strong>{row.fullName}</strong>
          <div style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>
            {row.email}
          </div>
        </div>
      ),
    },
    {
      key: 'department',
      header: 'Department',
      render: (row) => row.departmentName,
    },
    {
      key: 'designation',
      header: 'Designation',
      render: (row) => row.designation,
    },
    {
      key: 'type',
      header: 'Type',
      render: (row) => EMPLOYMENT_TYPE_LABELS[row.employmentType],
    },
    {
      key: 'status',
      header: 'Status',
      render: (row) => (
        <Badge variant={getEmploymentStatusVariant(row.employmentStatus)}>
          {EMPLOYMENT_STATUS_LABELS[row.employmentStatus]}
        </Badge>
      ),
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
            onClick={() => setSelectedEmployee(row)}
          >
            View
          </Button>
          {canWrite ? (
            <>
              <Button
                variant="ghost"
                size="sm"
                leftIcon={<Pencil size={14} aria-hidden="true" />}
                onClick={() => openEdit(row)}
              >
                Edit
              </Button>
              {row.employmentStatus === 'ACTIVE' ? (
                <Button
                  variant="ghost"
                  size="sm"
                  leftIcon={<UserX size={14} aria-hidden="true" />}
                  onClick={() => setDeactivateTarget(row)}
                >
                  Deactivate
                </Button>
              ) : null}
            </>
          ) : null}
        </div>
      ),
    },
  ];

  const managerCandidates = allEmployees.filter(
    (emp) => !editingEmployee || emp.id !== editingEmployee.id,
  );

  return (
    <div className="foundation-stack">
      <Card>
        <CardContent className="foundation-stack">
          <div
            className="foundation-row"
            style={{ alignItems: 'flex-end', justifyContent: 'space-between' }}
          >
            {!selfServiceOnly ? (
              <div className="foundation-row" style={{ alignItems: 'flex-end' }}>
                <FormField
                  label="Search"
                  placeholder="Name, ID, email, designation..."
                  value={search}
                  onChange={(e) => {
                    setSearch(e.target.value);
                    setPage(1);
                  }}
                />
                <Select
                  label="Department"
                  value={departmentFilter}
                  onChange={(e) => {
                    setDepartmentFilter(e.target.value);
                    setPage(1);
                  }}
                  options={[
                    { value: '', label: 'All departments' },
                    ...departments.map((d) => ({
                      value: d.id,
                      label: d.name,
                    })),
                  ]}
                />
                <FormField
                  label="Designation"
                  placeholder="Exact designation"
                  value={designationFilter}
                  onChange={(e) => {
                    setDesignationFilter(e.target.value);
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
                    { value: 'ACTIVE', label: 'Active' },
                    { value: 'INACTIVE', label: 'Inactive' },
                  ]}
                />
              </div>
            ) : (
              <div style={{ fontSize: 'var(--text-sm)', color: 'var(--color-text-secondary)' }}>
                Your linked employee profile in this organization.
              </div>
            )}

            {canWrite ? (
              <Button
                variant="primary"
                leftIcon={<Plus size={16} aria-hidden="true" />}
                onClick={openCreate}
              >
                Add Employee
              </Button>
            ) : null}
          </div>

          <StateBoundary
            status={status}
            errorTitle="Could not load employees"
            errorMessage={error ?? 'Failed to fetch employees.'}
            onRetry={() => void loadEmployees()}
            emptyTitle={
              selfServiceOnly
                ? 'No employee profile linked'
                : 'No matching employees'
            }
            emptyDescription={
              selfServiceOnly
                ? 'Ask an administrator to link your user account to an employee record.'
                : 'Try adjusting your filters or add a new employee.'
            }
          >
            <Table
              caption="Employee directory"
              columns={columns}
              rows={employees}
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

      {/* Employee Detail Profile Dialog */}
      <Dialog
        open={Boolean(selectedEmployee)}
        onClose={() => setSelectedEmployee(null)}
        title={
          selectedEmployee
            ? `${selectedEmployee.fullName} · ${selectedEmployee.employeeCode}`
            : 'Employee Profile'
        }
        description={
          selectedEmployee
            ? `${selectedEmployee.designation} · ${selectedEmployee.departmentName}`
            : undefined
        }
        size="lg"
      >
        {selectedEmployee ? (
          <div className="foundation-stack">
            <div className="foundation-grid">
              <div className="foundation-col-6">
                <div style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>
                  Email
                </div>
                <div>{selectedEmployee.email}</div>
              </div>
              <div className="foundation-col-6">
                <div style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>
                  Phone
                </div>
                <div>{selectedEmployee.phone}</div>
              </div>
              <div className="foundation-col-6">
                <div style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>
                  Employment Type
                </div>
                <div>{EMPLOYMENT_TYPE_LABELS[selectedEmployee.employmentType]}</div>
              </div>
              <div className="foundation-col-6">
                <div style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>
                  Employment Status
                </div>
                <div>
                  <Badge variant={getEmploymentStatusVariant(selectedEmployee.employmentStatus)}>
                    {EMPLOYMENT_STATUS_LABELS[selectedEmployee.employmentStatus]}
                  </Badge>
                </div>
              </div>
              <div className="foundation-col-6">
                <div style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>
                  Date of Joining
                </div>
                <div>{formatIsoDate(selectedEmployee.dateOfJoining)}</div>
              </div>
              <div className="foundation-col-6">
                <div style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>
                  Reporting Manager
                </div>
                <div>{selectedEmployee.reportingManagerName ?? 'None'}</div>
              </div>
              <div className="foundation-col-12">
                <div style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>
                  Address
                </div>
                <div>{selectedEmployee.address || '—'}</div>
              </div>
              <div className="foundation-col-6">
                <div style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>
                  Created
                </div>
                <div>{formatTimestamp(selectedEmployee.createdAt)}</div>
              </div>
              <div className="foundation-col-6">
                <div style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>
                  Last Updated
                </div>
                <div>{formatTimestamp(selectedEmployee.updatedAt)}</div>
              </div>
            </div>
            <div className="foundation-row" style={{ justifyContent: 'flex-end' }}>
              {canWrite ? (
                <Button
                  variant="secondary"
                  onClick={() => {
                    const emp = selectedEmployee;
                    setSelectedEmployee(null);
                    openEdit(emp);
                  }}
                >
                  Edit Employee
                </Button>
              ) : null}
              <Button variant="primary" onClick={() => setSelectedEmployee(null)}>
                Close
              </Button>
            </div>
          </div>
        ) : null}
      </Dialog>

      {/* Add / Edit Employee Dialog */}
      <Dialog
        open={formOpen}
        onClose={() => setFormOpen(false)}
        title={editingEmployee ? `Edit ${editingEmployee.fullName}` : 'Add Employee'}
        description="Employee records do not automatically create user login accounts."
        size="lg"
      >
        <form onSubmit={handleSubmit} className="foundation-stack" noValidate>
          {formError ? <Alert variant="error" title={formError} /> : null}
          {departments.length === 0 ? (
            <Alert
              variant="warning"
              title="No departments available"
              description="Create a department in the Departments tab before adding an employee."
            />
          ) : null}

          <div className="foundation-grid">
            <div className="foundation-col-6">
              <FormField
                label="First Name"
                required
                value={form.firstName}
                onChange={(e) => setForm({ ...form, firstName: e.target.value })}
              />
            </div>
            <div className="foundation-col-6">
              <FormField
                label="Last Name"
                required
                value={form.lastName}
                onChange={(e) => setForm({ ...form, lastName: e.target.value })}
              />
            </div>
            <div className="foundation-col-6">
              <FormField
                label="Email"
                type="email"
                required
                value={form.email}
                onChange={(e) => setForm({ ...form, email: e.target.value })}
              />
            </div>
            <div className="foundation-col-6">
              <FormField
                label="Phone Number"
                required
                value={form.phone}
                onChange={(e) => setForm({ ...form, phone: e.target.value })}
              />
            </div>
            <div className="foundation-col-6">
              <Select
                label="Department"
                required
                value={form.departmentId}
                onChange={(e) => setForm({ ...form, departmentId: e.target.value })}
                placeholder="Select department"
                options={departments.map((d) => ({ value: d.id, label: d.name }))}
              />
            </div>
            <div className="foundation-col-6">
              <FormField
                label="Designation"
                required
                value={form.designation}
                onChange={(e) => setForm({ ...form, designation: e.target.value })}
                placeholder="e.g. Software Engineer"
              />
            </div>
            <div className="foundation-col-6">
              <Select
                label="Employment Type"
                required
                value={form.employmentType}
                onChange={(e) =>
                  setForm({ ...form, employmentType: e.target.value as EmploymentType })
                }
                options={[
                  { value: 'FULL_TIME', label: 'Full-time' },
                  { value: 'PART_TIME', label: 'Part-time' },
                  { value: 'CONTRACT', label: 'Contract' },
                ]}
              />
            </div>
            <div className="foundation-col-6">
              <FormField
                label="Date of Joining"
                type="date"
                required
                value={form.dateOfJoining}
                onChange={(e) => setForm({ ...form, dateOfJoining: e.target.value })}
              />
            </div>
            {editingEmployee ? (
              <div className="foundation-col-6">
                <Select
                  label="Employment Status"
                  value={form.employmentStatus}
                  onChange={(e) =>
                    setForm({
                      ...form,
                      employmentStatus: e.target.value as EmploymentStatus,
                    })
                  }
                  options={[
                    { value: 'ACTIVE', label: 'Active' },
                    { value: 'INACTIVE', label: 'Inactive' },
                  ]}
                />
              </div>
            ) : null}
            <div className="foundation-col-6">
              <Select
                label="Reporting Manager (optional)"
                value={form.reportingManagerId}
                onChange={(e) =>
                  setForm({ ...form, reportingManagerId: e.target.value })
                }
                options={[
                  { value: '', label: 'None' },
                  ...managerCandidates.map((m) => ({
                    value: m.id,
                    label: `${m.fullName} (${m.employeeCode})`,
                  })),
                ]}
              />
            </div>
            {orgMembers.length > 0 ? (
              <div className="foundation-col-6">
                <Select
                  label="Linked SmartBiz User (optional)"
                  value={form.userId}
                  onChange={(e) => setForm({ ...form, userId: e.target.value })}
                  options={[
                    { value: '', label: 'No linked user account' },
                    ...orgMembers.map((member) => ({
                      value: member.userId,
                      label: `${member.displayName} (${member.email})`,
                    })),
                  ]}
                />
              </div>
            ) : null}
            <div className="foundation-col-12">
              <TextareaField
                label="Address (optional)"
                value={form.address}
                onChange={(e) => setForm({ ...form, address: e.target.value })}
              />
            </div>
          </div>

          <div className="foundation-row" style={{ justifyContent: 'flex-end' }}>
            <Button
              type="button"
              variant="secondary"
              onClick={() => setFormOpen(false)}
            >
              Cancel
            </Button>
            <Button
              type="submit"
              variant="primary"
              loading={submitting}
              disabled={departments.length === 0}
            >
              {editingEmployee ? 'Save Changes' : 'Create Employee'}
            </Button>
          </div>
        </form>
      </Dialog>

      {/* Deactivate Confirmation Dialog */}
      <Dialog
        open={Boolean(deactivateTarget)}
        onClose={() => setDeactivateTarget(null)}
        title="Deactivate Employee"
        description={
          deactivateTarget
            ? `Mark ${deactivateTarget.fullName} (${deactivateTarget.employeeCode}) as Inactive? Historical attendance and leave records will be preserved.`
            : undefined
        }
      >
        <div className="foundation-row" style={{ justifyContent: 'flex-end' }}>
          <Button variant="secondary" onClick={() => setDeactivateTarget(null)}>
            Cancel
          </Button>
          <Button
            variant="danger"
            loading={deactivating}
            onClick={() => void handleDeactivate()}
          >
            Deactivate
          </Button>
        </div>
      </Dialog>
    </div>
  );
}

