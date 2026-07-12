import {
  Activity, AlertTriangle, Bot, Boxes, CheckCircle2, CircleStop, Clock3, Database,
  Factory, Gauge, Globe2, Play, Plus, RefreshCw, ShieldCheck, Trash2, Truck, Wrench
} from 'lucide-react';
import type { ReactNode } from 'react';
import { useCallback, useEffect, useState } from 'react';
import { api } from './api';
import { languageOptions, useI18n } from './i18n';
import type { Locale } from './i18n/types';
import { ManufacturingAiPanel } from './ManufacturingAiPanel';
import { TaskOperationsPanel } from './TaskOperationsPanel';
import type {
  AiDashboardSummary, ArchiveOsInteraction, ArchiveOsStatus, BatchSnapshot, InventoryItem, InventoryTransaction,
  FactoryControlRequest, LogisticsShipment, MaintenanceEvent, NexusTask, Overview, PlatformManifest, ProductionOrder,
  QualityInspection, RpaTask, SimulatorPersistenceStatus, OperationsSummary
} from './types';

const tabs = ['Overview', 'Tasks', 'Manufacturing AI', 'Factories', 'Production', 'Inventory', 'Quality', 'Maintenance', 'Logistics', 'RPA', 'Settings'] as const;
type Tab = (typeof tabs)[number];

const tabLabels: Record<Tab, string> = {
  Overview: 'nav.overview',
  Tasks: 'nav.tasks',
  'Manufacturing AI': 'nav.manufacturingAi',
  Factories: 'nav.factories',
  Production: 'nav.production',
  Inventory: 'nav.inventory',
  Quality: 'nav.quality',
  Maintenance: 'nav.maintenance',
  Logistics: 'nav.logistics',
  RPA: 'nav.rpa',
  Settings: 'nav.settings'
};

type OperationsData = {
  productionOrders: ProductionOrder[];
  qualityInspections: QualityInspection[];
  inventoryItems: InventoryItem[];
  inventoryTransactions: InventoryTransaction[];
  logisticsShipments: LogisticsShipment[];
  maintenanceEvents: MaintenanceEvent[];
  rpaTasks: RpaTask[];
  tasks: NexusTask[];
};

const fallback: Overview = {
  simulator: { running: false, tick: 0, factoryCount: 0, alertCount: 0, rpaTaskCount: 0, parallelWorkerCount: 0, updatedAt: '' },
  factories: [], recentAlerts: [], pendingRpaTasks: [], kpis: {}
};
const emptyOperations: OperationsData = {
  productionOrders: [], qualityInspections: [], inventoryItems: [], inventoryTransactions: [],
  logisticsShipments: [], maintenanceEvents: [], rpaTasks: [], tasks: []
};
const fallbackPersistence: SimulatorPersistenceStatus = {
  enabled: false, storageMode: 'disabled', dbAvailable: false, fileSnapshotAvailable: false,
  stateFile: '', snapshotExists: false, lastSavedAt: null, lastPersistedAt: null, restoredFrom: 'seed'
};
const fallbackAiSummary: AiDashboardSummary = {
  totalQueries: 0, runningAgents: 0, agentFailures: 0, agentRpaTasks: 0, recentRecommendation: ''
};
const fallbackManifest: PlatformManifest = {
  product: 'archive-nexus',
  displayName: 'Archive Nexus',
  productLine: 'Archive Suite',
  role: 'Manufacturing Industry Application',
  version: 'unknown',
  contractVersion: 'industry-app-contract/v1',
  environment: 'local',
  repository: 'https://github.com/CSJ-PJT/Archive-Nexus',
  summary: '__ARCHIVE_NEXUS_FALLBACK_MANIFEST_SUMMARY__',
  capabilities: [],
  contractEndpoints: [],
  dependencies: [],
  ownedDomains: [],
  operationalGuarantees: [],
  archiveOsStatus: { status: 'UNAVAILABLE', httpStatus: null, message: 'Manifest status fallback', checkedAt: new Date().toISOString() },
  generatedAt: new Date().toISOString()
};

