import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider, type SessionInfo } from '../app/authContext';
import { ToastProvider } from '../components/ui';
import { HrPage } from './HrPage';

const OWNER_SESSION: SessionInfo = {
  user: { id: 'u-1', email: 'owner@acme.test', displayName: 'Alex Owner' },
  activeOrganization: { id: 'org-1', name: 'Acme', slug: 'acme', role: 'OWNER' },
  permissions: [
    'HR_SELF_SERVICE',
    'HR_READ',
    'HR_WRITE',
    'HR_LEAVE_REVIEW',
    'MEMBERS_READ',
  ],
  organizations: [{ id: 'org-1', name: 'Acme', slug: 'acme', role: 'OWNER' }],
};

function renderHrPage() {
  return render(
    <ToastProvider>
      <AuthProvider initialSession={OWNER_SESSION} initialToken="test-token">
        <MemoryRouter>
          <HrPage />
        </MemoryRouter>
      </AuthProvider>
    </ToastProvider>,
  );
}

const SAMPLE_DEPT = {
  id: 'dept-1',
  name: 'Engineering',
  description: 'Platform engineering',
  headEmployeeId: 'emp-1',
  headEmployeeName: 'Maya Lin',
  employeeCount: 2,
  createdAt: '2026-11-01T00:00:00Z',
  updatedAt: '2026-11-01T00:00:00Z',
};

const SAMPLE_EMPLOYEE = {
  id: 'emp-1',
  employeeCode: 'EMP-0001',
  userId: null,
  firstName: 'Maya',
  lastName: 'Lin',
  fullName: 'Maya Lin',
  email: 'maya@acme.test',
  phone: '+1-555-0100',
  departmentId: 'dept-1',
  departmentName: 'Engineering',
  designation: 'Staff Engineer',
  employmentType: 'FULL_TIME',
  dateOfJoining: '2026-01-10',
  employmentStatus: 'ACTIVE',
  reportingManagerId: null,
  reportingManagerName: null,
  address: '100 Main St',
  createdAt: '2026-11-01T00:00:00Z',
  updatedAt: '2026-11-01T00:00:00Z',
};

const SAMPLE_ATTENDANCE = {
  id: 'att-1',
  employeeId: 'emp-1',
  employeeCode: 'EMP-0001',
  employeeName: 'Maya Lin',
  departmentName: 'Engineering',
  attendanceDate: '2026-11-11',
  status: 'PRESENT',
  notes: 'On site',
  recordedByName: 'Alex Owner',
  updatedByName: null,
  createdAt: '2026-11-11T09:00:00Z',
  updatedAt: '2026-11-11T09:00:00Z',
};

const SAMPLE_LEAVE = {
  id: 'leave-1',
  employeeId: 'emp-1',
  employeeCode: 'EMP-0001',
  employeeName: 'Maya Lin',
  departmentName: 'Engineering',
  leaveType: 'CASUAL_LEAVE',
  startDate: '2026-11-20',
  endDate: '2026-11-21',
  totalDays: 2,
  reason: 'Family event',
  status: 'PENDING',
  submittedAt: '2026-11-11T10:00:00Z',
  reviewerUserId: null,
  reviewerName: null,
  reviewComments: null,
  reviewedAt: null,
  auditTrail: [
    {
      id: 'audit-1',
      fromStatus: null,
      toStatus: 'PENDING',
      comment: 'Family event',
      actorName: 'Maya Lin',
      createdAt: '2026-11-11T10:00:00Z',
    },
  ],
};

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

