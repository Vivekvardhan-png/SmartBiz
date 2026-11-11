import { Link } from 'react-router-dom';
import { Compass } from 'lucide-react';
import { EmptyState, PageHeader } from '../components/ui';

export function NotFoundPage() {
  return (
    <div className="foundation-stack">
      <PageHeader
        title="Page not found"
        subtitle="The requested route does not exist in this application shell."
        breadcrumbs={[
          { label: 'Workspace', to: '/dashboard' },
          { label: 'Not Found' },
        ]}
      />
      <EmptyState
        icon={<Compass size={20} />}
        title="Route unavailable"
        description="Use the navigation menu or return to the Dashboard."
        actions={
          <Link to="/dashboard" className="ui-btn ui-btn--primary ui-btn--md">
            Go to Dashboard
          </Link>
        }
      />
    </div>
  );
}