export function App() {
  const { locale, setLocale, t, formatTime } = useI18n();
  const [tab, setTab] = useState<Tab>('Overview');
  const [overview, setOverview] = useState<Overview>(fallback);
  const [operations, setOperations] = useState<OperationsData>(emptyOperations);
  const [batchSnapshots, setBatchSnapshots] = useState<BatchSnapshot[]>([]);
  const [archiveOsInteractions, setArchiveOsInteractions] = useState<ArchiveOsInteraction[]>([]);
  const [archiveOsStatus, setArchiveOsStatus] = useState<ArchiveOsStatus | null>(null);
  const [persistence, setPersistence] = useState<SimulatorPersistenceStatus>(fallbackPersistence);
  const [aiSummary, setAiSummary] = useState<AiDashboardSummary>(fallbackAiSummary);
  const [operationsSummary, setOperationsSummary] = useState<OperationsSummary | null>(null);
  const [platformManifest, setPlatformManifest] = useState<PlatformManifest>(fallbackManifest);
  const [loading, setLoading] = useState(true);
  const [actionPending, setActionPending] = useState(false);
  const [error, setError] = useState('');
  const [lastUpdatedAt, setLastUpdatedAt] = useState<Date | null>(null);

  const load = useCallback(async () => {
    try {
      const archiveOsStatusRequest = api.archiveOsStatus().catch((cause): ArchiveOsStatus => ({
        status: 'UNAVAILABLE',
        httpStatus: null,
        message: cause instanceof Error ? cause.message : t('error.archiveOsStatus'),
        checkedAt: new Date().toISOString()
      }));
      const platformManifestRequest = api.platformManifest().catch(() => fallbackManifest);
      const operationsSummaryRequest = api.operationsSummary().catch(() => null);
      const [nextOverview, productionOrders, qualityInspections, inventoryItems, inventoryTransactions,
        logisticsShipments, maintenanceEvents, rpaTasks, tasks, nextBatchSnapshots,
        nextArchiveOsInteractions, nextPersistence, nextAiSummary, nextArchiveOsStatus, nextPlatformManifest, nextOperationsSummary] = await Promise.all([
        api.overview(), api.productionOrders(), api.qualityInspections(), api.inventoryItems(),
        api.inventoryTransactions(), api.logisticsShipments(), api.maintenanceEvents(), api.rpaTasks(), api.tasks(),
        api.batchSnapshots(), api.archiveOsInteractions(), api.simulatorPersistence(), api.aiSummary(), archiveOsStatusRequest,
        platformManifestRequest, operationsSummaryRequest
      ]);
      setOverview(nextOverview);
      setOperations({ productionOrders, qualityInspections, inventoryItems, inventoryTransactions, logisticsShipments, maintenanceEvents, rpaTasks, tasks });
      setBatchSnapshots(nextBatchSnapshots);
      setArchiveOsInteractions(nextArchiveOsInteractions);
      setPersistence(nextPersistence);
      setAiSummary(nextAiSummary);
      setArchiveOsStatus(nextArchiveOsStatus);
      setPlatformManifest(nextPlatformManifest);
      setOperationsSummary(nextOperationsSummary);
      setLastUpdatedAt(new Date());
      setError('');
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : t('error.apiConnection'));
    } finally {
      setLoading(false);
    }
  }, [t]);

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
      setError(cause instanceof Error ? cause.message : t('error.actionFailed'));
    } finally {
      setActionPending(false);
    }
  };

  const criticalCount = overview.recentAlerts.filter((alert) => alert.severity === 'CRITICAL').length;
  const delayedCount = operations.logisticsShipments.filter((shipment) => shipment.status === 'DELAYED').length;
  const stockRiskCount = operations.inventoryItems.filter((item) => item.quantity <= item.safetyStock).length;

  return <main className="shell">
    <aside className="sidebar">
      <div className="brand"><img src="/archive-nexus-mark.svg" alt="" aria-hidden="true" /><div><strong>Archive Nexus</strong><span>Manufacturing AX</span></div></div>
      <nav aria-label={t('nav.aria')}>{tabs.map((item) => <button key={item} className={tab === item ? 'active' : ''} onClick={() => setTab(item)}>{t(tabLabels[item])}</button>)}</nav>
    </aside>
    <section className="workspace">
      <header className="topbar">
        <div className="topbar-title"><img src="/archive-nexus-mark.svg" alt="" aria-hidden="true" /><div><h1>{t(tabLabels[tab])}</h1><p>ArchiveOS Manufacturing AX · {lastUpdatedAt ? t('common.latestSync', { time: formatTime(lastUpdatedAt) }) : t('common.connectionPending')}</p></div><span className={`integration-state ${(archiveOsStatus?.status ?? 'CHECKING').toLowerCase()}`}>ArchiveOS {archiveOsStatus?.status ?? 'CHECKING'}</span></div>
        <div className="actions">
          <LanguageSelector value={locale} onChange={setLocale} />
          <button className="icon-button secondary" onClick={() => void load()} title={t('common.refresh')} aria-label={t('common.refresh')}><RefreshCw size={17} /></button>
          <button onClick={() => void runAction(api.startSimulator)} disabled={actionPending || overview.simulator.running}><Play size={17} />{t('common.start')}</button>
          <button className="stop" onClick={() => void runAction(api.stopSimulator)} disabled={actionPending || !overview.simulator.running}><CircleStop size={17} />{t('common.stop')}</button>
        </div>
      </header>
      {error && <div className="notice" role="alert">{error}</div>}
      {loading ? <LoadingState /> : <>
        <section className="metrics" aria-label={t('metric.aria')}>
          <Metric icon={<Activity />} label={t('metric.tick')} value={overview.simulator.tick} />
          <Metric icon={<Factory />} label={t('metric.factories')} value={overview.simulator.factoryCount} />
          <Metric icon={<Gauge />} label={t('metric.workers')} value={overview.simulator.parallelWorkerCount} />
          <Metric icon={<AlertTriangle />} label={t('metric.critical')} value={criticalCount} tone={criticalCount ? 'danger' : 'normal'} />
          <Metric icon={<Boxes />} label={t('metric.stockRisks')} value={stockRiskCount} tone={stockRiskCount ? 'warning' : 'normal'} />
          <Metric icon={<Truck />} label={t('metric.delayed')} value={delayedCount} tone={delayedCount ? 'warning' : 'normal'} />
        </section>
        {tab === 'Overview' && <OverviewPanel overview={overview} operations={operations} aiSummary={aiSummary} operationsSummary={operationsSummary} />}
        {tab === 'Tasks' && <TaskOperationsPanel factories={overview.factories} tasks={operations.tasks} onChanged={load} />}
        {tab === 'Manufacturing AI' && <ManufacturingAiPanel factories={overview.factories} onChanged={load} />}
        {tab === 'Factories' && <FactoriesPanel overview={overview} operations={operations} runAction={runAction} />}
        {tab === 'Production' && <ProductionPanel orders={operations.productionOrders} />}
        {tab === 'Inventory' && <InventoryPanel items={operations.inventoryItems} transactions={operations.inventoryTransactions} />}
        {tab === 'Quality' && <QualityPanel inspections={operations.qualityInspections} />}
        {tab === 'Maintenance' && <MaintenancePanel events={operations.maintenanceEvents} />}
        {tab === 'Logistics' && <LogisticsPanel shipments={operations.logisticsShipments} />}
        {tab === 'RPA' && <RpaPanel tasks={operations.rpaTasks} runAction={runAction} />}
        {tab === 'Settings' && <SettingsPanel overview={overview} batchSnapshots={batchSnapshots} archiveOsInteractions={archiveOsInteractions} persistence={persistence} archiveOsStatus={archiveOsStatus} manifest={platformManifest} />}
      </>}
    </section>
  </main>;
}

