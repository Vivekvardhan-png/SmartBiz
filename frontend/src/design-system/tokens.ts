/**
 * Centralized TypeScript design tokens matching `tokens.css`.
 * Keep synchronized when updating palette, typography, spacing, or motion.
 */
export const designTokens = {
  colors: {
    bgApp: '#FAF9F6',
    bgSurface: '#FFFFFF',
    bgSecondary: '#F5F5F4',
    border: '#E7E5E4',
    borderStrong: '#D6D3D1',
    textPrimary: '#1C1917',
    textSecondary: '#78716C',
    actionPrimary: '#2563EB',
    actionHover: '#1D4ED8',
    success: '#15803D',
    warning: '#B45309',
    error: '#B91C1C',
  },
  typography: {
    fontSans:
      "'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif",
    fontMono:
      "'JetBrains Mono', 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace",
    sizes: {
      xs: '0.75rem',
      sm: '0.8125rem',
      base: '0.875rem',
      md: '1rem',
      lg: '1.125rem',
      xl: '1.25rem',
      '2xl': '1.5rem',
    },
  },
  spacing: {
    1: '0.25rem',
    2: '0.5rem',
    3: '0.75rem',
    4: '1rem',
    5: '1.25rem',
    6: '1.5rem',
    8: '2rem',
    10: '2.5rem',
    12: '3rem',
  },
  radii: {
    sm: '0.375rem',
    md: '0.5rem',
    lg: '0.75rem',
    full: '9999px',
  },
  shadows: {
    xs: '0 1px 2px rgba(28, 25, 23, 0.04)',
    sm: '0 1px 3px rgba(28, 25, 23, 0.07), 0 1px 2px rgba(28, 25, 23, 0.04)',
    md: '0 4px 12px rgba(28, 25, 23, 0.08), 0 2px 4px rgba(28, 25, 23, 0.04)',
    lg: '0 12px 32px rgba(28, 25, 23, 0.14), 0 4px 8px rgba(28, 25, 23, 0.06)',
  },
  focusRings: {
    default: '0 0 0 2px #FFFFFF, 0 0 0 4px rgba(37, 99, 235, 0.55)',
    error: '0 0 0 2px #FFFFFF, 0 0 0 4px rgba(185, 28, 28, 0.45)',
  },
  transitions: {
    fast: '120ms cubic-bezier(0.2, 0, 0, 1)',
    base: '180ms cubic-bezier(0.2, 0, 0, 1)',
  },
} as const;

export type DesignTokens = typeof designTokens;

