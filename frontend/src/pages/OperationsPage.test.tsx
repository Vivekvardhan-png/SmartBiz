import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider, type SessionInfo } from '../app/authContext';
import { ToastProvider } from '../components/ui';
import { OperationsPage } from './OperationsPage';

const TEST_SESSION: SessionInfo = {
  user: { id: 'u-1', email: 'owner@acme.test', displayName: 'Alex Owner' },
  activeOrganization: { id: 'org-1', name: 'Acme', slug: 'acme', role: 'OWNER' },
  permissions: ['INVENTORY_READ', 'INVENTORY_WRITE'],
  organizations: [{ id: 'org-1', name: 'Acme', slug: 'acme', role: 'OWNER' }],
};

describe('OperationsPage', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('displays low-stock indicators and movement history from API', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch');

    fetchMock.mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          data: [
            {
              id: 'prod-1',
              sku: 'CABLE-01',
              name: 'USB-C Cable',
              description: null,
              category: 'Accessories',
              unitOfMeasure: 'PCS',
              unitPrice: 19.99,
              costPrice: 7.5,
              stockQuantity: 3,
              lowStockThreshold: 5,
              lowStock: true,
              active: true,
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

    fetchMock.mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          data: [
            {
              id: 'mov-1',
              productId: 'prod-1',
              productSku: 'CABLE-01',
              productName: 'USB-C Cable',
              supplierId: null,
              supplierName: null,
              movementType: 'INITIAL',
              quantityDelta: 3,
              quantityBefore: 0,
              quantityAfter: 3,
              reason: 'Initial opening stock balance',
              reference: null,
              actorName: 'Alex Owner',
              createdAt: '2026-11-11T00:00:00Z',
              idempotentReplay: false,
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
        <AuthProvider initialSession={TEST_SESSION} initialToken="jwt-token">
          <MemoryRouter>
            <OperationsPage />
          </MemoryRouter>
        </AuthProvider>
      </ToastProvider>,
    );

    expect(await screen.findByText('USB-C Cable')).toBeInTheDocument();
    expect(screen.getByText('Low Stock (≤ 5)')).toBeInTheDocument();

    await user.click(screen.getByRole('tab', { name: 'Movement History' }));
    await waitFor(() => {
      expect(
        screen.getByText('Initial opening stock balance'),
      ).toBeInTheDocument();
    });
  });
});

