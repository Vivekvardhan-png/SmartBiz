import { useCallback, useEffect, useState } from 'react';
import { useAuth } from '../app/authContext';
import { Alert, Badge, Button, PageHeader } from '../components/ui';
import { apiRequest, ApiError } from '../lib/apiClient';
import type { PagedResponse } from '../lib/crmTypes';
import type {
  HrDepartment,
  HrEmployee,
  MyEmployeeProfile,
} from '../lib/hrTypes';
import { HrOverviewTab } from '../components/hr/HrOverviewTab';
import {
  HrEmployeesTab,
  type OrgMemberOption,
} from '../components/hr/HrEmployeesTab';
import { HrDepartmentsTab } from '../components/hr/HrDepartmentsTab';
import { HrAttendanceTab } from '../components/hr/HrAttendanceTab';
import { HrLeaveTab } from '../components/hr/HrLeaveTab';

type HrTab = 'overview' | 'employees' | 'departments' | 'attendance' | 'leave';

export function HrPage() {
  const { token, hasPermission } = useAuth();
  const canReadHr = hasPermission('HR_READ');
  const canWriteHr = hasPermission('HR_WRITE');
  const canReviewLeave = hasPermission('HR_LEAVE_REVIEW');
  const canSelfService = hasPermission('HR_SELF_SERVICE');
  const canReadMembers = hasPermission('MEMBERS_READ');

  const selfServiceOnly = !canReadHr && canSelfService;

  const [activeTab, setActiveTab] = useState<HrTab>('overview');
  const [departments, setDepartments] = useState<HrDepartment[]>([]);
  const [departmentsLoading, setDepartmentsLoading] = useState(false);
  const [departmentsError, setDepartmentsError] = useState<string | null>(null);

  const [allEmployees, setAllEmployees] = useState<HrEmployee[]>([]);
  const [linkedEmployee, setLinkedEmployee] = useState<HrEmployee | null>(null);
  const [orgMembers, setOrgMembers] = useState<OrgMemberOption[]>([]);

  const loadLookups = useCallback(async () => {
    if (!token) return;

    if (canSelfService) {
      apiRequest<MyEmployeeProfile>('/api/v1/hr/me', { token })
        .then((res) => setLinkedEmployee(res.linked ? res.employee : null))
        .catch(() => setLinkedEmployee(null));
    }

    if (canReadHr) {
      setDepartmentsLoading(true);
      setDepartmentsError(null);
      apiRequest<HrDepartment[]>('/api/v1/hr/departments', { token })
        .then((list) => setDepartments(list))
        .catch((err) =>
          setDepartmentsError(
            err instanceof ApiError ? err.message : 'Unable to load departments.',
          ),
        )
        .finally(() => setDepartmentsLoading(false));

      apiRequest<PagedResponse<HrEmployee>>(
        '/api/v1/hr/employees?page=1&pageSize=100',
        { token },
      )
        .then((res) => setAllEmployees(res.data))
        .catch(() => setAllEmployees([]));
    }

    if (canWriteHr && canReadMembers) {
      apiRequest<OrgMemberOption[]>('/api/v1/organizations/current/members', {
        token,
      })
        .then((list) => setOrgMembers(list))
        .catch(() => setOrgMembers([]));
    }
  }, [canReadHr, canReadMembers, canSelfService, canWriteHr, token]);

  useEffect(() => {
    void loadLookups();
  }, [loadLookups]);

  if (!canReadHr && !canSelfService) {
    return (
      <div className="foundation-stack">
        <PageHeader
          title="Human Resources"
          subtitle="Employee directory, departments, daily attendance, and leave management."
          breadcrumbs={[
            { label: 'Workspace', to: '/dashboard' },
            { label: 'HR' },
          ]}
          badge={<Badge variant="warning">Restricted</Badge>}
        />
        <Alert
          variant="warning"
          title="Insufficient permissions"
          description="You do not have permission to access Human Resources workflows."
        />
      </div>
    );
  }

  return (
    <div className="foundation-stack">
      <PageHeader
        title="Human Resources"
        subtitle="Employee directory, department management, daily attendance tracking, and leave workflows."
        breadcrumbs={[
          { label: 'Workspace', to: '/dashboard' },
          { label: 'HR' },
        ]}
        badge={<Badge variant="success">Active</Badge>}
      />

      <div
        className="foundation-row"
        role="tablist"
        aria-label="HR views"
        style={{ flexWrap: 'wrap' }}
      >
        <Button
          role="tab"
          aria-selected={activeTab === 'overview'}
          variant={activeTab === 'overview' ? 'primary' : 'secondary'}
          onClick={() => setActiveTab('overview')}
        >
          Overview
        </Button>
        <Button
          role="tab"
          aria-selected={activeTab === 'employees'}
          variant={activeTab === 'employees' ? 'primary' : 'secondary'}
          onClick={() => setActiveTab('employees')}
        >
          Employees
        </Button>
        {canReadHr ? (
          <Button
            role="tab"
            aria-selected={activeTab === 'departments'}
            variant={activeTab === 'departments' ? 'primary' : 'secondary'}
            onClick={() => setActiveTab('departments')}
          >
            Departments
          </Button>
        ) : null}
        <Button
          role="tab"
          aria-selected={activeTab === 'attendance'}
          variant={activeTab === 'attendance' ? 'primary' : 'secondary'}
          onClick={() => setActiveTab('attendance')}
        >
          Attendance
        </Button>
        <Button
          role="tab"
          aria-selected={activeTab === 'leave'}
          variant={activeTab === 'leave' ? 'primary' : 'secondary'}
          onClick={() => setActiveTab('leave')}
        >
          Leave Management
        </Button>
      </div>

      {activeTab === 'overview' ? (
        <HrOverviewTab token={token} onNavigateTab={setActiveTab} />
      ) : null}

      {activeTab === 'employees' ? (
        <HrEmployeesTab
          token={token}
          canWrite={canWriteHr}
          selfServiceOnly={selfServiceOnly}
          departments={departments}
          allEmployees={allEmployees}
          orgMembers={orgMembers}
          onDirectoryChanged={loadLookups}
        />
      ) : null}

      {activeTab === 'departments' && canReadHr ? (
        <HrDepartmentsTab
          token={token}
          canWrite={canWriteHr}
          departments={departments}
          employees={allEmployees}
          loading={departmentsLoading}
          error={departmentsError}
          onRefresh={loadLookups}
        />
      ) : null}

      {activeTab === 'attendance' ? (
        <HrAttendanceTab
          token={token}
          canWrite={canWriteHr}
          selfServiceOnly={selfServiceOnly}
          employees={allEmployees}
        />
      ) : null}

      {activeTab === 'leave' ? (
        <HrLeaveTab
          token={token}
          canWrite={canWriteHr}
          canReview={canReviewLeave}
          selfServiceOnly={selfServiceOnly}
          linkedEmployee={linkedEmployee}
          employees={allEmployees}
        />
      ) : null}
    </div>
  );
}
