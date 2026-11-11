import { useEffect, useRef, useState, type ReactNode } from 'react';
import { LogOut, Menu, Palette, X } from 'lucide-react';
import { Link, NavLink, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../../app/authContext';
import {
  getVisibleNavItems,
  type NavItem,
} from '../../app/navigation';
import { Badge, Button, useToast } from '../ui';

const NAV_GROUPS: NavItem['group'][] = ['Workspace', 'Modules', 'Governance'];

export function AppShell({ children }: { children: ReactNode }) {
  const [drawerOpen, setDrawerOpen] = useState(false);
  const { session, logout, switchOrganization } = useAuth();
  const { notify } = useToast();
  const location = useLocation();
  const navigate = useNavigate();
  const menuButtonRef = useRef<HTMLButtonElement>(null);

  const role = session?.activeOrganization.role ?? 'EMPLOYEE';
  const visibleItems = getVisibleNavItems(role);

  useEffect(() => {
    setDrawerOpen(false);
  }, [location.pathname]);

  useEffect(() => {
    if (!drawerOpen) {
      return;
    }

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setDrawerOpen(false);
        menuButtonRef.current?.focus();
      }
    };

    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [drawerOpen]);

  const handleOrgSwitch = async (nextOrgId: string) => {
    if (!session || nextOrgId === session.activeOrganization.id) {
      return;
    }
    try {
      await switchOrganization(nextOrgId);
      notify({
        variant: 'info',
        title: 'Organization switched',
        description: 'Active tenant context updated.',
      });
    } catch (err) {
      notify({
        variant: 'error',
        title: 'Organization switch failed',
        description:
          err instanceof Error ? err.message : 'Unable to switch organization.',
      });
    }
  };

  const handleLogout = async () => {
    await logout();
    navigate('/login', { replace: true });
  };

  return (
    <div className="app-shell">
      <a href="#main-content" className="skip-link">
        Skip to main content
      </a>

      {drawerOpen ? (
        <div
          className="app-drawer-backdrop app-drawer-backdrop--open"
          aria-hidden="true"
          onClick={() => setDrawerOpen(false)}
        />
      ) : null}

      <aside
        id="primary-sidebar"
        aria-label="Primary navigation"
        className={`app-sidebar ${drawerOpen ? 'app-sidebar--open' : ''}`.trim()}
      >
        <div className="app-sidebar__brand">
          <Link to="/dashboard" className="app-brand">
            <span className="app-brand__mark" aria-hidden="true">
              SB
            </span>
            <span>SmartBiz</span>
          </Link>
          <Button
            variant="ghost"
            size="sm"
            className="app-sidebar-close-btn"
            onClick={() => {
              setDrawerOpen(false);
              menuButtonRef.current?.focus();
            }}
            aria-label="Close navigation menu"
          >
            <X size={16} aria-hidden="true" />
          </Button>
        </div>

        <nav className="app-sidebar__nav" aria-label="Modules">
          {NAV_GROUPS.map((group) => {
            const items = visibleItems.filter((item) => item.group === group);
            if (items.length === 0) {
              return null;
            }

            return (
              <div key={group}>
                <div className="app-nav-group__label">{group}</div>
                <ul className="app-nav-list">
                  {items.map((item) => {
                    const Icon = item.icon;
                    return (
                      <li key={item.id}>
                        <NavLink
                          to={item.path}
                          className={({ isActive }) =>
                            `app-nav-link ${isActive ? 'app-nav-link--active' : ''}`.trim()
                          }
                        >
                          <span className="app-nav-link__main">
                            <Icon size={16} aria-hidden="true" />
                            <span>{item.label}</span>
                          </span>
                          <Badge
                            variant={item.statusLabel === 'Active' ? 'success' : 'neutral'}
                            size="sm"
                          >
                            {item.statusLabel}
                          </Badge>
                        </NavLink>
                      </li>
                    );
                  })}
                </ul>
              </div>
            );
          })}

          <div>
            <div className="app-nav-group__label">Foundation</div>
            <ul className="app-nav-list">
              <li>
                <NavLink
                  to="/design-system"
                  className={({ isActive }) =>
                    `app-nav-link ${isActive ? 'app-nav-link--active' : ''}`.trim()
                  }
                >
                  <span className="app-nav-link__main">
                    <Palette size={16} aria-hidden="true" />
                    <span>Design System</span>
                  </span>
                  <Badge variant="info" size="sm">
                    Phase 00
                  </Badge>
                </NavLink>
              </li>
            </ul>
          </div>
        </nav>

        <div className="app-sidebar__footer">
          <Badge variant="success" size="sm">
            {session?.activeOrganization.role ?? 'AUTHENTICATED'}
          </Badge>
          <span
            style={{
              fontSize: 'var(--text-xs)',
              color: 'var(--color-text-secondary)',
            }}
          >
            {session?.user.displayName || session?.user.email} ·{' '}
            {session?.activeOrganization.slug}
          </span>
        </div>
      </aside>

      <div className="app-main-column">
        <header className="app-topbar">
          <div className="app-topbar__left">
            <Button
              ref={menuButtonRef}
              variant="secondary"
              size="sm"
              className="app-mobile-menu-btn"
              aria-label="Open navigation menu"
              aria-expanded={drawerOpen}
              aria-controls="primary-sidebar"
              onClick={() => setDrawerOpen(true)}
            >
              <Menu size={16} aria-hidden="true" />
            </Button>
            <span
              style={{
                fontSize: 'var(--text-sm)',
                fontWeight: 'var(--weight-medium)',
                color: 'var(--color-text-secondary)',
              }}
            >
              {session?.activeOrganization.name ?? 'Enterprise CRM + ERP'}
            </span>
          </div>

          <div className="app-topbar__right">
            {session && session.organizations.length > 0 ? (
              <div className="app-role-switcher">
                <label
                  htmlFor="active-organization-select"
                  className="app-role-switcher__label"
                >
                  Organization:
                </label>
                <select
                  id="active-organization-select"
                  aria-label="Active organization"
                  className="ui-select app-role-switcher__select"
                  value={session.activeOrganization.id}
                  onChange={(event) => void handleOrgSwitch(event.target.value)}
                >
                  {session.organizations.map((org) => (
                    <option key={org.id} value={org.id}>
                      {org.name} ({org.role})
                    </option>
                  ))}
                </select>
              </div>
            ) : null}

            <Button
              variant="ghost"
              size="sm"
              leftIcon={<LogOut size={15} aria-hidden="true" />}
              onClick={() => void handleLogout()}
            >
              Sign out
            </Button>
          </div>
        </header>

        <main id="main-content" className="app-content" tabIndex={-1}>
          {children}
        </main>
      </div>
    </div>
  );
}
