import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider, type SessionInfo } from '../app/authContext';
import { ToastProvider } from '../components/ui';
import { InsightsPage } from './InsightsPage';

const MANAGER_SESSION: SessionInfo = {
  user: { id: 'u-1', email: 'mgr@acme.test', displayName: 'Manager' },
  activeOrganization: { id: 'org-1', name: 'Acme', slug: 'acme', role: 'MANAGER' },
  permissions: ['INSIGHTS_READ'],
  organizations: [{ id: 'org-1', name: 'Acme', slug: 'acme', role: 'MANAGER' }],
};

function jsonRes(data: unknown) {
  return new Response(JSON.stringify(data), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  });
}

describe('InsightsPage', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('loads business reports and shows unavailable forecasting state gracefully', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch');

    fetchMock
      .mockResolvedValueOnce(
        jsonRes({
          data: {
            periodStart: '2026-10-13',
            periodEnd: '2026-11-11',
            totalRevenue: 150,
            totalInvoices: 1,
            definition: 'Daily invoiced revenue',
            series: [{ date: '2026-11-11', revenue: 150, invoiceCount: 1 }],
          },
        }),
      )
      .mockResolvedValueOnce(
        jsonRes({
          data: {
            periodStart: '2026-10-13',
            periodEnd: '2026-11-11',
            totalOrders: 1,
            totalValue: 150,
            rows: [{ status: 'CONFIRMED', count: 1, totalAmount: 150 }],
          },
        }),
      )
      .mockResolvedValueOnce(
        jsonRes({
          data: {
            periodStart: '2026-10-13',
            periodEnd: '2026-11-11',
            outstandingTotal: 50,
            totalPaymentsInPeriod: 100,
            totalRefundsInPeriod: 0,
            outstandingInvoices: [],
            paymentsInPeriod: [],
          },
        }),
      )
      .mockResolvedValueOnce(
        jsonRes({
          data: {
            totalLeads: 2,
            openLeads: 1,
            convertedLeads: 1,
            conversionRatePercent: 50,
            stages: [],
          },
        }),
      )
      .mockResolvedValueOnce(
        jsonRes({
          data: {
            totalSkus: 1,
            lowStockCount: 0,
            totalStockValue: 200,
            items: [],
            page: 1,
            pageSize: 20,
            totalItems: 0,
            totalPages: 1,
          },
        }),
      )
      .mockResolvedValueOnce(
        jsonRes({
          data: [],
          page: 1,
          pageSize: 10,
          totalItems: 0,
          totalPages: 1,
        }),
      )
      .mockResolvedValueOnce(
        jsonRes({
          data: {
            status: 'UNAVAILABLE',
            serviceAvailable: false,
            predictionTarget: 'Daily invoiced revenue (USD)',
            horizonDays: 14,
            actualDays: 0,
            nonZeroDays: 0,
            minimumDaysRequired: 14,
            message: 'Forecasting service is currently unavailable.',
            evaluation: null,
            predictions: [],
            projectedTotal: null,
            estimatedStockoutDate: null,
            limitations: ['No predictions generated.'],
          },
        }),
      )
      .mockResolvedValueOnce(
        jsonRes({ data: [], page: 1, pageSize: 100, totalItems: 0, totalPages: 1 }),
      );

    const user = userEvent.setup();
    render(
      <ToastProvider>
        <AuthProvider initialSession={MANAGER_SESSION} initialToken="jwt-token">
          <MemoryRouter>
            <InsightsPage />
          </MemoryRouter>
        </AuthProvider>
      </ToastProvider>,
    );

    expect(
      await screen.findByText(/Sales Over Time \(2026-10-13 to 2026-11-11\)/),
    ).toBeInTheDocument();

    await user.click(screen.getByRole('tab', { name: 'Forecasting' }));
    await waitFor(() => {
      expect(screen.getByText('Forecast Status: UNAVAILABLE')).toBeInTheDocument();
    });
  });

  it('handles AI assistant citations, write action confirmation, provider failures, and automation workflows', async () => {
    const aiSession: SessionInfo = {
      ...MANAGER_SESSION,
      permissions: ['INSIGHTS_READ', 'AI_USE', 'CRM_WRITE', 'INVENTORY_READ', 'CRM_READ'],
    };

    const fetchMock = vi.spyOn(globalThis, 'fetch');
    fetchMock.mockImplementation((input: RequestInfo | URL) => {
      const url = typeof input === 'string' ? input : input.toString();
      if (url.includes('/reports/sales-over-time')) {
        return Promise.resolve(
          jsonRes({
            data: {
              periodStart: '2026-10-13',
              periodEnd: '2026-11-11',
              totalRevenue: 0,
              totalInvoices: 0,
              definition: 'Daily invoiced revenue',
              series: [],
            },
          }),
        );
      }
      if (url.includes('/reports/orders-by-status')) {
        return Promise.resolve(
          jsonRes({
            data: {
              periodStart: '2026-10-13',
              periodEnd: '2026-11-11',
              totalOrders: 0,
              totalValue: 0,
              rows: [],
            },
          }),
        );
      }
      if (url.includes('/reports/receivables')) {
        return Promise.resolve(
          jsonRes({
            data: {
              periodStart: '2026-10-13',
              periodEnd: '2026-11-11',
              outstandingTotal: 0,
              totalPaymentsInPeriod: 0,
              totalRefundsInPeriod: 0,
              outstandingInvoices: [],
              paymentsInPeriod: [],
            },
          }),
        );
      }
      if (url.includes('/reports/lead-pipeline')) {
        return Promise.resolve(
          jsonRes({
            data: {
              totalLeads: 0,
              openLeads: 0,
              convertedLeads: 0,
              conversionRatePercent: 0,
              stages: [],
            },
          }),
        );
      }
      if (url.includes('/reports/inventory-stock')) {
        return Promise.resolve(
          jsonRes({
            data: {
              totalSkus: 0,
              lowStockCount: 0,
              totalStockValue: 0,
              items: [],
              page: 1,
              pageSize: 20,
              totalItems: 0,
              totalPages: 1,
            },
          }),
        );
      }
      if (url.includes('/reports/stock-movements')) {
        return Promise.resolve(
          jsonRes({ data: [], page: 1, pageSize: 10, totalItems: 0, totalPages: 1 }),
        );
      }
      if (url.includes('/forecast/sales')) {
        return Promise.resolve(
          jsonRes({
            data: {
              status: 'UNAVAILABLE',
              serviceAvailable: false,
              predictionTarget: 'Daily invoiced revenue (USD)',
              horizonDays: 14,
              actualDays: 0,
              nonZeroDays: 0,
              minimumDaysRequired: 14,
              message: 'Disabled',
              evaluation: null,
              predictions: [],
              projectedTotal: null,
              estimatedStockoutDate: null,
              limitations: [],
            },
          }),
        );
      }
      if (url.includes('/inventory/products')) {
        return Promise.resolve(
          jsonRes({ data: [], page: 1, pageSize: 100, totalItems: 0, totalPages: 1 }),
        );
      }
      if (url.includes('/assistant/chat')) {
        return Promise.resolve(
          jsonRes({
            data: {
              status: 'OK',
              reply: 'Found SKU-LOW-1 below threshold and drafted a follow-up note.',
              errorMessage: null,
              toolsUsed: ['list_low_stock_products', 'create_crm_follow_up_note'],
              citations: [
                {
                  toolName: 'list_low_stock_products',
                  entityType: 'PRODUCT',
                  label: 'SKU-LOW-1 — Scanner',
                  summary: 'Stock 2 PCS (threshold 5)',
                },
              ],
              pendingActions: [
                {
                  id: 'prop-1',
                  toolName: 'create_crm_follow_up_note',
                  actionSummary: "Add CRM note on Lead 'Q4 Deal': Call tomorrow",
                  status: 'PENDING',
                  resultSummary: null,
                  createdAt: '2026-11-11T16:00:00Z',
                  expiresAt: '2026-11-11T16:15:00Z',
                  resolvedAt: null,
                },
              ],
              disclaimer: 'Verify critical business figures against source records.',
            },
          }),
        );
      }
      if (url.includes('/assistant/actions/prop-1/confirm')) {
        return Promise.resolve(
          jsonRes({
            data: {
              id: 'prop-1',
              toolName: 'create_crm_follow_up_note',
              actionSummary: "Add CRM note on Lead 'Q4 Deal': Call tomorrow",
              status: 'EXECUTED',
              resultSummary: 'Recorded CRM note on LEAD.',
              createdAt: '2026-11-11T16:00:00Z',
              expiresAt: '2026-11-11T16:15:00Z',
              resolvedAt: '2026-11-11T16:01:00Z',
            },
          }),
        );
      }
      if (url.includes('/automation/alerts')) {
        return Promise.resolve(
          jsonRes({
            data: [
              {
                id: 'alert-1',
                workflowType: 'LOW_STOCK_ALERT',
                entityType: 'PRODUCT',
                entityId: 'prod-1',
                severity: 'WARNING',
                title: 'Low stock: SKU-LOW-1 (Scanner)',
                message: 'Stock 2 PCS <= threshold 5 PCS.',
                status: 'OPEN',
                runId: 'run-1',
                acknowledgedAt: null,
                createdAt: '2026-11-11T16:00:00Z',
              },
            ],
            page: 1,
            pageSize: 25,
            totalItems: 1,
            totalPages: 1,
          }),
        );
      }
      if (url.includes('/automation/runs')) {
        return Promise.resolve(
          jsonRes({
            data: [
              {
                id: 'run-1',
                workflowType: 'LOW_STOCK_ALERT',
                triggerSource: 'MANUAL',
                status: 'COMPLETED',
                alertsCreated: 1,
                alertsSkippedDuplicate: 0,
                errorMessage: null,
                startedAt: '2026-11-11T16:00:00Z',
                completedAt: '2026-11-11T16:00:01Z',
              },
            ],
            page: 1,
            pageSize: 15,
            totalItems: 1,
            totalPages: 1,
          }),
        );
      }
      if (url.includes('/automation/workflows/run')) {
        return Promise.resolve(
          jsonRes({
            data: {
              runs: [
                {
                  id: 'run-2',
                  workflowType: 'LOW_STOCK_ALERT',
                  triggerSource: 'MANUAL',
                  status: 'COMPLETED',
                  alertsCreated: 0,
                  alertsSkippedDuplicate: 1,
                  errorMessage: null,
                  startedAt: '2026-11-11T16:05:00Z',
                  completedAt: '2026-11-11T16:05:01Z',
                },
              ],
            },
          }),
        );
      }
      return Promise.resolve(jsonRes({ data: null }));
    });

    const user = userEvent.setup();
    render(
      <ToastProvider>
        <AuthProvider initialSession={aiSession} initialToken="jwt-token">
          <MemoryRouter>
            <InsightsPage />
          </MemoryRouter>
        </AuthProvider>
      </ToastProvider>,
    );

    await user.click(screen.getByRole('tab', { name: 'AI Assistant' }));
    const input = screen.getByLabelText('Business Question or Action Request');
    await user.type(input, 'Check low stock and propose a CRM note');
    await user.click(screen.getByRole('button', { name: 'Send to Assistant' }));

    expect(
      await screen.findByText(/Found SKU-LOW-1 below threshold/i),
    ).toBeInTheDocument();
    expect(screen.getByText('SKU-LOW-1 — Scanner')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Confirm & Execute' })).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Confirm & Execute' }));
    await waitFor(() => {
      expect(screen.getByText('EXECUTED')).toBeInTheDocument();
    });

    await user.click(screen.getByRole('tab', { name: 'Automation' }));
    expect(
      await screen.findByText('Low stock: SKU-LOW-1 (Scanner)'),
    ).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Run All Workflows' }));
    await waitFor(() => {
      expect(screen.getByText('0 new alert(s) created, 1 duplicate(s) skipped.')).toBeInTheDocument();
    });
  });
});

