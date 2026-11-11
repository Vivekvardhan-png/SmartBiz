import { useEffect, useRef, useState, type ReactNode } from 'react';
import { Menu, Palette, X } from 'lucide-react';
import { Link, NavLink, useLocation } from 'react-router-dom';
import { Badge, Button } from '../ui';
import {
  getVisibleNavItems,
  ROLE_OPTIONS,
  type NavItem,
  type UserRole,
} from '../../app/navigation';
import { useRolePreview } from '../../app/roleContext';

const NAV_GROUPS: NavItem['group'][] = ['Workspace', 'Modules', 'Governance'];

export function AppShell({ children }: { children: ReactNode }) {
  const [drawerOpen, setDrawerOpen] = useState(false);
  const { role, setRole } = useRolePreview();
  const location = useLocation();
  const menuButtonRef = useRef<HTMLButtonElement>(null);

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
              NX
            </span>
            <span>NexusOps</span>
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
                          <Badge variant="neutral" size="sm">
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
          <Badge variant="warning" size="sm">
            Auth Pending (Prompt 02)
          </Badge>
          <span
            style={{
              fontSize: 'var(--text-xs)',
              color: 'var(--color-text-secondary)',
            }}
          >
            Role visibility is a UI shell preview only. Server-side tenant
            isolation starts in Prompt 02.
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
              Enterprise CRM + ERP Shell
            </span>
          </div>

          <div className="app-topbar__right">
            <div className="app-role-switcher">
              <label
                htmlFor="shell-role-preview"
                className="app-role-switcher__label"
              >
                Role visibility preview:
              </label>
              <select
                id="shell-role-preview"
                aria-label="Role visibility preview"
                className="ui-select app-role-switcher__select"
                value={role}
                onChange={(event) => setRole(event.target.value as UserRole)}
              >
                {ROLE_OPTIONS.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </div>
          </div>
        </header>

        <main id="main-content" className="app-content" tabIndex={-1}>
          {children}
        </main>
      </div>
    </div>
  );
}
