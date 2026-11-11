export type LeadStatus =
  | 'NEW'
  | 'CONTACTED'
  | 'QUALIFIED'
  | 'PROPOSAL'
  | 'CONVERTED'
  | 'LOST';

export type CustomerStatus = 'ACTIVE' | 'INACTIVE';

export interface PagedResponse<T> {
  data: T[];
  page: number;
  pageSize: number;
  totalItems: number;
  totalPages: number;
}

export interface Customer {
  id: string;
  name: string;
  companyName: string | null;
  email: string | null;
  phone: string | null;
  address: string | null;
  followUpDate: string | null;
  notes: string | null;
  status: CustomerStatus;
  createdAt: string;
  updatedAt: string;
}

export interface Lead {
  id: string;
  title: string;
  contactName: string;
  companyName: string | null;
  email: string | null;
  phone: string | null;
  source: string | null;
  status: LeadStatus;
  allowedNextStatuses: LeadStatus[];
  estimatedValue: number | null;
  followUpDate: string | null;
  notes: string | null;
  convertedCustomerId: string | null;
  convertedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface LeadConversionResult {
  lead: Lead;
  customer: Customer;
  alreadyConverted: boolean;
}

export interface CrmNote {
  id: string;
  entityType: 'LEAD' | 'CUSTOMER';
  entityId: string;
  content: string;
  followUpDate: string | null;
  authorName: string | null;
  createdAt: string;
}

export interface DeferredMetric<T> {
  value: T;
  moduleAvailable: boolean;
  note: string;
}

export interface MetricDefinition {
  metric: string;
  formula: string;
  dateScope: string;
  active: boolean;
}

export interface DashboardSummary {
  periodStart: string;
  periodEnd: string;
  totalCustomers: number;
  openLeads: number;
  leadsByStage: Record<LeadStatus, number>;
  salesOrdersByStatus: DeferredMetric<Record<string, number>>;
  periodRevenue: DeferredMetric<number>;
  outstandingInvoiceBalance: DeferredMetric<number>;
  lowStockProductsCount: DeferredMetric<number>;
  metricDefinitions: MetricDefinition[];
}

