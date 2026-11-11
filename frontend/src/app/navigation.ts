import type { ComponentType } from 'react';
import {
  BarChart3,
  Briefcase,
  LayoutDashboard,
  Package,
  Receipt,
  Settings,
  Users,
} from 'lucide-react';

export type UserRole = 'OWNER' | 'ADMIN' | 'MANAGER' | 'EMPLOYEE';

export interface NavItem {
  id: string;
  label: string;
  path: string;
  group: 'Workspace' | 'Modules' | 'Governance';
  icon: ComponentType<{ size?: number; className?: string }>;
  allowedRoles: readonly UserRole[];
  statusLabel: string;
  description: string;
}

export const ROLE_OPTIONS: { value: UserRole; label: string }[] = [
  { value: 'OWNER', label: 'Owner' },
  { value: 'ADMIN', label: 'Administrator' },
  { value: 'MANAGER', label: 'Manager' },
  { value: 'EMPLOYEE', label: 'Employee' },
];

export const NAVIGATION_ITEMS: NavItem[] = [
  {
    id: 'dashboard',
    label: 'Dashboard',
    path: '/dashboard',
    group: 'Workspace',
    icon: LayoutDashboard,
    allowedRoles: ['OWNER', 'ADMIN', 'MANAGER', 'EMPLOYEE'],
    statusLabel: 'Active',
    description:
      'Executive overview for authorized organization metrics, pipeline activity, invoice balances, and low-stock alerts.',
  },
  {
    id: 'crm',
    label: 'CRM',
    path: '/crm',
    group: 'Modules',
    icon: Users,
    allowedRoles: ['OWNER', 'ADMIN', 'MANAGER', 'EMPLOYEE'],
    statusLabel: 'Active',
    description:
      'Lead pipeline, customer directory, follow-up tracking, and transactional lead-to-customer conversion.',
  },
  {
    id: 'operations',
    label: 'Operations',
    path: '/operations',
    group: 'Modules',
    icon: Package,
    allowedRoles: ['OWNER', 'ADMIN', 'MANAGER', 'EMPLOYEE'],
    statusLabel: 'Active',
    description:
      'Product catalog, SKU management, suppliers, stock levels, and auditable inventory movements.',
  },
  {
    id: 'finance',
    label: 'Finance',
    path: '/finance',
    group: 'Modules',
    icon: Receipt,
    allowedRoles: ['OWNER', 'ADMIN', 'MANAGER'],
    statusLabel: 'Active',
    description:
      'Sales orders, invoice generation, payment recording, and reconciled outstanding balances.',
  },
  {
    id: 'hr',
    label: 'HR',
    path: '/hr',
    group: 'Modules',
    icon: Briefcase,
    allowedRoles: ['OWNER', 'ADMIN', 'MANAGER', 'EMPLOYEE'],
    statusLabel: 'Active',
    description:
      'Employee directory, department management, daily attendance tracking, and leave workflows.',
  },
  {
    id: 'insights',
    label: 'Insights',
    path: '/insights',
    group: 'Modules',
    icon: BarChart3,
    allowedRoles: ['OWNER', 'ADMIN', 'MANAGER'],
    statusLabel: 'Active',
    description:
      'Organization-scoped business reporting, demand forecasting, and the permission-checked AI assistant.',
  },
  {
    id: 'administration',
    label: 'Administration',
    path: '/administration',
    group: 'Governance',
    icon: Settings,
    allowedRoles: ['OWNER', 'ADMIN'],
    statusLabel: 'Active',
    description:
      'Organization onboarding, membership management, role permissions, and security configuration.',
  },
];

export function getVisibleNavItems(role: UserRole): NavItem[] {
  return NAVIGATION_ITEMS.filter((item) => item.allowedRoles.includes(role));
}

export function isRouteAllowedForRole(item: NavItem, role: UserRole): boolean {
  return item.allowedRoles.includes(role);
}

