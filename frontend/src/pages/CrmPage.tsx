import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { Plus, UserCheck } from 'lucide-react';
import { useAuth } from '../app/authContext';
import {
  Alert,
  Badge,
  Button,
  Card,
  CardContent,
  Dialog,
  FormField,
  PageHeader,
  Pagination,
  Select,
  Table,
  TextareaField,
  useToast,
  type TableColumn,
} from '../components/ui';
import { apiRequest, ApiError } from '../lib/apiClient';
import type {
  CrmNote,
  Customer,
  CustomerStatus,
  Lead,
  LeadConversionResult,
  LeadStatus,
  PagedResponse,
} from '../lib/crmTypes';

type CrmTab = 'leads' | 'customers';

const LEAD_STATUS_OPTIONS = [
  { value: '', label: 'All stages' },
  { value: 'NEW', label: 'New' },
  { value: 'CONTACTED', label: 'Contacted' },
  { value: 'QUALIFIED', label: 'Qualified' },
  { value: 'PROPOSAL', label: 'Proposal' },
  { value: 'CONVERTED', label: 'Converted' },
  { value: 'LOST', label: 'Lost' },
];

const CUSTOMER_STATUS_OPTIONS = [
  { value: '', label: 'All statuses' },
  { value: 'ACTIVE', label: 'Active' },
  { value: 'INACTIVE', label: 'Inactive' },
];

function leadBadgeVariant(status: LeadStatus) {
  switch (status) {
    case 'CONVERTED':
      return 'success' as const;
    case 'LOST':
      return 'error' as const;
    case 'QUALIFIED':
    case 'PROPOSAL':
      return 'info' as const;
    default:
      return 'neutral' as const;
  }
}

