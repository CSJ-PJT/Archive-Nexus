import type {
  ArchiveOsInteraction, BatchSnapshot, Factory, FactoryAlert, InventoryItem,
  InventoryTransaction, LogisticsShipment, MaintenanceEvent, Overview,
  ProductionOrder, QualityInspection, RpaTask, SimulatorPersistenceStatus,
  SimulatorStatus
} from './types';

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? '';

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, init);
  if (!response.ok) {
    throw new Error(`API 요청 실패: ${response.status}`);
  }
  return response.json() as Promise<T>;
}

export const api = {
  overview: () => request<Overview>('/api/overview'),
  factories: () => request<Factory[]>('/api/factories'),
  alerts: (factoryId: string) => request<FactoryAlert[]>(`/api/factories/${factoryId}/alerts`),
  rpaTasks: () => request<RpaTask[]>('/api/rpa/tasks'),
  simulatorStatus: () => request<SimulatorStatus>('/api/simulator/status'),
  startSimulator: () => request<SimulatorStatus>('/api/simulator/start', { method: 'POST' }),
  stopSimulator: () => request<SimulatorStatus>('/api/simulator/stop', { method: 'POST' }),
  simulatorPersistence: () => request<SimulatorPersistenceStatus>('/api/simulator/persistence'),
  productionOrders: () => request<ProductionOrder[]>('/api/production/orders'),
  qualityInspections: () => request<QualityInspection[]>('/api/quality/inspections'),
  inventoryItems: () => request<InventoryItem[]>('/api/inventory/items'),
  inventoryTransactions: () => request<InventoryTransaction[]>('/api/inventory/transactions'),
  logisticsShipments: () => request<LogisticsShipment[]>('/api/logistics/shipments'),
  maintenanceEvents: () => request<MaintenanceEvent[]>('/api/maintenance/events'),
  approveRpa: (id: string) => request<RpaTask>(`/api/rpa/tasks/${id}/approve`, { method: 'POST' }),
  rejectRpa: (id: string) => request<RpaTask>(`/api/rpa/tasks/${id}/reject`, { method: 'POST' }),
  batchSnapshots: () => request<BatchSnapshot[]>('/api/batch/snapshots'),
  archiveOsInteractions: () => request<ArchiveOsInteraction[]>('/api/archiveos/interactions')
};
