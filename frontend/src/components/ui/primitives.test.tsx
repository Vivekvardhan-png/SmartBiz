import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { designTokens } from '../../design-system/tokens';
import {
  Alert,
  Badge,
  Breadcrumbs,
  Button,
  Dialog,
  FormField,
  PageHeader,
  Pagination,
  Select,
  StateBoundary,
  Table,
  ToastProvider,
  useToast,
  type TableColumn,
} from './index';

describe('Design tokens & shared UI primitives', () => {
  it('exposes the centralized warm neutral and blue enterprise palette', () => {
    expect(designTokens.colors.bgApp).toBe('#FAF9F6');
    expect(designTokens.colors.bgSurface).toBe('#FFFFFF');
    expect(designTokens.colors.bgSecondary).toBe('#F5F5F4');
    expect(designTokens.colors.border).toBe('#E7E5E4');
    expect(designTokens.colors.textPrimary).toBe('#1C1917');
    expect(designTokens.colors.textSecondary).toBe('#78716C');
    expect(designTokens.colors.actionPrimary).toBe('#2563EB');
    expect(designTokens.colors.actionHover).toBe('#1D4ED8');
    expect(designTokens.colors.success).toBe('#15803D');
    expect(designTokens.colors.warning).toBe('#B45309');
    expect(designTokens.colors.error).toBe('#B91C1C');
  });

  it('renders Button loading and disabled states cleanly', () => {
    render(
      <Button variant="primary" loading>
        Save
      </Button>,
    );
    const btn = screen.getByRole('button', { name: 'Save' });
    expect(btn).toBeDisabled();
    expect(btn).toHaveAttribute('aria-busy', 'true');
  });

  it('wires FormField and Select labels, hints, and error alerts accessibly', () => {
    render(
      <div>
        <FormField
          label="Email"
          required
          hint="Use your work email"
          error="Email is required"
        />
        <Select
          label="Role"
          options={[{ value: 'ADMIN', label: 'Administrator' }]}
          defaultValue="ADMIN"
          error="Role mismatch"
        />
      </div>,
    );

    const input = screen.getByLabelText(/Email/);
    expect(input).toHaveAttribute('aria-invalid', 'true');
    expect(input).toHaveAttribute('aria-required', 'true');
    expect(screen.getByText('Use your work email')).toBeInTheDocument();
    expect(screen.getByText('Email is required')).toHaveAttribute(
      'role',
      'alert',
    );

    const select = screen.getByLabelText(/Role/);
    expect(select).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByText('Role mismatch')).toBeInTheDocument();
  });

  it('supports accessible Dialog open and Escape dismissal', async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();

    render(
      <Dialog open onClose={onClose} title="Confirm action" description="Details">
        <p>Body copy</p>
      </Dialog>,
    );

    expect(screen.getByRole('dialog', { name: 'Confirm action' })).toHaveAttribute(
      'aria-modal',
      'true',
    );
    await user.keyboard('{Escape}');
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('handles Table loading, error retry, empty, and sorted populated states', async () => {
    const user = userEvent.setup();
    const onRetry = vi.fn();
    const onSort = vi.fn();
    const columns: TableColumn<{ id: string; name: string }>[] = [
      {
        key: 'name',
        header: 'Name',
        sortable: true,
        render: (row) => row.name,
      },
    ];

    const { rerender } = render(
      <Table
        caption="Sample table"
        columns={columns}
        rows={[]}
        getRowKey={(r) => r.id}
        loading
      />,
    );
    expect(screen.getByLabelText('Loading table data')).toBeInTheDocument();

    rerender(
      <Table
        caption="Sample table"
        columns={columns}
        rows={[]}
        getRowKey={(r) => r.id}
        error="Connection refused"
        onRetry={onRetry}
      />,
    );
    await user.click(screen.getByRole('button', { name: 'Retry' }));
    expect(onRetry).toHaveBeenCalledTimes(1);

    rerender(
      <Table
        caption="Sample table"
        columns={columns}
        rows={[{ id: '1', name: 'Alpha' }]}
        getRowKey={(r) => r.id}
        sortKey="name"
        sortDirection="asc"
        onSortChange={onSort}
      />,
    );
    await user.click(screen.getByRole('button', { name: 'Name' }));
    expect(onSort).toHaveBeenCalledWith('name');
  });

  it('navigates pages in Pagination and marks current page', async () => {
    const user = userEvent.setup();
    const onPageChange = vi.fn();

    render(
      <Pagination
        page={1}
        pageSize={4}
        totalItems={10}
        onPageChange={onPageChange}
      />,
    );

    expect(screen.getByRole('button', { name: '1' })).toHaveAttribute(
      'aria-current',
      'page',
    );
    await user.click(screen.getByRole('button', { name: 'Next page' }));
    expect(onPageChange).toHaveBeenCalledWith(2);
  });

  it('triggers and dismisses Toast notifications', async () => {
    const user = userEvent.setup();

    function Trigger() {
      const { notify } = useToast();
      return (
        <Button
          onClick={() =>
            notify({
              variant: 'success',
              title: 'Saved',
              description: 'Preferences updated.',
              durationMs: 0,
            })
          }
        >
          Notify
        </Button>
      );
    }

    render(
      <ToastProvider>
        <Trigger />
      </ToastProvider>,
    );

    await user.click(screen.getByRole('button', { name: 'Notify' }));
    expect(screen.getByText('Saved')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Dismiss Saved' }));
    expect(screen.queryByText('Saved')).not.toBeInTheDocument();
  });

  it('renders Breadcrumbs, PageHeader, Alert, Badge, and StateBoundary', () => {
    render(
      <MemoryRouter>
        <Breadcrumbs
          items={[{ label: 'Home', to: '/' }, { label: 'Current' }]}
        />
        <PageHeader title="Title" subtitle="Subtitle" badge={<Badge>Tag</Badge>} />
        <Alert variant="info" title="Notice" description="Detail" />
        <StateBoundary status="empty" emptyTitle="Nothing yet" emptyDescription="None">
          <span>Hidden</span>
        </StateBoundary>
      </MemoryRouter>,
    );

    expect(screen.getByRole('navigation', { name: 'Breadcrumb' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Title' })).toBeInTheDocument();
    expect(screen.getByText('Nothing yet')).toBeInTheDocument();
  });
});

