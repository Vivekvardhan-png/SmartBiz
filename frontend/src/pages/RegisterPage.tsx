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

export function RegisterPage() {
  const { status, register } = useAuth();
  const { notify } = useToast();
  const navigate = useNavigate();

  const [displayName, setDisplayName] = useState('');
  const [email, setEmail] = useState('');
  const [organizationName, setOrganizationName] = useState('');
  const [password, setPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  if (status === 'authenticated') {
    return <Navigate to="/dashboard" replace />;
  }

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setErrorMessage(null);
    setFieldErrors({});

    setSubmitting(true);
    try {
      await register({
        displayName: displayName.trim(),
        email: email.trim(),
        organizationName: organizationName.trim(),
        password,
      });
      notify({
        variant: 'success',
        title: 'Organization created',
        description: 'Your owner account and organization are ready.',
      });
      navigate('/dashboard', { replace: true });
    } catch (err) {
      if (err instanceof ApiError) {
        setErrorMessage(err.message);
        const mapped: Record<string, string> = {};
        for (const fe of err.fieldErrors) {
          mapped[fe.field] = fe.message;
        }
        setFieldErrors(mapped);
      } else {
        setErrorMessage('Unable to complete registration right now.');
      }
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
      <div style={{ width: '100%', maxWidth: '28rem' }}>
        <Card>
          <form onSubmit={handleSubmit} noValidate>
            <CardHeader
              title="Create your organization"
              description="Register an owner account and provision a tenant workspace."
            />
            <CardContent className="foundation-stack">
              {errorMessage ? (
                <Alert
                  variant="error"
                  title="Registration could not be completed"
                  description={errorMessage}
                />
              ) : null}

              <FormField
                label="Full Name"
                required
                placeholder="Alex Rivera"
                value={displayName}
                error={fieldErrors.displayName}
                onChange={(event) => setDisplayName(event.target.value)}
              />

              <FormField
                label="Work Email"
                type="email"
                autoComplete="email"
                required
                placeholder="alex@company.com"
                value={email}
                error={fieldErrors.email}
                onChange={(event) => setEmail(event.target.value)}
              />

              <FormField
                label="Organization Name"
                required
                placeholder="Acme Logistics"
                value={organizationName}
                error={fieldErrors.organizationName}
                onChange={(event) => setOrganizationName(event.target.value)}
              />

              <FormField
                label="Password"
                type="password"
                autoComplete="new-password"
                required
                hint="10+ characters with uppercase, lowercase, and a number."
                value={password}
                error={fieldErrors.password}
                onChange={(event) => setPassword(event.target.value)}
              />
            </CardContent>
            <CardFooter style={{ justifyContent: 'space-between' }}>
              <Link to="/login" style={{ fontSize: 'var(--text-sm)' }}>
                Already have an account?
              </Link>
              <Button type="submit" variant="primary" loading={submitting}>
                Create workspace
              </Button>
            </CardFooter>
          </form>
        </Card>
      </div>
    </div>
  );
}