function Metric({ icon, label, value, tone = 'normal' }: { icon: ReactNode; label: string; value: number; tone?: 'normal' | 'warning' | 'danger' }) {
  return <div className={`metric ${tone}`}><span>{icon}</span><small>{label}</small><strong>{value}</strong></div>;
}

function DashboardCard({ label, value, detail, tone = 'normal' }: { label: string; value: string; detail?: string; tone?: 'normal' | 'good' | 'warning' | 'danger' }) {
  return <article className={`dashboard-card ${tone}`}><small>{label}</small><strong>{value}</strong>{detail && <span>{detail}</span>}</article>;
}

function LanguageSelector({ value, onChange }: { value: Locale; onChange: (value: Locale | string) => void }) {
  const { t } = useI18n();
  return <label className="language-selector" title={t('common.language')}><Globe2 size={15} aria-hidden="true" /><select aria-label={t('common.language')} value={value} onChange={(event) => onChange(event.target.value)}>{languageOptions.map((option) => <option key={option.code} value={option.code}>{option.label}</option>)}</select></label>;
}

function OverviewPanel({ overview, operations, aiSummary, operationsSummary }: { overview: Overview; operations: OperationsData; aiSummary: AiDashboardSummary; operationsSummary: OperationsSummary | null }) {
  const { t } = useI18n();
  const totalTarget = operations.productionOrders.reduce((sum, order) => sum + order.targetQuantity, 0);
  const totalProduced = operations.productionOrders.reduce((sum, order) => sum + order.producedQuantity, 0);
  const avgDefect = average(operations.qualityInspections.map((item) => item.defectRate));
  const openMaintenance = operations.maintenanceEvents.filter((item) => item.status === 'OPEN').length;
  const production = operationsSummary?.production;
  const economy = operationsSummary?.economy;
  const workforce = operationsSummary?.workforce;
  const outbox = operationsSummary?.outbox;
  const runtime = operationsSummary?.runtime;
  const productionRequested = production?.productionRequested ?? production?.requested;
  const productionCompleted = production?.productionCompleted ?? production?.completed;
  const productionBacklog = production?.productionBacklog ?? production?.backlog;
  return <div className="grid dashboard-grid">
    <section className="panel full runtime-dashboard"><PanelTitle icon={<Activity />} title={t('dashboard.runtime')} /><div className="dashboard-cards">
      <DashboardCard label={t('dashboard.pipeline')} value={runtime?.pipelineStatus ?? t('common.noData')} tone={runtime?.pipelineStatus === 'LIVE' ? 'good' : 'warning'} />
      <DashboardCard label={t('dashboard.production')} value={production ? `${productionCompleted ?? 0} / ${productionRequested ?? 0}` : t('common.noData')} detail={`${t('dashboard.backlog')} ${productionBacklog ?? '-'} · ${t('dashboard.capacity')} ${formatMetricPercent(production?.capacityUtilization)}`} />
      <DashboardCard label={t('dashboard.workforce')} value={workforce ? `${workforce.usedCapacity} / ${workforce.effectiveCapacity}` : t('common.noData')} detail={`${t('dashboard.bottleneck')} ${workforce?.bottleneckRole ?? '-'}`} />
      <DashboardCard label={t('dashboard.outbox')} value={outbox ? `${outbox.pending} ${t('dashboard.pending')}` : t('common.noData')} detail={outbox ? `${t('dashboard.published')} ${outbox.published} · ${t('dashboard.retry')} ${outbox.retry} · ${t('dashboard.failed')} ${outbox.failed}` : undefined} tone={outbox?.failed ? 'danger' : outbox?.retry ? 'warning' : 'good'} />
      <DashboardCard label={t('dashboard.operatingProfit')} value={economy?.available ? formatAmount(economy.operatingProfit) : t('common.noData')} detail={economy?.available ? `${t('dashboard.revenue')} ${formatAmount(economy.manufacturingRevenue)} · ${t('dashboard.cost')} ${formatAmount(economy.totalCost)} · ${t('dashboard.margin')} ${formatMetricPercent(economy.operatingMargin)}` : undefined} tone={(economy?.operatingProfit ?? 0) < 0 ? 'danger' : 'good'} />
    </div></section>
    <section className="panel wide"><PanelTitle icon={<AlertTriangle />} title={t('overview.recentAlerts')} count={overview.recentAlerts.length} /><EventList overview={overview} /></section>
    <section className="panel"><PanelTitle icon={<Activity />} title={t('overview.operationalState')} /><p className={overview.simulator.running ? 'state on' : 'state'}>{overview.simulator.running ? 'RUNNING' : 'STOPPED'}</p><dl className="summary-list"><Summary label={t('overview.productionAchievement')} value={percent(totalProduced, totalTarget)} /><Summary label={t('overview.averageDefectRate')} value={formatPercent(avgDefect)} /><Summary label={t('overview.openMaintenance')} value={t('common.count', { count: openMaintenance })} /><Summary label={t('overview.pendingApproval')} value={t('common.count', { count: overview.pendingRpaTasks.length })} /></dl></section>
    <section className="panel full"><PanelTitle icon={<Factory />} title={t('overview.factoryOperations')} /><FactoryTable overview={overview} operations={operations} /></section>
    <section className="panel full ai-overview"><PanelTitle icon={<Bot />} title="Manufacturing AI" /><div className="ai-overview-stats"><Summary label={t('overview.recentAiQuery')} value={t('common.count', { count: aiSummary.totalQueries })} /><Summary label={t('overview.runningAgents')} value={t('common.units.agent', { count: aiSummary.runningAgents })} /><Summary label={t('overview.agentFailures')} value={t('common.count', { count: aiSummary.agentFailures })} /><Summary label={t('overview.agentRpaTasks')} value={t('common.count', { count: aiSummary.agentRpaTasks })} /></div><p><strong>{t('overview.recentRecommendation')}</strong> · {aiSummary.recentRecommendation || t('overview.noRecommendation')}</p></section>
  </div>;
}

