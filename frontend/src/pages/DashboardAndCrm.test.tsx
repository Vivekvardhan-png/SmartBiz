import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider, type SessionInfo } from '../app/authContext';
import { ToastProvider } from '../components/ui';
import { CrmPage } from './CrmPage';
import { DashboardPage } from './DashboardPage';

const TEST_SESSION: SessionInfo = {
  user: {
    id: 'u-1',
    email: 'owner@acme.test',
    displayName: 'Alex Owner',
  },
  activeOrganization: {
    id: 'org-1',
    name: 'Acme Corp',
    slug: 'acme-corp',
    role: 'OWNER',
  },
  permissions: [
    'ORG_MANAGE',
    'MEMBERS_READ',
    'MEMBERS_MANAGE',
    'CRM_READ',
    'CRM_WRITE',
  ],
  organizations: [
    {
      id: 'org-1',
      name: 'Acme Corp',
      slug: 'acme-corp',
      role: 'OWNER',
    },
  ],
};

function renderWithAuth(ui: React.ReactNode) {
  return render(
    <ToastProvider>
      <AuthProvider initialSession={TEST_SESSION} initialToken="jwt-token">
        <MemoryRouter>{ui}</MemoryRouter>
      </AuthProvider>
    </ToastProvider>,
  );
}

describe('DashboardPage and CrmPage', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('renders fetched dashboard metrics and shows error state without fake data on API failure', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          data: {
            periodStart: '2026-10-13',
            periodEnd: '2026-11-11',
            totalCustomers: 12,
            openLeads: 5,
            leadsByStage: {
              NEW: 2,
              CONTACTED: 1,
              QUALIFIED: 1,
              PROPOSAL: 1,
              CONVERTED: 4,
              LOST: 1,
            },
            salesOrdersByStatus: { value: {}, moduleAvailable: false, note: 'Module restricted for role' },
            periodRevenue: { value: 0, moduleAvailable: false, note: 'Module restricted for role' },
            outstandingInvoiceBalance: { value: 0, moduleAvailable: false, note: 'Module restricted for role' },
            lowStockProductsCount: { value: 0, moduleAvailable: false, note: 'Module restricted for role' },
            metricDefinitions: [],
          },
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
      ),
    );

    renderWithAuth(<DashboardPage />);

    expect(await screen.findByText('12')).toBeInTheDocument();
    expect(screen.getByText('5')).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledTimes(1);

    // Failure case shows explicit error banner instead of mock numbers
    fetchMock.mockResolvedValueOnce(
      new Response(
        JSON.stringify({ status: 500, error: 'Internal Server Error', message: 'DB unavailable' }),
        { status: 500, headers: { 'Content-Type': 'application/json' } },
      ),
    );

    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: 'Apply Period' }));

    expect(await screen.findByText('Dashboard unavailable')).toBeInTheDocument();
    expect(screen.getByText('DB unavailable')).toBeInTheDocument();
  });

  it('loads CRM leads, switches to customers, and opens conversion flow', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch');

    // Initial leads list
    fetchMock.mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          data: [
            {
              id: 'lead-1',
              title: 'Enterprise Suite',
              contactName: 'Maya Lin',
              companyName: 'Horizon Co',
              email: 'maya@horizon.test',
              phone: null,
              source: null,
              status: 'QUALIFIED',
              allowedNextStatuses: ['PROPOSAL', 'LOST'],
              estimatedValue: 15000,
              followUpDate: '2026-11-20',
              notes: null,
              convertedCustomerId: null,
              convertedAt: null,
              createdAt: '2026-11-11T00:00:00Z',
              updatedAt: '2026-11-11T00:00:00Z',
            },
          ],
          page: 1,
          pageSize: 10,
          totalItems: 1,
          totalPages: 1,
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
      ),
    );

    // Notes for lead-1 when clicked
    fetchMock.mockResolvedValueOnce(
      new Response(JSON.stringify({ data: [] }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    );

    // Convert lead-1 response
    fetchMock.mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          data: {
            lead: {
              id: 'lead-1',
              title: 'Enterprise Suite',
              contactName: 'Maya Lin',
              companyName: 'Horizon Co',
              email: 'maya@horizon.test',
              phone: null,
              source: null,
              status: 'CONVERTED',
              allowedNextStatuses: [],
              estimatedValue: 15000,
              followUpDate: '2026-11-20',
              notes: null,
              convertedCustomerId: 'cust-1',
              convertedAt: '2026-11-11T01:00:00Z',
              createdAt: '2026-11-11T00:00:00Z',
              updatedAt: '2026-11-11T01:00:00Z',
            },
            customer: {
              id: 'cust-1',
              name: 'Maya Lin',
              companyName: 'Horizon Co',
              email: 'maya@horizon.test',
              phone: null,
              address: null,
              followUpDate: '2026-11-20',
              notes: null,
              status: 'ACTIVE',
              createdAt: '2026-11-11T01:00:00Z',
              updatedAt: '2026-11-11T01:00:00Z',
            },
            alreadyConverted: false,
          },
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
      ),
    );

    // Refresh leads after conversion
    fetchMock.mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          data: [],
          page: 1,
          pageSize: 10,
          totalItems: 0,
          totalPages: 1,
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
      ),
    );

    const user = userEvent.setup();
    renderWithAuth(<CrmPage />);

    expect(await screen.findByText('Enterprise Suite')).toBeInTheDocument();

    await user.click(screen.getByText('Enterprise Suite'));
    const convertBtn = await screen.findByRole('button', {
      name: 'Convert to Customer',
    });
    await user.click(convertBtn);

    await waitFor(() => {
      expect(screen.getByText('Lead converted to customer')).toBeInTheDocument();
    });
  });
});

