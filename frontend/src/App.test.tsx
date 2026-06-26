import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { App } from './App';

vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
  const url = String(input);
  const body = ['/api/production/orders', '/api/quality/inspections', '/api/inventory/items',
    '/api/inventory/transactions', '/api/logistics/shipments', '/api/maintenance/events',
    '/api/rpa/tasks', '/api/batch/snapshots', '/api/archiveos/interactions'].some((path) => url.includes(path))
    ? []
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
  it('renders Archive Nexus control surface', async () => {
    render(<App />);
    expect(await screen.findByText('Archive Nexus')).toBeInTheDocument();
    expect(screen.getAllByText('Overview').length).toBeGreaterThan(0);
    expect(await screen.findByText('공장 운영 현황')).toBeInTheDocument();
  });

  it('opens a domain operations view', async () => {
    render(<App />);
    fireEvent.click(await screen.findByRole('button', { name: 'Inventory' }));
    expect(await screen.findByText('재고 현황')).toBeInTheDocument();
    expect(screen.getByText('최근 입출고')).toBeInTheDocument();
  });
});