function FactoriesPanel({ overview, operations, runAction }: { overview: Overview; operations: OperationsData; runAction: (action: () => Promise<unknown>) => Promise<void> }) {
  const { t } = useI18n();
  const [form, setForm] = useState<FactoryControlRequest>({ id: '', name: '', kind: 'AUTOMOTIVE_PARTS', product: '', scenario: '', initialInventory: 2000, safetyStock: 800 });
  const update = <K extends keyof FactoryControlRequest>(key: K, value: FactoryControlRequest[K]) => setForm((current) => ({ ...current, [key]: value }));
  const createFactory = () => runAction(() => api.addFactory(form));
  const removeFactory = (factoryId: string) => runAction(() => api.removeFactory(factoryId));
  return <div className="grid">
    <section className="panel full"><PanelTitle icon={<Factory />} title={t('factories.control')} count={overview.factories.length} /><div className="factory-control-form">
      <label>{t('factories.id')}<input value={form.id} placeholder={t('factories.autoId')} onChange={(event) => update('id', event.target.value)} /></label>
      <label>{t('factories.name')}<input value={form.name} placeholder={t('factories.namePlaceholder')} onChange={(event) => update('name', event.target.value)} /></label>
      <label>{t('factories.kind')}<select value={form.kind} onChange={(event) => update('kind', event.target.value as FactoryControlRequest['kind'])}><option value="AUTOMOTIVE_PARTS">AUTOMOTIVE_PARTS</option><option value="BATTERY_MODULE">BATTERY_MODULE</option><option value="ELECTRONICS">ELECTRONICS</option></select></label>
      <label>{t('factories.product')}<input value={form.product} placeholder={t('factories.productPlaceholder')} onChange={(event) => update('product', event.target.value)} /></label>
      <label>{t('factories.initialInventory')}<input type="number" min="1" value={form.initialInventory} onChange={(event) => update('initialInventory', Number(event.target.value))} /></label>
      <label>{t('factories.safetyStock')}<input type="number" min="1" value={form.safetyStock} onChange={(event) => update('safetyStock', Number(event.target.value))} /></label>
      <label className="wide-field">{t('factories.scenario')}<input value={form.scenario} placeholder={t('factories.scenarioPlaceholder')} onChange={(event) => update('scenario', event.target.value)} /></label>
      <button onClick={() => void createFactory()}><Plus size={15} />{t('factories.add')}</button>
    </div></section>
    <section className="panel full"><PanelTitle icon={<Factory />} title={t('factories.active')} count={overview.factories.length} /><div className="factory-list">{overview.factories.map((factory) => {
      const alerts = overview.recentAlerts.filter((item) => item.factoryId === factory.id);
      const orders = operations.productionOrders.filter((item) => item.factoryId === factory.id);
      const produced = orders.reduce((sum, item) => sum + item.producedQuantity, 0);
      const target = orders.reduce((sum, item) => sum + item.targetQuantity, 0);
      return <article className="panel" key={factory.id}><div className="card-heading"><div><h2>{factory.name}</h2><span className="muted">{factory.id} / {factory.kind}</span></div><StatusBadge value={alerts.some((item) => item.severity === 'CRITICAL') ? 'CRITICAL' : alerts.length ? 'WARNING' : 'NORMAL'} /></div><p>{factory.scenario}</p><dl className="summary-list"><Summary label={t('factories.production')} value={percent(produced, target)} /><Summary label={t('factories.alerts')} value={`${alerts.length}`} /><Summary label={t('factories.lines')} value={`${factory.lines.length}`} /><Summary label={t('factories.product')} value={factory.lines[0]?.product ?? '-'} /></dl><div className="inline-actions factory-card-actions"><button className="reject" disabled={overview.factories.length <= 1} onClick={() => void removeFactory(factory.id)}><Trash2 size={15} />{t('factories.remove')}</button></div></article>;
    })}</div></section>
  </div>;
}

