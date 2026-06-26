import { Activity, AlertTriangle, Boxes, CheckCircle2, Factory, Gauge, Play, ShieldCheck, Truck, Wrench } from 'lucide-react';
import type { ReactNode } from 'react';
import { useEffect, useMemo, useState } from 'react';
import { api } from './api';
import type { ArchiveOsInteraction, BatchSnapshot, Overview } from './types';

const tabs = ['Overview', 'Factories', 'Inventory', 'Quality', 'Maintenance', 'Logistics', 'RPA', 'Settings'] as const;
type Tab = (typeof tabs)[number];

const fallback: Overview = {
  simulator: { running: false, tick: 0, factoryCount: 0, alertCount: 0, rpaTaskCount: 0, updatedAt: '' },
  factories: [],
  recentAlerts: [],
  pendingRpaTasks: [],
  kpis: {}
};

export function App() {
  const [tab, setTab] = useState<Tab>('Overview');
  const [overview, setOverview] = useState<Overview>(fallback);
  const [batchSnapshots, setBatchSnapshots] = useState<BatchSnapshot[]>([]);
  const [archiveOsInteractions, setArchiveOsInteractions] = useState<ArchiveOsInteraction[]>([]);
  const [error, setError] = useState('');

  const load = async () => {
    try {
      const [nextOverview, nextBatchSnapshots, nextArchiveOsInteractions] = await Promise.all([
        api.overview(),
        api.batchSnapshots(),
        api.archiveOsInteractions()
      ]);
      setOverview(nextOverview);
      setBatchSnapshots(nextBatchSnapshots);
      setArchiveOsInteractions(nextArchiveOsInteractions);
      setError('');
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'API 연결 실패');
    }
  };

  useEffect(() => {
    load();
    const timer = window.setInterval(load, 5000);
    return () => window.clearInterval(timer);
  }, []);

  const criticalCount = useMemo(() => overview.recentAlerts.filter((alert) => alert.severity === 'CRITICAL').length, [overview]);

  const start = async () => {
    await api.startSimulator();
    await load();
  };

  const stop = async () => {
    await api.stopSimulator();
    await load();
  };

  return (
    <main className="shell">
      <aside className="sidebar">
        <div className="brand">
          <Factory size={28} />
          <div>
            <strong>Archive Nexus</strong>
            <span>Manufacturing AX</span>
          </div>
        </div>
        <nav>
          {tabs.map((item) => (
            <button key={item} className={tab === item ? 'active' : ''} onClick={() => setTab(item)}>
              {item}
            </button>
          ))}
        </nav>
      </aside>

      <section className="workspace">
        <header className="topbar">
          <div>
            <h1>{tab}</h1>
            <p>ArchiveOS 위에서 동작하는 제조 AX 시뮬레이션 관제</p>
          </div>
          <div className="actions">
            <button onClick={start} title="시뮬레이터 시작"><Play size={17} />Start</button>
            <button onClick={stop} title="시뮬레이터 정지">Stop</button>
          </div>
        </header>

        {error && <div className="notice">{error}</div>}

        <section className="metrics">
          <Metric icon={<Activity />} label="Tick" value={overview.simulator.tick} />
          <Metric icon={<Factory />} label="Factories" value={overview.simulator.factoryCount} />
          <Metric icon={<AlertTriangle />} label="Alerts" value={overview.simulator.alertCount} />
          <Metric icon={<ShieldCheck />} label="RPA Tasks" value={overview.simulator.rpaTaskCount} />
          <Metric icon={<Gauge />} label="Critical" value={criticalCount} />
        </section>

        {tab === 'Overview' && <OverviewPanel overview={overview} />}
        {tab === 'Factories' && <FactoriesPanel overview={overview} />}
        {tab === 'Inventory' && <DomainPanel icon={<Boxes />} title="Inventory Hub" rows={['원자재/반제품/완제품 재고', '안전재고 경고', '입출고 이력', '공장 간 이동']} />}
        {tab === 'Quality' && <DomainPanel icon={<CheckCircle2 />} title="Quality System" rows={['Lot 검사 결과', '불량률 추이', '출하 보류', '재작업 대상']} />}
        {tab === 'Maintenance' && <DomainPanel icon={<Wrench />} title="Maintenance System" rows={['설비 이상', '정비 이력', '예지보전 후보', '고장 위험도']} />}
        {tab === 'Logistics' && <DomainPanel icon={<Truck />} title="Logistics Hub" rows={['출하 상태', '공장 간 이동', '납기 지연', '출하 우선순위']} />}
        {tab === 'RPA' && <RpaPanel overview={overview} reload={load} />}
        {tab === 'Settings' && <SettingsPanel overview={overview} batchSnapshots={batchSnapshots} archiveOsInteractions={archiveOsInteractions} />}
      </section>
    </main>
  );
}

