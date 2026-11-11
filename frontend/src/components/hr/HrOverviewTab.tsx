import { useCallback, useEffect, useState, type FormEvent } from 'react';
import {
  Badge,
  Button,
  Card,
  CardContent,
  CardHeader,
  EmptyState,
  FormField,
  StateBoundary,
  Table,
  type ScreenStatus,
  type TableColumn,
} from '../ui';
import { apiRequest, ApiError } from '../../lib/apiClient';
import type {
  DepartmentDistributionItem,
  HrDashboard,
  HrEmployee,
  HrLeaveRequest,
} from '../../lib/hrTypes';
import {
  EMPLOYMENT_TYPE_LABELS,
  LEAVE_STATUS_LABELS,
  LEAVE_TYPE_LABELS,
  formatIsoDate,
  getEmploymentStatusVariant,
  getLeaveStatusVariant,
} from './hrFormatters';

interface HrOverviewTabProps {
  token: string | null;
  onNavigateTab: (
    tab: 'overview' | 'employees' | 'departments' | 'attendance' | 'leave',
  ) => void;
}

export function HrOverviewTab({ token, onNavigateTab }: HrOverviewTabProps) {
  const [dateFilter, setDateFilter] = useState('');
  const [dashboard, setDashboard] = useState<HrDashboard | null>(null);
  const [status, setStatus] = useState<ScreenStatus>('loading');
  const [error, setError] = useState<string | null>(null);

  const loadDashboard = useCallback(
    async (date?: string) => {
      if (!token) return;
      setStatus('loading');
      setError(null);
      const query = date ? `?date=${encodeURIComponent(date)}` : '';
      try {
        const data = await apiRequest<HrDashboard>(`/api/v1/hr/dashboard${query}`, {
          token,
        });
        setDashboard(data);
        setStatus('success');
      } catch (err) {
        setError(
          err instanceof ApiError ? err.message : 'Unable to load HR overview.',
        );
        setStatus('error');
      }
    },
    [token],
  );

  useEffect(() => {
    void loadDashboard();
  }, [loadDashboard]);

  const handleDateSubmit = (event: FormEvent) => {
    event.preventDefault();
    void loadDashboard(dateFilter || undefined);
  };

  const deptColumns: TableColumn<DepartmentDistributionItem>[] = [
    {
      key: 'departmentName',
      header: 'Department',
      render: (row) => <strong>{row.departmentName}</strong>,
    },
    {
      key: 'employeeCount',
      header: 'Employees',
      align: 'right',
      render: (row) => row.employeeCount,
    },
  ];

  const recentEmpColumns: TableColumn<HrEmployee>[] = [
    {
      key: 'code',
      header: 'ID',
      render: (row) => <span>{row.employeeCode}</span>,
    },
    {
      key: 'name',
      header: 'Employee',
      render: (row) => (
        <div>
          <strong>{row.fullName}</strong>
          <div style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>
            {row.designation} · {row.departmentName}
          </div>
        </div>
      ),
    },
    {
      key: 'type',
      header: 'Type',
      render: (row) => EMPLOYMENT_TYPE_LABELS[row.employmentType],
    },
    {
      key: 'status',
      header: 'Status',
      align: 'right',
      render: (row) => (
        <Badge variant={getEmploymentStatusVariant(row.employmentStatus)}>
          {row.employmentStatus === 'ACTIVE' ? 'Active' : 'Inactive'}
        </Badge>
      ),
    },
  ];

  const recentLeaveColumns: TableColumn<HrLeaveRequest>[] = [
    {
      key: 'employee',
      header: 'Employee',
      render: (row) => (
        <div>
          <strong>{row.employeeName}</strong>
          <div style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>
            {LEAVE_TYPE_LABELS[row.leaveType]}
          </div>
        </div>
      ),
    },
    {
      key: 'dates',
      header: 'Dates',
      render: (row) => `${row.startDate} to ${row.endDate} (${row.totalDays}d)`,
    },
    {
      key: 'status',
      header: 'Status',
      align: 'right',
      render: (row) => (
        <Badge variant={getLeaveStatusVariant(row.status)}>
          {LEAVE_STATUS_LABELS[row.status]}
        </Badge>
      ),
    },
  ];

  return (
    <div className="foundation-stack">
      <Card>
        <CardContent compact>
          <form
            onSubmit={handleDateSubmit}
            className="foundation-row"
            style={{ alignItems: 'flex-end', justifyContent: 'space-between' }}
          >
            <div className="foundation-row" style={{ alignItems: 'flex-end' }}>
              <FormField
                label="Attendance Snapshot Date"
                type="date"
                value={dateFilter}
                onChange={(e) => setDateFilter(e.target.value)}
              />
              <Button type="submit" variant="secondary">
                Apply Date
              </Button>
            </div>
            {dashboard ? (
              <span
                style={{
                  fontSize: 'var(--text-xs)',
                  color: 'var(--color-text-secondary)',
                }}
              >
                Showing metrics for {dashboard.date}
              </span>
            ) : null}
          </form>
        </CardContent>
      </Card>

      <StateBoundary
        status={status}
        errorTitle="HR Overview unavailable"
        errorMessage={error ?? 'Failed to load HR metrics.'}
        onRetry={() => void loadDashboard(dateFilter || undefined)}
      >
        {dashboard ? (
          dashboard.selfServiceOnly ? (
            <div className="foundation-stack">
              {!dashboard.linkedEmployee ? (
                <EmptyState
                  title="Employee profile not linked"
                  description="Ask your organization administrator to create or link your employee record to view attendance and submit leave requests."
                />
              ) : (
                <div className="foundation-grid">
                  <div className="foundation-col-12">
                    <Card>
                      <CardHeader
                        title={`My Employee Profile · ${dashboard.linkedEmployee.employeeCode}`}
                        description={`${dashboard.linkedEmployee.designation} · ${dashboard.linkedEmployee.departmentName}`}
                        actions={
                          <Badge
                            variant={getEmploymentStatusVariant(
                              dashboard.linkedEmployee.employmentStatus,
                            )}
                          >
                            {dashboard.linkedEmployee.employmentStatus === 'ACTIVE'
                              ? 'Active'
                              : 'Inactive'}
                          </Badge>
                        }
                      />
                      <CardContent>
                        <div className="foundation-row" style={{ justifyContent: 'space-between' }}>
                          <div>
                            <div style={{ fontWeight: 'var(--weight-semibold)' }}>
                              {dashboard.linkedEmployee.fullName}
                            </div>
                            <div style={{ fontSize: 'var(--text-sm)', color: 'var(--color-text-secondary)' }}>
                              {dashboard.linkedEmployee.email} · {dashboard.linkedEmployee.phone}
                            </div>
                            <div style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)', marginTop: 'var(--space-1)' }}>
                              Joined {formatIsoDate(dashboard.linkedEmployee.dateOfJoining)}
                              {dashboard.linkedEmployee.reportingManagerName
                                ? ` · Reports to ${dashboard.linkedEmployee.reportingManagerName}`
                                : ''}
                            </div>
                          </div>
                          <div className="foundation-row">
                            <Button variant="secondary" onClick={() => onNavigateTab('attendance')}>
                              My Attendance
                            </Button>
                            <Button variant="primary" onClick={() => onNavigateTab('leave')}>
                              Request Leave
                            </Button>
                          </div>
                        </div>
                      </CardContent>
                    </Card>
                  </div>

                  <div className="foundation-col-4">
                    <Card>
                      <CardHeader title="Present Today" />
                      <CardContent>
                        <div style={{ fontSize: 'var(--text-2xl)', fontWeight: 'var(--weight-bold)' }}>
                          {dashboard.presentToday}
                        </div>
                      </CardContent>
                    </Card>
                  </div>
                  <div className="foundation-col-4">
                    <Card>
                      <CardHeader title="On Leave Today" />
                      <CardContent>
                        <div style={{ fontSize: 'var(--text-2xl)', fontWeight: 'var(--weight-bold)' }}>
                          {dashboard.onLeaveToday}
                        </div>
                      </CardContent>
                    </Card>
                  </div>
                  <div className="foundation-col-4">
                    <Card>
                      <CardHeader title="My Pending Leave Requests" />
                      <CardContent>
                        <div style={{ fontSize: 'var(--text-2xl)', fontWeight: 'var(--weight-bold)' }}>
                          {dashboard.pendingLeaveRequests}
                        </div>
                      </CardContent>
                    </Card>
                  </div>

                  <div className="foundation-col-12">
                    <Card>
                      <CardHeader title="Recent Leave Requests" />
                      <CardContent>
                        {dashboard.recentLeaveRequests.length === 0 ? (
                          <EmptyState
                            title="No leave requests yet"
                            description="Submit a leave request from the Leave Management tab."
                          />
                        ) : (
                          <Table
                            caption="Recent leave requests"
                            columns={recentLeaveColumns}
                            rows={dashboard.recentLeaveRequests}
                            getRowKey={(row) => row.id}
                          />
                        )}
                      </CardContent>
                    </Card>
                  </div>
                </div>
              )}
            </div>
          ) : (
            <div className="foundation-grid">
              <div className="foundation-col-4">
                <Card>
                  <CardHeader title="Total Employees" />
                  <CardContent>
                    <div style={{ fontSize: 'var(--text-2xl)', fontWeight: 'var(--weight-bold)' }}>
                      {dashboard.totalEmployees}
                    </div>
                    <p style={{ marginTop: 'var(--space-1)', fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>
                      {dashboard.activeEmployees} active · {dashboard.inactiveEmployees} inactive
                    </p>
                  </CardContent>
                </Card>
              </div>

              <div className="foundation-col-4">
                <Card>
                  <CardHeader title="Present Today" />
                  <CardContent>
                    <div style={{ fontSize: 'var(--text-2xl)', fontWeight: 'var(--weight-bold)' }}>
                      {dashboard.presentToday}
                    </div>
                    <p style={{ marginTop: 'var(--space-1)', fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>
                      {dashboard.halfDayToday} half day · {dashboard.absentToday} absent
                    </p>
                  </CardContent>
                </Card>
              </div>

              <div className="foundation-col-4">
                <Card>
                  <CardHeader title="On Leave / Pending Requests" />
                  <CardContent>
                    <div style={{ fontSize: 'var(--text-2xl)', fontWeight: 'var(--weight-bold)' }}>
                      {dashboard.onLeaveToday}
                    </div>
                    <p style={{ marginTop: 'var(--space-1)', fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>
                      {dashboard.pendingLeaveRequests} pending leave request(s)
                    </p>
                  </CardContent>
                </Card>
              </div>

              <div className="foundation-col-6">
                <Card>
                  <CardHeader
                    title="Department Distribution"
                    actions={
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => onNavigateTab('departments')}
                      >
                        Manage
                      </Button>
                    }
                  />
                  <CardContent>
                    {dashboard.departmentDistribution.length === 0 ? (
                      <EmptyState
                        title="No departments yet"
                        description="Create a department to organize employees."
                      />
                    ) : (
                      <Table
                        caption="Department distribution"
                        columns={deptColumns}
                        rows={dashboard.departmentDistribution}
                        getRowKey={(row) => row.departmentId}
                      />
                    )}
                  </CardContent>
                </Card>
              </div>

              <div className="foundation-col-6">
                <Card>
                  <CardHeader
                    title="Recent Leave Requests"
                    actions={
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => onNavigateTab('leave')}
                      >
                        View All
                      </Button>
                    }
                  />
                  <CardContent>
                    {dashboard.recentLeaveRequests.length === 0 ? (
                      <EmptyState
                        title="No leave requests"
                        description="Submitted leave requests will appear here."
                      />
                    ) : (
                      <Table
                        caption="Recent leave requests"
                        columns={recentLeaveColumns}
                        rows={dashboard.recentLeaveRequests}
                        getRowKey={(row) => row.id}
                      />
                    )}
                  </CardContent>
                </Card>
              </div>

              <div className="foundation-col-12">
                <Card>
                  <CardHeader
                    title="Recent Employee Additions"
                    actions={
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => onNavigateTab('employees')}
                      >
                        Directory
                      </Button>
                    }
                  />
                  <CardContent>
                    {dashboard.recentEmployees.length === 0 ? (
                      <EmptyState
                        title="No employees added yet"
                        description="Add employees in the Employees tab to populate the directory."
                      />
                    ) : (
                      <Table
                        caption="Recent employees"
                        columns={recentEmpColumns}
                        rows={dashboard.recentEmployees}
                        getRowKey={(row) => row.id}
                      />
                    )}
                  </CardContent>
                </Card>
              </div>
            </div>
          )
        ) : null}
      </StateBoundary>
    </div>
  );
}

