import type { ReactNode } from 'react';
import { Breadcrumbs, type BreadcrumbItem } from './Breadcrumbs';

export interface PageHeaderProps {
  title: string;
  subtitle?: string;
  breadcrumbs?: BreadcrumbItem[];
  badge?: ReactNode;
  actions?: ReactNode;
}

export function PageHeader({
  title,
  subtitle,
  breadcrumbs,
  badge,
  actions,
}: PageHeaderProps) {
  return (
    <header className="ui-page-header">
      {breadcrumbs && breadcrumbs.length > 0 ? (
        <Breadcrumbs items={breadcrumbs} />
      ) : null}
      <div className="ui-page-header__row">
        <div>
          <div className="ui-page-header__title-group">
            <h1 className="ui-page-header__title">{title}</h1>
            {badge}
          </div>
          {subtitle ? (
            <p className="ui-page-header__subtitle">{subtitle}</p>
          ) : null}
        </div>
        {actions ? (
          <div className="ui-page-header__actions">{actions}</div>
        ) : null}
      </div>
    </header>
  );
}

