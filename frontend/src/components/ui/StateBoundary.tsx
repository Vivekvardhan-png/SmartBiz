import type { ReactNode } from 'react';
import { Alert } from './Alert';
import { Button } from './Button';
import { EmptyState } from './EmptyState';
import { SkeletonText } from './Skeleton';

export type ScreenStatus = 'loading' | 'error' | 'empty' | 'success';

export interface StateBoundaryProps {
  status: ScreenStatus;
  errorTitle?: string;
  errorMessage?: string;
  onRetry?: () => void;
  emptyTitle?: string;
  emptyDescription?: string;
  emptyAction?: ReactNode;
  loadingFallback?: ReactNode;
  children: ReactNode;
}

export function StateBoundary({
  status,
  errorTitle = 'Request failed',
  errorMessage = 'The server did not return a valid response.',
  onRetry,
  emptyTitle = 'Nothing to display',
  emptyDescription = 'No records exist in this view yet.',
  emptyAction,
  loadingFallback,
  children,
}: StateBoundaryProps) {
  if (status === 'loading') {
    return <>{loadingFallback ?? <SkeletonText lines={4} />}</>;
  }

  if (status === 'error') {
    return (
      <Alert
        variant="error"
        title={errorTitle}
        description={errorMessage}
        actions={
          onRetry ? (
            <Button variant="secondary" size="sm" onClick={onRetry}>
              Retry
            </Button>
          ) : undefined
        }
      />
    );
  }

  if (status === 'empty') {
    return (
      <EmptyState
        title={emptyTitle}
        description={emptyDescription}
        actions={emptyAction}
      />
    );
  }

  return <>{children}</>;
}