function ProductionPanel({ orders }: { orders: ProductionOrder[] }) {
  const { t } = useI18n();
  return <DataPanel icon={<Gauge />} title={t('production.orders')} count={orders.length}><Table headers={[t('table.order'), t('table.factory'), t('table.product'), t('table.target'), t('table.actual'), t('table.achievement'), t('table.status')]} rows={orders.slice().reverse().map((order) => [order.id, order.factoryId, order.product, order.targetQuantity, order.producedQuantity, percent(order.producedQuantity, order.targetQuantity), <StatusBadge value={order.status} />])} /></DataPanel>;
}

function InventoryPanel({ items, transactions }: { items: InventoryItem[]; transactions: InventoryTransaction[] }) {
  const { t, formatDate } = useI18n();
  return <div className="grid"><DataPanel icon={<Boxes />} title={t('inventory.status')} count={items.length}><Table headers={[t('table.item'), t('table.type'), t('table.currentStock'), t('table.safetyStock'), t('table.status')]} rows={items.map((item) => [item.name, item.type, item.quantity, item.safetyStock, <StatusBadge value={item.quantity <= item.safetyStock ? 'LOW' : 'NORMAL'} />])} /></DataPanel><DataPanel icon={<Clock3 />} title={t('inventory.recentTransactions')} count={transactions.length}><Table headers={[t('table.factory'), t('table.item'), t('table.type'), t('table.quantity'), t('table.occurredAt')]} rows={transactions.slice(-8).reverse().map((item) => [item.factoryId, item.itemId, item.type, item.quantity, formatDate(item.occurredAt)])} /></DataPanel></div>;
}

