import { useState, type FormEvent } from 'react';
import { Bot, CheckCircle2, ShieldAlert, XCircle } from 'lucide-react';
import { useAuth } from '../../app/authContext';
import {
  Alert,
  Badge,
  Button,
  Card,
  CardContent,
  CardHeader,
  FormField,
  useToast,
} from '../ui';
import { apiRequest, ApiError } from '../../lib/apiClient';
import type {
  ActionProposalDto,
  AssistantChatResponse,
  SourceCitationDto,
} from '../../lib/assistantTypes';

interface ThreadEntry {
  id: string;
  question: string;
  response: AssistantChatResponse;
}

const EXAMPLE_PROMPTS = [
  'Summarize sales revenue and order counts for this month.',
  'Which products are currently low in stock?',
  'List outstanding invoices and unpaid balances.',
];

export function AssistantPanel() {
  const { token, hasPermission } = useAuth();
  const { notify } = useToast();

  const [prompt, setPrompt] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [requestError, setRequestError] = useState<string | null>(null);
  const [thread, setThread] = useState<ThreadEntry[]>([]);
  const [actionBusyId, setActionBusyId] = useState<string | null>(null);

  if (!hasPermission('AI_USE')) {
    return (
      <Alert
        variant="warning"
        title="AI Assistant access not enabled for your role"
        description="Your current organization role does not grant PERM_AI_USE."
      />
    );
  }

  async function handleAsk(e: FormEvent) {
    e.preventDefault();
    const trimmed = prompt.trim();
    if (!trimmed) {
      return;
    }

    setSubmitting(true);
    setRequestError(null);

    const history = thread.slice(-5).flatMap((entry) => {
      const turns: { role: string; content: string }[] = [
        { role: 'user', content: entry.question },
      ];
      if (entry.response.reply) {
        turns.push({ role: 'assistant', content: entry.response.reply });
      }
      return turns;
    });

    try {
      const data = await apiRequest<AssistantChatResponse>('/api/v1/assistant/chat', {
        method: 'POST',
        token,
        body: { message: trimmed, history },
      });
      setThread((prev) => [
        ...prev,
        { id: `${Date.now()}-${prev.length}`, question: trimmed, response: data },
      ]);
      setPrompt('');
    } catch (err) {
      const msg =
        err instanceof ApiError
          ? err.message
          : 'Unable to reach AI assistant endpoint.';
      setRequestError(msg);
    } finally {
      setSubmitting(false);
    }
  }

  async function handleProposalDecision(
    proposalId: string,
    decision: 'confirm' | 'cancel',
  ) {
    setActionBusyId(proposalId);
    try {
      const updated = await apiRequest<ActionProposalDto>(
        `/api/v1/assistant/actions/${proposalId}/${decision}`,
        { method: 'POST', token },
      );

      setThread((prev) =>
        prev.map((entry) => ({
          ...entry,
          response: {
            ...entry.response,
            pendingActions: entry.response.pendingActions.map((p) =>
              p.id === proposalId ? updated : p,
            ),
          },
        })),
      );

      notify({
        title:
          decision === 'confirm' ? 'Action executed' : 'Action cancelled',
        description: updated.resultSummary ?? updated.actionSummary,
        variant: decision === 'confirm' ? 'success' : 'info',
      });
    } catch (err) {
      const msg =
        err instanceof ApiError ? err.message : 'Unable to process action.';
      notify({ title: 'Action failed', description: msg, variant: 'error' });
    } finally {
      setActionBusyId(null);
    }
  }

  return (
    <div className="foundation-stack">
      <Alert
        variant="info"
        title="Permission-Checked Business Assistant"
        description="Reads run strictly through typed organization-scoped services. Any write action creates a pending proposal that executes only after your explicit confirmation."
      />

      <Card>
        <CardHeader
          title="Ask SmartBiz Assistant"
          description="Query authorized sales summaries, low-stock SKUs, customer records, order status, or outstanding balances."
          actions={<Bot size={18} aria-hidden="true" />}
        />
        <CardContent className="foundation-stack">
          <div className="foundation-row" style={{ flexWrap: 'wrap' }}>
            {EXAMPLE_PROMPTS.map((example) => (
              <Button
                key={example}
                type="button"
                variant="secondary"
                size="sm"
                onClick={() => setPrompt(example)}
              >
                {example}
              </Button>
            ))}
          </div>

          <form onSubmit={(e) => void handleAsk(e)} className="foundation-stack">
            <FormField
              label="Business Question or Action Request"
              placeholder="e.g. Show low-stock SKUs or draft a follow-up note..."
              value={prompt}
              maxLength={2000}
              onChange={(e) => setPrompt(e.target.value)}
            />
            <div className="foundation-row" style={{ justifyContent: 'space-between', alignItems: 'center' }}>
              <span style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>
                {prompt.length}/2000 characters
              </span>
              <Button type="submit" loading={submitting} disabled={!prompt.trim()}>
                Send to Assistant
              </Button>
            </div>
          </form>

          {requestError ? (
            <Alert
              variant="error"
              title="Assistant request failed"
              description={requestError}
            />
          ) : null}
        </CardContent>
      </Card>

      {thread.map((entry) => (
        <Card key={entry.id}>
          <CardHeader
            title={entry.question}
            actions={
              <Badge variant={entry.response.status === 'OK' ? 'success' : 'warning'}>
                {entry.response.status}
              </Badge>
            }
          />
          <CardContent className="foundation-stack">
            {entry.response.status !== 'OK' ? (
              <Alert
                variant="warning"
                title={`Provider status: ${entry.response.status}`}
                description={
                  entry.response.errorMessage ??
                  'The configured AI provider could not complete this request. Business reports and records remain unaffected.'
                }
              />
            ) : (
              <p style={{ whiteSpace: 'pre-wrap', lineHeight: 1.55 }}>
                {entry.response.reply}
              </p>
            )}

            {entry.response.toolsUsed.length > 0 ? (
              <div className="foundation-row" style={{ flexWrap: 'wrap' }}>
                {entry.response.toolsUsed.map((tool) => (
                  <Badge key={tool} variant="neutral">
                    Tool: {tool}
                  </Badge>
                ))}
              </div>
            ) : null}

            {entry.response.citations.length > 0 ? (
              <div className="foundation-stack">
                <strong style={{ fontSize: 'var(--text-sm)' }}>Source Records</strong>
                <ul style={{ paddingLeft: '1.25rem', margin: 0, display: 'grid', gap: '0.35rem' }}>
                  {entry.response.citations.map((c: SourceCitationDto, idx: number) => (
                    <li key={`${c.toolName}-${idx}`} style={{ fontSize: 'var(--text-xs)' }}>
                      <Badge variant="info">{c.entityType}</Badge>{' '}
                      <strong>{c.label}</strong>: {c.summary}
                    </li>
                  ))}
                </ul>
              </div>
            ) : null}

            {entry.response.pendingActions.map((proposal) => (
              <Card key={proposal.id}>
                <CardContent compact className="foundation-stack">
                  <div className="foundation-row" style={{ justifyContent: 'space-between', alignItems: 'center' }}>
                    <span style={{ display: 'inline-flex', alignItems: 'center', gap: '0.4rem', fontWeight: 600 }}>
                      <ShieldAlert size={16} aria-hidden="true" />
                      Proposed Write Action ({proposal.toolName})
                    </span>
                    <Badge
                      variant={
                        proposal.status === 'EXECUTED'
                          ? 'success'
                          : proposal.status === 'PENDING'
                            ? 'warning'
                            : 'neutral'
                      }
                    >
                      {proposal.status}
                    </Badge>
                  </div>
                  <p style={{ fontSize: 'var(--text-sm)', margin: 0 }}>{proposal.actionSummary}</p>
                  {proposal.resultSummary ? (
                    <p style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)', margin: 0 }}>
                      {proposal.resultSummary}
                    </p>
                  ) : null}
                  {proposal.status === 'PENDING' ? (
                    <div className="foundation-row">
                      <Button
                        type="button"
                        size="sm"
                        loading={actionBusyId === proposal.id}
                        onClick={() => void handleProposalDecision(proposal.id, 'confirm')}
                      >
                        <CheckCircle2 size={14} aria-hidden="true" />
                        Confirm & Execute
                      </Button>
                      <Button
                        type="button"
                        size="sm"
                        variant="secondary"
                        disabled={actionBusyId === proposal.id}
                        onClick={() => void handleProposalDecision(proposal.id, 'cancel')}
                      >
                        <XCircle size={14} aria-hidden="true" />
                        Cancel
                      </Button>
                    </div>
                  ) : null}
                </CardContent>
              </Card>
            ))}

            <p style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)', margin: 0 }}>
              {entry.response.disclaimer}
            </p>
          </CardContent>
        </Card>
      ))}
    </div>
  );
}
