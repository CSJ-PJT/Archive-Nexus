import type {
  AiDashboardSummary, AiQueryRequest, AiQueryResponse, ArchiveOsInteraction, ArchiveOsStatus, BatchSnapshot, Factory, FactoryAlert, InventoryItem,
  InventoryTransaction, LogisticsShipment, MaintenanceEvent, Overview,
  CreateNexusTask, NexusTask, NexusTaskLog, ProductionOrder, QualityInspection, RpaTask, SimulatorPersistenceStatus,
  SimulatorStatus
} from './types';

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? '';

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const controller = new AbortController();
  const timeout = window.setTimeout(() => controller.abort(), 10000);
  try {
    const response = await fetch(`${API_BASE}${path}`, { ...init, signal: controller.signal });
    if (!response.ok) {
      throw new Error(`API 요청 실패: ${response.status}`);
    }
    return response.json() as Promise<T>;
  } catch (cause) {
    if (cause instanceof DOMException && cause.name === 'AbortError') {
      throw new Error('API 응답 시간이 초과되었습니다. Backend 연결 상태를 확인하세요.');
    }
    throw cause;
  } finally {
    window.clearTimeout(timeout);
  }
}

export const api = {
  overview: () => request<Overview>('/api/overview?pendingLimit=50'),
  factories: () => request<Factory[]>('/api/factories'),
  alerts: (factoryId: string) => request<FactoryAlert[]>(`/api/factories/${factoryId}/alerts`),
  rpaTasks: () => request<RpaTask[]>('/api/rpa/tasks?limit=100'),
  tasks: () => request<NexusTask[]>('/api/tasks'),
  createTask: (body: CreateNexusTask) => request<NexusTask>('/api/tasks', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }),
  taskLogs: (id: string) => request<NexusTaskLog[]>(`/api/tasks/${id}/logs`),
  runTask: (id: string) => request<NexusTask>(`/api/tasks/${id}/run`, { method: 'POST' }),
  syncTask: (id: string) => request<NexusTask>(`/api/tasks/${id}/sync`, { method: 'POST' }),
  cancelTask: (id: string) => request<NexusTask>(`/api/tasks/${id}/cancel`, { method: 'POST' }),
  retryTask: (id: string) => request<NexusTask>(`/api/tasks/${id}/retry`, { method: 'POST' }),
  simulatorStatus: () => request<SimulatorStatus>('/api/simulator/status'),
  startSimulator: () => request<SimulatorStatus>('/api/simulator/start', { method: 'POST' }),
  stopSimulator: () => request<SimulatorStatus>('/api/simulator/stop', { method: 'POST' }),
  simulatorPersistence: () => request<SimulatorPersistenceStatus>('/api/simulator/persistence'),
  productionOrders: () => request<ProductionOrder[]>('/api/production/orders?limit=300'),
  qualityInspections: () => request<QualityInspection[]>('/api/quality/inspections?limit=300'),
  inventoryItems: () => request<InventoryItem[]>('/api/inventory/items'),
  inventoryTransactions: () => request<InventoryTransaction[]>('/api/inventory/transactions?limit=300'),
  logisticsShipments: () => request<LogisticsShipment[]>('/api/logistics/shipments?limit=300'),
  maintenanceEvents: () => request<MaintenanceEvent[]>('/api/maintenance/events?limit=300'),
  approveRpa: (id: string) => request<RpaTask>(`/api/rpa/tasks/${id}/approve`, { method: 'POST' }),
  rejectRpa: (id: string) => request<RpaTask>(`/api/rpa/tasks/${id}/reject`, { method: 'POST' }),
  batchSnapshots: () => request<BatchSnapshot[]>('/api/batch/snapshots?limit=100'),
  archiveOsInteractions: () => request<ArchiveOsInteraction[]>('/api/archiveos/interactions?limit=100'),
  archiveOsStatus: () => request<ArchiveOsStatus>('/api/archiveos/status'),
  aiQuery: (body: AiQueryRequest) => request<AiQueryResponse>('/api/ai/query', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body)
  }),
  aiQueries: () => request<AiQueryResponse[]>('/api/ai/queries'),
  aiQueryDetail: (id: string) => request<AiQueryResponse>(`/api/ai/queries/${id}`),
  aiSummary: () => request<AiDashboardSummary>('/api/ai/summary')
};
