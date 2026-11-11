import { useState, type FormEvent } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { useAuth } from '../app/authContext';
import {
  Alert,
  Button,
  Card,
  CardContent,
  CardFooter,
  CardHeader,
  FormField,
  useToast,
} from '../components/ui';
import { ApiError } from '../lib/apiClient';

export function LoginPage() {
  const { status, login, sessionError } = useAuth();
  const { notify } = useToast();
  const navigate = useNavigate();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  if (status === 'authenticated') {
    return <Navigate to="/dashboard" replace />;
  }

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setErrorMessage(null);

    if (!email.trim() || !password) {
      setErrorMessage('Enter both your work email and password.');
      return;
    }

    setSubmitting(true);
    try {
      await login({ email: email.trim(), password });
      notify({
        variant: 'success',
        title: 'Signed in',
        description: 'Your organization session is active.',
      });
      navigate('/dashboard', { replace: true });
    } catch (err) {
      const message =
        err instanceof ApiError
          ? err.message
          : 'Unable to sign in right now.';
      setErrorMessage(message);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: 'var(--space-4)',
        backgroundColor: 'var(--color-bg-app)',
      }}
    >
      <div style={{ width: '100%', maxWidth: '26rem' }}>
        <Card>
          <form onSubmit={handleSubmit} noValidate>
            <CardHeader
              title="Sign in to NexusOps"
              description="Access your organization workspace."
            />
            <CardContent className="foundation-stack">
              {errorMessage || sessionError ? (
                <Alert
                  variant="error"
                  title="Authentication failed"
                  description={errorMessage ?? sessionError}
                />
              ) : null}

              <FormField
                label="Work Email"
                type="email"
                autoComplete="email"
                required
                placeholder="name@company.com"
                value={email}
                onChange={(event) => setEmail(event.target.value)}
              />

              <FormField
                label="Password"
                type="password"
                autoComplete="current-password"
                required
                value={password}
                onChange={(event) => setPassword(event.target.value)}
              />
            </CardContent>
            <CardFooter style={{ justifyContent: 'space-between' }}>
              <Link
                to="/register"
                style={{ fontSize: 'var(--text-sm)' }}
              >
                Create organization
              </Link>
              <Button type="submit" variant="primary" loading={submitting}>
                Sign in
              </Button>
            </CardFooter>
          </form>
        </Card>
      </div>
    </div>
  );
}

