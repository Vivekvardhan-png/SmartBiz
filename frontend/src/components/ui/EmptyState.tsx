import type { ReactNode } from 'react';
import { Inbox } from 'lucide-react';

export interface EmptyStateProps {
  icon?: ReactNode;
  title: string;
  description: string;
  actions?: ReactNode;
  className?: string;
}

export function EmptyState({
  icon,
  title,
  description,
  actions,
  className = '',
}: EmptyStateProps) {
  return (
    <div className={`ui-empty ${className}`.trim()}>
      <div className="ui-empty__icon" aria-hidden="true">
        {icon ?? <Inbox size={20} />}
      </div>
      <h3 className="ui-empty__title">{title}</h3>
      <p className="ui-empty__description">{description}</p>
      {actions ? <div className="ui-empty__actions">{actions}</div> : null}
    </div>
  );
}

