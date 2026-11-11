import type { ReactNode } from 'react';
import { ArrowDown, ArrowUp, ArrowUpDown } from 'lucide-react';
import { Alert } from './Alert';
import { Button } from './Button';
import { EmptyState } from './EmptyState';
import { SkeletonTable } from './Skeleton';

export type SortDirection = 'asc' | 'desc';

export interface TableColumn<T> {
  key: string;
  header: string;
  align?: 'left' | 'center' | 'right';
  sortable?: boolean;
  render: (row: T) => ReactNode;
}

export interface TableProps<T> {
  caption: string;
  columns: TableColumn<T>[];
  rows: T[];
  getRowKey: (row: T) => string;
  loading?: boolean;
  error?: string | null;
  onRetry?: () => void;
  emptyTitle?: string;
  emptyDescription?: string;
  emptyAction?: ReactNode;
  sortKey?: string;
  sortDirection?: SortDirection;
  onSortChange?: (key: string) => void;
  onRowClick?: (row: T) => void;
}

export function Table<T>({
  caption,
  columns,
  rows,
  getRowKey,
  loading = false,
  error = null,
  onRetry,
  emptyTitle = 'No records available',
  emptyDescription = 'There are no items matching the current criteria.',
  emptyAction,
  sortKey,
  sortDirection,
  onSortChange,
  onRowClick,
}: TableProps<T>) {
  if (loading) {
    return <SkeletonTable rows={4} columns={columns.length} />;
  }

  if (error) {
    return (
      <Alert
        variant="error"
        title="Unable to load records"
        description={error}
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

  if (rows.length === 0) {
    return (
      <EmptyState
        title={emptyTitle}
        description={emptyDescription}
        actions={emptyAction}
      />
    );
  }

  return (
    <div className="ui-table-wrapper">
      <table className="ui-table">
        <caption className="sr-only">{caption}</caption>
        <thead>
          <tr>
            {columns.map((column) => {
              const alignClass =
                column.align === 'right'
                  ? 'ui-table__align-right'
                  : column.align === 'center'
                    ? 'ui-table__align-center'
                    : '';
              const isSorted = sortKey === column.key;

              return (
                <th
                  key={column.key}
                  scope="col"
                  className={alignClass}
                  aria-sort={
                    column.sortable && isSorted
                      ? sortDirection === 'asc'
                        ? 'ascending'
                        : 'descending'
                      : undefined
                  }
                >
                  {column.sortable && onSortChange ? (
                    <button
                      type="button"
                      className="ui-table__sort-btn"
                      onClick={() => onSortChange(column.key)}
                    >
                      <span>{column.header}</span>
                      {isSorted ? (
                        sortDirection === 'asc' ? (
                          <ArrowUp size={13} aria-hidden="true" />
                        ) : (
                          <ArrowDown size={13} aria-hidden="true" />
                        )
                      ) : (
                        <ArrowUpDown size={13} aria-hidden="true" />
                      )}
                    </button>
                  ) : (
                    column.header
                  )}
                </th>
              );
            })}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr
              key={getRowKey(row)}
              className={onRowClick ? 'ui-table__row--interactive' : undefined}
              onClick={onRowClick ? () => onRowClick(row) : undefined}
            >
              {columns.map((column) => {
                const alignClass =
                  column.align === 'right'
                    ? 'ui-table__align-right'
                    : column.align === 'center'
                      ? 'ui-table__align-center'
                      : '';
                return (
                  <td key={column.key} className={alignClass}>
                    {column.render(row)}
                  </td>
                );
              })}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

