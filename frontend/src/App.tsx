import {
  Activity, AlertTriangle, Bot, Boxes, CheckCircle2, CircleStop, Clock3, Database,
  Factory, Gauge, Play, RefreshCw, ShieldCheck, Truck, Wrench
} from 'lucide-react';
import type { ReactNode } from 'react';
import { useCallback, useEffect, useState } from 'react';
import { api } from './api';
import { ManufacturingAiPanel } from './ManufacturingAiPanel';
import type {
  AiDashboardSummary, ArchiveOsInteraction, BatchSnapshot, InventoryItem, InventoryTransaction,
  LogisticsShipment, MaintenanceEvent, Overview, ProductionOrder,
  QualityInspection, RpaTask, SimulatorPersistenceStatus
} from './types';

const tabs = ['Overview', 'Manufacturing AI', 'Factories', 'Production', 'Inventory', 'Quality', 'Maintenance', 'Logistics', 'RPA', 'Settings'] as const;
type Tab = (typeof tabs)[number];

type OperationsData = {
  productionOrders: ProductionOrder[];
  qualityInspections: QualityInspection[];
  inventoryItems: InventoryItem[];
  inventoryTransactions: InventoryTransaction[];
  logisticsShipments: LogisticsShipment[];
  maintenanceEvents: MaintenanceEvent[];
  rpaTasks: RpaTask[];
};

const fallback: Overview = {
  simulator: { running: false, tick: 0, factoryCount: 0, alertCount: 0, rpaTaskCount: 0, parallelWorkerCount: 0, updatedAt: '' },
  factories: [], recentAlerts: [], pendingRpaTasks: [], kpis: {}
};
const emptyOperations: OperationsData = {
  productionOrders: [], qualityInspections: [], inventoryItems: [], inventoryTransactions: [],
  logisticsShipments: [], maintenanceEvents: [], rpaTasks: []
};
const fallbackPersistence: SimulatorPersistenceStatus = {
  enabled: false, storageMode: 'disabled', dbAvailable: false, fileSnapshotAvailable: false,
  stateFile: '', snapshotExists: false, lastSavedAt: null, lastPersistedAt: null, restoredFrom: 'seed'
};
const fallbackAiSummary: AiDashboardSummary = {
  totalQueries: 0, runningAgents: 0, agentFailures: 0, agentRpaTasks: 0, recentRecommendation: '최근 권장 조치 없음'
};

