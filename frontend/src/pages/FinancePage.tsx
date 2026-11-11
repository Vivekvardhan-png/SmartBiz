import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { Download, FileText, Plus, Printer } from 'lucide-react';
import { useAuth } from '../app/authContext';
import {
  Alert,
  Badge,
  Button,
  Card,
  CardContent,
  Dialog,
  FormField,
  PageHeader,
  Pagination,
  Select,
  Table,
  TextareaField,
  useToast,
  type TableColumn,
} from '../components/ui';
import { apiRequest, ApiError } from '../lib/apiClient';
import type { Customer, PagedResponse } from '../lib/crmTypes';
import type {
  InvoiceDetail,
  InvoiceStatus,
  InvoiceSummary,
  PaymentEntryType,
  PaymentMethod,
  SalesOrderDetail,
  SalesOrderStatus,
  SalesOrderSummary,
} from '../lib/financeTypes';
import type { Product } from '../lib/inventoryTypes';

type FinanceTab = 'orders' | 'invoices';

function orderStatusBadge(status: SalesOrderStatus) {
  switch (status) {
    case 'FULFILLED':
      return 'success' as const;
    case 'CONFIRMED':
      return 'info' as const;
    case 'CANCELLED':
      return 'error' as const;
    default:
      return 'neutral' as const;
  }
}

function invoiceStatusBadge(status: InvoiceStatus) {
  switch (status) {
    case 'PAID':
      return 'success' as const;
    case 'PARTIALLY_PAID':
      return 'warning' as const;
    case 'REFUNDED':
    case 'VOID':
      return 'error' as const;
    default:
      return 'info' as const;
  }
}

