import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import type { UserRole } from './navigation';
import {
  apiRequest,
  ApiError,
  setUnauthorizedHandler,
} from '../lib/apiClient';

export type Permission =
  | 'ORG_MANAGE'
  | 'MEMBERS_READ'
  | 'MEMBERS_MANAGE'
  | 'CRM_READ'
  | 'CRM_WRITE'
  | 'INVENTORY_READ'
  | 'INVENTORY_WRITE'
  | 'FINANCE_READ'
  | 'FINANCE_WRITE'
  | 'INSIGHTS_READ'
  | 'AI_USE';

export interface UserSummary {
  id: string;
  email: string;
  displayName: string;
}

export interface OrganizationSummary {
  id: string;
  name: string;
  slug: string;
  role: UserRole;
}

export interface SessionInfo {
  user: UserSummary;
  activeOrganization: OrganizationSummary;
  permissions: Permission[];
  organizations: OrganizationSummary[];
}

export interface AuthResponse extends SessionInfo {
  accessToken: string;
  tokenType: string;
  expiresAt: string;
}

export interface RegisterInput {
  email: string;
  password: string;
  displayName: string;
  organizationName: string;
  organizationSlug?: string;
}

export interface LoginInput {
  email: string;
  password: string;
  organizationId?: string;
}

export type AuthStatus = 'loading' | 'authenticated' | 'unauthenticated';

interface AuthContextValue {
  status: AuthStatus;
  token: string | null;
  session: SessionInfo | null;
  sessionError: string | null;
  login: (input: LoginInput) => Promise<void>;
  register: (input: RegisterInput) => Promise<void>;
  logout: () => Promise<void>;
  switchOrganization: (organizationId: string) => Promise<void>;
  refreshSession: () => Promise<void>;
  hasPermission: (permission: Permission) => boolean;
}

const TOKEN_STORAGE_KEY = 'smartbiz.auth.token';

const AuthContext = createContext<AuthContextValue | null>(null);

function readStoredToken(): string | null {
  try {
    return window.sessionStorage.getItem(TOKEN_STORAGE_KEY);
  } catch {
    return null;
  }
}

function writeStoredToken(token: string | null): void {
  try {
    if (token) {
      window.sessionStorage.setItem(TOKEN_STORAGE_KEY, token);
    } else {
      window.sessionStorage.removeItem(TOKEN_STORAGE_KEY);
    }
  } catch {
    // Ignore storage unavailability
  }
}

export function AuthProvider({
  children,
  initialSession = null,
  initialToken = null,
}: {
  children: ReactNode;
  initialSession?: SessionInfo | null;
  initialToken?: string | null;
}) {
  const [token, setToken] = useState<string | null>(
    () => initialToken ?? readStoredToken(),
  );
  const [session, setSession] = useState<SessionInfo | null>(initialSession);
  const [status, setStatus] = useState<AuthStatus>(() => {
    if (initialSession) {
      return 'authenticated';
    }
    return readStoredToken() ? 'loading' : 'unauthenticated';
  });
  const [sessionError, setSessionError] = useState<string | null>(null);

  const clearAuth = useCallback(() => {
    writeStoredToken(null);
    setToken(null);
    setSession(null);
    setStatus('unauthenticated');
  }, []);

  useEffect(() => {
    setUnauthorizedHandler(clearAuth);
    return () => setUnauthorizedHandler(null);
  }, [clearAuth]);

  const applyAuthResponse = useCallback((auth: AuthResponse) => {
    writeStoredToken(auth.accessToken);
    setToken(auth.accessToken);
    setSession({
      user: auth.user,
      activeOrganization: auth.activeOrganization,
      permissions: auth.permissions,
      organizations: auth.organizations,
    });
    setSessionError(null);
    setStatus('authenticated');
  }, []);

  const refreshSession = useCallback(async () => {
    const activeToken = token ?? readStoredToken();
    if (!activeToken) {
      clearAuth();
      return;
    }

    setStatus((prev) => (prev === 'authenticated' ? prev : 'loading'));
    try {
      const info = await apiRequest<SessionInfo>('/api/v1/auth/me', {
        token: activeToken,
      });
      setSession(info);
      setSessionError(null);
      setStatus('authenticated');
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) {
        clearAuth();
        return;
      }
      setSessionError(
        err instanceof Error
          ? err.message
          : 'Unable to validate current session.',
      );
      setStatus('unauthenticated');
    }
  }, [clearAuth, token]);

  useEffect(() => {
    if (!initialSession && !session && token) {
      void refreshSession();
    }
  }, [initialSession, refreshSession, session, token]);

  const login = useCallback(
    async (input: LoginInput) => {
      const auth = await apiRequest<AuthResponse>('/api/v1/auth/login', {
        method: 'POST',
        body: input,
        skipAuthExpiryHook: true,
      });
      applyAuthResponse(auth);
    },
    [applyAuthResponse],
  );

  const register = useCallback(
    async (input: RegisterInput) => {
      const auth = await apiRequest<AuthResponse>('/api/v1/auth/register', {
        method: 'POST',
        body: input,
        skipAuthExpiryHook: true,
      });
      applyAuthResponse(auth);
    },
    [applyAuthResponse],
  );

  const switchOrganization = useCallback(
    async (organizationId: string) => {
      if (!token) {
        throw new ApiError(401, 'Not authenticated.');
      }
      const auth = await apiRequest<AuthResponse>(
        '/api/v1/auth/switch-organization',
        {
          method: 'POST',
          token,
          body: { organizationId },
        },
      );
      applyAuthResponse(auth);
    },
    [applyAuthResponse, token],
  );

  const logout = useCallback(async () => {
    const activeToken = token;
    clearAuth();
    if (activeToken) {
      try {
        await apiRequest<void>('/api/v1/auth/logout', {
          method: 'POST',
          token: activeToken,
          skipAuthExpiryHook: true,
        });
      } catch {
        // Local session already cleared; ignore network errors on logout.
      }
    }
  }, [clearAuth, token]);

  const hasPermission = useCallback(
    (permission: Permission) =>
      session?.permissions.includes(permission) ?? false,
    [session],
  );

  const value = useMemo<AuthContextValue>(
    () => ({
      status,
      token,
      session,
      sessionError,
      login,
      register,
      logout,
      switchOrganization,
      refreshSession,
      hasPermission,
    }),
    [
      hasPermission,
      login,
      logout,
      refreshSession,
      register,
      session,
      sessionError,
      status,
      switchOrganization,
      token,
    ],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return ctx;
}
