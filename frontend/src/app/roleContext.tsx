import { createContext, useContext, useMemo, useState, type ReactNode } from 'react';
import type { UserRole } from './navigation';

interface RoleContextValue {
  role: UserRole;
  setRole: (role: UserRole) => void;
}

const RoleContext = createContext<RoleContextValue | null>(null);

export function RoleProvider({
  initialRole = 'OWNER',
  children,
}: {
  initialRole?: UserRole;
  children: ReactNode;
}) {
  const [role, setRole] = useState<UserRole>(initialRole);
  const value = useMemo(() => ({ role, setRole }), [role]);

  return <RoleContext.Provider value={value}>{children}</RoleContext.Provider>;
}

// eslint-disable-next-line react-refresh/only-export-components
export function useRolePreview(): RoleContextValue {
  const ctx = useContext(RoleContext);
  if (!ctx) {
    throw new Error('useRolePreview must be used within a RoleProvider');
  }
  return ctx;
}