function QualityPanel({ inspections }: { inspections: QualityInspection[] }) {
  const { t } = useI18n();
  return <DataPanel icon={<CheckCircle2 />} title={t('quality.inspections')} count={inspections.length}><Table headers={[t('table.inspection'), t('table.factory'), 'Lot', t('table.result'), t('table.status')]} rows={inspections.slice().reverse().map((item) => [item.id, item.factoryId, item.lotId, formatPercent(item.defectRate), <StatusBadge value={item.result} />])} /></DataPanel>;
}

function MaintenancePanel({ events }: { events: MaintenanceEvent[] }) {
  const { t } = useI18n();
  return <DataPanel icon={<Wrench />} title={t('maintenance.events')} count={events.length}><Table headers={[t('table.event'), t('table.factory'), t('table.equipment'), t('table.severity'), t('table.cause'), t('table.status')]} rows={events.slice().reverse().map((item) => [item.id, item.factoryId, item.machineId, <StatusBadge value={item.severity} />, item.cause, <StatusBadge value={item.status} />])} /></DataPanel>;
}

function LogisticsPanel({ shipments }: { shipments: LogisticsShipment[] }) {
  const { t } = useI18n();
  return <DataPanel icon={<Truck />} title={t('logistics.control')} count={shipments.length}><Table headers={[t('table.shipment'), t('table.factory'), t('table.destination'), t('table.priority'), t('table.status')]} rows={shipments.slice().reverse().map((item) => [item.id, item.factoryId, item.destination, item.priority, <StatusBadge value={item.status} />])} /></DataPanel>;
}