export function App() {
  const [tab, setTab] = useState<Tab>('Overview');
  const [overview, setOverview] = useState<Overview>(fallback);
  const [operations, setOperations] = useState<OperationsData>(emptyOperations);
  const [batchSnapshots, setBatchSnapshots] = useState<BatchSnapshot[]>([]);
  const [archiveOsInteractions, setArchiveOsInteractions] = useState<ArchiveOsInteraction[]>([]);
  const [persistence, setPersistence] = useState<SimulatorPersistenceStatus>(fallbackPersistence);
  const [aiSummary, setAiSummary] = useState<AiDashboardSummary>(fallbackAiSummary);
  const [loading, setLoading] = useState(true);
  const [actionPending, setActionPending] = useState(false);
  const [error, setError] = useState('');
  const [lastUpdatedAt, setLastUpdatedAt] = useState<Date | null>(null);

  const load = useCallback(async () => {
    try {
      const [nextOverview, productionOrders, qualityInspections, inventoryItems, inventoryTransactions,
        logisticsShipments, maintenanceEvents, rpaTasks, nextBatchSnapshots,
        nextArchiveOsInteractions, nextPersistence, nextAiSummary] = await Promise.all([
        api.overview(), api.productionOrders(), api.qualityInspections(), api.inventoryItems(),
        api.inventoryTransactions(), api.logisticsShipments(), api.maintenanceEvents(), api.rpaTasks(),
        api.batchSnapshots(), api.archiveOsInteractions(), api.simulatorPersistence(), api.aiSummary()
      ]);
      setOverview(nextOverview);
      setOperations({ productionOrders, qualityInspections, inventoryItems, inventoryTransactions, logisticsShipments, maintenanceEvents, rpaTasks });
      setBatchSnapshots(nextBatchSnapshots);
      setArchiveOsInteractions(nextArchiveOsInteractions);
      setPersistence(nextPersistence);
      setAiSummary(nextAiSummary);
      setLastUpdatedAt(new Date());
      setError('');
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'API 연결 실패');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
    const timer = window.setInterval(() => void load(), 5000);
    return () => window.clearInterval(timer);
  }, [load]);

  const runAction = async (action: () => Promise<unknown>) => {
    setActionPending(true);
    try {
      await action();
      await load();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '작업 실행 실패');
    } finally {
      setActionPending(false);
    }
  };

  const criticalCount = overview.recentAlerts.filter((alert) => alert.severity === 'CRITICAL').length;
  const delayedCount = operations.logisticsShipments.filter((shipment) => shipment.status === 'DELAYED').length;
  const stockRiskCount = operations.inventoryItems.filter((item) => item.quantity <= item.safetyStock).length;

  return <main className="shell">
    <aside className="sidebar">
      <div className="brand"><Factory size={28} /><div><strong>Archive Nexus</strong><span>Manufacturing AX</span></div></div>
      <nav aria-label="주요 메뉴">{tabs.map((item) => <button key={item} className={tab === item ? 'active' : ''} onClick={() => setTab(item)}>{item}</button>)}</nav>
    </aside>
    <section className="workspace">
      <header className="topbar">
        <div><h1>{tab}</h1><p>ArchiveOS 제조 운영 관제 · {lastUpdatedAt ? `최근 동기화 ${formatTime(lastUpdatedAt)}` : '연결 중'}</p></div>
        <div className="actions">
          <button className="icon-button secondary" onClick={() => void load()} title="데이터 새로고침" aria-label="데이터 새로고침"><RefreshCw size={17} /></button>
          <button onClick={() => void runAction(api.startSimulator)} disabled={actionPending || overview.simulator.running}><Play size={17} />Start</button>
          <button className="stop" onClick={() => void runAction(api.stopSimulator)} disabled={actionPending || !overview.simulator.running}><CircleStop size={17} />Stop</button>
        </div>
      </header>
      {error && <div className="notice" role="alert">{error}</div>}
      {loading ? <LoadingState /> : <>
        <section className="metrics" aria-label="운영 핵심 지표">
          <Metric icon={<Activity />} label="Tick" value={overview.simulator.tick} />
          <Metric icon={<Factory />} label="Factories" value={overview.simulator.factoryCount} />
          <Metric icon={<Gauge />} label="Workers" value={overview.simulator.parallelWorkerCount} />
          <Metric icon={<AlertTriangle />} label="Critical" value={criticalCount} tone={criticalCount ? 'danger' : 'normal'} />
          <Metric icon={<Boxes />} label="Stock risks" value={stockRiskCount} tone={stockRiskCount ? 'warning' : 'normal'} />
          <Metric icon={<Truck />} label="Delayed" value={delayedCount} tone={delayedCount ? 'warning' : 'normal'} />
        </section>
        {tab === 'Overview' && <OverviewPanel overview={overview} operations={operations} aiSummary={aiSummary} />}
        {tab === 'Manufacturing AI' && <ManufacturingAiPanel factories={overview.factories} onChanged={load} />}
        {tab === 'Factories' && <FactoriesPanel overview={overview} operations={operations} />}
        {tab === 'Production' && <ProductionPanel orders={operations.productionOrders} />}
        {tab === 'Inventory' && <InventoryPanel items={operations.inventoryItems} transactions={operations.inventoryTransactions} />}
        {tab === 'Quality' && <QualityPanel inspections={operations.qualityInspections} />}
        {tab === 'Maintenance' && <MaintenancePanel events={operations.maintenanceEvents} />}
        {tab === 'Logistics' && <LogisticsPanel shipments={operations.logisticsShipments} />}
        {tab === 'RPA' && <RpaPanel tasks={operations.rpaTasks} runAction={runAction} />}
        {tab === 'Settings' && <SettingsPanel overview={overview} batchSnapshots={batchSnapshots} archiveOsInteractions={archiveOsInteractions} persistence={persistence} />}
      </>}
    </section>
  </main>;
}

function Metric({ icon, label, value, tone = 'normal' }: { icon: ReactNode; label: string; value: number; tone?: 'normal' | 'warning' | 'danger' }) {
  return <div className={`metric ${tone}`}><span>{icon}</span><small>{label}</small><strong>{value}</strong></div>;
}

