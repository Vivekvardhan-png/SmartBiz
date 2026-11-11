import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Layers, ShieldAlert } from 'lucide-react';
import { isRouteAllowedForRole, type NavItem } from '../app/navigation';
import { useRolePreview } from '../app/roleContext';
import {
  Alert,
  Badge,
  Button,
  Card,
  CardContent,
  CardHeader,
  PageHeader,
  Select,
  StateBoundary,
  type ScreenStatus,
} from '../components/ui';

export function ModuleFoundationPage({ module }: { module: NavItem }) {
  const { role } = useRolePreview();
  const [previewStatus, setPreviewStatus] = useState<ScreenStatus>('empty');
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
          badge={<Badge variant="warning">Restricted in Role View</Badge>}
        />

        <Alert
          variant="warning"
          title="Module hidden for the selected role"
          description={`The ${module.label} section is not visible to the ${role} role in the shell visibility policy. Server-side authorization will enforce this boundary in Prompt 02.`}
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
        badge={
          <Badge variant="neutral">
            {module.statusLabel} · {module.targetPhase}
          </Badge>
        }
        actions={
          <div style={{ minWidth: '11rem' }}>
            <Select
              label="State Preview"
              aria-label="Screen state preview"
              value={previewStatus}
              onChange={(event) =>
                setPreviewStatus(event.target.value as ScreenStatus)
              }
              options={[
                { value: 'empty', label: 'Empty State' },
                { value: 'loading', label: 'Loading State' },
                { value: 'error', label: 'Error State' },
                { value: 'success', label: 'Module Scope' },
              ]}
            />
          </div>
        }
      />

      <Alert
        variant="info"
        title={`${module.label} workflows are not implemented yet`}
        description={`Phase 00 establishes the design system and application shell only. Backend APIs and database persistence for ${module.label} are scheduled for ${module.targetPhase}.`}
      />

      <StateBoundary
        status={previewStatus}
        errorTitle={`${module.label} service unavailable`}
        errorMessage="No backend endpoint is connected in Phase 00. Failed requests surface explicit recovery actions rather than fallback fixture data."
        onRetry={() => setPreviewStatus('empty')}
        emptyTitle={`${module.label} module is awaiting ${module.targetPhase}`}
        emptyDescription="No live records are queried or fabricated in the Phase 00 design foundation."
        emptyAction={
          <>
            <Link
              to="/design-system"
              className="ui-btn ui-btn--primary ui-btn--md"
            >
              Inspect Design System
            </Link>
            <Button
              variant="secondary"
              onClick={() => setPreviewStatus('success')}
            >
              View Module Scope
            </Button>
          </>
        }
      >
        <Card>
          <CardHeader
            title={`${module.label} Planned Architecture Scope`}
            description={`Scheduled delivery: ${module.targetPhase}`}
            actions={<Badge variant="info">Foundation Ready</Badge>}
          />
          <CardContent>
            <div className="foundation-stack">
              <p style={{ color: 'var(--color-text-secondary)' }}>
                {module.description}
              </p>
              <div className="foundation-row">
                <span className="ui-badge ui-badge--neutral ui-badge--md">
                  <Layers size={14} aria-hidden="true" />
                  Authorized Roles: {module.allowedRoles.join(', ')}
                </span>
                <span className="ui-badge ui-badge--neutral ui-badge--md">
                  <ShieldAlert size={14} aria-hidden="true" />
                  Organization-Scoped Access Required
                </span>
              </div>
            </div>
          </CardContent>
        </Card>
      </StateBoundary>
    </div>
  );
}
