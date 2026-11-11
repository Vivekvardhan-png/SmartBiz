import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider, useAuth } from './app/authContext';
import { NAVIGATION_ITEMS } from './app/navigation';
import { AppShell } from './components/shell/AppShell';
import { SkeletonText, ToastProvider } from './components/ui';
import { AdministrationPage } from './pages/AdministrationPage';
import { DesignSystemPage } from './pages/DesignSystemPage';
import { LoginPage } from './pages/LoginPage';
import { ModuleFoundationPage } from './pages/ModuleFoundationPage';
import { NotFoundPage } from './pages/NotFoundPage';
import { RegisterPage } from './pages/RegisterPage';

function ProtectedShellRoutes() {
  const { status } = useAuth();

  if (status === 'loading') {
    return (
      <div style={{ maxWidth: '40rem', margin: '4rem auto', padding: '0 1rem' }}>
        <SkeletonText lines={4} />
      </div>
    );
  }

  if (status !== 'authenticated') {
    return <Navigate to="/login" replace />;
  }

  return (
    <AppShell>
      <Routes>
        <Route path="/" element={<Navigate to="/dashboard" replace />} />
        {NAVIGATION_ITEMS.map((item) => (
          <Route
            key={item.id}
            path={item.path}
            element={
              item.id === 'administration' ? (
                <AdministrationPage />
              ) : (
                <ModuleFoundationPage module={item} />
              )
            }
          />
        ))}
        <Route path="/design-system" element={<DesignSystemPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </AppShell>
  );
}

export function AppRoutes() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/*" element={<ProtectedShellRoutes />} />
    </Routes>
  );
}

export function App() {
  return (
    <ToastProvider>
      <AuthProvider>
        <BrowserRouter>
          <AppRoutes />
        </BrowserRouter>
      </AuthProvider>
    </ToastProvider>
  );
}

export default App;
