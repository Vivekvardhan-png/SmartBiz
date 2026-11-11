export type SalesOrderStatus = 'DRAFT' | 'CONFIRMED' | 'FULFILLED' | 'CANCELLED';

export type InvoiceStatus =
  | 'ISSUED'
  | 'PARTIALLY_PAID'
  | 'PAID'
  | 'REFUNDED'
  | 'VOID';

export type PaymentEntryType = 'PAYMENT' | 'REFUND';

export type PaymentMethod =
  | 'CASH'
  | 'BANK_TRANSFER'
  | 'CARD'
  | 'CHECK'
  | 'OTHER';

export interface SalesOrderSummary {
  id: string;
  orderNumber: string;
  customerId: string;
  customerName: string;
  status: SalesOrderStatus;
  orderDate: string;
  subtotalAmount: number;
  discountAmount: number;
  taxRatePercent: number;
  taxAmount: number;
  totalAmount: number;
  stockDeducted: boolean;
  hasInvoice: boolean;
  createdAt: string;
}

export interface SalesOrderItem {
  id: string;
  productId: string;
  productSku: string;
  productName: string;
  unitOfMeasure: string;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
}

export interface SalesOrderHistory {
  id: string;
  fromStatus: SalesOrderStatus | null;
  toStatus: SalesOrderStatus;
  note: string | null;
  actorName: string | null;
  createdAt: string;
}

export interface SalesOrderDetail {
  id: string;
  orderNumber: string;
  customerId: string;
  customerName: string;
  customerEmail: string | null;
  status: SalesOrderStatus;
  orderDate: string;
  subtotalAmount: number;
  discountAmount: number;
  taxRatePercent: number;
  taxAmount: number;
  totalAmount: number;
  stockDeducted: boolean;
  notes: string | null;
  items: SalesOrderItem[];
  history: SalesOrderHistory[];
  invoiceId: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface InvoiceSummary {
  id: string;
  invoiceNumber: string;
  salesOrderId: string;
  customerId: string;
  customerName: string;
  status: InvoiceStatus;
  issueDate: string;
  dueDate: string | null;
  totalAmount: number;
  paidAmount: number;
  outstandingBalance: number;
  createdAt: string;
}

export interface InvoiceItem {
  id: string;
  productId: string;
  productSku: string;
  description: string;
  unitOfMeasure: string;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
}

export interface PaymentEntry {
  id: string;
  invoiceId: string;
  entryType: PaymentEntryType;
  amount: number;
  paymentDate: string;
  method: PaymentMethod;
  reference: string | null;
  notes: string | null;
  recordedByName: string | null;
  createdAt: string;
  idempotentReplay: boolean;
}

export interface InvoiceDetail {
  id: string;
  invoiceNumber: string;
  salesOrderId: string;
  customerId: string;
  customerName: string;
  customerEmail: string | null;
  status: InvoiceStatus;
  issueDate: string;
  dueDate: string | null;
  subtotalAmount: number;
  discountAmount: number;
  taxRatePercent: number;
  taxAmount: number;
  totalAmount: number;
  paidAmount: number;
  outstandingBalance: number;
  notes: string | null;
  items: InvoiceItem[];
  payments: PaymentEntry[];
  alreadyExisted: boolean;
  createdAt: string;
}