function Metric({ icon, label, value }: { icon: ReactNode; label: string; value: number }) {
  return (
    <div className="metric">
      <span>{icon}</span>
      <small>{label}</small>
      <strong>{value}</strong>
    </div>
  );
}

function OverviewPanel({ overview }: { overview: Overview }) {
  return (
    <div className="grid">
      <div className="panel wide">
        <h2>최근 이상 이벤트</h2>
        <EventList overview={overview} />
      </div>
      <div className="panel">
        <h2>운영 상태</h2>
        <p className={overview.simulator.running ? 'state on' : 'state'}>{overview.simulator.running ? 'RUNNING' : 'STOPPED'}</p>
        <p>시뮬레이터는 시작 후 주기적으로 데이터를 생성한다.</p>
      </div>
    </div>
  );
}

function FactoriesPanel({ overview }: { overview: Overview }) {
  return (
    <div className="factory-list">
      {overview.factories.map((factory) => (
        <article className="panel" key={factory.id}>
          <h2>{factory.name}</h2>
          <p>{factory.scenario}</p>
          <strong>{factory.lines[0]?.product}</strong>
          <small>{factory.lines[0]?.machines[0]?.name}</small>
        </article>
      ))}
    </div>
  );
}

function DomainPanel({ icon, title, rows }: { icon: ReactNode; title: string; rows: string[] }) {
  return (
    <div className="panel wide">
      <h2>{icon}{title}</h2>
      <div className="rows">{rows.map((row) => <span key={row}>{row}</span>)}</div>
    </div>
  );
}

function RpaPanel({ overview, reload }: { overview: Overview; reload: () => Promise<void> }) {
  return (
    <div className="panel wide">
      <h2>승인 대기 RPA</h2>
      <div className="task-list">
        {overview.pendingRpaTasks.map((task) => (
          <article key={task.id}>
            <strong>{task.id}</strong>
            <p>{task.recommendation}</p>
            <button onClick={async () => { await api.approveRpa(task.id); await reload(); }}>Approve</button>
            <button onClick={async () => { await api.rejectRpa(task.id); await reload(); }}>Reject</button>
          </article>
        ))}
      </div>
    </div>
  );
}

function SettingsPanel({ overview, batchSnapshots, archiveOsInteractions }: { overview: Overview; batchSnapshots: BatchSnapshot[]; archiveOsInteractions: ArchiveOsInteraction[] }) {
  const latestSnapshot = batchSnapshots[batchSnapshots.length - 1];
  const latestInteractions = archiveOsInteractions.slice(-5).reverse();

  return (
    <div className="grid">
      <div className="panel">
        <h2>시뮬레이터 설정</h2>
        <div className="rows">
          <span>상태: {overview.simulator.running ? '실행 중' : '정지'}</span>
          <span>데이터 생성 주기: 5초</span>
          <span>ArchiveOS adapter: mock</span>
          <span>seed: 20260626</span>
        </div>
      </div>
      <div className="panel">
        <h2>Batch Snapshot</h2>
        <div className="rows">
          <span>스냅샷 수: {batchSnapshots.length}</span>
          <span>최근 tick: {latestSnapshot?.tick ?? 0}</span>
          <span>평균 불량률: {latestSnapshot?.averageDefectRate ?? 0}</span>
          <span>승인 대기: {latestSnapshot?.pendingApprovalCount ?? 0}</span>
        </div>
      </div>
      <div className="panel wide">
        <h2>ArchiveOS Interactions</h2>
        <div className="event-list">
          {latestInteractions.map((item) => (
            <article key={item.id}>
              <span className="badge info">{item.type}</span>
              <strong>{item.factoryId ?? 'ArchiveOS'} · {item.id}</strong>
              <p>{item.payload}</p>
            </article>
          ))}
        </div>
      </div>
    </div>
  );
}

function EventList({ overview }: { overview: Overview }) {
  return (
    <div className="event-list">
      {overview.recentAlerts.map((alert) => (
        <article key={alert.id}>
          <span className={`badge ${alert.severity.toLowerCase()}`}>{alert.severity}</span>
          <strong>{alert.factoryId} · {alert.category}</strong>
          <p>{alert.message}</p>
        </article>
      ))}
    </div>
  );
}
