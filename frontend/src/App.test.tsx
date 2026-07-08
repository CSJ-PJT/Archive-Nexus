import { fireEvent, render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { App } from './App';

let archiveStatus = { status: 'DEGRADED', httpStatus: 200, message: 'ArchiveOS optional service unavailable', checkedAt: new Date().toISOString() };

vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
  const url = String(input);
  const body = ['/api/production/orders', '/api/quality/inspections', '/api/inventory/items',
    '/api/inventory/transactions', '/api/logistics/shipments', '/api/maintenance/events',
    '/api/rpa/tasks', '/api/tasks', '/api/batch/snapshots', '/api/archiveos/interactions', '/api/ai/queries'].some((path) => url.includes(path))
    ? []
    : url.includes('/api/ai/summary')
      ? { totalQueries: 0, runningAgents: 0, agentFailures: 0, agentRpaTasks: 0, recentRecommendation: '최근 권장 조치 없음' }
    : url.includes('/api/platform/manifest')
      ? {
          product: 'archive-nexus',
          displayName: 'Archive Nexus',
          productLine: 'Archive Suite',
          role: 'Manufacturing Industry Application',
          version: 'test',
          contractVersion: 'industry-app-contract/v1',
          environment: 'test',
          repository: 'https://github.com/CSJ-PJT/Archive-Nexus',
          summary: '제조 도메인 데이터와 운영 판단을 소유하는 ArchiveOS 위의 Industry App입니다.',
          capabilities: [{ id: 'workflow-contract', name: 'ArchiveOS Workflow Contract', description: 'contract', status: 'ACTIVE' }],
          contractEndpoints: [],
          dependencies: [],
          ownedDomains: [],
          operationalGuarantees: [],
          archiveOsStatus: archiveStatus,
          generatedAt: new Date().toISOString()
        }
    : url.includes('/api/archiveos/status')
      ? archiveStatus
    : url.includes('/api/simulator/persistence')
        ? {
            enabled: true,
            storageMode: 'postgresql',
            dbAvailable: true,
            fileSnapshotAvailable: true,
            stateFile: 'data/archive-nexus-state.json',
            snapshotExists: true,
            lastSavedAt: new Date().toISOString(),
            lastPersistedAt: new Date().toISOString(),
            restoredFrom: 'postgresql'
          }
        : {
          simulator: { running: true, tick: 1, factoryCount: 3, alertCount: 0, rpaTaskCount: 0, parallelWorkerCount: 3, updatedAt: new Date().toISOString() },
          factories: [],
          recentAlerts: [],
          pendingRpaTasks: [],
          kpis: {}
        };

  return {
    ok: true,
    json: async () => body
  };
}));

describe('App', () => {
  beforeEach(() => {
    archiveStatus = { status: 'DEGRADED', httpStatus: 200, message: 'ArchiveOS optional service unavailable', checkedAt: new Date().toISOString() };
  });
  it('renders Archive Nexus control surface', async () => {
    render(<App />);
    expect(await screen.findByText('Archive Nexus')).toBeInTheDocument();
    expect(screen.getAllByText('Overview').length).toBeGreaterThan(0);
    expect(await screen.findByText('공장 운영 현황')).toBeInTheDocument();
    expect(await screen.findByText('ArchiveOS DEGRADED')).toBeInTheDocument();
  });

  it('shows the Nexus platform contract surface', async () => {
    render(<App />);
    fireEvent.click(await screen.findByRole('button', { name: 'Settings' }));
    expect(await screen.findByText('Platform Contract')).toBeInTheDocument();
    expect(screen.getByText('Archive Suite')).toBeInTheDocument();
    expect(screen.getByText('ArchiveOS Workflow Contract')).toBeInTheDocument();
  });

  it('opens a domain operations view', async () => {
    render(<App />);
    fireEvent.click(await screen.findByRole('button', { name: 'Inventory' }));
    expect(await screen.findByText('재고 현황')).toBeInTheDocument();
    expect(screen.getByText('최근 입출고')).toBeInTheDocument();
  });

  it('renders the Manufacturing AI query workspace', async () => {
    render(<App />);
    fireEvent.click(await screen.findByRole('button', { name: 'Manufacturing AI' }));
    expect(await screen.findByText('Manufacturing Orchestrator')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '질문 실행' })).toBeInTheDocument();
    expect(screen.getByText('최근 Query History')).toBeInTheDocument();
  });
  it('renders the task execution and evidence workspace', async () => {
    render(<App />);
    fireEvent.click(await screen.findByRole('button', { name: 'Tasks' }));
    expect(await screen.findByText('운영 작업 생성')).toBeInTheDocument();
    expect(screen.getByText('작업 상세와 실행 근거')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '초안 만들기' })).toBeInTheDocument();
  });
  it('keeps operations visible when ArchiveOS is unavailable', async () => {
    archiveStatus = { status: 'UNAVAILABLE', httpStatus: 503, message: 'ArchiveOS is unreachable', checkedAt: new Date().toISOString() };
    render(<App />);
    expect(await screen.findByText('ArchiveOS UNAVAILABLE')).toBeInTheDocument();
    expect(screen.queryByText('운영 데이터를 불러오는 중입니다.')).not.toBeInTheDocument();
  });
});
