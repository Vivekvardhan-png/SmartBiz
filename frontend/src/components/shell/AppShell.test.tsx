import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { AppRoutes } from '../../App';
import { AuthProvider, type SessionInfo } from '../../app/authContext';
import type { UserRole } from '../../app/navigation';
import { ToastProvider } from '../ui';

function buildSession(role: UserRole): SessionInfo {
  const isAdmin = role === 'OWNER' || role === 'ADMIN';
  return {
    user: {
      id: 'user-1',
      email: 'alex@acme.test',
      displayName: 'Alex Rivera',
    },
    activeOrganization: {
      id: 'org-1',
      name: 'Acme Corp',
      slug: 'acme-corp',
      role,
    },
    permissions: isAdmin
      ? [
          'ORG_MANAGE',
          'MEMBERS_READ',
          'MEMBERS_MANAGE',
          'CRM_READ',
          'CRM_WRITE',
          'INVENTORY_READ',
          'INVENTORY_WRITE',
          'FINANCE_READ',
          'FINANCE_WRITE',
          'INSIGHTS_READ',
          'AI_USE',
        ]
      : ['CRM_READ', 'CRM_WRITE', 'INVENTORY_READ', 'INVENTORY_WRITE'],
    organizations: [
      {
        id: 'org-1',
        name: 'Acme Corp',
        slug: 'acme-corp',
        role,
      },
    ],
  };
}

function renderAuthenticatedShell(
  role: UserRole = 'OWNER',
  initialEntries: string[] = ['/dashboard'],
) {
  return render(
    <ToastProvider>
      <AuthProvider initialSession={buildSession(role)} initialToken="test-token">
        <MemoryRouter initialEntries={initialEntries}>
          <AppRoutes />
        </MemoryRouter>
      </AuthProvider>
    </ToastProvider>,
  );
}

describe('AppShell authentication and role-aware visibility', () => {
  it('redirects unauthenticated users to /login', () => {
    render(
      <ToastProvider>
        <AuthProvider>
          <MemoryRouter initialEntries={['/dashboard']}>
            <AppRoutes />
          </MemoryRouter>
        </AuthProvider>
      </ToastProvider>,
    );

    expect(
      screen.getByRole('heading', { name: 'Sign in to SmartBiz' }),
    ).toBeInTheDocument();
  });

  it('renders all 7 core navigation modules for the Owner role', () => {
    renderAuthenticatedShell('OWNER', ['/dashboard']);
    const nav = screen.getByRole('navigation', { name: 'Modules' });

    for (const label of [
      'Dashboard',
      'CRM',
      'Operations',
      'Finance',
      'HR',
      'Insights',
      'Administration',
    ]) {
      expect(within(nav).getByText(label)).toBeInTheDocument();
    }
  });

  it('hides restricted modules for the Employee role', () => {
    renderAuthenticatedShell('EMPLOYEE', ['/dashboard']);
    const nav = screen.getByRole('navigation', { name: 'Modules' });

    expect(within(nav).getByText('Dashboard')).toBeInTheDocument();
    expect(within(nav).getByText('CRM')).toBeInTheDocument();
    expect(within(nav).getByText('Operations')).toBeInTheDocument();

    expect(within(nav).queryByText('Finance')).not.toBeInTheDocument();
    expect(within(nav).queryByText('HR')).not.toBeInTheDocument();
    expect(within(nav).queryByText('Insights')).not.toBeInTheDocument();
    expect(within(nav).queryByText('Administration')).not.toBeInTheDocument();
  });

  it('blocks Employee from accessing /administration controls', () => {
    renderAuthenticatedShell('EMPLOYEE', ['/administration']);
    expect(screen.getByText('Insufficient permissions')).toBeInTheDocument();
  });

  it('toggles mobile navigation drawer and closes with Escape', async () => {
    const user = userEvent.setup();
    renderAuthenticatedShell('OWNER', ['/dashboard']);

    const menuButton = screen.getByRole('button', {
      name: 'Open navigation menu',
    });
    expect(menuButton).toHaveAttribute('aria-expanded', 'false');

    await user.click(menuButton);
    expect(menuButton).toHaveAttribute('aria-expanded', 'true');

    await user.keyboard('{Escape}');
    expect(menuButton).toHaveAttribute('aria-expanded', 'false');
  });

  it('does not render Foundation or Design System navigation items', () => {
    renderAuthenticatedShell('OWNER', ['/dashboard']);
    const nav = screen.getByRole('navigation', { name: 'Modules' });

    expect(within(nav).queryByText('Foundation')).not.toBeInTheDocument();
    expect(within(nav).queryByText('Design System')).not.toBeInTheDocument();
    expect(within(nav).queryByText(/Phase 00/i)).not.toBeInTheDocument();
  });

  it('treats /design-system as an unknown route via the fallback page', () => {
    renderAuthenticatedShell('OWNER', ['/design-system']);
    expect(
      screen.getByRole('heading', { name: 'Page not found' }),
    ).toBeInTheDocument();
  });

  it('renders the planned HR module without development phase labels', () => {
    renderAuthenticatedShell('OWNER', ['/hr']);
    expect(screen.getByRole('heading', { name: 'HR' })).toBeInTheDocument();
    expect(
      screen.getByText('HR is planned for a future release'),
    ).toBeInTheDocument();
    expect(screen.queryByText(/Prompt|Phase/i)).not.toBeInTheDocument();
  });
});
