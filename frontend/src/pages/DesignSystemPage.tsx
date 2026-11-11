import { useMemo, useState, type FormEvent } from 'react';
import {
  Alert,
  Badge,
  Button,
  Card,
  CardContent,
  CardFooter,
  CardHeader,
  Dialog,
  FormField,
  PageHeader,
  Pagination,
  Select,
  StateBoundary,
  Table,
  TextareaField,
  useToast,
  type ScreenStatus,
  type SortDirection,
  type TableColumn,
} from '../components/ui';
import { designTokens } from '../design-system/tokens';

interface PrimitiveSpec {
  id: string;
  component: string;
  category: 'Input' | 'Feedback' | 'Layout' | 'Data Display';
  accessibility: string;
  status: 'Ready';
}

const PRIMITIVE_SPECS: PrimitiveSpec[] = [
  {
    id: 'btn',
    component: 'Button',
    category: 'Input',
    accessibility: 'Keyboard focus ring, aria-busy loading state',
    status: 'Ready',
  },
  {
    id: 'field',
    component: 'FormField & TextareaField',
    category: 'Input',
    accessibility: 'Associated label, aria-invalid, aria-describedby error',
    status: 'Ready',
  },
  {
    id: 'select',
    component: 'Select',
    category: 'Input',
    accessibility: 'Native keyboard navigation and validation hint wiring',
    status: 'Ready',
  },
  {
    id: 'dialog',
    component: 'Dialog',
    category: 'Feedback',
    accessibility: 'aria-modal, Escape close, focus restoration',
    status: 'Ready',
  },
  {
    id: 'table',
    component: 'Table & Pagination',
    category: 'Data Display',
    accessibility: 'Scope headers, aria-sort, aria-current page button',
    status: 'Ready',
  },
  {
    id: 'toast',
    component: 'Toast & Alert',
    category: 'Feedback',
    accessibility: 'Live region announcements and dismiss controls',
    status: 'Ready',
  },
  {
    id: 'header',
    component: 'PageHeader & Breadcrumbs',
    category: 'Layout',
    accessibility: 'Breadcrumb landmark and semantic heading hierarchy',
    status: 'Ready',
  },
  {
    id: 'state',
    component: 'Skeleton, EmptyState & StateBoundary',
    category: 'Feedback',
    accessibility: 'aria-busy placeholders and explicit error retry',
    status: 'Ready',
  },
];

const PALETTE_SWATCHES = [
  { name: 'App Background', hex: designTokens.colors.bgApp },
  { name: 'Surface', hex: designTokens.colors.bgSurface },
  { name: 'Secondary Surface', hex: designTokens.colors.bgSecondary },
  { name: 'Border', hex: designTokens.colors.border },
  { name: 'Primary Text', hex: designTokens.colors.textPrimary },
  { name: 'Secondary Text', hex: designTokens.colors.textSecondary },
  { name: 'Primary Action', hex: designTokens.colors.actionPrimary },
  { name: 'Hover Action', hex: designTokens.colors.actionHover },
  { name: 'Success', hex: designTokens.colors.success },
  { name: 'Warning', hex: designTokens.colors.warning },
  { name: 'Error', hex: designTokens.colors.error },
];

