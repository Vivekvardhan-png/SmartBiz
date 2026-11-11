import type {
  AttendanceStatus,
  EmploymentStatus,
  EmploymentType,
  LeaveRequestStatus,
  LeaveType,
} from '../../lib/hrTypes';

export const EMPLOYMENT_TYPE_LABELS: Record<EmploymentType, string> = {
  FULL_TIME: 'Full-time',
  PART_TIME: 'Part-time',
  CONTRACT: 'Contract',
};

export const EMPLOYMENT_STATUS_LABELS: Record<EmploymentStatus, string> = {
  ACTIVE: 'Active',
  INACTIVE: 'Inactive',
};

export const ATTENDANCE_STATUS_LABELS: Record<AttendanceStatus, string> = {
  PRESENT: 'Present',
  ABSENT: 'Absent',
  HALF_DAY: 'Half Day',
  ON_LEAVE: 'On Leave',
};

export const LEAVE_TYPE_LABELS: Record<LeaveType, string> = {
  CASUAL_LEAVE: 'Casual Leave',
  SICK_LEAVE: 'Sick Leave',
  EARNED_LEAVE: 'Earned Leave',
  UNPAID_LEAVE: 'Unpaid Leave',
};

export const LEAVE_STATUS_LABELS: Record<LeaveRequestStatus, string> = {
  PENDING: 'Pending',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
  CANCELLED: 'Cancelled',
};

export function getEmploymentStatusVariant(status: EmploymentStatus) {
  return status === 'ACTIVE' ? ('success' as const) : ('neutral' as const);
}

export function getAttendanceStatusVariant(status: AttendanceStatus) {
  switch (status) {
    case 'PRESENT':
      return 'success' as const;
    case 'HALF_DAY':
      return 'info' as const;
    case 'ON_LEAVE':
      return 'warning' as const;
    case 'ABSENT':
      return 'error' as const;
  }
}

export function getLeaveStatusVariant(status: LeaveRequestStatus) {
  switch (status) {
    case 'APPROVED':
      return 'success' as const;
    case 'REJECTED':
      return 'error' as const;
    case 'PENDING':
      return 'warning' as const;
    case 'CANCELLED':
      return 'neutral' as const;
  }
}

export function formatIsoDate(value: string | null | undefined): string {
  if (!value) return '—';
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return value;
  }
  return parsed.toLocaleDateString();
}

export function formatTimestamp(value: string | null | undefined): string {
  if (!value) return '—';
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return value;
  }
  return parsed.toLocaleString();
}

