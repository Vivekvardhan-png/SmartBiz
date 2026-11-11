import { Link } from 'react-router-dom';
import { Briefcase } from 'lucide-react';
import { isRouteAllowedForRole, type NavItem } from '../app/navigation';
import { useAuth } from '../app/authContext';
import {
  Alert,
  Badge,
  EmptyState,
  PageHeader,
} from '../components/ui';

export function PlannedModulePage({ module }: { module: NavItem }) {
  const { session } = useAuth();
  const role = session?.activeOrganization.role ?? 'EMPLOYEE';
  const allowed = isRouteAllowedForRole(module, role);

  if (!allowed) {
    return (
      <div className="foundation-stack">
        <PageHeader
          title={module.label}
          subtitle={module.description}
          breadcrumbs={[
            { label: 'Workspace', to: '/dashboard' },
            { label: module.label },
          ]}
          badge={<Badge variant="warning">Restricted</Badge>}
        />

        <Alert
          variant="warning"
          title="Insufficient permissions"
          description={`The ${module.label} module is not available for the ${role} role.`}
          actions={
            <Link to="/dashboard" className="ui-btn ui-btn--secondary ui-btn--sm">
              Return to Dashboard
            </Link>
          }
        />
      </div>
    );
  }

  return (
    <div className="foundation-stack">
      <PageHeader
        title={module.label}
        subtitle={module.description}
        breadcrumbs={[
          { label: 'Workspace', to: '/dashboard' },
          { label: module.label },
        ]}
        badge={<Badge variant="neutral">{module.statusLabel}</Badge>}
      />

      <EmptyState
        icon={<Briefcase size={20} />}
        title={`${module.label} is planned for a future release`}
        description="This workspace module is not yet enabled for your organization."
        actions={
          <Link to="/dashboard" className="ui-btn ui-btn--primary ui-btn--md">
            Return to Dashboard
          </Link>
        }
      />
    </div>
  );
}

