export type ProposalStatus = 'PENDING' | 'EXECUTED' | 'CANCELLED' | 'EXPIRED' | 'FAILED';

export interface SourceCitationDto {
  toolName: string;
  entityType: string;
  label: string;
  summary: string;
}

export interface ActionProposalDto {
  id: string;
  toolName: string;
  actionSummary: string;
  status: ProposalStatus;
  resultSummary: string | null;
  createdAt: string;
  expiresAt: string;
  resolvedAt: string | null;
}

export interface AssistantChatResponse {
  status: 'OK' | 'PROVIDER_UNAVAILABLE' | 'PROVIDER_ERROR' | string;
  reply: string | null;
  errorMessage: string | null;
  toolsUsed: string[];
  citations: SourceCitationDto[];
  pendingActions: ActionProposalDto[];
  disclaimer: string;
}

export interface AssistantAuditLogDto {
  id: string;
  userId: string | null;
  eventType: string;
  toolName: string | null;
  outcome: string;
  details: string | null;
  createdAt: string;
}

export type AutomationWorkflowType = 'LOW_STOCK_ALERT' | 'FOLLOW_UP_REMINDER';
export type AutomationAlertStatus = 'OPEN' | 'ACKNOWLEDGED';

export interface AutomationRunResponse {
  id: string;
  workflowType: AutomationWorkflowType;
  triggerSource: 'MANUAL' | 'SCHEDULED';
  status: 'COMPLETED' | 'FAILED';
  alertsCreated: number;
  alertsSkippedDuplicate: number;
  errorMessage: string | null;
  startedAt: string;
  completedAt: string | null;
}

export interface AutomationRunBatchResponse {
  runs: AutomationRunResponse[];
}

export interface AutomationAlertResponse {
  id: string;
  workflowType: AutomationWorkflowType;
  entityType: 'PRODUCT' | 'LEAD';
  entityId: string;
  severity: 'INFO' | 'WARNING';
  title: string;
  message: string;
  status: AutomationAlertStatus;
  runId: string;
  acknowledgedAt: string | null;
  createdAt: string;
}