function RpaPanel({ tasks, runAction }: { tasks: RpaTask[]; runAction: (action: () => Promise<unknown>) => Promise<void> }) {
  const { t, formatDate } = useI18n();
  return <DataPanel icon={<ShieldCheck />} title={t('rpa.tasks')} count={tasks.length}><div className="task-list">{tasks.length === 0 ? <EmptyState /> : tasks.slice().reverse().map((task) => <article key={task.id}><div className="card-heading"><div><strong>{task.id}</strong><span className="muted">{task.factoryId} · {formatDate(task.createdAt)}</span></div><StatusBadge value={task.status} /></div><p>{task.recommendation}</p>{task.status === 'APPROVAL_REQUIRED' && <div className="inline-actions"><button onClick={() => void runAction(() => api.approveRpa(task.id))}>{t('common.approve')}</button><button className="reject" onClick={() => void runAction(() => api.rejectRpa(task.id))}>{t('common.reject')}</button></div>}</article>)}</div></DataPanel>;
}

function SettingsPanel({ overview, batchSnapshots, archiveOsInteractions, persistence, archiveOsStatus, manifest }: { overview: Overview; batchSnapshots: BatchSnapshot[]; archiveOsInteractions: ArchiveOsInteraction[]; persistence: SimulatorPersistenceStatus; archiveOsStatus: ArchiveOsStatus | null; manifest: PlatformManifest }) {
  const { t, formatDate } = useI18n();
  const latestSnapshot = batchSnapshots[batchSnapshots.length - 1];
  const manifestSummary = manifest.summary === fallbackManifest.summary ? t('settings.fallbackManifestSummary') : manifest.summary;
  return <div className="grid"><section className="panel full"><PanelTitle icon={<ShieldCheck />} title={t('settings.platformContract')} /><p>{manifestSummary}</p><dl className="summary-list"><Summary label={t('settings.productLine')} value={manifest.productLine} /><Summary label={t('settings.role')} value={manifest.role} /><Summary label={t('settings.contractVersion')} value={manifest.contractVersion} /><Summary label={t('settings.environment')} value={manifest.environment} /></dl><div className="tag-row">{manifest.capabilities.slice(0, 6).map((capability) => <span className="agent-tag" key={capability.id}>{capability.name}</span>)}</div></section><section className="panel"><PanelTitle icon={<Database />} title={t('settings.runtimeStorage')} /><dl className="summary-list"><Summary label={t('settings.storageMode')} value={persistence.storageMode} /><Summary label="PostgreSQL" value={persistence.dbAvailable ? 'AVAILABLE' : 'UNAVAILABLE'} /><Summary label={t('settings.fileBackup')} value={persistence.fileSnapshotAvailable ? 'AVAILABLE' : 'EMPTY'} /><Summary label={t('settings.restoreSource')} value={persistence.restoredFrom} /><Summary label={t('settings.lastSaved')} value={formatDate(persistence.lastSavedAt ?? persistence.lastPersistedAt)} /></dl></section><section className="panel"><PanelTitle icon={<Activity />} title={t('settings.batchSnapshot')} /><dl className="summary-list"><Summary label={t('settings.snapshot')} value={t('common.units.agent', { count: batchSnapshots.length })} /><Summary label={t('settings.latestTick')} value={String(latestSnapshot?.tick ?? 0)} /><Summary label={t('overview.averageDefectRate')} value={formatPercent(latestSnapshot?.averageDefectRate ?? 0)} /><Summary label={t('overview.pendingApproval')} value={t('common.count', { count: latestSnapshot?.pendingApprovalCount ?? 0 })} /><Summary label={t('settings.simulator')} value={overview.simulator.running ? 'RUNNING' : 'STOPPED'} /></dl></section><section className="panel full"><PanelTitle icon={<ShieldCheck />} title={t('settings.archiveOsStatus')} /><div className={`integration-detail ${(archiveOsStatus?.status ?? 'UNAVAILABLE').toLowerCase()}`}><StatusBadge value={archiveOsStatus?.status ?? 'CHECKING'} /><div><strong>{archiveOsStatus?.message ?? t('settings.archiveOsChecking')}</strong><small>{archiveOsStatus?.checkedAt ? t('settings.latestCheck', { time: formatDate(archiveOsStatus.checkedAt) }) : t('settings.checkPending')}</small></div></div></section><section className="panel full"><PanelTitle icon={<ShieldCheck />} title={t('settings.archiveOsInteractions')} count={archiveOsInteractions.length} /><Table headers={[t('table.time'), t('table.type'), t('table.factory'), t('table.content')]} rows={archiveOsInteractions.slice(-10).reverse().map((item) => [formatDate(item.occurredAt), item.type, item.factoryId ?? 'ArchiveOS', item.payload])} /></section></div>;
}