export function CrmPage() {
  const { token, hasPermission } = useAuth();
  const { notify } = useToast();
  const canWrite = hasPermission('CRM_WRITE');

  const [activeTab, setActiveTab] = useState<CrmTab>('leads');

  // Leads state
  const [leads, setLeads] = useState<Lead[]>([]);
  const [leadsTotal, setLeadsTotal] = useState(0);
  const [leadsPage, setLeadsPage] = useState(1);
  const [leadSearch, setLeadSearch] = useState('');
  const [leadStatusFilter, setLeadStatusFilter] = useState('');
  const [leadsLoading, setLeadsLoading] = useState(true);
  const [leadsError, setLeadsError] = useState<string | null>(null);

  // Customers state
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [customersTotal, setCustomersTotal] = useState(0);
  const [customersPage, setCustomersPage] = useState(1);
  const [customerSearch, setCustomerSearch] = useState('');
  const [customerStatusFilter, setCustomerStatusFilter] = useState('');
  const [customersLoading, setCustomersLoading] = useState(false);
  const [customersError, setCustomersError] = useState<string | null>(null);

  // Create Lead modal
  const [createLeadOpen, setCreateLeadOpen] = useState(false);
  const [leadForm, setLeadForm] = useState({
    title: '',
    contactName: '',
    companyName: '',
    email: '',
    phone: '',
    source: '',
    estimatedValue: '',
    followUpDate: '',
    notes: '',
  });
  const [leadFormError, setLeadFormError] = useState<string | null>(null);
  const [leadFieldErrors, setLeadFieldErrors] = useState<Record<string, string>>({});
  const [leadSubmitting, setLeadSubmitting] = useState(false);

  // Create Customer modal
  const [createCustomerOpen, setCreateCustomerOpen] = useState(false);
  const [customerForm, setCustomerForm] = useState({
    name: '',
    companyName: '',
    email: '',
    phone: '',
    address: '',
    followUpDate: '',
    notes: '',
  });
  const [customerFormError, setCustomerFormError] = useState<string | null>(null);
  const [customerFieldErrors, setCustomerFieldErrors] = useState<Record<string, string>>({});
  const [customerSubmitting, setCustomerSubmitting] = useState(false);

  // Selected Lead detail + notes + conversion
  const [selectedLead, setSelectedLead] = useState<Lead | null>(null);
  const [leadNotes, setLeadNotes] = useState<CrmNote[]>([]);
  const [newNoteContent, setNewNoteContent] = useState('');
  const [newNoteFollowUp, setNewNoteFollowUp] = useState('');
  const [noteSubmitting, setNoteSubmitting] = useState(false);
  const [convertConflict, setConvertConflict] = useState<string | null>(null);
  const [converting, setConverting] = useState(false);

  // Selected Customer detail + notes
  const [selectedCustomer, setSelectedCustomer] = useState<Customer | null>(null);
  const [customerNotes, setCustomerNotes] = useState<CrmNote[]>([]);
  const [customerStatusEdit, setCustomerStatusEdit] = useState<CustomerStatus>('ACTIVE');

  const pageSize = 10;

  const fetchLeads = useCallback(async () => {
    if (!token) return;
    setLeadsLoading(true);
    setLeadsError(null);

    const params = new URLSearchParams({
      page: String(leadsPage),
      pageSize: String(pageSize),
    });
    if (leadSearch.trim()) params.set('search', leadSearch.trim());
    if (leadStatusFilter) params.set('status', leadStatusFilter);

    try {
      const res = await apiRequest<PagedResponse<Lead>>(
        `/api/v1/crm/leads?${params.toString()}`,
        { token },
      );
      setLeads(res.data);
      setLeadsTotal(res.totalItems);
    } catch (err) {
      setLeadsError(
        err instanceof ApiError ? err.message : 'Unable to load leads.',
      );
    } finally {
      setLeadsLoading(false);
    }
  }, [leadSearch, leadStatusFilter, leadsPage, token]);

  const fetchCustomers = useCallback(async () => {
    if (!token) return;
    setCustomersLoading(true);
    setCustomersError(null);

    const params = new URLSearchParams({
      page: String(customersPage),
      pageSize: String(pageSize),
    });
    if (customerSearch.trim()) params.set('search', customerSearch.trim());
    if (customerStatusFilter) params.set('status', customerStatusFilter);

    try {
      const res = await apiRequest<PagedResponse<Customer>>(
        `/api/v1/crm/customers?${params.toString()}`,
        { token },
      );
      setCustomers(res.data);
      setCustomersTotal(res.totalItems);
    } catch (err) {
      setCustomersError(
        err instanceof ApiError ? err.message : 'Unable to load customers.',
      );
    } finally {
      setCustomersLoading(false);
    }
  }, [customerSearch, customerStatusFilter, customersPage, token]);

  useEffect(() => {
    if (activeTab === 'leads') {
      void fetchLeads();
    } else {
      void fetchCustomers();
    }
  }, [activeTab, fetchCustomers, fetchLeads]);

  const openLeadDetail = async (lead: Lead) => {
    setSelectedLead(lead);
    setConvertConflict(null);
    setNewNoteContent('');
    setNewNoteFollowUp('');
    if (!token) return;
    try {
      const notes = await apiRequest<CrmNote[]>(
        `/api/v1/crm/leads/${lead.id}/notes`,
        { token },
      );
      setLeadNotes(notes);
    } catch {
      setLeadNotes([]);
    }
  };

  const openCustomerDetail = async (customer: Customer) => {
    setSelectedCustomer(customer);
    setCustomerStatusEdit(customer.status);
    setNewNoteContent('');
    setNewNoteFollowUp('');
    if (!token) return;
    try {
      const notes = await apiRequest<CrmNote[]>(
        `/api/v1/crm/customers/${customer.id}/notes`,
        { token },
      );
      setCustomerNotes(notes);
    } catch {
      setCustomerNotes([]);
    }
  };

  const handleCreateLead = async (event: FormEvent) => {
    event.preventDefault();
    if (!token) return;
    setLeadFormError(null);
    setLeadFieldErrors({});
    setLeadSubmitting(true);

    try {
      await apiRequest<Lead>('/api/v1/crm/leads', {
        method: 'POST',
        token,
        body: {
          title: leadForm.title.trim(),
          contactName: leadForm.contactName.trim(),
          companyName: leadForm.companyName.trim() || undefined,
          email: leadForm.email.trim() || undefined,
          phone: leadForm.phone.trim() || undefined,
          source: leadForm.source.trim() || undefined,
          estimatedValue: leadForm.estimatedValue
            ? Number(leadForm.estimatedValue)
            : undefined,
          followUpDate: leadForm.followUpDate || undefined,
          notes: leadForm.notes.trim() || undefined,
        },
      });
      setCreateLeadOpen(false);
      setLeadForm({
        title: '',
        contactName: '',
        companyName: '',
        email: '',
        phone: '',
        source: '',
        estimatedValue: '',
        followUpDate: '',
        notes: '',
      });
      notify({
        variant: 'success',
        title: 'Lead created',
        description: 'Added to your organization pipeline.',
      });
      setLeadsPage(1);
      await fetchLeads();
    } catch (err) {
      if (err instanceof ApiError) {
        setLeadFormError(err.message);
        const mapped: Record<string, string> = {};
        for (const fe of err.fieldErrors) mapped[fe.field] = fe.message;
        setLeadFieldErrors(mapped);
      } else {
        setLeadFormError('Unable to create lead.');
      }
    } finally {
      setLeadSubmitting(false);
    }
  };

  const handleCreateCustomer = async (event: FormEvent) => {
    event.preventDefault();
    if (!token) return;
    setCustomerFormError(null);
    setCustomerFieldErrors({});
    setCustomerSubmitting(true);

    try {
      await apiRequest<Customer>('/api/v1/crm/customers', {
        method: 'POST',
        token,
        body: {
          name: customerForm.name.trim(),
          companyName: customerForm.companyName.trim() || undefined,
          email: customerForm.email.trim() || undefined,
          phone: customerForm.phone.trim() || undefined,
          address: customerForm.address.trim() || undefined,
          followUpDate: customerForm.followUpDate || undefined,
          notes: customerForm.notes.trim() || undefined,
        },
      });
      setCreateCustomerOpen(false);
      setCustomerForm({
        name: '',
        companyName: '',
        email: '',
        phone: '',
        address: '',
        followUpDate: '',
        notes: '',
      });
      notify({
        variant: 'success',
        title: 'Customer created',
        description: 'Customer record saved.',
      });
      setCustomersPage(1);
      await fetchCustomers();
    } catch (err) {
      if (err instanceof ApiError) {
        setCustomerFormError(err.message);
        const mapped: Record<string, string> = {};
        for (const fe of err.fieldErrors) mapped[fe.field] = fe.message;
        setCustomerFieldErrors(mapped);
      } else {
        setCustomerFormError('Unable to create customer.');
      }
    } finally {
      setCustomerSubmitting(false);
    }
  };

  const handleLeadStatusChange = async (nextStatus: LeadStatus) => {
    if (!token || !selectedLead || nextStatus === selectedLead.status) return;
    try {
      const updated = await apiRequest<Lead>(
        `/api/v1/crm/leads/${selectedLead.id}`,
        {
          method: 'PATCH',
          token,
          body: { status: nextStatus },
        },
      );
      setSelectedLead(updated);
      notify({
        variant: 'success',
        title: 'Stage updated',
        description: `Lead moved to ${updated.status}.`,
      });
      await fetchLeads();
    } catch (err) {
      notify({
        variant: 'error',
        title: 'Status change rejected',
        description:
          err instanceof ApiError ? err.message : 'Invalid status transition.',
      });
    }
  };

  const handleConvertLead = async (linkExistingCustomer = false) => {
    if (!token || !selectedLead) return;
    setConvertConflict(null);
    setConverting(true);
    try {
      const result = await apiRequest<LeadConversionResult>(
        `/api/v1/crm/leads/${selectedLead.id}/convert`,
        {
          method: 'POST',
          token,
          body: { linkExistingCustomer },
        },
      );
      setSelectedLead(result.lead);
      notify({
        variant: 'success',
        title: result.alreadyConverted
          ? 'Already converted'
          : 'Lead converted to customer',
        description: `Linked customer: ${result.customer.name}.`,
      });
      await fetchLeads();
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        setConvertConflict(err.message);
      } else {
        notify({
          variant: 'error',
          title: 'Conversion failed',
          description:
            err instanceof ApiError ? err.message : 'Unable to convert lead.',
        });
      }
    } finally {
      setConverting(false);
    }
  };

  const handleAddLeadNote = async (event: FormEvent) => {
    event.preventDefault();
    if (!token || !selectedLead || !newNoteContent.trim()) return;
    setNoteSubmitting(true);
    try {
      const created = await apiRequest<CrmNote>(
        `/api/v1/crm/leads/${selectedLead.id}/notes`,
        {
          method: 'POST',
          token,
          body: {
            content: newNoteContent.trim(),
            followUpDate: newNoteFollowUp || undefined,
          },
        },
      );
      setLeadNotes((prev) => [created, ...prev]);
      if (newNoteFollowUp) {
        setSelectedLead({ ...selectedLead, followUpDate: newNoteFollowUp });
      }
      setNewNoteContent('');
      setNewNoteFollowUp('');
      await fetchLeads();
    } catch (err) {
      notify({
        variant: 'error',
        title: 'Could not add note',
        description:
          err instanceof ApiError ? err.message : 'Unable to save note.',
      });
    } finally {
      setNoteSubmitting(false);
    }
  };

  const handleAddCustomerNote = async (event: FormEvent) => {
    event.preventDefault();
    if (!token || !selectedCustomer || !newNoteContent.trim()) return;
    setNoteSubmitting(true);
    try {
      const created = await apiRequest<CrmNote>(
        `/api/v1/crm/customers/${selectedCustomer.id}/notes`,
        {
          method: 'POST',
          token,
          body: {
            content: newNoteContent.trim(),
            followUpDate: newNoteFollowUp || undefined,
          },
        },
      );
      setCustomerNotes((prev) => [created, ...prev]);
      if (newNoteFollowUp) {
        setSelectedCustomer({
          ...selectedCustomer,
          followUpDate: newNoteFollowUp,
        });
      }
      setNewNoteContent('');
      setNewNoteFollowUp('');
      await fetchCustomers();
    } catch (err) {
      notify({
        variant: 'error',
        title: 'Could not add note',
        description:
          err instanceof ApiError ? err.message : 'Unable to save note.',
      });
    } finally {
      setNoteSubmitting(false);
    }
  };

  const handleCustomerStatusUpdate = async (nextStatus: CustomerStatus) => {
    if (!token || !selectedCustomer || nextStatus === selectedCustomer.status) return;
    try {
      const updated = await apiRequest<Customer>(
        `/api/v1/crm/customers/${selectedCustomer.id}`,
        {
          method: 'PATCH',
          token,
          body: { status: nextStatus },
        },
      );
      setSelectedCustomer(updated);
      setCustomerStatusEdit(updated.status);
      notify({
        variant: 'success',
        title: 'Customer updated',
        description: `Status set to ${updated.status}.`,
      });
      await fetchCustomers();
    } catch (err) {
      notify({
        variant: 'error',
        title: 'Update failed',
        description:
          err instanceof ApiError ? err.message : 'Unable to update customer.',
      });
    }
  };

  const leadColumns: TableColumn<Lead>[] = [
    {
      key: 'title',
      header: 'Lead / Contact',
      render: (row) => (
        <div>
          <strong>{row.title}</strong>
          <div
            style={{
              fontSize: 'var(--text-xs)',
              color: 'var(--color-text-secondary)',
            }}
          >
            {row.contactName}
            {row.companyName ? ` · ${row.companyName}` : ''}
          </div>
        </div>
      ),
    },
    {
      key: 'email',
      header: 'Email',
      render: (row) => row.email ?? '—',
    },
    {
      key: 'status',
      header: 'Stage',
      render: (row) => (
        <Badge variant={leadBadgeVariant(row.status)}>{row.status}</Badge>
      ),
    },
    {
      key: 'estimatedValue',
      header: 'Est. Value',
      align: 'right',
      render: (row) =>
        row.estimatedValue !== null
          ? `$${Number(row.estimatedValue).toFixed(2)}`
          : '—',
    },
    {
      key: 'followUpDate',
      header: 'Follow-up',
      render: (row) => row.followUpDate ?? '—',
    },
  ];

  const customerColumns: TableColumn<Customer>[] = [
    {
      key: 'name',
      header: 'Customer',
      render: (row) => (
        <div>
          <strong>{row.name}</strong>
          {row.companyName ? (
            <div
              style={{
                fontSize: 'var(--text-xs)',
                color: 'var(--color-text-secondary)',
              }}
            >
              {row.companyName}
            </div>
          ) : null}
        </div>
      ),
    },
    {
      key: 'email',
      header: 'Email',
      render: (row) => row.email ?? '—',
    },
    {
      key: 'phone',
      header: 'Phone',
      render: (row) => row.phone ?? '—',
    },
    {
      key: 'status',
      header: 'Status',
      render: (row) => (
        <Badge variant={row.status === 'ACTIVE' ? 'success' : 'neutral'}>
          {row.status}
        </Badge>
      ),
    },
    {
      key: 'followUpDate',
      header: 'Follow-up',
      render: (row) => row.followUpDate ?? '—',
    },
  ];

  return (
    <div className="foundation-stack">
      <PageHeader
        title="CRM"
        subtitle="Lead pipeline, follow-ups, customer directory, and transactional lead conversion."
        breadcrumbs={[
          { label: 'Workspace', to: '/dashboard' },
          { label: 'CRM' },
        ]}
        badge={<Badge variant="success">Active</Badge>}
        actions={
          canWrite ? (
            activeTab === 'leads' ? (
              <Button
                variant="primary"
                leftIcon={<Plus size={16} aria-hidden="true" />}
                onClick={() => setCreateLeadOpen(true)}
              >
                New Lead
              </Button>
            ) : (
              <Button
                variant="primary"
                leftIcon={<Plus size={16} aria-hidden="true" />}
                onClick={() => setCreateCustomerOpen(true)}
              >
                New Customer
              </Button>
            )
          ) : null
        }
      />

      <div className="foundation-row" role="tablist" aria-label="CRM views">
        <Button
          role="tab"
          aria-selected={activeTab === 'leads'}
          variant={activeTab === 'leads' ? 'primary' : 'secondary'}
          onClick={() => setActiveTab('leads')}
        >
          Leads
        </Button>
        <Button
          role="tab"
          aria-selected={activeTab === 'customers'}
          variant={activeTab === 'customers' ? 'primary' : 'secondary'}
          onClick={() => setActiveTab('customers')}
        >
          Customers
        </Button>
      </div>

      {activeTab === 'leads' ? (
        <Card>
          <CardContent className="foundation-stack">
            <div className="foundation-row" style={{ alignItems: 'flex-end' }}>
              <div style={{ flex: '1 1 16rem' }}>
                <FormField
                  label="Search Leads"
                  placeholder="Title, contact, company, or email..."
                  value={leadSearch}
                  onChange={(event) => {
                    setLeadSearch(event.target.value);
                    setLeadsPage(1);
                  }}
                />
              </div>
              <div style={{ minWidth: '12rem' }}>
                <Select
                  label="Pipeline Stage"
                  value={leadStatusFilter}
                  options={LEAD_STATUS_OPTIONS}
                  onChange={(event) => {
                    setLeadStatusFilter(event.target.value);
                    setLeadsPage(1);
                  }}
                />
              </div>
            </div>

            <Table
              caption="CRM leads"
              columns={leadColumns}
              rows={leads}
              getRowKey={(row) => row.id}
              loading={leadsLoading}
              error={leadsError}
              onRetry={() => void fetchLeads()}
              emptyTitle="No leads found"
              emptyDescription="Create a lead to begin tracking your organization sales pipeline."
              onRowClick={(row) => void openLeadDetail(row)}
            />

            {!leadsLoading && !leadsError && leadsTotal > 0 ? (
              <Pagination
                page={leadsPage}
                pageSize={pageSize}
                totalItems={leadsTotal}
                onPageChange={setLeadsPage}
              />
            ) : null}
          </CardContent>
        </Card>
      ) : (
        <Card>
          <CardContent className="foundation-stack">
            <div className="foundation-row" style={{ alignItems: 'flex-end' }}>
              <div style={{ flex: '1 1 16rem' }}>
                <FormField
                  label="Search Customers"
                  placeholder="Name, company, email, or phone..."
                  value={customerSearch}
                  onChange={(event) => {
                    setCustomerSearch(event.target.value);
                    setCustomersPage(1);
                  }}
                />
              </div>
              <div style={{ minWidth: '12rem' }}>
                <Select
                  label="Status"
                  value={customerStatusFilter}
                  options={CUSTOMER_STATUS_OPTIONS}
                  onChange={(event) => {
                    setCustomerStatusFilter(event.target.value);
                    setCustomersPage(1);
                  }}
                />
              </div>
            </div>

            <Table
              caption="CRM customers"
              columns={customerColumns}
              rows={customers}
              getRowKey={(row) => row.id}
              loading={customersLoading}
              error={customersError}
              onRetry={() => void fetchCustomers()}
              emptyTitle="No customers found"
              emptyDescription="Convert a qualified lead or create a customer directly."
              onRowClick={(row) => void openCustomerDetail(row)}
            />

            {!customersLoading && !customersError && customersTotal > 0 ? (
              <Pagination
                page={customersPage}
                pageSize={pageSize}
                totalItems={customersTotal}
                onPageChange={setCustomersPage}
              />
            ) : null}
          </CardContent>
        </Card>
      )}

      {/* Create Lead Dialog */}
      <Dialog
        open={createLeadOpen}
        onClose={() => setCreateLeadOpen(false)}
        title="Create Lead"
        size="lg"
      >
        <form onSubmit={handleCreateLead} className="foundation-stack" noValidate>
          {leadFormError ? (
            <Alert variant="error" title="Validation error" description={leadFormError} />
          ) : null}
          <FormField
            label="Deal / Opportunity Title"
            required
            value={leadForm.title}
            error={leadFieldErrors.title}
            onChange={(event) => setLeadForm({ ...leadForm, title: event.target.value })}
          />
          <FormField
            label="Contact Name"
            required
            value={leadForm.contactName}
            error={leadFieldErrors.contactName}
            onChange={(event) =>
              setLeadForm({ ...leadForm, contactName: event.target.value })
            }
          />
          <FormField
            label="Company Name"
            value={leadForm.companyName}
            onChange={(event) =>
              setLeadForm({ ...leadForm, companyName: event.target.value })
            }
          />
          <div className="foundation-row">
            <FormField
              label="Email"
              type="email"
              value={leadForm.email}
              error={leadFieldErrors.email}
              onChange={(event) => setLeadForm({ ...leadForm, email: event.target.value })}
            />
            <FormField
              label="Phone"
              value={leadForm.phone}
              onChange={(event) => setLeadForm({ ...leadForm, phone: event.target.value })}
            />
          </div>
          <div className="foundation-row">
            <FormField
              label="Estimated Value"
              type="number"
              step="0.01"
              min="0"
              value={leadForm.estimatedValue}
              error={leadFieldErrors.estimatedValue}
              onChange={(event) =>
                setLeadForm({ ...leadForm, estimatedValue: event.target.value })
              }
            />
            <FormField
              label="Follow-up Date"
              type="date"
              value={leadForm.followUpDate}
              onChange={(event) =>
                setLeadForm({ ...leadForm, followUpDate: event.target.value })
              }
            />
          </div>
          <TextareaField
            label="Notes"
            value={leadForm.notes}
            onChange={(event) => setLeadForm({ ...leadForm, notes: event.target.value })}
          />
          <div className="foundation-row" style={{ justifyContent: 'flex-end' }}>
            <Button variant="secondary" onClick={() => setCreateLeadOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" variant="primary" loading={leadSubmitting}>
              Create Lead
            </Button>
          </div>
        </form>
      </Dialog>

      {/* Create Customer Dialog */}
      <Dialog
        open={createCustomerOpen}
        onClose={() => setCreateCustomerOpen(false)}
        title="Create Customer"
        size="lg"
      >
        <form onSubmit={handleCreateCustomer} className="foundation-stack" noValidate>
          {customerFormError ? (
            <Alert
              variant="error"
              title="Could not create customer"
              description={customerFormError}
            />
          ) : null}
          <FormField
            label="Customer Name"
            required
            value={customerForm.name}
            error={customerFieldErrors.name}
            onChange={(event) =>
              setCustomerForm({ ...customerForm, name: event.target.value })
            }
          />
          <FormField
            label="Company Name"
            value={customerForm.companyName}
            onChange={(event) =>
              setCustomerForm({ ...customerForm, companyName: event.target.value })
            }
          />
          <div className="foundation-row">
            <FormField
              label="Email"
              type="email"
              value={customerForm.email}
              error={customerFieldErrors.email}
              onChange={(event) =>
                setCustomerForm({ ...customerForm, email: event.target.value })
              }
            />
            <FormField
              label="Phone"
              value={customerForm.phone}
              onChange={(event) =>
                setCustomerForm({ ...customerForm, phone: event.target.value })
              }
            />
          </div>
          <FormField
            label="Follow-up Date"
            type="date"
            value={customerForm.followUpDate}
            onChange={(event) =>
              setCustomerForm({ ...customerForm, followUpDate: event.target.value })
            }
          />
          <TextareaField
            label="Address"
            value={customerForm.address}
            onChange={(event) =>
              setCustomerForm({ ...customerForm, address: event.target.value })
            }
          />
          <div className="foundation-row" style={{ justifyContent: 'flex-end' }}>
            <Button
              variant="secondary"
              onClick={() => setCreateCustomerOpen(false)}
            >
              Cancel
            </Button>
            <Button type="submit" variant="primary" loading={customerSubmitting}>
              Create Customer
            </Button>
          </div>
        </form>
      </Dialog>

      {/* Lead Detail Dialog */}
      <Dialog
        open={Boolean(selectedLead)}
        onClose={() => setSelectedLead(null)}
        title={selectedLead?.title ?? 'Lead Details'}
        description={
          selectedLead
            ? `${selectedLead.contactName}${selectedLead.companyName ? ` · ${selectedLead.companyName}` : ''}`
            : undefined
        }
        size="lg"
      >
        {selectedLead ? (
          <div className="foundation-stack">
            <div className="foundation-row" style={{ justifyContent: 'space-between' }}>
              <Badge variant={leadBadgeVariant(selectedLead.status)} size="md">
                {selectedLead.status}
              </Badge>
              {canWrite && selectedLead.status !== 'CONVERTED' && selectedLead.status !== 'LOST' ? (
                <Button
                  variant="primary"
                  size="sm"
                  loading={converting}
                  leftIcon={<UserCheck size={15} aria-hidden="true" />}
                  onClick={() => void handleConvertLead(false)}
                >
                  Convert to Customer
                </Button>
              ) : null}
            </div>

            {convertConflict ? (
              <Alert
                variant="warning"
                title="Customer email already exists"
                description={convertConflict}
                actions={
                  <Button
                    variant="secondary"
                    size="sm"
                    loading={converting}
                    onClick={() => void handleConvertLead(true)}
                  >
                    Link to Existing Customer
                  </Button>
                }
              />
            ) : null}

            {canWrite && selectedLead.allowedNextStatuses.length > 0 ? (
              <Select
                label="Transition Stage"
                value={selectedLead.status}
                options={[
                  { value: selectedLead.status, label: `${selectedLead.status} (Current)` },
                  ...selectedLead.allowedNextStatuses.map((s) => ({
                    value: s,
                    label: s,
                  })),
                ]}
                onChange={(event) =>
                  void handleLeadStatusChange(event.target.value as LeadStatus)
                }
              />
            ) : null}

            {canWrite ? (
              <form onSubmit={handleAddLeadNote} className="foundation-stack">
                <TextareaField
                  label="Add Follow-up Note"
                  placeholder="Call summary or next step..."
                  value={newNoteContent}
                  onChange={(event) => setNewNoteContent(event.target.value)}
                />
                <div className="foundation-row" style={{ alignItems: 'flex-end' }}>
                  <FormField
                    label="Next Follow-up Date"
                    type="date"
                    value={newNoteFollowUp}
                    onChange={(event) => setNewNoteFollowUp(event.target.value)}
                  />
                  <Button type="submit" variant="secondary" loading={noteSubmitting}>
                    Save Note
                  </Button>
                </div>
              </form>
            ) : null}

            <div className="foundation-stack">
              <strong style={{ fontSize: 'var(--text-sm)' }}>Activity Notes</strong>
              {leadNotes.length === 0 ? (
                <span style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>
                  No follow-up notes recorded yet.
                </span>
              ) : (
                leadNotes.map((note) => (
                  <div
                    key={note.id}
                    style={{
                      padding: 'var(--space-3)',
                      border: '1px solid var(--color-border)',
                      borderRadius: 'var(--radius-md)',
                    }}
                  >
                    <p>{note.content}</p>
                    <div
                      style={{
                        marginTop: 'var(--space-1)',
                        fontSize: 'var(--text-xs)',
                        color: 'var(--color-text-secondary)',
                      }}
                    >
                      {note.authorName ?? 'Member'} ·{' '}
                      {new Date(note.createdAt).toLocaleString()}
                      {note.followUpDate ? ` · Follow-up: ${note.followUpDate}` : ''}
                    </div>
                  </div>
                ))
              )}
            </div>
          </div>
        ) : null}
      </Dialog>

      {/* Customer Detail Dialog */}
      <Dialog
        open={Boolean(selectedCustomer)}
        onClose={() => setSelectedCustomer(null)}
        title={selectedCustomer?.name ?? 'Customer Details'}
        description={selectedCustomer?.email ?? undefined}
        size="lg"
      >
        {selectedCustomer ? (
          <div className="foundation-stack">
            {canWrite ? (
              <Select
                label="Customer Status"
                value={customerStatusEdit}
                options={[
                  { value: 'ACTIVE', label: 'ACTIVE' },
                  { value: 'INACTIVE', label: 'INACTIVE' },
                ]}
                onChange={(event) =>
                  void handleCustomerStatusUpdate(event.target.value as CustomerStatus)
                }
              />
            ) : null}

            {canWrite ? (
              <form onSubmit={handleAddCustomerNote} className="foundation-stack">
                <TextareaField
                  label="Add Follow-up Note"
                  placeholder="Account update or next check-in..."
                  value={newNoteContent}
                  onChange={(event) => setNewNoteContent(event.target.value)}
                />
                <div className="foundation-row" style={{ alignItems: 'flex-end' }}>
                  <FormField
                    label="Next Follow-up Date"
                    type="date"
                    value={newNoteFollowUp}
                    onChange={(event) => setNewNoteFollowUp(event.target.value)}
                  />
                  <Button type="submit" variant="secondary" loading={noteSubmitting}>
                    Save Note
                  </Button>
                </div>
              </form>
            ) : null}

            <div className="foundation-stack">
              <strong style={{ fontSize: 'var(--text-sm)' }}>Activity Notes</strong>
              {customerNotes.length === 0 ? (
                <span style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>
                  No follow-up notes recorded yet.
                </span>
              ) : (
                customerNotes.map((note) => (
                  <div
                    key={note.id}
                    style={{
                      padding: 'var(--space-3)',
                      border: '1px solid var(--color-border)',
                      borderRadius: 'var(--radius-md)',
                    }}
                  >
                    <p>{note.content}</p>
                    <div
                      style={{
                        marginTop: 'var(--space-1)',
                        fontSize: 'var(--text-xs)',
                        color: 'var(--color-text-secondary)',
                      }}
                    >
                      {note.authorName ?? 'Member'} ·{' '}
                      {new Date(note.createdAt).toLocaleString()}
                      {note.followUpDate ? ` · Follow-up: ${note.followUpDate}` : ''}
                    </div>
                  </div>
                ))
              )}
            </div>
          </div>
        ) : null}
      </Dialog>
    </div>
  );
}

