export type SimulatorStatus = {
  running: boolean;
  tick: number;
  factoryCount: number;
  alertCount: number;
  rpaTaskCount: number;
  parallelWorkerCount: number;
  updatedAt: string;
};

export type Factory = {
  id: string;
  name: string;
  kind: string;
  scenario: string;
  lines: Array<{ id: string; name: string; product: string; machines: Array<{ id: string; name: string }> }>;
};

export type FactoryAlert = {
  id: string;
  factoryId: string;
  severity: 'INFO' | 'WARNING' | 'CRITICAL';
  category: string;
  message: string;
  occurredAt: string;
};

export type RpaTask = {
  id: string;
  factoryId: string;
  status: string;
  trigger: string;
  recommendation: string;
  approvalRequired: boolean;
  createdAt: string;
  actionType?: string;
  priority?: string;
  source?: string;
  sourceQueryId?: string | null;
  reason?: string;
  recommendedAction?: string;
  evidence?: string[];
  requiresApproval?: boolean;
};
export type NexusTaskStatus='PENDING'|'RUNNING'|'SUCCESS'|'FAILED'|'CANCELLED';
export type NexusTaskType='MANUFACTURING_QUERY'|'SIMULATOR_TICK';
export type NexusTask={id:string;title:string;type:NexusTaskType;factoryId:string|null;question:string|null;requestedBy:string;status:NexusTaskStatus;attemptCount:number;maxAttempts:number;resultSummary:string|null;errorMessage:string|null;createdAt:string;startedAt:string|null;completedAt:string|null;updatedAt:string;};
export type NexusTaskLog={id:number;taskId:string;level:string;message:string;createdAt:string;};
export type CreateNexusTask={title:string;type:NexusTaskType;factoryId?:string;question?:string;requestedBy?:string;maxAttempts?:number;};

export type AgentEvidence = {
  type: string;
  description: string;
  value: string;
  source: string;
};

export type AgentResult = {
  agentName: string;
  intent: string;
  summary: string;
  evidence: AgentEvidence[];
  recommendedActions: string[];
  confidence: number;
  executionTimeMs: number;
  status: string;
  errorMessage: string | null;
  actionRequired: boolean;
};

export type AiQueryRequest = {
  question: string;
  factoryId?: string;
  requestedBy?: string;
};

export type AiQueryResponse = {
  queryId: string;
  question: string;
  requestedBy: string;
  selectedFactoryId: string | null;
  routedIntents: string[];
  invokedAgents: string[];
  agentResults: AgentResult[];
  answer: string;
  evidence: AgentEvidence[];
  recommendedActions: string[];
  confidence: number;
  partialFailure: boolean;
  approvalRequired: boolean;
  rpaTaskId: string | null;
  executionStatus: string;
  executionTimeMs: number;
  errorMessage: string | null;
  createdAt: string;
};

export type AiDashboardSummary = {
  totalQueries: number;
  runningAgents: number;
  agentFailures: number;
  agentRpaTasks: number;
  recentRecommendation: string;
};

export type ProductionOrder = {
  id: string;
  factoryId: string;
  product: string;
  targetQuantity: number;
  producedQuantity: number;
  status: string;
};

export type QualityInspection = {
  id: string;
  lotId: string;
  factoryId: string;
  defectRate: number;
  result: string;
};

export type InventoryItem = {
  id: string;
  name: string;
  type: string;
  quantity: number;
  safetyStock: number;
};

export type InventoryTransaction = {
  id: string;
  itemId: string;
  factoryId: string;
  type: string;
  quantity: number;
  occurredAt: string;
};

export type LogisticsShipment = {
  id: string;
  factoryId: string;
  destination: string;
  status: string;
  priority: number;
};

export type MaintenanceEvent = {
  id: string;
  factoryId: string;
  machineId: string;
  severity: FactoryAlert['severity'];
  cause: string;
  status: string;
};

export type BatchSnapshot = {
  tick: number;
  factoryCount: number;
  productionOrderCount: number;
  totalProducedQuantity: number;
  averageDefectRate: number;
  alertCount: number;
  pendingApprovalCount: number;
  createdAt: string;
};

export type ArchiveOsInteraction = {
  id: string;
  type: string;
  factoryId: string | null;
  payload: string;
  occurredAt: string;
};

export type SimulatorPersistenceStatus = {
  enabled: boolean;
  storageMode: string;
  dbAvailable: boolean;
  fileSnapshotAvailable: boolean;
  stateFile: string;
  snapshotExists: boolean;
  lastSavedAt: string | null;
  lastPersistedAt: string | null;
  restoredFrom: string;
};

export type Overview = {
  simulator: SimulatorStatus;
  factories: Factory[];
  recentAlerts: FactoryAlert[];
  pendingRpaTasks: RpaTask[];
  kpis: Record<string, number>;
};