export function FinancePage() {
  const { token, hasPermission } = useAuth();
  const { notify } = useToast();
  const canRead = hasPermission('FINANCE_READ');
  const canWrite = hasPermission('FINANCE_WRITE');

  const [activeTab, setActiveTab] = useState<FinanceTab>('orders');

  // Orders state
  const [orders, setOrders] = useState<SalesOrderSummary[]>([]);
  const [ordersTotal, setOrdersTotal] = useState(0);
  const [ordersPage, setOrdersPage] = useState(1);
  const [orderStatusFilter, setOrderStatusFilter] = useState('');
  const [ordersLoading, setOrdersLoading] = useState(true);
  const [ordersError, setOrdersError] = useState<string | null>(null);

  // Invoices state
  const [invoices, setInvoices] = useState<InvoiceSummary[]>([]);
  const [invoicesTotal, setInvoicesTotal] = useState(0);
  const [invoicesPage, setInvoicesPage] = useState(1);
  const [invoiceStatusFilter, setInvoiceStatusFilter] = useState('');
  const [invoicesLoading, setInvoicesLoading] = useState(false);
  const [invoicesError, setInvoicesError] = useState<string | null>(null);

  // Create order modal
  const [createOrderOpen, setCreateOrderOpen] = useState(false);
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [products, setProducts] = useState<Product[]>([]);
  const [selectedCustomerId, setSelectedCustomerId] = useState('');
  const [orderLines, setOrderLines] = useState<
    { productId: string; quantity: string }[]
  >([{ productId: '', quantity: '1' }]);
  const [discountAmount, setDiscountAmount] = useState('0.00');
  const [taxRatePercent, setTaxRatePercent] = useState('0.00');
  const [orderNotes, setOrderNotes] = useState('');
  const [confirmImmediately, setConfirmImmediately] = useState(false);
  const [orderFormError, setOrderFormError] = useState<string | null>(null);
  const [orderSubmitting, setOrderSubmitting] = useState(false);

  // Order detail modal
  const [selectedOrder, setSelectedOrder] = useState<SalesOrderDetail | null>(null);
  const [orderActionLoading, setOrderActionLoading] = useState(false);

  // Invoice detail & payment modal
  const [selectedInvoice, setSelectedInvoice] = useState<InvoiceDetail | null>(null);
  const [paymentType, setPaymentType] = useState<PaymentEntryType>('PAYMENT');
  const [paymentAmount, setPaymentAmount] = useState('');
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>('BANK_TRANSFER');
  const [paymentReference, setPaymentReference] = useState('');
  const [paymentNotes, setPaymentNotes] = useState('');
  const [paymentError, setPaymentError] = useState<string | null>(null);
  const [paymentSubmitting, setPaymentSubmitting] = useState(false);

  const pageSize = 10;

  const fetchOrders = useCallback(async () => {
    if (!token || !canRead) return;
    setOrdersLoading(true);
    setOrdersError(null);
    const params = new URLSearchParams({
      page: String(ordersPage),
      pageSize: String(pageSize),
    });
    if (orderStatusFilter) params.set('status', orderStatusFilter);

    try {
      const res = await apiRequest<PagedResponse<SalesOrderSummary>>(
        `/api/v1/finance/orders?${params.toString()}`,
        { token },
      );
      setOrders(res.data);
      setOrdersTotal(res.totalItems);
    } catch (err) {
      setOrdersError(
        err instanceof ApiError ? err.message : 'Unable to load sales orders.',
      );
    } finally {
      setOrdersLoading(false);
    }
  }, [canRead, orderStatusFilter, ordersPage, token]);

  const fetchInvoices = useCallback(async () => {
    if (!token || !canRead) return;
    setInvoicesLoading(true);
    setInvoicesError(null);
    const params = new URLSearchParams({
      page: String(invoicesPage),
      pageSize: String(pageSize),
    });
    if (invoiceStatusFilter) params.set('status', invoiceStatusFilter);

    try {
      const res = await apiRequest<PagedResponse<InvoiceSummary>>(
        `/api/v1/finance/invoices?${params.toString()}`,
        { token },
      );
      setInvoices(res.data);
      setInvoicesTotal(res.totalItems);
    } catch (err) {
      setInvoicesError(
        err instanceof ApiError ? err.message : 'Unable to load invoices.',
      );
    } finally {
      setInvoicesLoading(false);
    }
  }, [canRead, invoiceStatusFilter, invoicesPage, token]);

  useEffect(() => {
    if (!canRead) return;
    if (activeTab === 'orders') void fetchOrders();
    else void fetchInvoices();
  }, [activeTab, canRead, fetchInvoices, fetchOrders]);

  if (!canRead) {
    return (
      <div className="foundation-stack">
        <PageHeader
          title="Sales & Finance"
          subtitle="Sales orders, invoice generation, and auditable payment recording."
          breadcrumbs={[
            { label: 'Workspace', to: '/dashboard' },
            { label: 'Finance' },
          ]}
          badge={<Badge variant="warning">Restricted</Badge>}
        />
        <Alert
          variant="warning"
          title="Insufficient permissions"
          description="Finance workflows require Manager, Administrator, or Owner permissions."
        />
      </div>
    );
  }

  const openCreateOrderModal = async () => {
    setCreateOrderOpen(true);
    setOrderFormError(null);
    if (!token) return;
    try {
      const [custRes, prodRes] = await Promise.all([
        apiRequest<PagedResponse<Customer>>(
          '/api/v1/crm/customers?status=ACTIVE&page=1&pageSize=100',
          { token },
        ),
        apiRequest<PagedResponse<Product>>(
          '/api/v1/inventory/products?active=true&page=1&pageSize=100',
          { token },
        ),
      ]);
      setCustomers(custRes.data);
      setProducts(prodRes.data);
    } catch (err) {
      setOrderFormError(
        err instanceof ApiError
          ? err.message
          : 'Unable to load customers or products.',
      );
    }
  };

  const handleCreateOrder = async (event: FormEvent) => {
    event.preventDefault();
    if (!token) return;
    setOrderFormError(null);

    const validLines = orderLines.filter(
      (line) => line.productId && Number(line.quantity) > 0,
    );
    if (!selectedCustomerId || validLines.length === 0) {
      setOrderFormError('Select a customer and at least one valid product line.');
      return;
    }

    setOrderSubmitting(true);
    try {
      const created = await apiRequest<SalesOrderDetail>('/api/v1/finance/orders', {
        method: 'POST',
        token,
        body: {
          customerId: selectedCustomerId,
          discountAmount: Number(discountAmount || 0),
          taxRatePercent: Number(taxRatePercent || 0),
          notes: orderNotes.trim() || undefined,
          confirmImmediately,
          items: validLines.map((l) => ({
            productId: l.productId,
            quantity: Number(l.quantity),
          })),
        },
      });
      setCreateOrderOpen(false);
      setSelectedCustomerId('');
      setOrderLines([{ productId: '', quantity: '1' }]);
      setDiscountAmount('0.00');
      setTaxRatePercent('0.00');
      setOrderNotes('');
      setConfirmImmediately(false);
      notify({
        variant: 'success',
        title: `Order ${created.orderNumber} created`,
        description: `Total calculated by backend: $${Number(created.totalAmount).toFixed(2)}.`,
      });
      setOrdersPage(1);
      await fetchOrders();
    } catch (err) {
      setOrderFormError(
        err instanceof ApiError ? err.message : 'Unable to create sales order.',
      );
    } finally {
      setOrderSubmitting(false);
    }
  };

  const openOrderDetail = async (orderId: string) => {
    if (!token) return;
    try {
      const detail = await apiRequest<SalesOrderDetail>(
        `/api/v1/finance/orders/${orderId}`,
        { token },
      );
      setSelectedOrder(detail);
    } catch (err) {
      notify({
        variant: 'error',
        title: 'Unable to load order',
        description: err instanceof ApiError ? err.message : 'Request failed.',
      });
    }
  };

  const openInvoiceDetail = async (invoiceId: string) => {
    if (!token) return;
    setPaymentError(null);
    try {
      const detail = await apiRequest<InvoiceDetail>(
        `/api/v1/finance/invoices/${invoiceId}`,
        { token },
      );
      setSelectedInvoice(detail);
      setPaymentAmount(
        detail.outstandingBalance > 0
          ? Number(detail.outstandingBalance).toFixed(2)
          : '',
      );
    } catch (err) {
      notify({
        variant: 'error',
        title: 'Unable to load invoice',
        description: err instanceof ApiError ? err.message : 'Request failed.',
      });
    }
  };

  const runOrderAction = async (action: 'confirm' | 'fulfill' | 'cancel') => {
    if (!token || !selectedOrder) return;
    setOrderActionLoading(true);
    try {
      const updated = await apiRequest<SalesOrderDetail>(
        `/api/v1/finance/orders/${selectedOrder.id}/${action}`,
        { method: 'POST', token },
      );
      setSelectedOrder(updated);
      notify({
        variant: 'success',
        title: `Order ${updated.orderNumber} updated`,
        description: `Status is now ${updated.status}.`,
      });
      await fetchOrders();
    } catch (err) {
      notify({
        variant: 'error',
        title: 'Order action rejected',
        description: err instanceof ApiError ? err.message : 'Action failed.',
      });
    } finally {
      setOrderActionLoading(false);
    }
  };

  const handleGenerateInvoice = async () => {
    if (!token || !selectedOrder) return;
    setOrderActionLoading(true);
    try {
      const invoice = await apiRequest<InvoiceDetail>(
        `/api/v1/finance/orders/${selectedOrder.id}/invoice`,
        { method: 'POST', token },
      );
      notify({
        variant: 'success',
        title: invoice.alreadyExisted
          ? `Opened existing ${invoice.invoiceNumber}`
          : `Generated ${invoice.invoiceNumber}`,
        description: `Total: $${Number(invoice.totalAmount).toFixed(2)}.`,
      });
      setSelectedOrder(null);
      setActiveTab('invoices');
      setSelectedInvoice(invoice);
      await fetchInvoices();
    } catch (err) {
      notify({
        variant: 'error',
        title: 'Invoice generation failed',
        description: err instanceof ApiError ? err.message : 'Unable to generate invoice.',
      });
    } finally {
      setOrderActionLoading(false);
    }
  };

  const handleRecordPayment = async (event: FormEvent) => {
    event.preventDefault();
    if (!token || !selectedInvoice) return;
    setPaymentError(null);
    if (!paymentAmount || Number(paymentAmount) <= 0) {
      setPaymentError('Enter a positive payment or refund amount.');
      return;
    }

    setPaymentSubmitting(true);
    try {
      await apiRequest(`/api/v1/finance/invoices/${selectedInvoice.id}/payments`, {
        method: 'POST',
        token,
        body: {
          entryType: paymentType,
          amount: Number(paymentAmount),
          method: paymentMethod,
          reference: paymentReference.trim() || undefined,
          notes: paymentNotes.trim() || undefined,
        },
      });
      setPaymentReference('');
      setPaymentNotes('');
      await openInvoiceDetail(selectedInvoice.id);
      await fetchInvoices();
      notify({
        variant: 'success',
        title: paymentType === 'PAYMENT' ? 'Payment recorded' : 'Refund recorded',
        description: 'Invoice balance reconciled.',
      });
    } catch (err) {
      setPaymentError(
        err instanceof ApiError ? err.message : 'Unable to record entry.',
      );
    } finally {
      setPaymentSubmitting(false);
    }
  };

  const downloadInvoiceText = (invoice: InvoiceDetail) => {
    const lines = [
      `INVOICE: ${invoice.invoiceNumber}`,
      `Status: ${invoice.status}`,
      `Issue Date: ${invoice.issueDate}`,
      `Due Date: ${invoice.dueDate ?? 'N/A'}`,
      `Customer: ${invoice.customerName}${invoice.customerEmail ? ` (${invoice.customerEmail})` : ''}`,
      '',
      'ITEMS:',
      ...invoice.items.map(
        (item) =>
          `- ${item.productSku} | ${item.description} | ${item.quantity} ${item.unitOfMeasure} x $${Number(item.unitPrice).toFixed(2)} = $${Number(item.lineTotal).toFixed(2)}`,
      ),
      '',
      `Subtotal: $${Number(invoice.subtotalAmount).toFixed(2)}`,
      `Discount: $${Number(invoice.discountAmount).toFixed(2)}`,
      `Tax (${Number(invoice.taxRatePercent).toFixed(2)}%): $${Number(invoice.taxAmount).toFixed(2)}`,
      `Total: $${Number(invoice.totalAmount).toFixed(2)}`,
      `Paid: $${Number(invoice.paidAmount).toFixed(2)}`,
      `Outstanding Balance: $${Number(invoice.outstandingBalance).toFixed(2)}`,
    ].join('\n');

    const blob = new Blob([lines], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `${invoice.invoiceNumber}.txt`;
    link.click();
    URL.revokeObjectURL(url);
  };

  const orderColumns: TableColumn<SalesOrderSummary>[] = [
    {
      key: 'orderNumber',
      header: 'Order #',
      render: (row) => (
        <div>
          <strong style={{ fontFamily: 'var(--font-mono)' }}>{row.orderNumber}</strong>
          <div style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>
            {row.customerName}
          </div>
        </div>
      ),
    },
    {
      key: 'orderDate',
      header: 'Date',
      render: (row) => row.orderDate,
    },
    {
      key: 'status',
      header: 'Status',
      render: (row) => (
        <Badge variant={orderStatusBadge(row.status)}>{row.status}</Badge>
      ),
    },
    {
      key: 'totalAmount',
      header: 'Total',
      align: 'right',
      render: (row) => `$${Number(row.totalAmount).toFixed(2)}`,
    },
    {
      key: 'invoice',
      header: 'Invoice',
      align: 'right',
      render: (row) => (
        <Badge variant={row.hasInvoice ? 'success' : 'neutral'}>
          {row.hasInvoice ? 'Invoiced' : 'Uninvoiced'}
        </Badge>
      ),
    },
  ];

  const invoiceColumns: TableColumn<InvoiceSummary>[] = [
    {
      key: 'invoiceNumber',
      header: 'Invoice #',
      render: (row) => (
        <div>
          <strong style={{ fontFamily: 'var(--font-mono)' }}>{row.invoiceNumber}</strong>
          <div style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>
            {row.customerName}
          </div>
        </div>
      ),
    },
    {
      key: 'issueDate',
      header: 'Issued',
      render: (row) => row.issueDate,
    },
    {
      key: 'status',
      header: 'Status',
      render: (row) => (
        <Badge variant={invoiceStatusBadge(row.status)}>{row.status}</Badge>
      ),
    },
    {
      key: 'totalAmount',
      header: 'Total',
      align: 'right',
      render: (row) => `$${Number(row.totalAmount).toFixed(2)}`,
    },
    {
      key: 'outstandingBalance',
      header: 'Balance Due',
      align: 'right',
      render: (row) => <strong>${Number(row.outstandingBalance).toFixed(2)}</strong>,
    },
  ];

  return (
    <div className="foundation-stack">
      <PageHeader
        title="Sales & Finance"
        subtitle="Sales orders, inventory-backed fulfillment, sequential invoicing, and payment ledgers."
        breadcrumbs={[
          { label: 'Workspace', to: '/dashboard' },
          { label: 'Finance' },
        ]}
        badge={<Badge variant="success">Active</Badge>}
        actions={
          canWrite ? (
            <Button
              variant="primary"
              leftIcon={<Plus size={16} aria-hidden="true" />}
              onClick={() => void openCreateOrderModal()}
            >
              New Sales Order
            </Button>
          ) : null
        }
      />

      <div className="foundation-row" role="tablist" aria-label="Finance views">
        <Button
          role="tab"
          aria-selected={activeTab === 'orders'}
          variant={activeTab === 'orders' ? 'primary' : 'secondary'}
          onClick={() => setActiveTab('orders')}
        >
          Sales Orders
        </Button>
        <Button
          role="tab"
          aria-selected={activeTab === 'invoices'}
          variant={activeTab === 'invoices' ? 'primary' : 'secondary'}
          onClick={() => setActiveTab('invoices')}
        >
          Invoices
        </Button>
      </div>

      {activeTab === 'orders' ? (
        <Card>
          <CardContent className="foundation-stack">
            <div style={{ maxWidth: '14rem' }}>
              <Select
                label="Order Status"
                value={orderStatusFilter}
                options={[
                  { value: '', label: 'All statuses' },
                  { value: 'DRAFT', label: 'Draft' },
                  { value: 'CONFIRMED', label: 'Confirmed' },
                  { value: 'FULFILLED', label: 'Fulfilled' },
                  { value: 'CANCELLED', label: 'Cancelled' },
                ]}
                onChange={(event) => {
                  setOrderStatusFilter(event.target.value);
                  setOrdersPage(1);
                }}
              />
            </div>

            <Table
              caption="Sales orders"
              columns={orderColumns}
              rows={orders}
              getRowKey={(row) => row.id}
              loading={ordersLoading}
              error={ordersError}
              onRetry={() => void fetchOrders()}
              emptyTitle="No sales orders yet"
              emptyDescription="Create a sales order for an existing customer to begin order-to-cash tracking."
              onRowClick={(row) => void openOrderDetail(row.id)}
            />

            {!ordersLoading && !ordersError && ordersTotal > 0 ? (
              <Pagination
                page={ordersPage}
                pageSize={pageSize}
                totalItems={ordersTotal}
                onPageChange={setOrdersPage}
              />
            ) : null}
          </CardContent>
        </Card>
      ) : (
        <Card>
          <CardContent className="foundation-stack">
            <div style={{ maxWidth: '14rem' }}>
              <Select
                label="Invoice Status"
                value={invoiceStatusFilter}
                options={[
                  { value: '', label: 'All statuses' },
                  { value: 'ISSUED', label: 'Issued' },
                  { value: 'PARTIALLY_PAID', label: 'Partially Paid' },
                  { value: 'PAID', label: 'Paid' },
                  { value: 'REFUNDED', label: 'Refunded' },
                  { value: 'VOID', label: 'Void' },
                ]}
                onChange={(event) => {
                  setInvoiceStatusFilter(event.target.value);
                  setInvoicesPage(1);
                }}
              />
            </div>

            <Table
              caption="Invoices"
              columns={invoiceColumns}
              rows={invoices}
              getRowKey={(row) => row.id}
              loading={invoicesLoading}
              error={invoicesError}
              onRetry={() => void fetchInvoices()}
              emptyTitle="No invoices generated"
              emptyDescription="Confirm a sales order and generate an invoice from its detail view."
              onRowClick={(row) => void openInvoiceDetail(row.id)}
            />

            {!invoicesLoading && !invoicesError && invoicesTotal > 0 ? (
              <Pagination
                page={invoicesPage}
                pageSize={pageSize}
                totalItems={invoicesTotal}
                onPageChange={setInvoicesPage}
              />
            ) : null}
          </CardContent>
        </Card>
      )}

      {/* Create Sales Order Modal */}
      <Dialog
        open={createOrderOpen}
        onClose={() => setCreateOrderOpen(false)}
        title="Create Sales Order"
        description="Line prices and totals are calculated authoritatively on the backend."
        size="lg"
      >
        <form onSubmit={handleCreateOrder} className="foundation-stack" noValidate>
          {orderFormError ? (
            <Alert variant="error" title="Order validation failed" description={orderFormError} />
          ) : null}
          <Select
            label="Customer"
            required
            placeholder="Select a customer..."
            value={selectedCustomerId}
            options={customers.map((c) => ({
              value: c.id,
              label: c.companyName ? `${c.name} (${c.companyName})` : c.name,
            }))}
            onChange={(event) => setSelectedCustomerId(event.target.value)}
          />

          <div className="foundation-stack">
            <strong style={{ fontSize: 'var(--text-sm)' }}>Order Items</strong>
            {orderLines.map((line, index) => (
              <div key={index} className="foundation-row" style={{ alignItems: 'flex-end' }}>
                <div style={{ flex: '1 1 14rem' }}>
                  <Select
                    label={`Product #${index + 1}`}
                    placeholder="Select product..."
                    value={line.productId}
                    options={products.map((p) => ({
                      value: p.id,
                      label: `${p.sku} — ${p.name} ($${Number(p.unitPrice).toFixed(2)}, Avail: ${p.stockQuantity})`,
                    }))}
                    onChange={(event) => {
                      const next = [...orderLines];
                      next[index] = { ...next[index], productId: event.target.value };
                      setOrderLines(next);
                    }}
                  />
                </div>
                <div style={{ width: '7rem' }}>
                  <FormField
                    label="Qty"
                    type="number"
                    step="0.001"
                    min="0.001"
                    value={line.quantity}
                    onChange={(event) => {
                      const next = [...orderLines];
                      next[index] = { ...next[index], quantity: event.target.value };
                      setOrderLines(next);
                    }}
                  />
                </div>
              </div>
            ))}
            <div>
              <Button
                variant="secondary"
                size="sm"
                onClick={() =>
                  setOrderLines([...orderLines, { productId: '', quantity: '1' }])
                }
              >
                Add Line Item
              </Button>
            </div>
          </div>

          <div className="foundation-row">
            <FormField
              label="Discount Amount"
              type="number"
              step="0.01"
              min="0"
              value={discountAmount}
              onChange={(event) => setDiscountAmount(event.target.value)}
            />
            <FormField
              label="Tax Rate (%)"
              type="number"
              step="0.01"
              min="0"
              max="100"
              hint="Simple configurable percentage rate."
              value={taxRatePercent}
              onChange={(event) => setTaxRatePercent(event.target.value)}
            />
          </div>

          <TextareaField
            label="Order Notes"
            value={orderNotes}
            onChange={(event) => setOrderNotes(event.target.value)}
          />

          <label style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)' }}>
            <input
              type="checkbox"
              checked={confirmImmediately}
              onChange={(event) => setConfirmImmediately(event.target.checked)}
            />
            <span>Confirm immediately and deduct stock</span>
          </label>

          <div className="foundation-row" style={{ justifyContent: 'flex-end' }}>
            <Button variant="secondary" onClick={() => setCreateOrderOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" variant="primary" loading={orderSubmitting}>
              Create Order
            </Button>
          </div>
        </form>
      </Dialog>

      {/* Sales Order Detail Modal */}
      <Dialog
        open={Boolean(selectedOrder)}
        onClose={() => setSelectedOrder(null)}
        title={selectedOrder ? `Order ${selectedOrder.orderNumber}` : 'Order'}
        description={
          selectedOrder
            ? `${selectedOrder.customerName} · ${selectedOrder.orderDate}`
            : undefined
        }
        size="lg"
      >
        {selectedOrder ? (
          <div className="foundation-stack">
            <div className="foundation-row" style={{ justifyContent: 'space-between' }}>
              <div className="foundation-row">
                <Badge variant={orderStatusBadge(selectedOrder.status)} size="md">
                  {selectedOrder.status}
                </Badge>
                <Badge variant={selectedOrder.stockDeducted ? 'info' : 'neutral'} size="md">
                  {selectedOrder.stockDeducted ? 'Stock Deducted' : 'Stock Not Deducted'}
                </Badge>
              </div>
              {canWrite ? (
                <div className="foundation-row">
                  {selectedOrder.status === 'DRAFT' ? (
                    <Button
                      variant="primary"
                      size="sm"
                      loading={orderActionLoading}
                      onClick={() => void runOrderAction('confirm')}
                    >
                      Confirm &amp; Deduct Stock
                    </Button>
                  ) : null}
                  {selectedOrder.status === 'CONFIRMED' ? (
                    <Button
                      variant="secondary"
                      size="sm"
                      loading={orderActionLoading}
                      onClick={() => void runOrderAction('fulfill')}
                    >
                      Mark Fulfilled
                    </Button>
                  ) : null}
                  {(selectedOrder.status === 'CONFIRMED' ||
                    selectedOrder.status === 'FULFILLED') ? (
                    <Button
                      variant="primary"
                      size="sm"
                      loading={orderActionLoading}
                      leftIcon={<FileText size={14} aria-hidden="true" />}
                      onClick={() => void handleGenerateInvoice()}
                    >
                      {selectedOrder.invoiceId ? 'View Invoice' : 'Generate Invoice'}
                    </Button>
                  ) : null}
                  {(selectedOrder.status === 'DRAFT' ||
                    (selectedOrder.status === 'CONFIRMED' && !selectedOrder.invoiceId)) ? (
                    <Button
                      variant="danger"
                      size="sm"
                      loading={orderActionLoading}
                      onClick={() => void runOrderAction('cancel')}
                    >
                      Cancel Order
                    </Button>
                  ) : null}
                </div>
              ) : null}
            </div>

            <div className="ui-table-wrapper">
              <table className="ui-table">
                <thead>
                  <tr>
                    <th>SKU / Item</th>
                    <th className="ui-table__align-right">Qty</th>
                    <th className="ui-table__align-right">Unit Price</th>
                    <th className="ui-table__align-right">Line Total</th>
                  </tr>
                </thead>
                <tbody>
                  {selectedOrder.items.map((item) => (
                    <tr key={item.id}>
                      <td>
                        <strong>{item.productName}</strong> ({item.productSku})
                      </td>
                      <td className="ui-table__align-right">
                        {item.quantity} {item.unitOfMeasure}
                      </td>
                      <td className="ui-table__align-right">
                        ${Number(item.unitPrice).toFixed(2)}
                      </td>
                      <td className="ui-table__align-right">
                        ${Number(item.lineTotal).toFixed(2)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <div style={{ textAlign: 'right', fontSize: 'var(--text-sm)' }}>
              <div>Subtotal: ${Number(selectedOrder.subtotalAmount).toFixed(2)}</div>
              <div>Discount: -${Number(selectedOrder.discountAmount).toFixed(2)}</div>
              <div>
                Tax ({Number(selectedOrder.taxRatePercent).toFixed(2)}%): $
                {Number(selectedOrder.taxAmount).toFixed(2)}
              </div>
              <strong style={{ fontSize: 'var(--text-md)' }}>
                Total: ${Number(selectedOrder.totalAmount).toFixed(2)}
              </strong>
            </div>

            <div className="foundation-stack">
              <strong style={{ fontSize: 'var(--text-sm)' }}>Order History</strong>
              {selectedOrder.history.map((h) => (
                <div
                  key={h.id}
                  style={{
                    fontSize: 'var(--text-xs)',
                    color: 'var(--color-text-secondary)',
                  }}
                >
                  {new Date(h.createdAt).toLocaleString()} ·{' '}
                  {h.fromStatus ? `${h.fromStatus} → ` : ''}
                  <strong>{h.toStatus}</strong>
                  {h.note ? ` — ${h.note}` : ''} ({h.actorName ?? 'System'})
                </div>
              ))}
            </div>
          </div>
        ) : null}
      </Dialog>

      {/* Invoice Detail & Payment Ledger Modal */}
      <Dialog
        open={Boolean(selectedInvoice)}
        onClose={() => setSelectedInvoice(null)}
        title={selectedInvoice ? `Invoice ${selectedInvoice.invoiceNumber}` : 'Invoice'}
        description={
          selectedInvoice
            ? `Customer: ${selectedInvoice.customerName} · Issued ${selectedInvoice.issueDate}`
            : undefined
        }
        size="lg"
      >
        {selectedInvoice ? (
          <div className="foundation-stack">
            <div className="foundation-row" style={{ justifyContent: 'space-between' }}>
              <Badge variant={invoiceStatusBadge(selectedInvoice.status)} size="md">
                {selectedInvoice.status}
              </Badge>
              <div className="foundation-row">
                <Button
                  variant="secondary"
                  size="sm"
                  leftIcon={<Printer size={14} aria-hidden="true" />}
                  onClick={() => window.print()}
                >
                  Print
                </Button>
                <Button
                  variant="secondary"
                  size="sm"
                  leftIcon={<Download size={14} aria-hidden="true" />}
                  onClick={() => downloadInvoiceText(selectedInvoice)}
                >
                  Download
                </Button>
              </div>
            </div>

            <div className="ui-table-wrapper">
              <table className="ui-table">
                <thead>
                  <tr>
                    <th>Description</th>
                    <th className="ui-table__align-right">Qty</th>
                    <th className="ui-table__align-right">Unit Price</th>
                    <th className="ui-table__align-right">Total</th>
                  </tr>
                </thead>
                <tbody>
                  {selectedInvoice.items.map((item) => (
                    <tr key={item.id}>
                      <td>
                        {item.description} ({item.productSku})
                      </td>
                      <td className="ui-table__align-right">
                        {item.quantity} {item.unitOfMeasure}
                      </td>
                      <td className="ui-table__align-right">
                        ${Number(item.unitPrice).toFixed(2)}
                      </td>
                      <td className="ui-table__align-right">
                        ${Number(item.lineTotal).toFixed(2)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <div style={{ textAlign: 'right', fontSize: 'var(--text-sm)' }}>
              <div>Total: ${Number(selectedInvoice.totalAmount).toFixed(2)}</div>
              <div>Paid: ${Number(selectedInvoice.paidAmount).toFixed(2)}</div>
              <strong style={{ fontSize: 'var(--text-md)' }}>
                Outstanding Balance: ${Number(selectedInvoice.outstandingBalance).toFixed(2)}
              </strong>
            </div>

            {canWrite && selectedInvoice.status !== 'VOID' ? (
              <form onSubmit={handleRecordPayment} className="foundation-stack">
                <strong style={{ fontSize: 'var(--text-sm)' }}>
                  Record Confirmed Payment / Refund
                </strong>
                {paymentError ? (
                  <Alert
                    variant="error"
                    title="Payment rejected"
                    description={paymentError}
                  />
                ) : null}
                <div className="foundation-row">
                  <Select
                    label="Entry Type"
                    value={paymentType}
                    options={[
                      { value: 'PAYMENT', label: 'Payment' },
                      { value: 'REFUND', label: 'Refund' },
                    ]}
                    onChange={(event) =>
                      setPaymentType(event.target.value as PaymentEntryType)
                    }
                  />
                  <FormField
                    label="Amount"
                    type="number"
                    step="0.01"
                    min="0.01"
                    required
                    value={paymentAmount}
                    onChange={(event) => setPaymentAmount(event.target.value)}
                  />
                  <Select
                    label="Method"
                    value={paymentMethod}
                    options={[
                      { value: 'BANK_TRANSFER', label: 'Bank Transfer' },
                      { value: 'CARD', label: 'Card' },
                      { value: 'CASH', label: 'Cash' },
                      { value: 'CHECK', label: 'Check' },
                      { value: 'OTHER', label: 'Other' },
                    ]}
                    onChange={(event) =>
                      setPaymentMethod(event.target.value as PaymentMethod)
                    }
                  />
                </div>
                <div className="foundation-row" style={{ alignItems: 'flex-end' }}>
                  <FormField
                    label="Reference"
                    placeholder="Transaction / wire reference"
                    value={paymentReference}
                    onChange={(event) => setPaymentReference(event.target.value)}
                  />
                  <Button type="submit" variant="primary" loading={paymentSubmitting}>
                    Record Entry
                  </Button>
                </div>
              </form>
            ) : null}

            <div className="foundation-stack">
              <strong style={{ fontSize: 'var(--text-sm)' }}>Payment History</strong>
              {selectedInvoice.payments.length === 0 ? (
                <span style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>
                  No payments or refunds recorded yet.
                </span>
              ) : (
                selectedInvoice.payments.map((p) => (
                  <div
                    key={p.id}
                    style={{
                      padding: 'var(--space-2) var(--space-3)',
                      border: '1px solid var(--color-border)',
                      borderRadius: 'var(--radius-md)',
                      fontSize: 'var(--text-xs)',
                    }}
                  >
                    <Badge variant={p.entryType === 'PAYMENT' ? 'success' : 'warning'}>
                      {p.entryType}
                    </Badge>{' '}
                    <strong>${Number(p.amount).toFixed(2)}</strong> via {p.method} on{' '}
                    {p.paymentDate}
                    {p.reference ? ` · Ref: ${p.reference}` : ''} ·{' '}
                    {p.recordedByName ?? 'Member'}
                  </div>
                ))
              )}
            </div>
          </div>
        ) : null}
      </Dialog>
    </div>
  );
}