function FactoryTable({ overview, operations }: { overview: Overview; operations: OperationsData }) {
  const { t } = useI18n();
  return <Table headers={[t('table.factory'), t('production.orders'), t('quality.inspections'), t('table.shipment'), t('table.maintenance'), t('table.alert'), t('table.status')]} rows={overview.factories.map((factory) => {
    const count = <T extends { factoryId: string }>(items: T[]) => items.filter((item) => item.factoryId === factory.id).length;
    const factoryAlerts = overview.recentAlerts.filter((item) => item.factoryId === factory.id);
    const status = factoryAlerts.some((item) => item.severity === 'CRITICAL') ? 'CRITICAL' : factoryAlerts.length ? 'WARNING' : 'NORMAL';
    return [factory.name, count(operations.productionOrders), count(operations.qualityInspections), count(operations.logisticsShipments), count(operations.maintenanceEvents), factoryAlerts.length, <StatusBadge value={status} />];
  })} />;
}

function DataPanel({ icon, title, count, children }: { icon: ReactNode; title: string; count: number; children: ReactNode }) { return <section className="panel full"><PanelTitle icon={icon} title={title} count={count} />{children}</section>; }
function PanelTitle({ icon, title, count }: { icon: ReactNode; title: string; count?: number }) { return <div className="panel-title"><h2>{icon}{title}</h2>{count !== undefined && <span>{count}</span>}</div>; }
function Table({ headers, rows }: { headers: string[]; rows: ReactNode[][] }) { return rows.length === 0 ? <EmptyState /> : <div className="table-wrap"><table><thead><tr>{headers.map((header) => <th key={header}>{header}</th>)}</tr></thead><tbody>{rows.map((row, rowIndex) => <tr key={rowIndex}>{row.map((cell, cellIndex) => <td key={cellIndex}>{cell}</td>)}</tr>)}</tbody></table></div>; }
function EventList({ overview }: { overview: Overview }) { const { t, formatDate } = useI18n(); return overview.recentAlerts.length === 0 ? <EmptyState label={t('overview.noAlerts')} /> : <div className="event-list">{overview.recentAlerts.map((alert) => <article key={alert.id}><StatusBadge value={alert.severity} /><div><strong>{alert.factoryId} · {alert.category}</strong><p>{alert.message}</p><small>{formatDate(alert.occurredAt)}</small></div></article>)}</div>; }
function StatusBadge({ value }: { value: string }) { const { statusLabel } = useI18n(); const danger = ['CRITICAL', 'FAILED', 'REJECTED', 'DELAYED', 'HOLD', 'NG'].includes(value); const warning = ['WARNING', 'LOW', 'OPEN', 'APPROVAL_REQUIRED', 'PENDING', 'DRAFT', 'ANALYZING', 'WAITING_APPROVAL', 'RUNNING', 'VERIFYING', 'RETRY_REQUESTED', 'CANCELLED'].includes(value); return <span className={`status-badge ${danger ? 'danger' : warning ? 'warning' : 'success'}`} title={value}>{statusLabel(value)}</span>; }
function Summary({ label, value }: { label: string; value: string }) { return <div><dt>{label}</dt><dd>{value}</dd></div>; }
function EmptyState({ label }: { label?: string }) { const { t } = useI18n(); return <div className="empty-state">{label ?? t('common.noData')}</div>; }
function LoadingState() { const { t } = useI18n(); return <div className="loading-state"><RefreshCw size={22} />{t('common.loading')}</div>; }
function average(values: number[]) { return values.length ? values.reduce((sum, value) => sum + value, 0) / values.length : 0; }
function percent(value: number, total: number) { return total ? `${Math.round((value / total) * 100)}%` : '0%'; }
function formatPercent(value: number) { return `${(value * 100).toFixed(2)}%`; }
function formatMetricPercent(value: number | null | undefined) { return value == null ? '-' : `${Number(value).toFixed(2)}%`; }
function formatAmount(value: number | null | undefined) { return value == null ? '-' : `${Math.round(value).toLocaleString()} KRW`; }
