import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { AppRoutes } from '../../App';
import { RoleProvider } from '../../app/roleContext';
import { ToastProvider } from '../ui';

function renderShell(initialEntries: string[] = ['/dashboard']) {
  return render(
    <ToastProvider>
      <RoleProvider>
        <MemoryRouter initialEntries={initialEntries}>
          <AppRoutes />
        </MemoryRouter>
      </RoleProvider>
    </ToastProvider>,
  );
}

describe('AppShell navigation and role-aware visibility', () => {
  it('renders all 7 core navigation modules for the default Owner role', () => {
    renderShell(['/dashboard']);
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

  it('hides restricted modules when switching to Employee role preview', async () => {
    const user = userEvent.setup();
    renderShell(['/dashboard']);

    const roleSelect = screen.getByLabelText('Role visibility preview');
    await user.selectOptions(roleSelect, 'EMPLOYEE');

    const nav = screen.getByRole('navigation', { name: 'Modules' });
    expect(within(nav).getByText('Dashboard')).toBeInTheDocument();
    expect(within(nav).getByText('CRM')).toBeInTheDocument();
    expect(within(nav).getByText('Operations')).toBeInTheDocument();

    expect(within(nav).queryByText('Finance')).not.toBeInTheDocument();
    expect(within(nav).queryByText('HR')).not.toBeInTheDocument();
    expect(within(nav).queryByText('Insights')).not.toBeInTheDocument();
    expect(within(nav).queryByText('Administration')).not.toBeInTheDocument();
  });

  it('does not expose restricted route content when Employee visits /administration', async () => {
    const user = userEvent.setup();
    renderShell(['/administration']);

    const roleSelect = screen.getByLabelText('Role visibility preview');
    await user.selectOptions(roleSelect, 'EMPLOYEE');

    expect(
      screen.getByText('Module hidden for the selected role'),
    ).toBeInTheDocument();
  });

  it('toggles mobile navigation drawer and closes with Escape', async () => {
    const user = userEvent.setup();
    renderShell(['/dashboard']);

    const menuButton = screen.getByRole('button', {
      name: 'Open navigation menu',
    });
    expect(menuButton).toHaveAttribute('aria-expanded', 'false');

    await user.click(menuButton);
    expect(menuButton).toHaveAttribute('aria-expanded', 'true');

    await user.keyboard('{Escape}');
    expect(menuButton).toHaveAttribute('aria-expanded', 'false');
  });

  it('allows inspecting the Design System foundation route', () => {
    renderShell(['/design-system']);
    expect(
      screen.getByRole('heading', { name: 'Design System Foundation' }),
    ).toBeInTheDocument();
    expect(screen.getByText('Color Palette & Semantic Tokens')).toBeInTheDocument();
  });
});