function OverviewPanel({ overview, operations, aiSummary }: { overview: Overview; operations: OperationsData; aiSummary: AiDashboardSummary }) {
  const totalTarget = operations.productionOrders.reduce((sum, order) => sum + order.targetQuantity, 0);
  const totalProduced = operations.productionOrders.reduce((sum, order) => sum + order.producedQuantity, 0);
  const avgDefect = average(operations.qualityInspections.map((item) => item.defectRate));
  const openMaintenance = operations.maintenanceEvents.filter((item) => item.status === 'OPEN').length;
  return <div className="grid dashboard-grid">
    <section className="panel wide"><PanelTitle icon={<AlertTriangle />} title="최근 이상 이벤트" count={overview.recentAlerts.length} /><EventList overview={overview} /></section>
    <section className="panel"><PanelTitle icon={<Activity />} title="운영 상태" /><p className={overview.simulator.running ? 'state on' : 'state'}>{overview.simulator.running ? 'RUNNING' : 'STOPPED'}</p><dl className="summary-list"><Summary label="생산 달성률" value={percent(totalProduced, totalTarget)} /><Summary label="평균 불량률" value={formatPercent(avgDefect)} /><Summary label="미처리 정비" value={`${openMaintenance}건`} /><Summary label="승인 대기" value={`${overview.pendingRpaTasks.length}건`} /></dl></section>
    <section className="panel full"><PanelTitle icon={<Factory />} title="공장 운영 현황" /><FactoryTable overview={overview} operations={operations} /></section>
    <section className="panel full ai-overview"><PanelTitle icon={<Bot />} title="Manufacturing AI" /><div className="ai-overview-stats"><Summary label="최근 AI Query" value={`${aiSummary.totalQueries}건`} /><Summary label="실행 중 Agent" value={`${aiSummary.runningAgents}개`} /><Summary label="Agent 실패" value={`${aiSummary.agentFailures}건`} /><Summary label="Agent 기반 RPA" value={`${aiSummary.agentRpaTasks}건`} /></div><p><strong>최근 권장 조치</strong> · {aiSummary.recentRecommendation}</p></section>
  </div>;
}

function FactoriesPanel({ overview, operations }: { overview: Overview; operations: OperationsData }) {
  return <div className="factory-list">{overview.factories.map((factory) => {
    const alerts = overview.recentAlerts.filter((item) => item.factoryId === factory.id);
    const orders = operations.productionOrders.filter((item) => item.factoryId === factory.id);
    const produced = orders.reduce((sum, item) => sum + item.producedQuantity, 0);
    const target = orders.reduce((sum, item) => sum + item.targetQuantity, 0);
    return <article className="panel" key={factory.id}><div className="card-heading"><div><h2>{factory.name}</h2><span className="muted">{factory.id} · {factory.kind}</span></div><StatusBadge value={alerts.some((item) => item.severity === 'CRITICAL') ? 'CRITICAL' : alerts.length ? 'WARNING' : 'NORMAL'} /></div><p>{factory.scenario}</p><dl className="summary-list"><Summary label="생산 달성률" value={percent(produced, target)} /><Summary label="최근 경보" value={`${alerts.length}건`} /><Summary label="라인" value={`${factory.lines.length}개`} /><Summary label="주요 제품" value={factory.lines[0]?.product ?? '-'} /></dl></article>;
  })}</div>;
}

function ProductionPanel({ orders }: { orders: ProductionOrder[] }) {
  return <DataPanel icon={<Gauge />} title="생산 오더" count={orders.length}><Table headers={['오더', '공장', '제품', '목표', '실적', '달성률', '상태']} rows={orders.slice().reverse().map((order) => [order.id, order.factoryId, order.product, order.targetQuantity, order.producedQuantity, percent(order.producedQuantity, order.targetQuantity), <StatusBadge value={order.status} />])} /></DataPanel>;
}

