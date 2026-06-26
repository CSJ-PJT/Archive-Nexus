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
