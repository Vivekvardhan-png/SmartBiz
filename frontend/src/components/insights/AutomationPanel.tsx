import { useCallback, useEffect, useState } from 'react';
import { BellRing, Play } from 'lucide-react';
import { useAuth } from '../../app/authContext';
import {
  Alert,
  Badge,
  Button,
  Card,
  CardContent,
  CardHeader,
  Select,
  StateBoundary,
  Table,
  useToast,
  type ScreenStatus,
  type TableColumn,
} from '../ui';
import { apiRequest, ApiError } from '../../lib/apiClient';
import type { PagedResponse } from '../../lib/crmTypes';
import type {
  AutomationAlertResponse,
  AutomationRunBatchResponse,
  AutomationRunResponse,
  AutomationWorkflowType,
} from '../../lib/assistantTypes';

export function AutomationPanel() {
  const { token, hasPermission } = useAuth();
  const { notify } = useToast();

  const [statusFilter, setStatusFilter] = useState<string>('OPEN');
  const [screenStatus, setScreenStatus] = useState<ScreenStatus>('loading');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [alerts, setAlerts] = useState<AutomationAlertResponse[]>([]);
  const [runs, setRuns] = useState<AutomationRunResponse[]>([]);
  const [runningWorkflow, setRunningWorkflow] = useState(false);

  const canUse = hasPermission('AI_USE');

  const loadAutomationData = useCallback(async () => {
    if (!canUse) {
      setScreenStatus('success');
      return;
    }
    setScreenStatus('loading');
    setErrorMessage(null);
    try {
      const alertPath =
        statusFilter === 'ALL'
          ? '/api/v1/automation/alerts?page=1&pageSize=25'
          : `/api/v1/automation/alerts?status=${statusFilter}&page=1&pageSize=25`;
      const [alertsRes, runsRes] = await Promise.all([
        apiRequest<PagedResponse<AutomationAlertResponse>>(alertPath, {
          token,
        }),
        apiRequest<PagedResponse<AutomationRunResponse>>(
          '/api/v1/automation/runs?page=1&pageSize=15',
          { token },
        ),
      ]);
      setAlerts(alertsRes.data);
      setRuns(runsRes.data);
      setScreenStatus('success');
    } catch (err) {
      setErrorMessage(
        err instanceof ApiError
          ? err.message
          : 'Unable to load automation alerts and audit runs.',
      );
      setScreenStatus('error');
    }
  }, [canUse, statusFilter, token]);

  useEffect(() => {
    void loadAutomationData();
  }, [loadAutomationData]);

  if (!canUse) {
    return (
      <Alert
        variant="warning"
        title="Automation workflows require AI Assistant permission"
        description="Your current role does not grant PERM_AI_USE."
      />
    );
  }

  async function handleRun(workflowType?: AutomationWorkflowType) {
    setRunningWorkflow(true);
    try {
      const result = await apiRequest<AutomationRunBatchResponse>(
        '/api/v1/automation/workflows/run',
        {
          method: 'POST',
          token,
          body: workflowType ? { workflowType } : {},
        },
      );
      const createdTotal = result.runs.reduce((sum, r) => sum + r.alertsCreated, 0);
      const skippedTotal = result.runs.reduce(
        (sum, r) => sum + r.alertsSkippedDuplicate,
        0,
      );
      notify({
        title: 'Automation workflow run completed',
        description: `${createdTotal} new alert(s) created, ${skippedTotal} duplicate(s) skipped.`,
        variant: 'success',
      });
      await loadAutomationData();
    } catch (err) {
      notify({
        title: 'Automation run failed',
        description: err instanceof ApiError ? err.message : 'Unable to execute workflow.',
        variant: 'error',
      });
    } finally {
      setRunningWorkflow(false);
    }
  }

  async function handleAcknowledge(alertId: string) {
    try {
      await apiRequest(`/api/v1/automation/alerts/${alertId}/acknowledge`, {
        method: 'POST',
        token,
      });
      notify({
        title: 'Alert acknowledged',
        description: 'The automation alert has been marked as acknowledged.',
        variant: 'info',
      });
      await loadAutomationData();
    } catch (err) {
      notify({
        title: 'Acknowledge failed',
        description: err instanceof ApiError ? err.message : 'Unable to update alert.',
        variant: 'error',
      });
    }
  }

  const alertColumns: TableColumn<AutomationAlertResponse>[] = [
    {
      key: 'workflow',
      header: 'Workflow',
      render: (r) => <Badge variant={r.severity === 'WARNING' ? 'warning' : 'info'}>{r.workflowType}</Badge>,
    },
    {
      key: 'title',
      header: 'Alert',
      render: (r) => (
        <div>
          <strong>{r.title}</strong>
          <div style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>
            {r.message}
          </div>
        </div>
      ),
    },
    {
      key: 'status',
      header: 'Status',
      render: (r) => (
        <Badge variant={r.status === 'OPEN' ? 'warning' : 'neutral'}>
          {r.status}
        </Badge>
      ),
    },
    {
      key: 'actions',
      header: 'Action',
      align: 'right',
      render: (r) =>
        r.status === 'OPEN' ? (
          <Button
            type="button"
            size="sm"
            variant="secondary"
            onClick={() => void handleAcknowledge(r.id)}
          >
            Acknowledge
          </Button>
        ) : (
          <span style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>
            {r.acknowledgedAt ? new Date(r.acknowledgedAt).toLocaleString() : 'Done'}
          </span>
        ),
    },
  ];

  const runColumns: TableColumn<AutomationRunResponse>[] = [
    {
      key: 'started',
      header: 'Started',
      render: (r) => new Date(r.startedAt).toLocaleString(),
    },
    {
      key: 'workflow',
      header: 'Workflow',
      render: (r) => r.workflowType,
    },
    {
      key: 'trigger',
      header: 'Trigger',
      render: (r) => <Badge variant="neutral">{r.triggerSource}</Badge>,
    },
    {
      key: 'status',
      header: 'Outcome',
      render: (r) => (
        <Badge variant={r.status === 'COMPLETED' ? 'success' : 'error'}>
          {r.status}
        </Badge>
      ),
    },
    {
      key: 'created',
      header: 'Created',
      align: 'right',
      render: (r) => r.alertsCreated,
    },
    {
      key: 'skipped',
      header: 'Duplicates Skipped',
      align: 'right',
      render: (r) => r.alertsSkippedDuplicate,
    },
  ];

  return (
    <div className="foundation-stack">
      <Card>
        <CardHeader
          title="Deterministic Automation Workflows"
          description="Idempotent checks for low-stock SKUs and due CRM follow-ups with duplicate prevention and audit runs."
          actions={<BellRing size={18} aria-hidden="true" />}
        />
        <CardContent compact>
          <div className="foundation-row" style={{ flexWrap: 'wrap', alignItems: 'flex-end', justifyContent: 'space-between' }}>
            <div className="foundation-row" style={{ flexWrap: 'wrap' }}>
              <Button
                type="button"
                loading={runningWorkflow}
                onClick={() => void handleRun()}
              >
                <Play size={14} aria-hidden="true" />
                Run All Workflows
              </Button>
              <Button
                type="button"
                variant="secondary"
                disabled={runningWorkflow}
                onClick={() => void handleRun('LOW_STOCK_ALERT')}
              >
                Check Low Stock
              </Button>
              <Button
                type="button"
                variant="secondary"
                disabled={runningWorkflow}
                onClick={() => void handleRun('FOLLOW_UP_REMINDER')}
              >
                Check Due Follow-Ups
              </Button>
            </div>
            <div style={{ minWidth: '11rem' }}>
              <Select
                label="Alert Status"
                value={statusFilter}
                options={[
                  { value: 'OPEN', label: 'Open Only' },
                  { value: 'ACKNOWLEDGED', label: 'Acknowledged' },
                  { value: 'ALL', label: 'All Alerts' },
                ]}
                onChange={(e) => setStatusFilter(e.target.value)}
              />
            </div>
          </div>
        </CardContent>
      </Card>

      <StateBoundary
        status={screenStatus}
        errorTitle="Automation data unavailable"
        errorMessage={errorMessage ?? undefined}
        onRetry={() => void loadAutomationData()}
      >
        <Card>
          <CardHeader title="Automation Alerts" />
          <CardContent>
            <Table
              caption="Automation alerts"
              columns={alertColumns}
              rows={alerts}
              getRowKey={(r) => r.id}
              emptyTitle="No matching automation alerts"
            />
          </CardContent>
        </Card>

        <Card>
          <CardHeader title="Execution Audit History" />
          <CardContent>
            <Table
              caption="Automation run audit log"
              columns={runColumns}
              rows={runs}
              getRowKey={(r) => r.id}
              emptyTitle="No workflow runs recorded yet"
            />
          </CardContent>
        </Card>
      </StateBoundary>
    </div>
  );
}