export function DesignSystemPage() {
  const { notify } = useToast();
  const [dialogOpen, setDialogOpen] = useState(false);
  const [tableStatus, setTableStatus] = useState<ScreenStatus>('success');
  const [page, setPage] = useState(1);
  const [sortDirection, setSortDirection] = useState<SortDirection>('asc');

  const [sampleName, setSampleName] = useState('');
  const [sampleCategory, setSampleCategory] = useState('Input');
  const [sampleNotes, setSampleNotes] = useState('');
  const [nameError, setNameError] = useState<string | undefined>();

  const pageSize = 4;

  const sortedRows = useMemo(() => {
    const copy = [...PRIMITIVE_SPECS];
    copy.sort((a, b) =>
      sortDirection === 'asc'
        ? a.component.localeCompare(b.component)
        : b.component.localeCompare(a.component),
    );
    return copy;
  }, [sortDirection]);

  const paginatedRows = useMemo(() => {
    const start = (page - 1) * pageSize;
    return sortedRows.slice(start, start + pageSize);
  }, [page, sortedRows]);

  const columns: TableColumn<PrimitiveSpec>[] = [
    {
      key: 'component',
      header: 'Primitive',
      sortable: true,
      render: (row) => <strong>{row.component}</strong>,
    },
    {
      key: 'category',
      header: 'Category',
      render: (row) => row.category,
    },
    {
      key: 'accessibility',
      header: 'Accessibility Behavior',
      render: (row) => (
        <span style={{ color: 'var(--color-text-secondary)' }}>
          {row.accessibility}
        </span>
      ),
    },
    {
      key: 'status',
      header: 'Status',
      align: 'right',
      render: (row) => <Badge variant="success">{row.status}</Badge>,
    },
  ];

  const handleSampleSubmit = (event: FormEvent) => {
    event.preventDefault();
    if (!sampleName.trim()) {
      setNameError('Enter a token or component name to validate the form.');
      notify({
        variant: 'error',
        title: 'Validation failed',
        description: 'A required form field was left blank.',
      });
      return;
    }

    setNameError(undefined);
    notify({
      variant: 'success',
      title: 'Form validation passed',
      description: `Validated "${sampleName.trim()}" under ${sampleCategory}.`,
    });
  };

  return (
    <div className="foundation-stack">
      <PageHeader
        title="Design System Foundation"
        subtitle="Centralized warm-neutral enterprise tokens, accessible UI primitives, and screen state patterns for Phase 00."
        breadcrumbs={[
          { label: 'Workspace', to: '/dashboard' },
          { label: 'Design System' },
        ]}
        badge={<Badge variant="info">Phase 00</Badge>}
        actions={
          <>
            <Button
              variant="secondary"
              onClick={() =>
                notify({
                  variant: 'info',
                  title: 'Design System Toast',
                  description:
                    'Notifications announce updates cleanly through an accessible live region.',
                })
              }
            >
              Trigger Toast
            </Button>
            <Button variant="primary" onClick={() => setDialogOpen(true)}>
              Open Dialog
            </Button>
          </>
        }
      />

      <div className="foundation-grid">
        <div className="foundation-col-12">
          <Card>
            <CardHeader
              title="Color Palette & Semantic Tokens"
              description="Defined once in tokens.css and mirrored in tokens.ts."
            />
            <CardContent>
              <div
                style={{
                  display: 'grid',
                  gridTemplateColumns: 'repeat(auto-fill, minmax(11rem, 1fr))',
                  gap: 'var(--space-3)',
                }}
              >
                {PALETTE_SWATCHES.map((swatch) => (
                  <div
                    key={swatch.name}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 'var(--space-3)',
                      padding: 'var(--space-2)',
                      border: '1px solid var(--color-border)',
                      borderRadius: 'var(--radius-md)',
                    }}
                  >
                    <span
                      aria-hidden="true"
                      style={{
                        width: '2rem',
                        height: '2rem',
                        borderRadius: 'var(--radius-sm)',
                        border: '1px solid var(--color-border-strong)',
                        backgroundColor: swatch.hex,
                        flexShrink: 0,
                      }}
                    />
                    <div style={{ minWidth: 0 }}>
                      <div
                        style={{
                          fontSize: 'var(--text-xs)',
                          fontWeight: 'var(--weight-medium)',
                        }}
                      >
                        {swatch.name}
                      </div>
                      <code
                        style={{
                          fontFamily: 'var(--font-mono)',
                          fontSize: 'var(--text-xs)',
                          color: 'var(--color-text-secondary)',
                        }}
                      >
                        {swatch.hex}
                      </code>
                    </div>
                  </div>
                ))}
              </div>
            </CardContent>
          </Card>
        </div>

        <div className="foundation-col-6">
          <Card>
            <CardHeader
              title="Buttons, Badges & Alerts"
              description="Restrained primary blue actions and clear semantic states."
            />
            <CardContent className="foundation-stack">
              <div className="foundation-row">
                <Button variant="primary">Primary</Button>
                <Button variant="secondary">Secondary</Button>
                <Button variant="ghost">Ghost</Button>
                <Button variant="danger">Danger</Button>
                <Button variant="primary" loading>
                  Saving
                </Button>
              </div>
              <div className="foundation-row">
                <Badge variant="neutral">Neutral</Badge>
                <Badge variant="info">Info</Badge>
                <Badge variant="success">Success</Badge>
                <Badge variant="warning">Warning</Badge>
                <Badge variant="error">Error</Badge>
              </div>
              <Alert
                variant="success"
                title="Token contrast & status clarity"
                description="Status banners communicate semantic outcomes with icons and accessible roles."
              />
            </CardContent>
          </Card>
        </div>

        <div className="foundation-col-6">
          <Card>
            <form onSubmit={handleSampleSubmit} noValidate>
              <CardHeader
                title="Form Validation Primitives"
                description="Accessible labels, hints, and error announcements."
              />
              <CardContent className="foundation-stack">
                <FormField
                  label="Component Identifier"
                  placeholder="e.g. primary-button"
                  required
                  hint="Submit empty to test inline error states."
                  error={nameError}
                  value={sampleName}
                  onChange={(event) => {
                    setSampleName(event.target.value);
                    if (nameError) {
                      setNameError(undefined);
                    }
                  }}
                />
                <Select
                  label="Primitive Category"
                  value={sampleCategory}
                  onChange={(event) => setSampleCategory(event.target.value)}
                  options={[
                    { value: 'Input', label: 'Input' },
                    { value: 'Feedback', label: 'Feedback' },
                    { value: 'Layout', label: 'Layout' },
                    { value: 'Data Display', label: 'Data Display' },
                  ]}
                />
                <TextareaField
                  label="Accessibility Notes"
                  placeholder="Optional keyboard and ARIA notes..."
                  value={sampleNotes}
                  onChange={(event) => setSampleNotes(event.target.value)}
                />
              </CardContent>
              <CardFooter>
                <Button
                  variant="ghost"
                  onClick={() => {
                    setSampleName('');
                    setSampleNotes('');
                    setNameError(undefined);
                  }}
                >
                  Reset
                </Button>
                <Button type="submit" variant="primary">
                  Validate Form
                </Button>
              </CardFooter>
            </form>
          </Card>
        </div>

        <div className="foundation-col-12">
          <Card>
            <CardHeader
              title="Data Table, Pagination & Screen State Boundary"
              description="Switch states to verify loading skeletons, empty states, error retries, and sorted pagination."
              actions={
                <div style={{ minWidth: '11rem' }}>
                  <Select
                    label="Table State"
                    aria-label="Table state preview"
                    value={tableStatus}
                    onChange={(event) =>
                      setTableStatus(event.target.value as ScreenStatus)
                    }
                    options={[
                      { value: 'success', label: 'Populated (Ready)' },
                      { value: 'loading', label: 'Loading Skeleton' },
                      { value: 'empty', label: 'Empty State' },
                      { value: 'error', label: 'Error State' },
                    ]}
                  />
                </div>
              }
            />
            <CardContent className="foundation-stack">
              <StateBoundary
                status={tableStatus}
                loadingFallback={
                  <Table
                    caption="UI primitive inventory"
                    columns={columns}
                    rows={[]}
                    getRowKey={(row) => row.id}
                    loading
                  />
                }
                errorTitle="Simulated API failure"
                errorMessage="Demonstrates explicit failure recovery without substituting fake data."
                onRetry={() => setTableStatus('success')}
                emptyTitle="No primitives matched"
                emptyDescription="Empty states guide users cleanly to the next valid action."
                emptyAction={
                  <Button
                    variant="secondary"
                    onClick={() => setTableStatus('success')}
                  >
                    Restore Catalog
                  </Button>
                }
              >
                <Table
                  caption="UI primitive inventory"
                  columns={columns}
                  rows={paginatedRows}
                  getRowKey={(row) => row.id}
                  sortKey="component"
                  sortDirection={sortDirection}
                  onSortChange={() =>
                    setSortDirection((prev) =>
                      prev === 'asc' ? 'desc' : 'asc',
                    )
                  }
                />
                <Pagination
                  page={page}
                  pageSize={pageSize}
                  totalItems={sortedRows.length}
                  onPageChange={setPage}
                />
              </StateBoundary>
            </CardContent>
          </Card>
        </div>
      </div>

      <Dialog
        open={dialogOpen}
        onClose={() => setDialogOpen(false)}
        title="Accessible Confirmation Dialog"
        description="Press Escape, click the close icon, or use the footer buttons to dismiss and restore focus."
        footer={
          <>
            <Button variant="secondary" onClick={() => setDialogOpen(false)}>
              Cancel
            </Button>
            <Button
              variant="primary"
              onClick={() => {
                setDialogOpen(false);
                notify({
                  variant: 'success',
                  title: 'Dialog confirmed',
                  description: 'Modal dialog closed cleanly.',
                });
              }}
            >
              Confirm
            </Button>
          </>
        }
      >
        <p style={{ color: 'var(--color-text-secondary)' }}>
          Dialogs in this design system trap modal semantics via{' '}
          <code>role=&quot;dialog&quot;</code> and <code>aria-modal=&quot;true&quot;</code>,
          support Escape key dismissal, and return focus to the trigger control.
        </p>
      </Dialog>
    </div>
  );
}

