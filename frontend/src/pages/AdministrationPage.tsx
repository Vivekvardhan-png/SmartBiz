import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { Plus, Trash2 } from 'lucide-react';
import { useAuth, type OrganizationSummary } from '../app/authContext';
import type { UserRole } from '../app/navigation';
import {
  Alert,
  Badge,
  Button,
  Card,
  CardContent,
  CardFooter,
  CardHeader,
  Dialog,
  FormField,
  PageHeader,
  Select,
  StateBoundary,
  Table,
  useToast,
  type ScreenStatus,
  type TableColumn,
} from '../components/ui';
import { apiRequest, ApiError } from '../lib/apiClient';

interface OrganizationMember {
  id: string;
  userId: string;
  email: string;
  displayName: string;
  role: UserRole;
  joinedAt: string;
}

export function AdministrationPage() {
  const { token, session, hasPermission, switchOrganization, refreshSession } =
    useAuth();
  const { notify } = useToast();

  const canReadMembers = hasPermission('MEMBERS_READ');
  const canManageMembers = hasPermission('MEMBERS_MANAGE');
  const isOwner = session?.activeOrganization.role === 'OWNER';

  const [members, setMembers] = useState<OrganizationMember[]>([]);
  const [membersStatus, setMembersStatus] = useState<ScreenStatus>('loading');
  const [membersError, setMembersError] = useState<string | null>(null);

  const [addOpen, setAddOpen] = useState(false);
  const [newEmail, setNewEmail] = useState('');
  const [newName, setNewName] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [newRole, setNewRole] = useState<UserRole>('EMPLOYEE');
  const [addSubmitting, setAddSubmitting] = useState(false);
  const [addError, setAddError] = useState<string | null>(null);

  const [newOrgName, setNewOrgName] = useState('');
  const [orgSubmitting, setOrgSubmitting] = useState(false);
  const [orgError, setOrgError] = useState<string | null>(null);

  const [memberToRemove, setMemberToRemove] =
    useState<OrganizationMember | null>(null);
  const [removing, setRemoving] = useState(false);

  const loadMembers = useCallback(async () => {
    if (!token || !canReadMembers) {
      setMembersStatus('empty');
      return;
    }

    setMembersStatus('loading');
    setMembersError(null);
    try {
      const list = await apiRequest<OrganizationMember[]>(
        '/api/v1/organizations/current/members',
        { token },
      );
      setMembers(list);
      setMembersStatus(list.length === 0 ? 'empty' : 'success');
    } catch (err) {
      setMembersError(
        err instanceof ApiError
          ? err.message
          : 'Failed to load organization members.',
      );
      setMembersStatus('error');
    }
  }, [canReadMembers, token]);

  useEffect(() => {
    void loadMembers();
  }, [loadMembers]);

  if (!canReadMembers) {
    return (
      <div className="foundation-stack">
        <PageHeader
          title="Administration"
          subtitle="Organization settings, membership roles, and tenant isolation."
          breadcrumbs={[
            { label: 'Workspace', to: '/dashboard' },
            { label: 'Administration' },
          ]}
          badge={<Badge variant="warning">Restricted</Badge>}
        />
        <Alert
          variant="warning"
          title="Insufficient permissions"
          description="Only organization owners, administrators, or managers may inspect administration settings."
        />
      </div>
    );
  }

  const handleAddMember = async (event: FormEvent) => {
    event.preventDefault();
    if (!token) return;

    setAddError(null);
    setAddSubmitting(true);
    try {
      await apiRequest<OrganizationMember>(
        '/api/v1/organizations/current/members',
        {
          method: 'POST',
          token,
          body: {
            email: newEmail.trim(),
            displayName: newName.trim() || undefined,
            password: newPassword || undefined,
            role: newRole,
          },
        },
      );
      setAddOpen(false);
      setNewEmail('');
      setNewName('');
      setNewPassword('');
      setNewRole('EMPLOYEE');
      notify({
        variant: 'success',
        title: 'Member added',
        description: 'Organization membership has been updated.',
      });
      await loadMembers();
    } catch (err) {
      setAddError(
        err instanceof ApiError ? err.message : 'Unable to add member.',
      );
    } finally {
      setAddSubmitting(false);
    }
  };

  const handleRoleChange = async (member: OrganizationMember, nextRole: UserRole) => {
    if (!token) return;
    try {
      await apiRequest<OrganizationMember>(
        `/api/v1/organizations/current/members/${member.id}`,
        {
          method: 'PATCH',
          token,
          body: { role: nextRole },
        },
      );
      notify({
        variant: 'success',
        title: 'Role updated',
        description: `${member.email} is now ${nextRole}.`,
      });
      await loadMembers();
    } catch (err) {
      notify({
        variant: 'error',
        title: 'Role update rejected',
        description:
          err instanceof ApiError ? err.message : 'Unable to update role.',
      });
    }
  };

  const handleRemoveMember = async () => {
    if (!token || !memberToRemove) return;
    setRemoving(true);
    try {
      await apiRequest<void>(
        `/api/v1/organizations/current/members/${memberToRemove.id}`,
        {
          method: 'DELETE',
          token,
        },
      );
      notify({
        variant: 'success',
        title: 'Member removed',
        description: `Access revoked for ${memberToRemove.email}.`,
      });
      setMemberToRemove(null);
      await loadMembers();
    } catch (err) {
      notify({
        variant: 'error',
        title: 'Could not remove member',
        description:
          err instanceof ApiError ? err.message : 'Unable to remove member.',
      });
    } finally {
      setRemoving(false);
    }
  };

  const handleCreateOrg = async (event: FormEvent) => {
    event.preventDefault();
    if (!token || !newOrgName.trim()) {
      setOrgError('Organization name is required.');
      return;
    }

    setOrgError(null);
    setOrgSubmitting(true);
    try {
      const created = await apiRequest<OrganizationSummary>(
        '/api/v1/organizations',
        {
          method: 'POST',
          token,
          body: { name: newOrgName.trim() },
        },
      );
      setNewOrgName('');
      await switchOrganization(created.id);
      await refreshSession();
      notify({
        variant: 'success',
        title: 'Organization created',
        description: `Switched active workspace to ${created.name}.`,
      });
    } catch (err) {
      setOrgError(
        err instanceof ApiError
          ? err.message
          : 'Unable to create organization.',
      );
    } finally {
      setOrgSubmitting(false);
    }
  };

  const assignableRoles = [
    ...(isOwner ? [{ value: 'OWNER', label: 'Owner' }] : []),
    { value: 'ADMIN', label: 'Administrator' },
    { value: 'MANAGER', label: 'Manager' },
    { value: 'EMPLOYEE', label: 'Employee' },
  ];

  const columns: TableColumn<OrganizationMember>[] = [
    {
      key: 'member',
      header: 'Member',
      render: (row) => (
        <div>
          <strong>{row.displayName || row.email}</strong>
          <div
            style={{
              fontSize: 'var(--text-xs)',
              color: 'var(--color-text-secondary)',
            }}
          >
            {row.email}
          </div>
        </div>
      ),
    },
    {
      key: 'role',
      header: 'Role',
      render: (row) =>
        canManageMembers && (isOwner || row.role !== 'OWNER') ? (
          <Select
            label={`Role for ${row.email}`}
            aria-label={`Role for ${row.email}`}
            value={row.role}
            options={assignableRoles}
            onChange={(event) =>
              void handleRoleChange(row, event.target.value as UserRole)
            }
          />
        ) : (
          <Badge variant="info">{row.role}</Badge>
        ),
    },
    {
      key: 'joinedAt',
      header: 'Joined',
      render: (row) => new Date(row.joinedAt).toLocaleDateString(),
    },
    ...(canManageMembers
      ? [
          {
            key: 'actions',
            header: 'Actions',
            align: 'right' as const,
            render: (row: OrganizationMember) =>
              isOwner || row.role !== 'OWNER' ? (
                <Button
                  variant="ghost"
                  size="sm"
                  aria-label={`Remove ${row.email}`}
                  onClick={() => setMemberToRemove(row)}
                >
                  <Trash2 size={15} aria-hidden="true" />
                </Button>
              ) : null,
          },
        ]
      : []),
  ];

  return (
    <div className="foundation-stack">
      <PageHeader
        title="Administration"
        subtitle={`Managing organization "${session?.activeOrganization.name ?? ''}" (${session?.activeOrganization.slug ?? ''}).`}
        breadcrumbs={[
          { label: 'Workspace', to: '/dashboard' },
          { label: 'Administration' },
        ]}
        badge={<Badge variant="success">Active · Prompt 02</Badge>}
        actions={
          canManageMembers ? (
            <Button
              variant="primary"
              leftIcon={<Plus size={16} aria-hidden="true" />}
              onClick={() => setAddOpen(true)}
            >
              Add Member
            </Button>
          ) : null
        }
      />

      <div className="foundation-grid">
        <div className="foundation-col-12">
          <Card>
            <CardHeader
              title="Organization Members"
              description="Membership and role assignments enforced server-side."
            />
            <CardContent>
              <StateBoundary
                status={membersStatus}
                errorTitle="Could not load members"
                errorMessage={membersError ?? undefined}
                onRetry={() => void loadMembers()}
                emptyTitle="No members found"
                emptyDescription="Add team members to collaborate inside this organization."
              >
                <Table
                  caption="Organization members"
                  columns={columns}
                  rows={members}
                  getRowKey={(row) => row.id}
                />
              </StateBoundary>
            </CardContent>
          </Card>
        </div>

        <div className="foundation-col-6">
          <Card>
            <form onSubmit={handleCreateOrg} noValidate>
              <CardHeader
                title="Create Additional Organization"
                description="Creates a new isolated tenant with you as OWNER."
              />
              <CardContent className="foundation-stack">
                {orgError ? (
                  <Alert
                    variant="error"
                    title="Organization creation failed"
                    description={orgError}
                  />
                ) : null}
                <FormField
                  label="New Organization Name"
                  required
                  placeholder="Northwind Trading"
                  value={newOrgName}
                  onChange={(event) => setNewOrgName(event.target.value)}
                />
              </CardContent>
              <CardFooter>
                <Button
                  type="submit"
                  variant="secondary"
                  loading={orgSubmitting}
                >
                  Create &amp; Switch
                </Button>
              </CardFooter>
            </form>
          </Card>
        </div>
      </div>

      <Dialog
        open={addOpen}
        onClose={() => setAddOpen(false)}
        title="Add Organization Member"
        description="Attach an existing user by email or provision a new member account."
      >
        <form onSubmit={handleAddMember} className="foundation-stack" noValidate>
          {addError ? (
            <Alert
              variant="error"
              title="Could not add member"
              description={addError}
            />
          ) : null}
          <FormField
            label="Email"
            type="email"
            required
            placeholder="teammate@company.com"
            value={newEmail}
            onChange={(event) => setNewEmail(event.target.value)}
          />
          <FormField
            label="Display Name (for new accounts)"
            placeholder="Jordan Lee"
            value={newName}
            onChange={(event) => setNewName(event.target.value)}
          />
          <FormField
            label="Initial Password (for new accounts)"
            type="password"
            hint="Required if the user does not already exist (10+ chars, upper/lower/number)."
            value={newPassword}
            onChange={(event) => setNewPassword(event.target.value)}
          />
          <Select
            label="Role"
            value={newRole}
            options={assignableRoles}
            onChange={(event) => setNewRole(event.target.value as UserRole)}
          />
          <div className="foundation-row" style={{ justifyContent: 'flex-end' }}>
            <Button variant="secondary" onClick={() => setAddOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" variant="primary" loading={addSubmitting}>
              Add Member
            </Button>
          </div>
        </form>
      </Dialog>

      <Dialog
        open={Boolean(memberToRemove)}
        onClose={() => setMemberToRemove(null)}
        title="Remove Member"
        description="This immediately revokes all active sessions for the user in this organization."
        footer={
          <>
            <Button
              variant="secondary"
              onClick={() => setMemberToRemove(null)}
            >
              Cancel
            </Button>
            <Button
              variant="danger"
              loading={removing}
              onClick={() => void handleRemoveMember()}
            >
              Remove Member
            </Button>
          </>
        }
      >
        <p style={{ color: 'var(--color-text-secondary)' }}>
          Remove <strong>{memberToRemove?.email}</strong> from{' '}
          <strong>{session?.activeOrganization.name}</strong>?
        </p>
      </Dialog>
    </div>
  );
}