function InventoryPanel({ items, transactions }: { items: InventoryItem[]; transactions: InventoryTransaction[] }) {
  return <div className="grid"><DataPanel icon={<Boxes />} title="재고 현황" count={items.length}><Table headers={['품목', '유형', '현재고', '안전재고', '상태']} rows={items.map((item) => [item.name, item.type, item.quantity, item.safetyStock, <StatusBadge value={item.quantity <= item.safetyStock ? 'LOW' : 'NORMAL'} />])} /></DataPanel><DataPanel icon={<Clock3 />} title="최근 입출고" count={transactions.length}><Table headers={['공장', '품목', '구분', '수량', '발생 시각']} rows={transactions.slice(-8).reverse().map((item) => [item.factoryId, item.itemId, item.type, item.quantity, formatDate(item.occurredAt)])} /></DataPanel></div>;
}

function QualityPanel({ inspections }: { inspections: QualityInspection[] }) {
  return <DataPanel icon={<CheckCircle2 />} title="Lot 품질 검사" count={inspections.length}><Table headers={['검사', '공장', 'Lot', '불량률', '판정']} rows={inspections.slice().reverse().map((item) => [item.id, item.factoryId, item.lotId, formatPercent(item.defectRate), <StatusBadge value={item.result} />])} /></DataPanel>;
}

function MaintenancePanel({ events }: { events: MaintenanceEvent[] }) {
  return <DataPanel icon={<Wrench />} title="설비 정비 이벤트" count={events.length}><Table headers={['이벤트', '공장', '설비', '심각도', '원인', '상태']} rows={events.slice().reverse().map((item) => [item.id, item.factoryId, item.machineId, <StatusBadge value={item.severity} />, item.cause, item.status])} /></DataPanel>;
}

function LogisticsPanel({ shipments }: { shipments: LogisticsShipment[] }) {
  return <DataPanel icon={<Truck />} title="출하 관제" count={shipments.length}><Table headers={['출하', '공장', '도착지', '우선순위', '상태']} rows={shipments.slice().reverse().map((item) => [item.id, item.factoryId, item.destination, item.priority, <StatusBadge value={item.status} />])} /></DataPanel>;
}

function RpaPanel({ tasks, runAction }: { tasks: RpaTask[]; runAction: (action: () => Promise<unknown>) => Promise<void> }) {
  return <DataPanel icon={<ShieldCheck />} title="ArchiveOS RPA 작업" count={tasks.length}><div className="task-list">{tasks.length === 0 ? <EmptyState /> : tasks.slice().reverse().map((task) => <article key={task.id}><div className="card-heading"><div><strong>{task.id}</strong><span className="muted">{task.factoryId} · {formatDate(task.createdAt)}</span></div><StatusBadge value={task.status} /></div><p>{task.recommendation}</p>{task.status === 'APPROVAL_REQUIRED' && <div className="inline-actions"><button onClick={() => void runAction(() => api.approveRpa(task.id))}>Approve</button><button className="reject" onClick={() => void runAction(() => api.rejectRpa(task.id))}>Reject</button></div>}</article>)}</div></DataPanel>;
}

function SettingsPanel({ overview, batchSnapshots, archiveOsInteractions, persistence }: { overview: Overview; batchSnapshots: BatchSnapshot[]; archiveOsInteractions: ArchiveOsInteraction[]; persistence: SimulatorPersistenceStatus }) {
  const latestSnapshot = batchSnapshots[batchSnapshots.length - 1];
  return <div className="grid"><section className="panel"><PanelTitle icon={<Database />} title="런타임 저장소" /><dl className="summary-list"><Summary label="저장 모드" value={persistence.storageMode} /><Summary label="PostgreSQL" value={persistence.dbAvailable ? 'AVAILABLE' : 'UNAVAILABLE'} /><Summary label="파일 백업" value={persistence.fileSnapshotAvailable ? 'AVAILABLE' : 'EMPTY'} /><Summary label="복구 원본" value={persistence.restoredFrom} /><Summary label="최근 저장" value={formatDate(persistence.lastSavedAt ?? persistence.lastPersistedAt)} /></dl></section><section className="panel"><PanelTitle icon={<Activity />} title="Batch Snapshot" /><dl className="summary-list"><Summary label="스냅샷" value={`${batchSnapshots.length}개`} /><Summary label="최근 tick" value={String(latestSnapshot?.tick ?? 0)} /><Summary label="평균 불량률" value={formatPercent(latestSnapshot?.averageDefectRate ?? 0)} /><Summary label="승인 대기" value={`${latestSnapshot?.pendingApprovalCount ?? 0}건`} /><Summary label="시뮬레이터" value={overview.simulator.running ? 'RUNNING' : 'STOPPED'} /></dl></section><section className="panel full"><PanelTitle icon={<ShieldCheck />} title="ArchiveOS 상호작용" count={archiveOsInteractions.length} /><Table headers={['시각', '유형', '공장', '내용']} rows={archiveOsInteractions.slice(-10).reverse().map((item) => [formatDate(item.occurredAt), item.type, item.factoryId ?? 'ArchiveOS', item.payload])} /></section></div>;
}

