import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider, type SessionInfo } from '../app/authContext';
import { ToastProvider } from '../components/ui';
import { FinancePage } from './FinancePage';

const OWNER_SESSION: SessionInfo = {
  user: { id: 'u-1', email: 'owner@acme.test', displayName: 'Alex Owner' },
  activeOrganization: { id: 'org-1', name: 'Acme', slug: 'acme', role: 'OWNER' },
  permissions: ['FINANCE_READ', 'FINANCE_WRITE'],
  organizations: [{ id: 'org-1', name: 'Acme', slug: 'acme', role: 'OWNER' }],
};

describe('FinancePage', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('loads sales orders and switches to invoices with balance display', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch');

    fetchMock.mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          data: [
            {
              id: 'ord-1',
              orderNumber: 'SO-20261111-0001',
              customerId: 'cust-1',
              customerName: 'Maya Lin',
              status: 'CONFIRMED',
              orderDate: '2026-11-11',
              subtotalAmount: 200,
              discountAmount: 0,
              taxRatePercent: 0,
              taxAmount: 0,
              totalAmount: 200,
              stockDeducted: true,
              hasInvoice: true,
              createdAt: '2026-11-11T00:00:00Z',
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

    fetchMock.mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          data: [
            {
              id: 'inv-1',
              invoiceNumber: 'INV-000001',
              salesOrderId: 'ord-1',
              customerId: 'cust-1',
              customerName: 'Maya Lin',
              status: 'PARTIALLY_PAID',
              issueDate: '2026-11-11',
              dueDate: '2026-12-11',
              totalAmount: 200,
              paidAmount: 75,
              outstandingBalance: 125,
              createdAt: '2026-11-11T00:00:00Z',
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

    const user = userEvent.setup();
    render(
      <ToastProvider>
        <AuthProvider initialSession={OWNER_SESSION} initialToken="jwt-token">
          <MemoryRouter>
            <FinancePage />
          </MemoryRouter>
        </AuthProvider>
      </ToastProvider>,
    );

    expect(await screen.findByText('SO-20261111-0001')).toBeInTheDocument();

    await user.click(screen.getByRole('tab', { name: 'Invoices' }));
    await waitFor(() => {
      expect(screen.getByText('INV-000001')).toBeInTheDocument();
      expect(screen.getByText('$125.00')).toBeInTheDocument();
    });
  });
});

