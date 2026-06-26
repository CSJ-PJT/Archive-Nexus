export type SimulatorStatus = {
  running: boolean;
  tick: number;
  factoryCount: number;
  alertCount: number;
  rpaTaskCount: number;
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

export type Overview = {
  simulator: SimulatorStatus;
  factories: Factory[];
  recentAlerts: FactoryAlert[];
  pendingRpaTasks: RpaTask[];
  kpis: Record<string, number>;
};
