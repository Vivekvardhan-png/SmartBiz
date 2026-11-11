import type { HTMLAttributes, ReactNode } from 'react';

export type BadgeVariant = 'neutral' | 'info' | 'success' | 'warning' | 'error';
export type BadgeSize = 'sm' | 'md';

export interface BadgeProps extends HTMLAttributes<HTMLSpanElement> {
  variant?: BadgeVariant;
  size?: BadgeSize;
  children: ReactNode;
}

export function Badge({
  variant = 'neutral',
  size = 'sm',
  children,
  className = '',
  ...rest
}: BadgeProps) {
  return (
    <span
      className={`ui-badge ui-badge--${variant} ui-badge--${size} ${className}`.trim()}
      {...rest}
    >
      {children}
    </span>
  );
}

