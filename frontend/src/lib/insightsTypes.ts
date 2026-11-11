import type { LeadStatus } from './crmTypes';
import type { InvoiceSummary, PaymentEntry, SalesOrderStatus } from './financeTypes';

export interface SalesOverTimePoint {
  date: string;
  revenue: number;
  invoiceCount: number;
}

export interface SalesOverTimeReport {
  periodStart: string;
  periodEnd: string;
  totalRevenue: number;
  totalInvoices: number;
  definition: string;
  series: SalesOverTimePoint[];
}

export interface OrderStatusReportRow {
  status: SalesOrderStatus;
  count: number;
  totalAmount: number;
}

export interface OrdersByStatusReport {
  periodStart: string;
  periodEnd: string;
  totalOrders: number;
  totalValue: number;
  rows: OrderStatusReportRow[];
}

export interface ReceivablesReport {
  periodStart: string;
  periodEnd: string;
  outstandingTotal: number;
  totalPaymentsInPeriod: number;
  totalRefundsInPeriod: number;
  outstandingInvoices: InvoiceSummary[];
  paymentsInPeriod: PaymentEntry[];
}

export interface LeadPipelineRow {
  status: LeadStatus;
  count: number;
  estimatedValue: number;
}

export interface LeadPipelineReport {
  totalLeads: number;
  openLeads: number;
  convertedLeads: number;
  conversionRatePercent: number;
  stages: LeadPipelineRow[];
}

export interface InventoryStockRow {
  id: string;
  sku: string;
  name: string;
  category: string | null;
  unitOfMeasure: string;
  stockQuantity: number;
  lowStockThreshold: number;
  lowStock: boolean;
  unitPrice: number;
  stockValue: number;
}

export interface InventoryStockReport {
  totalSkus: number;
  lowStockCount: number;
  totalStockValue: number;
  items: InventoryStockRow[];
  page: number;
  pageSize: number;
  totalItems: number;
  totalPages: number;
}

export interface ForecastEvaluation {
  holdoutDays: number;
  baselineMae: number;
  baselineWape: number | null;
  modelMae: number;
  modelWape: number | null;
  selectedModel: 'RIDGE_REGRESSION' | 'MOVING_AVG_BASELINE';
}

export interface ForecastPoint {
  date: string;
  predictedValue: number;
  baselineValue: number;
}

export interface ForecastResponse {
  status: 'OK' | 'INSUFFICIENT_DATA' | 'UNAVAILABLE';
  serviceAvailable: boolean;
  predictionTarget: string;
  horizonDays: number;
  actualDays: number;
  nonZeroDays: number;
  minimumDaysRequired: number;
  message: string;
  evaluation: ForecastEvaluation | null;
  predictions: ForecastPoint[];
  projectedTotal: number | null;
  estimatedStockoutDate: string | null;
  limitations: string[];
}

