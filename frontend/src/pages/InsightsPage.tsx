import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { Download } from 'lucide-react';
import { useAuth } from '../app/authContext';
import {
  Alert,
  Badge,
  Button,
  Card,
  CardContent,
  CardHeader,
  FormField,
  PageHeader,
  Pagination,
  Select,
  StateBoundary,
  Table,
  useToast,
  type ScreenStatus,
  type TableColumn,
} from '../components/ui';
import { apiRequest, ApiError, getApiBaseUrl } from '../lib/apiClient';
import type { PagedResponse } from '../lib/crmTypes';
import type { Product, StockMovement } from '../lib/inventoryTypes';
import { AssistantPanel } from '../components/insights/AssistantPanel';
import { AutomationPanel } from '../components/insights/AutomationPanel';
import type {
  ForecastPoint,
  ForecastResponse,
  InventoryStockReport,
  InventoryStockRow,
  LeadPipelineReport,
  LeadPipelineRow,
  OrderStatusReportRow,
  OrdersByStatusReport,
  ReceivablesReport,
  SalesOverTimePoint,
  SalesOverTimeReport,
} from '../lib/insightsTypes';

type InsightsTab = 'reports' | 'forecasting' | 'assistant' | 'automation';

function SimpleBarChart({
  title,
  items,
  isPrediction = false,
}: {
  title: string;
  items: { label: string; value: number; secondary?: string }[];
  isPrediction?: boolean;
}) {
  const maxVal = Math.max(1, ...items.map((i) => i.value));
  if (items.length === 0) {
    return (
      <span style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>
        No data points in selected period.
      </span>
    );
  }

  return (
    <div className="foundation-stack" aria-label={title}>
      {items.map((item) => {
        const pct = Math.max(2, Math.round((item.value / maxVal) * 100));
        return (
          <div key={item.label} style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-3)' }}>
            <span
              style={{
                width: '6.5rem',
                fontSize: 'var(--text-xs)',
                fontFamily: 'var(--font-mono)',
                color: 'var(--color-text-secondary)',
                flexShrink: 0,
              }}
            >
              {item.label}
            </span>
            <div
              style={{
                flex: 1,
                height: '0.85rem',
                backgroundColor: 'var(--color-bg-secondary)',
                borderRadius: 'var(--radius-sm)',
                overflow: 'hidden',
              }}
            >
              <div
                style={{
                  width: `${pct}%`,
                  height: '100%',
                  backgroundColor: isPrediction
                    ? 'var(--color-status-warning)'
                    : 'var(--color-action-primary)',
                }}
              />
            </div>
            <span
              style={{
                width: '6.5rem',
                textAlign: 'right',
                fontSize: 'var(--text-xs)',
                fontWeight: 'var(--weight-medium)',
                flexShrink: 0,
              }}
            >
              ${Number(item.value).toFixed(2)}
            </span>
          </div>
        );
      })}
    </div>
  );
}

