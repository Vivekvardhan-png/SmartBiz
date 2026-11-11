export type EmploymentType = 'FULL_TIME' | 'PART_TIME' | 'CONTRACT';
export type EmploymentStatus = 'ACTIVE' | 'INACTIVE';
export type AttendanceStatus = 'PRESENT' | 'ABSENT' | 'HALF_DAY' | 'ON_LEAVE';
export type LeaveType =
  | 'CASUAL_LEAVE'
  | 'SICK_LEAVE'
  | 'EARNED_LEAVE'
  | 'UNPAID_LEAVE';
export type LeaveRequestStatus =
  | 'PENDING'
  | 'APPROVED'
  | 'REJECTED'
  | 'CANCELLED';

export interface HrDepartment {
  id: string;
  name: string;
  description: string | null;
  headEmployeeId: string | null;
  headEmployeeName: string | null;
  employeeCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface HrEmployee {
  id: string;
  employeeCode: string;
  userId: string | null;
  firstName: string;
  lastName: string;
  fullName: string;
  email: string;
  phone: string;
  departmentId: string;
  departmentName: string;
  designation: string;
  employmentType: EmploymentType;
  dateOfJoining: string;
  employmentStatus: EmploymentStatus;
  reportingManagerId: string | null;
  reportingManagerName: string | null;
  address: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface MyEmployeeProfile {
  linked: boolean;
  employee: HrEmployee | null;
}

export interface HrAttendance {
  id: string;
  employeeId: string;
  employeeCode: string;
  employeeName: string;
  departmentName: string;
  attendanceDate: string;
  status: AttendanceStatus;
  notes: string | null;
  recordedByName: string | null;
  updatedByName: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface HrLeaveAuditLog {
  id: string;
  fromStatus: LeaveRequestStatus | null;
  toStatus: LeaveRequestStatus;
  comment: string | null;
  actorName: string | null;
  createdAt: string;
}

export interface HrLeaveRequest {
  id: string;
  employeeId: string;
  employeeCode: string;
  employeeName: string;
  departmentName: string;
  leaveType: LeaveType;
  startDate: string;
  endDate: string;
  totalDays: number;
  reason: string;
  status: LeaveRequestStatus;
  submittedAt: string;
  reviewerUserId: string | null;
  reviewerName: string | null;
  reviewComments: string | null;
  reviewedAt: string | null;
  auditTrail: HrLeaveAuditLog[];
}

export interface DepartmentDistributionItem {
  departmentId: string;
  departmentName: string;
  employeeCount: number;
}

export interface HrDashboard {
  date: string;
  selfServiceOnly: boolean;
  linkedEmployee: HrEmployee | null;
  totalEmployees: number;
  activeEmployees: number;
  inactiveEmployees: number;
  presentToday: number;
  halfDayToday: number;
  absentToday: number;
  onLeaveToday: number;
  pendingLeaveRequests: number;
  departmentDistribution: DepartmentDistributionItem[];
  recentEmployees: HrEmployee[];
  recentLeaveRequests: HrLeaveRequest[];
}

