export type StockMovementType =
  | 'INITIAL'
  | 'RECEIPT'
  | 'ADJUSTMENT_IN'
  | 'ADJUSTMENT_OUT'
  | 'RETURN_IN'
  | 'ORDER_DEDUCTION';

export interface Product {
  id: string;
  sku: string;
  name: string;
  description: string | null;
  category: string | null;
  unitOfMeasure: string;
  unitPrice: number;
  costPrice: number | null;
  stockQuantity: number;
  lowStockThreshold: number;
  lowStock: boolean;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface Supplier {
  id: string;
  name: string;
  contactName: string | null;
  email: string | null;
  phone: string | null;
  address: string | null;
  notes: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface SupplierProduct {
  id: string;
  supplierId: string;
  supplierName: string;
  productId: string;
  productSku: string;
  productName: string;
  supplierSku: string | null;
  unitCost: number | null;
  leadTimeDays: number | null;
  createdAt: string;
}

export interface StockMovement {
  id: string;
  productId: string;
  productSku: string;
  productName: string;
  supplierId: string | null;
  supplierName: string | null;
  movementType: StockMovementType;
  quantityDelta: number;
  quantityBefore: number;
  quantityAfter: number;
  reason: string;
  reference: string | null;
  actorName: string | null;
  createdAt: string;
  idempotentReplay: boolean;
}

