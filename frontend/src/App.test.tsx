import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { App } from './App';

vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
  const url = String(input);
  const body = url.includes('/api/batch/snapshots')
    ? []
    : url.includes('/api/archiveos/interactions')
      ? []
      : {
          simulator: { running: true, tick: 1, factoryCount: 3, alertCount: 0, rpaTaskCount: 0, updatedAt: new Date().toISOString() },
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
  });
});
