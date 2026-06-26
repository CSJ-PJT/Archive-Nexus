import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { App } from './App';

vi.stubGlobal('fetch', vi.fn(async () => ({
  ok: true,
  json: async () => ({
    simulator: { running: true, tick: 1, factoryCount: 3, alertCount: 0, rpaTaskCount: 0, updatedAt: new Date().toISOString() },
    factories: [],
    recentAlerts: [],
    pendingRpaTasks: [],
    kpis: {}
  })
})));

describe('App', () => {
  it('renders Archive Nexus control surface', async () => {
    render(<App />);
    expect(await screen.findByText('Archive Nexus')).toBeInTheDocument();
    expect(screen.getAllByText('Overview').length).toBeGreaterThan(0);
  });
});