function FactoryTable({ overview, operations }: { overview: Overview; operations: OperationsData }) {
  return <Table headers={['공장', '생산 오더', '품질 검사', '출하', '정비', '경보', '상태']} rows={overview.factories.map((factory) => {
    const count = <T extends { factoryId: string }>(items: T[]) => items.filter((item) => item.factoryId === factory.id).length;
    const factoryAlerts = overview.recentAlerts.filter((item) => item.factoryId === factory.id);
    const status = factoryAlerts.some((item) => item.severity === 'CRITICAL') ? 'CRITICAL' : factoryAlerts.length ? 'WARNING' : 'NORMAL';
    return [factory.name, count(operations.productionOrders), count(operations.qualityInspections), count(operations.logisticsShipments), count(operations.maintenanceEvents), factoryAlerts.length, <StatusBadge value={status} />];
  })} />;
}

function DataPanel({ icon, title, count, children }: { icon: ReactNode; title: string; count: number; children: ReactNode }) { return <section className="panel full"><PanelTitle icon={icon} title={title} count={count} />{children}</section>; }
function PanelTitle({ icon, title, count }: { icon: ReactNode; title: string; count?: number }) { return <div className="panel-title"><h2>{icon}{title}</h2>{count !== undefined && <span>{count}</span>}</div>; }
function Table({ headers, rows }: { headers: string[]; rows: ReactNode[][] }) { return rows.length === 0 ? <EmptyState /> : <div className="table-wrap"><table><thead><tr>{headers.map((header) => <th key={header}>{header}</th>)}</tr></thead><tbody>{rows.map((row, rowIndex) => <tr key={rowIndex}>{row.map((cell, cellIndex) => <td key={cellIndex}>{cell}</td>)}</tr>)}</tbody></table></div>; }
function EventList({ overview }: { overview: Overview }) { return overview.recentAlerts.length === 0 ? <EmptyState label="현재 감지된 이상 이벤트가 없습니다." /> : <div className="event-list">{overview.recentAlerts.map((alert) => <article key={alert.id}><StatusBadge value={alert.severity} /><div><strong>{alert.factoryId} · {alert.category}</strong><p>{alert.message}</p><small>{formatDate(alert.occurredAt)}</small></div></article>)}</div>; }
function StatusBadge({ value }: { value: string }) { const danger = ['CRITICAL', 'FAILED', 'REJECTED', 'DELAYED', 'HOLD', 'NG'].includes(value); const warning = ['WARNING', 'LOW', 'OPEN', 'APPROVAL_REQUIRED'].includes(value); return <span className={`status-badge ${danger ? 'danger' : warning ? 'warning' : 'success'}`}>{value}</span>; }
function Summary({ label, value }: { label: string; value: string }) { return <div><dt>{label}</dt><dd>{value}</dd></div>; }
function EmptyState({ label = '표시할 운영 데이터가 없습니다.' }: { label?: string }) { return <div className="empty-state">{label}</div>; }
function LoadingState() { return <div className="loading-state"><RefreshCw size={22} />운영 데이터를 불러오는 중입니다.</div>; }
function average(values: number[]) { return values.length ? values.reduce((sum, value) => sum + value, 0) / values.length : 0; }
function percent(value: number, total: number) { return total ? `${Math.round((value / total) * 100)}%` : '0%'; }
function formatPercent(value: number) { return `${(value * 100).toFixed(2)}%`; }
function formatTime(value: Date) { return new Intl.DateTimeFormat('ko-KR', { hour: '2-digit', minute: '2-digit', second: '2-digit' }).format(value); }
function formatDate(value?: string | null) { return value ? new Intl.DateTimeFormat('ko-KR', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }).format(new Date(value)) : '-'; }
