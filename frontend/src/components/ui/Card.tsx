import type { HTMLAttributes, ReactNode } from 'react';

export interface CardProps extends HTMLAttributes<HTMLDivElement> {
  children: ReactNode;
}

export function Card({ children, className = '', ...rest }: CardProps) {
  return (
    <section className={`ui-card ${className}`.trim()} {...rest}>
      {children}
    </section>
  );
}

export interface CardHeaderProps
  extends Omit<HTMLAttributes<HTMLDivElement>, 'title'> {
  title: ReactNode;
  description?: ReactNode;
  actions?: ReactNode;
}

export function CardHeader({
  title,
  description,
  actions,
  className = '',
  ...rest
}: CardHeaderProps) {
  return (
    <div className={`ui-card__header ${className}`.trim()} {...rest}>
      <div>
        <h2 className="ui-card__title">{title}</h2>
        {description ? (
          <p className="ui-card__description">{description}</p>
        ) : null}
      </div>
      {actions ? <div>{actions}</div> : null}
    </div>
  );
}

export interface CardContentProps extends HTMLAttributes<HTMLDivElement> {
  compact?: boolean;
  children: ReactNode;
}

export function CardContent({
  compact = false,
  children,
  className = '',
  ...rest
}: CardContentProps) {
  return (
    <div
      className={`ui-card__content ${compact ? 'ui-card__content--compact' : ''} ${className}`.trim()}
      {...rest}
    >
      {children}
    </div>
  );
}

export interface CardFooterProps extends HTMLAttributes<HTMLDivElement> {
  children: ReactNode;
}

export function CardFooter({
  children,
  className = '',
  ...rest
}: CardFooterProps) {
  return (
    <div className={`ui-card__footer ${className}`.trim()} {...rest}>
      {children}
    </div>
  );
}

