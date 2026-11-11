import { useState, type FormEvent } from 'react';
import { Pencil, Plus, Trash2 } from 'lucide-react';
import {
  Alert,
  Badge,
  Button,
  Card,
  CardContent,
  Dialog,
  FormField,
  Select,
  StateBoundary,
  Table,
  TextareaField,
  useToast,
  type ScreenStatus,
  type TableColumn,
} from '../ui';
import { apiRequest, ApiError } from '../../lib/apiClient';
import type { HrDepartment, HrEmployee } from '../../lib/hrTypes';
import { formatIsoDate } from './hrFormatters';

interface HrDepartmentsTabProps {
  token: string | null;
  canWrite: boolean;
  departments: HrDepartment[];
  employees: HrEmployee[];
  loading: boolean;
  error: string | null;
  onRefresh: () => Promise<void>;
}

export function HrDepartmentsTab({
  token,
  canWrite,
  departments,
  employees,
  loading,
  error,
  onRefresh,
}: HrDepartmentsTabProps) {
  const { notify } = useToast();

  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<HrDepartment | null>(null);
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [headEmployeeId, setHeadEmployeeId] = useState('');
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const [deleteTarget, setDeleteTarget] = useState<HrDepartment | null>(null);
  const [deleteError, setDeleteError] = useState<string | null>(null);
  const [deleting, setDeleting] = useState(false);

  const openCreate = () => {
    setEditing(null);
    setName('');
    setDescription('');
    setHeadEmployeeId('');
    setFormError(null);
    setFormOpen(true);
  };

  const openEdit = (dept: HrDepartment) => {
    setEditing(dept);
    setName(dept.name);
    setDescription(dept.description ?? '');
    setHeadEmployeeId(dept.headEmployeeId ?? '');
    setFormError(null);
    setFormOpen(true);
  };

  const handleSave = async (event: FormEvent) => {
    event.preventDefault();
    if (!token) return;
    if (!name.trim()) {
      setFormError('Department name is required.');
      return;
    }

    setSubmitting(true);
    setFormError(null);
    try {
      if (editing) {
        await apiRequest<HrDepartment>(`/api/v1/hr/departments/${editing.id}`, {
          method: 'PATCH',
          token,
          body: {
            name: name.trim(),
            description: description.trim(),
            headEmployeeId: headEmployeeId || null,
            clearHeadEmployee: !headEmployeeId,
          },
        });
        notify({ variant: 'success', title: 'Department updated' });
      } else {
        await apiRequest<HrDepartment>('/api/v1/hr/departments', {
          method: 'POST',
          token,
          body: {
            name: name.trim(),
            description: description.trim() || null,
            headEmployeeId: headEmployeeId || null,
          },
        });
        notify({ variant: 'success', title: 'Department created' });
      }
      setFormOpen(false);
      await onRefresh();
    } catch (err) {
      setFormError(
        err instanceof ApiError ? err.message : 'Unable to save department.',
      );
    } finally {
      setSubmitting(false);
    }
  };

  const handleDelete = async () => {
    if (!token || !deleteTarget) return;
    setDeleting(true);
    setDeleteError(null);
    try {
      await apiRequest<void>(`/api/v1/hr/departments/${deleteTarget.id}`, {
        method: 'DELETE',
        token,
      });
      notify({ variant: 'success', title: 'Department deleted' });
      setDeleteTarget(null);
      await onRefresh();
    } catch (err) {
      setDeleteError(
        err instanceof ApiError ? err.message : 'Unable to delete department.',
      );
    } finally {
      setDeleting(false);
    }
  };

  const status: ScreenStatus = loading
    ? 'loading'
    : error
      ? 'error'
      : departments.length === 0
        ? 'empty'
        : 'success';

  const columns: TableColumn<HrDepartment>[] = [
    {
      key: 'name',
      header: 'Department',
      render: (row) => (
        <div>
          <strong>{row.name}</strong>
          {row.description ? (
            <div
              style={{
                fontSize: 'var(--text-xs)',
                color: 'var(--color-text-secondary)',
              }}
            >
              {row.description}
            </div>
          ) : null}
        </div>
      ),
    },
    {
      key: 'head',
      header: 'Department Head',
      render: (row) => row.headEmployeeName ?? 'Unassigned',
    },
    {
      key: 'count',
      header: 'Employees',
      align: 'right',
      render: (row) => <Badge variant="neutral">{row.employeeCount}</Badge>,
    },
    {
      key: 'createdAt',
      header: 'Created',
      render: (row) => formatIsoDate(row.createdAt),
    },
    ...(canWrite
      ? [
          {
            key: 'actions',
            header: 'Actions',
            align: 'right' as const,
            render: (row: HrDepartment) => (
              <div className="foundation-row" style={{ justifyContent: 'flex-end' }}>
                <Button
                  variant="ghost"
                  size="sm"
                  leftIcon={<Pencil size={14} aria-hidden="true" />}
                  onClick={() => openEdit(row)}
                >
                  Edit
                </Button>
                <Button
                  variant="ghost"
                  size="sm"
                  leftIcon={<Trash2 size={14} aria-hidden="true" />}
                  onClick={() => {
                    setDeleteError(null);
                    setDeleteTarget(row);
                  }}
                >
                  Delete
                </Button>
              </div>
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
            style={{ justifyContent: 'space-between', alignItems: 'center' }}
          >
            <div style={{ fontSize: 'var(--text-sm)', color: 'var(--color-text-secondary)' }}>
              Organize teams, assign department heads, and track headcount per department.
            </div>
            {canWrite ? (
              <Button
                variant="primary"
                leftIcon={<Plus size={16} aria-hidden="true" />}
                onClick={openCreate}
              >
                New Department
              </Button>
            ) : null}
          </div>

          <StateBoundary
            status={status}
            errorTitle="Could not load departments"
            errorMessage={error ?? 'Failed to fetch departments.'}
            onRetry={() => void onRefresh()}
            emptyTitle="No departments yet"
            emptyDescription="Create your first department before adding employees."
            emptyAction={
              canWrite ? (
                <Button variant="primary" onClick={openCreate}>
                  New Department
                </Button>
              ) : undefined
            }
          >
            <Table
              caption="Organization departments"
              columns={columns}
              rows={departments}
              getRowKey={(row) => row.id}
            />
          </StateBoundary>
        </CardContent>
      </Card>

      <Dialog
        open={formOpen}
        onClose={() => setFormOpen(false)}
        title={editing ? 'Edit Department' : 'New Department'}
        description="Departments group employees and track team leadership."
      >
        <form onSubmit={handleSave} className="foundation-stack" noValidate>
          {formError ? <Alert variant="error" title={formError} /> : null}
          <FormField
            label="Department Name"
            required
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="e.g. Engineering"
          />
          <TextareaField
            label="Description"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="Team scope and responsibilities..."
          />
          <Select
            label="Department Head (optional)"
            value={headEmployeeId}
            onChange={(e) => setHeadEmployeeId(e.target.value)}
            options={[
              { value: '', label: 'Unassigned' },
              ...employees
                .filter((emp) => emp.employmentStatus === 'ACTIVE')
                .map((emp) => ({
                  value: emp.id,
                  label: `${emp.fullName} (${emp.employeeCode})`,
                })),
            ]}
          />
          <div className="foundation-row" style={{ justifyContent: 'flex-end' }}>
            <Button
              type="button"
              variant="secondary"
              onClick={() => setFormOpen(false)}
            >
              Cancel
            </Button>
            <Button type="submit" variant="primary" loading={submitting}>
              {editing ? 'Save Changes' : 'Create Department'}
            </Button>
          </div>
        </form>
      </Dialog>

      <Dialog
        open={Boolean(deleteTarget)}
        onClose={() => setDeleteTarget(null)}
        title="Delete Department"
        description={
          deleteTarget
            ? `Delete department "${deleteTarget.name}"? Departments with assigned employees cannot be deleted.`
            : undefined
        }
      >
        <div className="foundation-stack">
          {deleteError ? <Alert variant="error" title={deleteError} /> : null}
          <div className="foundation-row" style={{ justifyContent: 'flex-end' }}>
            <Button variant="secondary" onClick={() => setDeleteTarget(null)}>
              Cancel
            </Button>
            <Button
              variant="danger"
              loading={deleting}
              onClick={() => void handleDelete()}
            >
              Delete Department
            </Button>
          </div>
        </div>
      </Dialog>
    </div>
  );
}
