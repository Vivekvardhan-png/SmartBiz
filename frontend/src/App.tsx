import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { NAVIGATION_ITEMS } from './app/navigation';
import { RoleProvider } from './app/roleContext';
import { AppShell } from './components/shell/AppShell';
import { ToastProvider } from './components/ui';
import { DesignSystemPage } from './pages/DesignSystemPage';
import { ModuleFoundationPage } from './pages/ModuleFoundationPage';
import { NotFoundPage } from './pages/NotFoundPage';

export function AppRoutes() {
  return (
    <AppShell>
      <Routes>
        <Route path="/" element={<Navigate to="/dashboard" replace />} />
        {NAVIGATION_ITEMS.map((item) => (
          <Route
            key={item.id}
            path={item.path}
            element={<ModuleFoundationPage module={item} />}
          />
        ))}
        <Route path="/design-system" element={<DesignSystemPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </AppShell>
  );
}

export function App() {
  return (
    <ToastProvider>
      <RoleProvider>
        <BrowserRouter>
          <AppRoutes />
        </BrowserRouter>
      </RoleProvider>
    </ToastProvider>
  );
}

export default App;

