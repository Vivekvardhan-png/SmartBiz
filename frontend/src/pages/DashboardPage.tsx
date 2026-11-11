import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../app/authContext';
import {
  Badge,
  Button,
  Card,
  CardContent,
  CardHeader,
  FormField,
  PageHeader,
  StateBoundary,
  Table,
  type ScreenStatus,
  type TableColumn,
} from '../components/ui';
import { apiRequest, ApiError } from '../lib/apiClient';
import type {
  DashboardSummary,
  LeadStatus,
  MetricDefinition,
} from '../lib/crmTypes';

const STAGE_ORDER: LeadStatus[] = [
  'NEW',
  'CONTACTED',
  'QUALIFIED',
  'PROPOSAL',
  'CONVERTED',
  'LOST',
];

function getStageBadgeVariant(stage: LeadStatus) {
  switch (stage) {
    case 'CONVERTED':
      return 'success' as const;
    case 'LOST':
      return 'error' as const;
    case 'PROPOSAL':
    case 'QUALIFIED':
      return 'info' as const;
    default:
      return 'neutral' as const;
  }
}

export function DashboardPage() {
  const { token, session } = useAuth();
  const [fromDate, setFromDate] = useState('');
  const [toDate, setToDate] = useState('');
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [status, setStatus] = useState<ScreenStatus>('loading');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const loadSummary = useCallback(
    async (from?: string, to?: string) => {
      if (!token) return;
      setStatus('loading');
      setErrorMessage(null);

      const params = new URLSearchParams();
      if (from) params.set('from', from);
      if (to) params.set('to', to);
      const query = params.toString() ? `?${params.toString()}` : '';

      try {
        const data = await apiRequest<DashboardSummary>(
          `/api/v1/dashboard/summary${query}`,
          { token },
        );
        setSummary(data);
        setStatus('success');
      } catch (err) {
        setErrorMessage(
          err instanceof ApiError
            ? err.message
            : 'Unable to load dashboard metrics.',
        );
        setStatus('error');
      }
    },
    [token],
  );

  useEffect(() => {
    void loadSummary();
  }, [loadSummary]);

  const handleApplyPeriod = (event: FormEvent) => {
    event.preventDefault();
    void loadSummary(fromDate || undefined, toDate || undefined);
  };

  const definitionColumns: TableColumn<MetricDefinition>[] = [
    {
      key: 'metric',
      header: 'Metric',
      render: (row) => <strong>{row.metric}</strong>,
    },
    {
      key: 'formula',
      header: 'Calculation Rule',
      render: (row) => (
        <span style={{ color: 'var(--color-text-secondary)' }}>
          {row.formula}
        </span>
      ),
    },
    {
      key: 'dateScope',
      header: 'Window',
      render: (row) => row.dateScope,
    },
    {
      key: 'active',
      header: 'Status',
      align: 'right',
      render: (row) => (
        <Badge variant={row.active ? 'success' : 'neutral'}>
          {row.active ? 'Live DB' : 'Deferred'}
        </Badge>
      ),
    },
  ];

  return (
    <div className="foundation-stack">
      <PageHeader
        title="Dashboard"
        subtitle={`Authorized executive metrics for ${session?.activeOrganization.name ?? 'your organization'}.`}
        breadcrumbs={[
          { label: 'Workspace', to: '/dashboard' },
          { label: 'Dashboard' },
        ]}
        badge={<Badge variant="success">Active · Prompt 03</Badge>}
        actions={
          <Link to="/crm" className="ui-btn ui-btn--primary ui-btn--md">
            Open CRM Pipeline
          </Link>
        }
      />

      <Card>
        <CardContent compact>
          <form
            onSubmit={handleApplyPeriod}
            className="foundation-row"
            style={{ alignItems: 'flex-end' }}
          >
            <FormField
              label="Period From (UTC)"
              type="date"
              value={fromDate}
              onChange={(event) => setFromDate(event.target.value)}
            />
            <FormField
              label="Period To (UTC)"
              type="date"
              value={toDate}
              onChange={(event) => setToDate(event.target.value)}
            />
            <Button type="submit" variant="secondary">
              Apply Period
            </Button>
            {summary ? (
              <span
                style={{
                  fontSize: 'var(--text-xs)',
                  color: 'var(--color-text-secondary)',
                }}
              >
                Active window: {summary.periodStart} to {summary.periodEnd}
              </span>
            ) : null}
          </form>
        </CardContent>
      </Card>

      <StateBoundary
        status={status}
        errorTitle="Dashboard unavailable"
        errorMessage={errorMessage ?? undefined}
        onRetry={() => void loadSummary(fromDate || undefined, toDate || undefined)}
      >
        {summary ? (
          <div className="foundation-grid">
            <div className="foundation-col-4">
              <Card>
                <CardHeader
                  title="Total Customers"
                  actions={<Badge variant="success">Live</Badge>}
                />
                <CardContent>
                  <div
                    style={{
                      fontSize: 'var(--text-2xl)',
                      fontWeight: 'var(--weight-bold)',
                    }}
                  >
                    {summary.totalCustomers}
                  </div>
                  <p
                    style={{
                      marginTop: 'var(--space-1)',
                      fontSize: 'var(--text-xs)',
                      color: 'var(--color-text-secondary)',
                    }}
                  >
                    Organization-scoped customer records
                  </p>
                </CardContent>
              </Card>
            </div>

            <div className="foundation-col-4">
              <Card>
                <CardHeader
                  title="Open Leads"
                  actions={<Badge variant="success">Live</Badge>}
                />
                <CardContent>
                  <div
                    style={{
                      fontSize: 'var(--text-2xl)',
                      fontWeight: 'var(--weight-bold)',
                    }}
                  >
                    {summary.openLeads}
                  </div>
                  <p
                    style={{
                      marginTop: 'var(--space-1)',
                      fontSize: 'var(--text-xs)',
                      color: 'var(--color-text-secondary)',
                    }}
                  >
                    New, Contacted, Qualified, and Proposal stages
                  </p>
                </CardContent>
              </Card>
            </div>

            <div className="foundation-col-4">
              <Card>
                <CardHeader
                  title="Low-Stock Products"
                  actions={
                    <Badge
                      variant={
                        summary.lowStockProductsCount.moduleAvailable
                          ? 'success'
                          : 'neutral'
                      }
                    >
                      {summary.lowStockProductsCount.moduleAvailable
                        ? 'Live'
                        : 'Prompt 04'}
                    </Badge>
                  }
                />
                <CardContent>
                  <div
                    style={{
                      fontSize: 'var(--text-2xl)',
                      fontWeight: 'var(--weight-bold)',
                    }}
                  >
                    {summary.lowStockProductsCount.value}
                  </div>
                  <p
                    style={{
                      marginTop: 'var(--space-1)',
                      fontSize: 'var(--text-xs)',
                      color: 'var(--color-text-secondary)',
                    }}
                  >
                    {summary.lowStockProductsCount.note}
                  </p>
                </CardContent>
              </Card>
            </div>

            <div className="foundation-col-4">
              <Card>
                <CardHeader
                  title="Period Revenue"
                  actions={<Badge variant="neutral">Prompt 05</Badge>}
                />
                <CardContent>
                  <div
                    style={{
                      fontSize: 'var(--text-2xl)',
                      fontWeight: 'var(--weight-bold)',
                    }}
                  >
                    ${Number(summary.periodRevenue.value).toFixed(2)}
                  </div>
                  <p
                    style={{
                      marginTop: 'var(--space-1)',
                      fontSize: 'var(--text-xs)',
                      color: 'var(--color-text-secondary)',
                    }}
                  >
                    {summary.periodRevenue.note}
                  </p>
                </CardContent>
              </Card>
            </div>

            <div className="foundation-col-4">
              <Card>
                <CardHeader
                  title="Outstanding Invoice Balance"
                  actions={<Badge variant="neutral">Prompt 05</Badge>}
                />
                <CardContent>
                  <div
                    style={{
                      fontSize: 'var(--text-2xl)',
                      fontWeight: 'var(--weight-bold)',
                    }}
                  >
                    ${Number(summary.outstandingInvoiceBalance.value).toFixed(2)}
                  </div>
                  <p
                    style={{
                      marginTop: 'var(--space-1)',
                      fontSize: 'var(--text-xs)',
                      color: 'var(--color-text-secondary)',
                    }}
                  >
                    {summary.outstandingInvoiceBalance.note}
                  </p>
                </CardContent>
              </Card>
            </div>

            <div className="foundation-col-4">
              <Card>
                <CardHeader
                  title="Sales Orders by Status"
                  actions={<Badge variant="neutral">Prompt 05</Badge>}
                />
                <CardContent>
                  <div
                    style={{
                      fontSize: 'var(--text-2xl)',
                      fontWeight: 'var(--weight-bold)',
                    }}
                  >
                    {Object.keys(summary.salesOrdersByStatus.value).length}
                  </div>
                  <p
                    style={{
                      marginTop: 'var(--space-1)',
                      fontSize: 'var(--text-xs)',
                      color: 'var(--color-text-secondary)',
                    }}
                  >
                    {summary.salesOrdersByStatus.note}
                  </p>
                </CardContent>
              </Card>
            </div>

            <div className="foundation-col-12">
              <Card>
                <CardHeader
                  title="Leads by Pipeline Stage"
                  description="Live counts grouped by lead status within the active organization."
                />
                <CardContent>
                  <div className="foundation-row">
                    {STAGE_ORDER.map((stage) => (
                      <div
                        key={stage}
                        style={{
                          padding: 'var(--space-3) var(--space-4)',
                          border: '1px solid var(--color-border)',
                          borderRadius: 'var(--radius-md)',
                          minWidth: '8.5rem',
                          backgroundColor: 'var(--color-bg-subtle)',
                        }}
                      >
                        <Badge variant={getStageBadgeVariant(stage)}>
                          {stage}
                        </Badge>
                        <div
                          style={{
                            marginTop: 'var(--space-2)',
                            fontSize: 'var(--text-xl)',
                            fontWeight: 'var(--weight-semibold)',
                          }}
                        >
                          {summary.leadsByStage[stage] ?? 0}
                        </div>
                      </div>
                    ))}
                  </div>
                </CardContent>
              </Card>
            </div>

            <div className="foundation-col-12">
              <Card>
                <CardHeader
                  title="Metric Definitions & Traceability"
                  description="Exact backend calculation rules and active module boundaries."
                />
                <CardContent>
                  <Table
                    caption="Dashboard metric definitions"
                    columns={definitionColumns}
                    rows={summary.metricDefinitions}
                    getRowKey={(row) => row.metric}
                  />
                </CardContent>
              </Card>
            </div>
          </div>
        ) : null}
      </StateBoundary>
    </div>
  );
}

