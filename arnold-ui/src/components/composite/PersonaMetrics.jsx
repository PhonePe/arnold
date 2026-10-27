'use client';

import { useState } from 'react';
import { ErrorState } from '@/components/ui/ErrorState';
import { LoadingState } from '@/components/ui/LoadingState';
import { fetchPersonaMetrics } from '@/lib/api';

export const PERSONA_METRICS_DAYS = 90;
export const personaMetricsRange = () => {
  const to = Date.now();
  return { from: to - PERSONA_METRICS_DAYS * 24 * 60 * 60 * 1000, to };
};
const percent = (value) => value == null ? '—' : `${value.toFixed(1)}%`;

function Stat({ label, value }) {
  return <div className="min-w-0"><dt className="font-mono text-[10px] uppercase tracking-[0.06em] text-faint">{label}</dt><dd className="mt-0.5 font-mono text-sm font-semibold text-fg">{value}</dd></div>;
}

export function PersonaMetricsStrip({ entry, className = '' }) {
  return <dl className={`grid grid-cols-3 gap-3 border-y border-border py-3 ${className}`} aria-label={`Last ${PERSONA_METRICS_DAYS} days`}>
    <Stat label="Runs" value={entry ? entry.runs : '—'} />
    <Stat label="Findings" value={entry ? entry.posted : '—'} />
    <Stat label="Accepted" value={entry ? percent(entry.acceptanceRate) : '—'} />
  </dl>;
}

export function PersonaMetricsDetail({ personaId }) {
  const [state, setState] = useState({ status: 'idle' });

  async function load() {
    if (state.status !== 'idle' && state.status !== 'error') return;
    setState({ status: 'loading' });
    try {
      setState({ status: 'ready', data: await fetchPersonaMetrics(personaId, personaMetricsRange()) });
    } catch (error) { setState({ status: 'error', error }); }
  }

  const data = state.data;
  const maxFindings = Math.max(1, ...(data?.weekly || []).map((week) => week.findings));
  return <details className="mt-3 border border-border bg-surface-2" onToggle={(event) => { if (event.currentTarget.open) load(); }}>
    <summary className="cursor-pointer px-3 py-2 font-mono text-[10px] uppercase tracking-[0.06em] text-muted">Metrics · last {PERSONA_METRICS_DAYS} days</summary>
    <div className="border-t border-border p-3">
      {state.status === 'loading' ? <LoadingState title="Loading persona metrics" /> : null}
      {state.status === 'error' ? <ErrorState title="Could not load persona metrics" error={state.error} /> : null}
      {data ? <>
        <dl className="grid grid-cols-2 gap-3 sm:grid-cols-4">
          <Stat label="Posted" value={data.findings.posted} />
          <Stat label="Failed" value={data.findings.failed} />
          <Stat label="Accepted" value={`${percent(data.acceptance.acceptanceRate)} (${data.acceptance.accepted}/${data.acceptance.actionable})`} />
          <Stat label="Resolved" value={`${percent(data.resolution.resolutionRate)} (${data.resolution.resolved}/${data.resolution.resolvable})`} />
        </dl>
        {data.weekly.length ? <div className="mt-4">
          <p className="font-mono text-[10px] uppercase tracking-[0.06em] text-faint">Weekly findings · acceptance</p>
          <div className="mt-2 flex h-20 items-end gap-1" role="img" aria-label="Weekly findings">{data.weekly.map((week) => (
            <div key={week.weekStart} className="flex min-w-0 flex-1 flex-col items-center justify-end" title={`${week.weekStart}: ${week.findings} findings, ${percent(week.acceptanceRate)} accepted`}>
              <span className="w-full bg-accent" style={{ height: `${Math.max(2, (week.findings / maxFindings) * 64)}px` }} />
            </div>
          ))}</div>
        </div> : null}
        {data.versions.length ? <table className="mt-4 w-full text-left font-mono text-[11px]">
          <thead className="text-faint"><tr><th className="py-1 font-normal">Version</th><th className="py-1 font-normal">Runs</th><th className="py-1 font-normal">Findings</th><th className="py-1 font-normal">Accepted</th></tr></thead>
          <tbody className="text-fg">{data.versions.map((version) => <tr key={version.version} className="border-t border-border"><td className="py-1">v{version.version}</td><td className="py-1">{version.runs}</td><td className="py-1">{version.findings}</td><td className="py-1">{percent(version.acceptanceRate)}</td></tr>)}</tbody>
        </table> : null}
        {!data.runs && !data.findings.total ? <p className="text-sm text-muted">No runs used this persona in this period.</p> : null}
      </> : null}
    </div>
  </details>;
}
