import type { CSSProperties } from 'react';

export interface SkeletonProps {
  width?: string | number;
  height?: string | number;
  className?: string;
  style?: CSSProperties;
}

export function Skeleton({
  width = '100%',
  height = '1rem',
  className = '',
  style,
}: SkeletonProps) {
  return (
    <span
      aria-hidden="true"
      className={`ui-skeleton ${className}`.trim()}
      style={{ width, height, ...style }}
    />
  );
}

export interface SkeletonTextProps {
  lines?: number;
}

export function SkeletonText({ lines = 3 }: SkeletonTextProps) {
  return (
    <div className="ui-skeleton-stack" aria-busy="true" aria-label="Loading content">
      {Array.from({ length: lines }, (_, index) => (
        <Skeleton
          key={index}
          width={index === lines - 1 ? '68%' : '100%'}
          height="0.875rem"
        />
      ))}
    </div>
  );
}

export interface SkeletonTableProps {
  rows?: number;
  columns?: number;
}

export function SkeletonTable({ rows = 4, columns = 4 }: SkeletonTableProps) {
  return (
    <div
      className="ui-table-wrapper"
      aria-busy="true"
      aria-label="Loading table data"
    >
      <table className="ui-table">
        <thead>
          <tr>
            {Array.from({ length: columns }, (_, colIndex) => (
              <th key={colIndex}>
                <Skeleton width="70%" height="0.75rem" />
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {Array.from({ length: rows }, (_, rowIndex) => (
            <tr key={rowIndex}>
              {Array.from({ length: columns }, (_, colIndex) => (
                <td key={colIndex}>
                  <Skeleton width={colIndex === 0 ? '80%' : '60%'} height="0.875rem" />
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