export function InsightsPage() {
  const { token, hasPermission } = useAuth();
  const { notify } = useToast();
  const canRead = hasPermission('INSIGHTS_READ');

  const [activeTab, setActiveTab] = useState<InsightsTab>('reports');
  const [fromDate, setFromDate] = useState('');
  const [toDate, setToDate] = useState('');

  // Reports state
  const [reportStatus, setReportStatus] = useState<ScreenStatus>('loading');
  const [reportError, setReportError] = useState<string | null>(null);
  const [salesReport, setSalesReport] = useState<SalesOverTimeReport | null>(null);
  const [ordersReport, setOrdersReport] = useState<OrdersByStatusReport | null>(null);
  const [receivables, setReceivables] = useState<ReceivablesReport | null>(null);
  const [pipeline, setPipeline] = useState<LeadPipelineReport | null>(null);
  const [stockReport, setStockReport] = useState<InventoryStockReport | null>(null);
  const [movementsPage, setMovementsPage] = useState(1);
  const [movements, setMovements] = useState<StockMovement[]>([]);
  const [movementsTotal, setMovementsTotal] = useState(0);

  // Forecasting state
  const [horizonDays, setHorizonDays] = useState('14');
  const [salesForecast, setSalesForecast] = useState<ForecastResponse | null>(null);
  const [forecastStatus, setForecastStatus] = useState<ScreenStatus>('loading');
  const [forecastError, setForecastError] = useState<string | null>(null);
  const [products, setProducts] = useState<Product[]>([]);
  const [selectedProductId, setSelectedProductId] = useState('');
  const [productForecast, setProductForecast] = useState<ForecastResponse | null>(null);

  const loadReports = useCallback(async () => {
    if (!token || !canRead) return;
    setReportStatus('loading');
    setReportError(null);

    const rangeParams = new URLSearchParams();
    if (fromDate) rangeParams.set('from', fromDate);
    if (toDate) rangeParams.set('to', toDate);
    const rangeQs = rangeParams.toString() ? `?${rangeParams.toString()}` : '';

    const movParams = new URLSearchParams(rangeParams);
    movParams.set('page', String(movementsPage));
    movParams.set('pageSize', '10');

    try {
      const [sales, orders, recv, pipe, stock, movs] = await Promise.all([
        apiRequest<SalesOverTimeReport>(
          `/api/v1/insights/reports/sales-over-time${rangeQs}`,
          { token },
        ),
        apiRequest<OrdersByStatusReport>(
          `/api/v1/insights/reports/orders-by-status${rangeQs}`,
          { token },
        ),
        apiRequest<ReceivablesReport>(
          `/api/v1/insights/reports/receivables${rangeQs}`,
          { token },
        ),
        apiRequest<LeadPipelineReport>(
          '/api/v1/insights/reports/lead-pipeline',
          { token },
        ),
        apiRequest<InventoryStockReport>(
          '/api/v1/insights/reports/inventory-stock?page=1&pageSize=20',
          { token },
        ),
        apiRequest<PagedResponse<StockMovement>>(
          `/api/v1/insights/reports/stock-movements?${movParams.toString()}`,
          { token },
        ),
      ]);
      setSalesReport(sales);
      setOrdersReport(orders);
      setReceivables(recv);
      setPipeline(pipe);
      setStockReport(stock);
      setMovements(movs.data);
      setMovementsTotal(movs.totalItems);
      setReportStatus('success');
    } catch (err) {
      setReportError(
        err instanceof ApiError ? err.message : 'Unable to load reports.',
      );
      setReportStatus('error');
    }
  }, [canRead, fromDate, movementsPage, toDate, token]);

  const loadForecasts = useCallback(async () => {
    if (!token || !canRead) return;
    setForecastStatus('loading');
    setForecastError(null);
    try {
      const [sf, prodRes] = await Promise.all([
        apiRequest<ForecastResponse>(
          `/api/v1/insights/forecast/sales?horizonDays=${horizonDays}`,
          { token },
        ),
        apiRequest<PagedResponse<Product>>(
          '/api/v1/inventory/products?active=true&page=1&pageSize=100',
          { token },
        ),
      ]);
      setSalesForecast(sf);
      setProducts(prodRes.data);
      setForecastStatus('success');
    } catch (err) {
      setForecastError(
        err instanceof ApiError ? err.message : 'Unable to query forecasting.',
      );
      setForecastStatus('error');
    }
  }, [canRead, horizonDays, token]);

  useEffect(() => {
    if (!canRead) return;
    if (activeTab === 'reports') void loadReports();
    else void loadForecasts();
  }, [activeTab, canRead, loadForecasts, loadReports]);

  if (!canRead) {
    return (
      <div className="foundation-stack">
        <PageHeader
          title="Insights & Forecasting"
          subtitle="Organization-scoped business reports, CSV exports, and demand forecasting."
          breadcrumbs={[
            { label: 'Workspace', to: '/dashboard' },
            { label: 'Insights' },
          ]}
          badge={<Badge variant="warning">Restricted</Badge>}
        />
        <Alert
          variant="warning"
          title="Insufficient permissions"
          description="Reports and forecasting require Manager, Administrator, or Owner permissions."
        />
      </div>
    );
  }

  const handleExportCsv = async (reportType: string) => {
    if (!token) return;
    const params = new URLSearchParams();
    if (fromDate) params.set('from', fromDate);
    if (toDate) params.set('to', toDate);
    const qs = params.toString() ? `?${params.toString()}` : '';

    try {
      const res = await fetch(
        `${getApiBaseUrl()}/api/v1/insights/reports/export/${reportType}${qs}`,
        { headers: { Authorization: `Bearer ${token}` } },
      );
      if (!res.ok) {
        throw new Error(`Export failed (${res.status})`);
      }
      const csv = await res.text();
      const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `${reportType}.csv`;
      a.click();
      URL.revokeObjectURL(url);
      notify({
        variant: 'success',
        title: 'CSV exported',
        description: `Downloaded ${reportType}.csv.`,
      });
    } catch (err) {
      notify({
        variant: 'error',
        title: 'CSV export failed',
        description: err instanceof Error ? err.message : 'Unable to export CSV.',
      });
    }
  };

  const handleProductForecast = async (productId: string) => {
    setSelectedProductId(productId);
    if (!token || !productId) {
      setProductForecast(null);
      return;
    }
    try {
      const res = await apiRequest<ForecastResponse>(
        `/api/v1/insights/forecast/inventory/${productId}?horizonDays=${horizonDays}`,
        { token },
      );
      setProductForecast(res);
    } catch (err) {
      notify({
        variant: 'error',
        title: 'Product forecast failed',
        description: err instanceof ApiError ? err.message : 'Request failed.',
      });
    }
  };

  const orderColumns: TableColumn<OrderStatusReportRow>[] = [
    { key: 'status', header: 'Order Status', render: (r) => <Badge>{r.status}</Badge> },
    { key: 'count', header: 'Orders', align: 'right', render: (r) => r.count },
    {
      key: 'totalAmount',
      header: 'Total Value',
      align: 'right',
      render: (r) => `$${Number(r.totalAmount).toFixed(2)}`,
    },
  ];

  const pipelineColumns: TableColumn<LeadPipelineRow>[] = [
    { key: 'status', header: 'Stage', render: (r) => <Badge>{r.status}</Badge> },
    { key: 'count', header: 'Leads', align: 'right', render: (r) => r.count },
    {
      key: 'estimatedValue',
      header: 'Est. Value',
      align: 'right',
      render: (r) => `$${Number(r.estimatedValue).toFixed(2)}`,
    },
  ];

  const stockColumns: TableColumn<InventoryStockRow>[] = [
    {
      key: 'sku',
      header: 'SKU / Product',
      render: (r) => (
        <div>
          <strong>{r.name}</strong>
          <div style={{ fontSize: 'var(--text-xs)', fontFamily: 'var(--font-mono)', color: 'var(--color-text-secondary)' }}>
            {r.sku}
          </div>
        </div>
      ),
    },
    {
      key: 'qty',
      header: 'On Hand',
      align: 'right',
      render: (r) => `${r.stockQuantity} ${r.unitOfMeasure}`,
    },
    {
      key: 'status',
      header: 'Status',
      render: (r) =>
        r.lowStock ? (
          <Badge variant="warning">Low (≤ {r.lowStockThreshold})</Badge>
        ) : (
          <Badge variant="success">Healthy</Badge>
        ),
    },
    {
      key: 'value',
      header: 'Stock Value',
      align: 'right',
      render: (r) => `$${Number(r.stockValue).toFixed(2)}`,
    },
  ];

  const movementColumns: TableColumn<StockMovement>[] = [
    { key: 'ts', header: 'Timestamp', render: (r) => new Date(r.createdAt).toLocaleString() },
    { key: 'sku', header: 'SKU', render: (r) => r.productSku },
    { key: 'type', header: 'Type', render: (r) => <Badge>{r.movementType}</Badge> },
    { key: 'delta', header: 'Delta', align: 'right', render: (r) => r.quantityDelta },
    { key: 'reason', header: 'Reason', render: (r) => r.reason },
  ];

  const forecastColumns: TableColumn<ForecastPoint>[] = [
    { key: 'date', header: 'Forecast Date (UTC)', render: (r) => r.date },
    {
      key: 'predicted',
      header: 'Model Estimate',
      align: 'right',
      render: (r) => <strong>{Number(r.predictedValue).toFixed(2)}</strong>,
    },
    {
      key: 'baseline',
      header: '7-Day Moving Avg Baseline',
      align: 'right',
      render: (r) => Number(r.baselineValue).toFixed(2),
    },
  ];

  return (
    <div className="foundation-stack">
      <PageHeader
        title="Insights, Forecasting & AI Assistant"
        subtitle="Reconciled organization reports, holdout-evaluated forecasting, controlled AI assistant, and deterministic automation."
        breadcrumbs={[
          { label: 'Workspace', to: '/dashboard' },
          { label: 'Insights' },
        ]}
        badge={<Badge variant="success">Active</Badge>}
      />

      <div className="foundation-row" role="tablist" aria-label="Insights views" style={{ flexWrap: 'wrap' }}>
        <Button
          role="tab"
          aria-selected={activeTab === 'reports'}
          variant={activeTab === 'reports' ? 'primary' : 'secondary'}
          onClick={() => setActiveTab('reports')}
        >
          Business Reports
        </Button>
        <Button
          role="tab"
          aria-selected={activeTab === 'forecasting'}
          variant={activeTab === 'forecasting' ? 'primary' : 'secondary'}
          onClick={() => setActiveTab('forecasting')}
        >
          Forecasting
        </Button>
        <Button
          role="tab"
          aria-selected={activeTab === 'assistant'}
          variant={activeTab === 'assistant' ? 'primary' : 'secondary'}
          onClick={() => setActiveTab('assistant')}
        >
          AI Assistant
        </Button>
        <Button
          role="tab"
          aria-selected={activeTab === 'automation'}
          variant={activeTab === 'automation' ? 'primary' : 'secondary'}
          onClick={() => setActiveTab('automation')}
        >
          Automation
        </Button>
      </div>

      {activeTab === 'reports' ? (
        <div className="foundation-stack">
          <Card>
            <CardContent compact>
              <form
                onSubmit={(e: FormEvent) => {
                  e.preventDefault();
                  void loadReports();
                }}
                className="foundation-row"
                style={{ alignItems: 'flex-end' }}
              >
                <FormField
                  label="From (UTC)"
                  type="date"
                  value={fromDate}
                  onChange={(e) => setFromDate(e.target.value)}
                />
                <FormField
                  label="To (UTC)"
                  type="date"
                  value={toDate}
                  onChange={(e) => setToDate(e.target.value)}
                />
                <Button type="submit" variant="secondary">
                  Refresh Reports
                </Button>
              </form>
            </CardContent>
          </Card>

          <StateBoundary
            status={reportStatus}
            errorTitle="Reports unavailable"
            errorMessage={reportError ?? undefined}
            onRetry={() => void loadReports()}
          >
            <div className="foundation-grid">
              {salesReport ? (
                <div className="foundation-col-12">
                  <Card>
                    <CardHeader
                      title={`Sales Over Time (${salesReport.periodStart} to ${salesReport.periodEnd})`}
                      description={`${salesReport.definition} Total: $${Number(salesReport.totalRevenue).toFixed(2)} across ${salesReport.totalInvoices} invoice(s).`}
                      actions={
                        <Button
                          variant="secondary"
                          size="sm"
                          leftIcon={<Download size={14} aria-hidden="true" />}
                          onClick={() => void handleExportCsv('sales-over-time')}
                        >
                          Export CSV
                        </Button>
                      }
                    />
                    <CardContent>
                      <SimpleBarChart
                        title="Actual daily invoiced revenue"
                        items={salesReport.series.map((s: SalesOverTimePoint) => ({
                          label: s.date,
                          value: Number(s.revenue),
                        }))}
                      />
                    </CardContent>
                  </Card>
                </div>
              ) : null}

              {ordersReport ? (
                <div className="foundation-col-6">
                  <Card>
                    <CardHeader
                      title="Orders by Status"
                      description={`${ordersReport.totalOrders} order(s), $${Number(ordersReport.totalValue).toFixed(2)} total.`}
                    />
                    <CardContent>
                      <Table
                        caption="Orders by status"
                        columns={orderColumns}
                        rows={ordersReport.rows}
                        getRowKey={(r) => r.status}
                      />
                    </CardContent>
                  </Card>
                </div>
              ) : null}

              {pipeline ? (
                <div className="foundation-col-6">
                  <Card>
                    <CardHeader
                      title="Lead Conversion & Pipeline"
                      description={`Total: ${pipeline.totalLeads} · Open: ${pipeline.openLeads} · Conversion: ${Number(pipeline.conversionRatePercent).toFixed(2)}%`}
                      actions={
                        <Button
                          variant="secondary"
                          size="sm"
                          leftIcon={<Download size={14} aria-hidden="true" />}
                          onClick={() => void handleExportCsv('lead-pipeline')}
                        >
                          CSV
                        </Button>
                      }
                    />
                    <CardContent>
                      <Table
                        caption="Lead pipeline"
                        columns={pipelineColumns}
                        rows={pipeline.stages}
                        getRowKey={(r) => r.status}
                      />
                    </CardContent>
                  </Card>
                </div>
              ) : null}

              {receivables ? (
                <div className="foundation-col-12">
                  <Card>
                    <CardHeader
                      title="Outstanding Invoices & Recorded Payments"
                      description={`Outstanding: $${Number(receivables.outstandingTotal).toFixed(2)} · Payments in period: $${Number(receivables.totalPaymentsInPeriod).toFixed(2)} · Refunds: $${Number(receivables.totalRefundsInPeriod).toFixed(2)}`}
                      actions={
                        <div className="foundation-row">
                          <Button
                            variant="secondary"
                            size="sm"
                            leftIcon={<Download size={14} aria-hidden="true" />}
                            onClick={() => void handleExportCsv('outstanding-invoices')}
                          >
                            Export Outstanding CSV
                          </Button>
                          <Button
                            variant="secondary"
                            size="sm"
                            leftIcon={<Download size={14} aria-hidden="true" />}
                            onClick={() => void handleExportCsv('payments')}
                          >
                            Export Payments CSV
                          </Button>
                        </div>
                      }
                    />
                    <CardContent>
                      <span style={{ fontSize: 'var(--text-sm)', color: 'var(--color-text-secondary)' }}>
                        {receivables.outstandingInvoices.length} open invoice(s) and{' '}
                        {receivables.paymentsInPeriod.length} recorded ledger entry/entries in range.
                      </span>
                    </CardContent>
                  </Card>
                </div>
              ) : null}

              {stockReport ? (
                <div className="foundation-col-12">
                  <Card>
                    <CardHeader
                      title="Current Stock & Low-Stock Valuation"
                      description={`${stockReport.totalSkus} active SKUs · ${stockReport.lowStockCount} low-stock · Valuation: $${Number(stockReport.totalStockValue).toFixed(2)}`}
                      actions={
                        <Button
                          variant="secondary"
                          size="sm"
                          leftIcon={<Download size={14} aria-hidden="true" />}
                          onClick={() => void handleExportCsv('inventory-stock')}
                        >
                          Export CSV
                        </Button>
                      }
                    />
                    <CardContent>
                      <Table
                        caption="Inventory stock report"
                        columns={stockColumns}
                        rows={stockReport.items}
                        getRowKey={(r) => r.id}
                      />
                    </CardContent>
                  </Card>
                </div>
              ) : null}

              <div className="foundation-col-12">
                <Card>
                  <CardHeader
                    title="Stock Movement History Report"
                    actions={
                      <Button
                        variant="secondary"
                        size="sm"
                        leftIcon={<Download size={14} aria-hidden="true" />}
                        onClick={() => void handleExportCsv('stock-movements')}
                      >
                        Export CSV
                      </Button>
                    }
                  />
                  <CardContent className="foundation-stack">
                    <Table
                      caption="Stock movement history report"
                      columns={movementColumns}
                      rows={movements}
                      getRowKey={(r) => r.id}
                      emptyTitle="No movements in range"
                      emptyDescription="No inventory movements were recorded during the selected UTC window."
                    />
                    {movementsTotal > 0 ? (
                      <Pagination
                        page={movementsPage}
                        pageSize={10}
                        totalItems={movementsTotal}
                        onPageChange={setMovementsPage}
                      />
                    ) : null}
                  </CardContent>
                </Card>
              </div>
            </div>
          </StateBoundary>
        </div>
      ) : activeTab === 'forecasting' ? (
        <div className="foundation-stack">
          <Alert
            variant="info"
            title="Estimates vs. Actuals"
            description="Forecast figures below are statistical estimates evaluated against a chronological holdout baseline. They are never mixed with recorded actuals."
          />

          <Card>
            <CardContent compact>
              <div className="foundation-row" style={{ alignItems: 'flex-end' }}>
                <div style={{ minWidth: '12rem' }}>
                  <Select
                    label="Forecast Horizon"
                    value={horizonDays}
                    options={[
                      { value: '7', label: '7 days' },
                      { value: '14', label: '14 days' },
                      { value: '30', label: '30 days' },
                    ]}
                    onChange={(e) => setHorizonDays(e.target.value)}
                  />
                </div>
                <div style={{ flex: '1 1 16rem' }}>
                  <Select
                    label="Product Demand Forecast (Optional)"
                    value={selectedProductId}
                    options={[
                      { value: '', label: 'Select a product...' },
                      ...products.map((p) => ({
                        value: p.id,
                        label: `${p.sku} — ${p.name}`,
                      })),
                    ]}
                    onChange={(e) => void handleProductForecast(e.target.value)}
                  />
                </div>
              </div>
            </CardContent>
          </Card>

          <StateBoundary
            status={forecastStatus}
            errorTitle="Forecasting check failed"
            errorMessage={forecastError ?? undefined}
            onRetry={() => void loadForecasts()}
          >
            {salesForecast ? (
              <Card>
                <CardHeader
                  title="Sales Revenue Forecast"
                  description={salesForecast.predictionTarget}
                  actions={
                    <Badge
                      variant={
                        salesForecast.status === 'OK'
                          ? 'warning'
                          : salesForecast.status === 'INSUFFICIENT_DATA'
                            ? 'info'
                            : 'neutral'
                      }
                    >
                      {salesForecast.status}
                    </Badge>
                  }
                />
                <CardContent className="foundation-stack">
                  <Alert
                    variant={salesForecast.status === 'OK' ? 'warning' : 'info'}
                    title={`Forecast Status: ${salesForecast.status}`}
                    description={salesForecast.message}
                  />

                  {salesForecast.evaluation ? (
                    <div className="foundation-row">
                      <Badge variant="neutral">
                        Selected: {salesForecast.evaluation.selectedModel}
                      </Badge>
                      <Badge variant="neutral">
                        Holdout MAE: {salesForecast.evaluation.modelMae} (Baseline:{' '}
                        {salesForecast.evaluation.baselineMae})
                      </Badge>
                      {salesForecast.evaluation.modelWape !== null ? (
                        <Badge variant="neutral">
                          WAPE: {(salesForecast.evaluation.modelWape * 100).toFixed(1)}%
                        </Badge>
                      ) : null}
                    </div>
                  ) : null}

                  {salesForecast.predictions.length > 0 ? (
                    <>
                      <SimpleBarChart
                        title="Predicted daily revenue (Estimate)"
                        isPrediction
                        items={salesForecast.predictions.map((p) => ({
                          label: p.date,
                          value: p.predictedValue,
                        }))}
                      />
                      <Table
                        caption="Forecast predictions"
                        columns={forecastColumns}
                        rows={salesForecast.predictions}
                        getRowKey={(r) => r.date}
                      />
                    </>
                  ) : null}

                  {salesForecast.limitations.length > 0 ? (
                    <ul style={{ paddingLeft: '1.25rem', color: 'var(--color-text-secondary)', fontSize: 'var(--text-xs)' }}>
                      {salesForecast.limitations.map((lim) => (
                        <li key={lim}>{lim}</li>
                      ))}
                    </ul>
                  ) : null}
                </CardContent>
              </Card>
            ) : null}

            {productForecast ? (
              <Card>
                <CardHeader
                  title="Inventory Product Demand Forecast"
                  description={productForecast.predictionTarget}
                  actions={<Badge variant="warning">{productForecast.status}</Badge>}
                />
                <CardContent className="foundation-stack">
                  <Alert
                    variant={productForecast.status === 'OK' ? 'warning' : 'info'}
                    title={
                      productForecast.estimatedStockoutDate
                        ? `Estimated stockout date: ${productForecast.estimatedStockoutDate}`
                        : `Status: ${productForecast.status}`
                    }
                    description={productForecast.message}
                  />
                  {productForecast.predictions.length > 0 ? (
                    <Table
                      caption="Product demand forecast"
                      columns={forecastColumns}
                      rows={productForecast.predictions}
                      getRowKey={(r) => r.date}
                    />
                  ) : null}
                </CardContent>
              </Card>
            ) : null}
          </StateBoundary>
        </div>
      ) : activeTab === 'assistant' ? (
        <AssistantPanel />
      ) : (
        <AutomationPanel />
      )}
    </div>
  );
}

