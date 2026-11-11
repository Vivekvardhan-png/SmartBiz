import { ChevronRight } from 'lucide-react';
import { Link } from 'react-router-dom';

export interface BreadcrumbItem {
  label: string;
  to?: string;
}

export interface BreadcrumbsProps {
  items: BreadcrumbItem[];
}

export function Breadcrumbs({ items }: BreadcrumbsProps) {
  if (items.length === 0) {
    return null;
  }

  return (
    <nav className="ui-breadcrumbs" aria-label="Breadcrumb">
      <ol className="ui-breadcrumbs__list">
        {items.map((item, index) => {
          const isLast = index === items.length - 1;
          return (
            <li key={`${item.label}-${index}`} className="ui-breadcrumbs__item">
              {index > 0 ? (
                <ChevronRight size={13} aria-hidden="true" />
              ) : null}
              {item.to && !isLast ? (
                <Link to={item.to} className="ui-breadcrumbs__link">
                  {item.label}
                </Link>
              ) : (
                <span
                  className={isLast ? 'ui-breadcrumbs__current' : undefined}
                  aria-current={isLast ? 'page' : undefined}
                >
                  {item.label}
                </span>
              )}
            </li>
          );
        })}
      </ol>
    </nav>
  );
}