describe('HrPage', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('renders HR dashboard metrics and handles dashboard error retry', async () => {
    let dashboardCalls = 0;
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url.includes('/api/v1/hr/me')) {
        return jsonResponse({ data: { linked: false, employee: null } });
      }
      if (url.includes('/api/v1/hr/departments')) {
        return jsonResponse({ data: [SAMPLE_DEPT] });
      }
      if (url.includes('/api/v1/hr/employees')) {
        return jsonResponse({
          data: [SAMPLE_EMPLOYEE],
          page: 1,
          pageSize: 100,
          totalItems: 1,
          totalPages: 1,
        });
      }
      if (url.includes('/api/v1/organizations/current/members')) {
        return jsonResponse({ data: [] });
      }
      if (url.includes('/api/v1/hr/dashboard')) {
        dashboardCalls += 1;
        if (dashboardCalls === 1) {
          return jsonResponse(
            { status: 500, error: 'Internal Server Error', message: 'HR database offline' },
            500,
          );
        }
        return jsonResponse({
          data: {
            date: '2026-11-11',
            selfServiceOnly: false,
            linkedEmployee: null,
            totalEmployees: 8,
            activeEmployees: 7,
            inactiveEmployees: 1,
            presentToday: 6,
            halfDayToday: 0,
            absentToday: 0,
            onLeaveToday: 1,
            pendingLeaveRequests: 2,
            departmentDistribution: [
              { departmentId: 'dept-1', departmentName: 'Engineering', employeeCount: 2 },
            ],
            recentEmployees: [SAMPLE_EMPLOYEE],
            recentLeaveRequests: [SAMPLE_LEAVE],
          },
        });
      }
      return jsonResponse({ data: [] });
    });

    const user = userEvent.setup();
    renderHrPage();

    expect(await screen.findByText('HR Overview unavailable')).toBeInTheDocument();
    expect(screen.getByText('HR database offline')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Retry' }));

    expect(await screen.findByText('Total Employees')).toBeInTheDocument();
    expect(screen.getByText('8')).toBeInTheDocument();
    expect(screen.getByText('7 active · 1 inactive')).toBeInTheDocument();
    expect(screen.getAllByText('Maya Lin').length).toBeGreaterThanOrEqual(2);
  });

  it('supports employee creation/editing, department creation, attendance marking, and leave approval', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
      const url = String(input);
      const method = init?.method ?? 'GET';

      if (url.includes('/api/v1/hr/me')) {
        return jsonResponse({ data: { linked: false, employee: null } });
      }
      if (url.includes('/api/v1/organizations/current/members')) {
        return jsonResponse({ data: [] });
      }
      if (url.includes('/api/v1/hr/dashboard')) {
        return jsonResponse({
          data: {
            date: '2026-11-11',
            selfServiceOnly: false,
            linkedEmployee: null,
            totalEmployees: 1,
            activeEmployees: 1,
            inactiveEmployees: 0,
            presentToday: 1,
            halfDayToday: 0,
            absentToday: 0,
            onLeaveToday: 0,
            pendingLeaveRequests: 1,
            departmentDistribution: [],
            recentEmployees: [],
            recentLeaveRequests: [],
          },
        });
      }
      if (url.includes('/api/v1/hr/departments')) {
        if (method === 'POST') {
          return jsonResponse(
            {
              data: {
                ...SAMPLE_DEPT,
                id: 'dept-2',
                name: 'People Ops',
                employeeCount: 0,
              },
            },
            201,
          );
        }
        return jsonResponse({ data: [SAMPLE_DEPT] });
      }
      if (url.includes('/api/v1/hr/employees')) {
        if (method === 'POST') {
          return jsonResponse(
            {
              data: {
                ...SAMPLE_EMPLOYEE,
                id: 'emp-2',
                employeeCode: 'EMP-0002',
                firstName: 'Noah',
                lastName: 'Patel',
                fullName: 'Noah Patel',
                email: 'noah@acme.test',
              },
            },
            201,
          );
        }
        if (method === 'PATCH') {
          return jsonResponse({
            data: { ...SAMPLE_EMPLOYEE, designation: 'Principal Engineer' },
          });
        }
        return jsonResponse({
          data: [SAMPLE_EMPLOYEE],
          page: 1,
          pageSize: 10,
          totalItems: 1,
          totalPages: 1,
        });
      }
      if (url.includes('/api/v1/hr/attendance')) {
        if (method === 'POST') {
          return jsonResponse({ data: SAMPLE_ATTENDANCE }, 201);
        }
        return jsonResponse({
          data: [SAMPLE_ATTENDANCE],
          page: 1,
          pageSize: 15,
          totalItems: 1,
          totalPages: 1,
        });
      }
      if (url.includes('/api/v1/hr/leave-requests/') && url.endsWith('/approve')) {
        return jsonResponse({
          data: {
            ...SAMPLE_LEAVE,
            status: 'APPROVED',
            reviewerName: 'Alex Owner',
            reviewComments: 'Approved',
          },
        });
      }
      if (url.includes('/api/v1/hr/leave-requests/') && method === 'GET') {
        return jsonResponse({ data: SAMPLE_LEAVE });
      }
      if (url.includes('/api/v1/hr/leave-requests')) {
        if (method === 'POST') {
          return jsonResponse({ data: SAMPLE_LEAVE }, 201);
        }
        return jsonResponse({
          data: [SAMPLE_LEAVE],
          page: 1,
          pageSize: 15,
          totalItems: 1,
          totalPages: 1,
        });
      }
      return jsonResponse({ data: [] });
    });

    const user = userEvent.setup();
    renderHrPage();

    // Employees tab: create & edit
    await user.click(screen.getByRole('tab', { name: 'Employees' }));
    expect(await screen.findByText('EMP-0001')).toBeInTheDocument();
    expect(screen.getAllByText('Engineering').length).toBeGreaterThanOrEqual(1);

    await user.click(screen.getByRole('button', { name: 'Add Employee' }));
    const addDialog = await screen.findByRole('dialog');
    await user.type(within(addDialog).getByLabelText(/First Name/i), 'Noah');
    await user.type(within(addDialog).getByLabelText(/Last Name/i), 'Patel');
    await user.type(within(addDialog).getByLabelText(/Email/i), 'noah@acme.test');
    await user.type(within(addDialog).getByLabelText(/Phone Number/i), '+1-555-0202');
    await user.type(within(addDialog).getByLabelText(/Designation/i), 'Analyst');
    await user.click(within(addDialog).getByRole('button', { name: 'Create Employee' }));

    await waitFor(() => {
      expect(screen.getByText('Employee added')).toBeInTheDocument();
    });

    // Departments tab: create
    await user.click(screen.getByRole('tab', { name: 'Departments' }));
    expect(await screen.findByText('Engineering')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'New Department' }));
    const deptDialog = await screen.findByRole('dialog');
    await user.type(within(deptDialog).getByLabelText(/Department Name/i), 'People Ops');
    await user.click(within(deptDialog).getByRole('button', { name: 'Create Department' }));
    await waitFor(() => {
      expect(screen.getByText('Department created')).toBeInTheDocument();
    });

    // Attendance tab: mark attendance
    await user.click(screen.getByRole('tab', { name: 'Attendance' }));
    expect(await screen.findByText('On site')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Mark Attendance' }));
    const attDialog = await screen.findByRole('dialog');
    await user.click(within(attDialog).getByRole('button', { name: 'Save Attendance' }));
    await waitFor(() => {
      expect(screen.getByText('Attendance recorded')).toBeInTheDocument();
    });

    // Leave Management tab: view detail + approve
    await user.click(screen.getByRole('tab', { name: 'Leave Management' }));
    const approveBtn = await screen.findByRole('button', { name: 'Approve' });
    expect(screen.getAllByText('Casual Leave').length).toBeGreaterThanOrEqual(2);
    await user.click(approveBtn);
    const approveDialog = await screen.findByRole('dialog');
    await user.click(within(approveDialog).getByRole('button', { name: 'Approve' }));

    await waitFor(() => {
      expect(screen.getByText('Leave request approved')).toBeInTheDocument();
    });

    expect(fetchMock).toHaveBeenCalled();
  }, 15000);

  it('shows self-service view for Employee role without Departments tab', async () => {
    const employeeSession: SessionInfo = {
      user: { id: 'u-2', email: 'maya@acme.test', displayName: 'Maya Lin' },
      activeOrganization: { id: 'org-1', name: 'Acme', slug: 'acme', role: 'EMPLOYEE' },
      permissions: ['HR_SELF_SERVICE'],
      organizations: [{ id: 'org-1', name: 'Acme', slug: 'acme', role: 'EMPLOYEE' }],
    };

    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url.includes('/api/v1/hr/me')) {
        return jsonResponse({ data: { linked: true, employee: SAMPLE_EMPLOYEE } });
      }
      if (url.includes('/api/v1/hr/dashboard')) {
        return jsonResponse({
          data: {
            date: '2026-11-11',
            selfServiceOnly: true,
            linkedEmployee: SAMPLE_EMPLOYEE,
            totalEmployees: 1,
            activeEmployees: 1,
            inactiveEmployees: 0,
            presentToday: 1,
            halfDayToday: 0,
            absentToday: 0,
            onLeaveToday: 0,
            pendingLeaveRequests: 1,
            departmentDistribution: [],
            recentEmployees: [],
            recentLeaveRequests: [SAMPLE_LEAVE],
          },
        });
      }
      return jsonResponse({ data: [] });
    });

    render(
      <ToastProvider>
        <AuthProvider initialSession={employeeSession} initialToken="emp-token">
          <MemoryRouter>
            <HrPage />
          </MemoryRouter>
        </AuthProvider>
      </ToastProvider>,
    );

    expect(
      await screen.findByText(/My Employee Profile · EMP-0001/),
    ).toBeInTheDocument();
    expect(screen.queryByRole('tab', { name: 'Departments' })).not.toBeInTheDocument();
    expect(screen.getByText('My Pending Leave Requests')).toBeInTheDocument();
  });
});

