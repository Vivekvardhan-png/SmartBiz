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
});

