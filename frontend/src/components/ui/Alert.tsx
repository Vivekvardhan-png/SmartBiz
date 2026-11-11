import type { ReactNode } from 'react';
import {
  AlertCircle,
  AlertTriangle,
  CheckCircle2,
  Info,
  X,
} from 'lucide-react';
import { Button } from './Button';

export type AlertVariant = 'info' | 'success' | 'warning' | 'error';

export interface AlertProps {
  variant?: AlertVariant;
  title: string;
  description?: ReactNode;
  actions?: ReactNode;
  onDismiss?: () => void;
  className?: string;
}

const ALERT_ICONS = {
  info: Info,
  success: CheckCircle2,
  warning: AlertTriangle,
  error: AlertCircle,
} as const;

export function Alert({
  variant = 'info',
  title,
  description,
  actions,
  onDismiss,
  className = '',
}: AlertProps) {
  const Icon = ALERT_ICONS[variant];
  const role = variant === 'error' || variant === 'warning' ? 'alert' : 'status';

  return (
    <div role={role} className={`ui-alert ui-alert--${variant} ${className}`.trim()}>
      <Icon className="ui-alert__icon" size={18} aria-hidden="true" />
      <div className="ui-alert__body">
        <div className="ui-alert__title">{title}</div>
        {description ? (
          <div className="ui-alert__description">{description}</div>
        ) : null}
        {actions ? <div className="ui-alert__actions">{actions}</div> : null}
      </div>
      {onDismiss ? (
        <Button
          variant="ghost"
          size="sm"
          onClick={onDismiss}
          aria-label={`Dismiss ${title}`}
        >
          <X size={16} aria-hidden="true" />
        </Button>
      ) : null}
    </div>
  );
}

