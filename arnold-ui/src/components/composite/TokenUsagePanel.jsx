'use client';

import React, { useEffect, useState } from 'react';
import Link from 'next/link';
import { ErrorState } from '@/components/ui/ErrorState';
import { LoadingState } from '@/components/ui/LoadingState';
import { StatCard } from '@/components/ui/StatCard';
import { Tabs } from '@/components/ui/Tabs';
import { fetchTokenUsageMetrics } from '@/lib/api';

const DAY_MS = 24 * 60 * 60 * 1000;
// Analysis runs expire after 90 days, so longer ranges would under-report.
const PRESETS = [
  { key: '7', label: '7d' },
  { key: '30', label: '30d' },
  { key: '90', label: '90d' },
];
const formatTokens = (value) => new Intl.NumberFormat(undefined, { notation: 'compact', maximumFractionDigits: 1 })
  .format(value);
const headClass = 'px-4 py-2 font-semibold';
const cellClass = 'px-4 py-2.5';

function Table({ title, detail, columns, children }) {
  return (
    <section className="border border-border bg-surface">
      <div className="border-b border-border px-4 py-3 sm:px-5">
        <h3 className="font-display text-[16px] font-bold text-fg">{title}</h3>
        <p className="mt-0.5 text-[12px] text-muted">{detail}</p>
      </div>
      <div className="overflow-x-auto">
        <table className="w-full min-w-[640px] text-left text-[12px]">
          <thead className="bg-[var(--ink-wash)] font-mono text-[10px] uppercase tracking-[0.1em] text-faint">
            <tr>
              {columns.map((column) => (
                <th key={column} className={`${headClass} ${column.endsWith('tokens') ? 'text-right' : ''}`}>{column}</th>
              ))}
            </tr>
          </thead>
          <tbody>{children}</tbody>
        </table>
      </div>
    </section>
  );
}

export function TokenUsagePanel({ className = '', ...props }) {
  const [days, setDays] = useState('30');
  const [metrics, setMetrics] = useState(null);
  const [status, setStatus] = useState('loading');
  const [error, setError] = useState(null);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    const to = Date.now();
    fetchTokenUsageMetrics({ from: to - Number(days) * DAY_MS, to, signal: controller.signal }).then((response) => {
      setMetrics(response);
      setStatus('ready');
    }, (nextError) => {
      if (controller.signal.aborted) return;
      setError(nextError);
      setStatus('error');
    });
    return () => controller.abort();
  }, [days, attempt]);

  const total = metrics ? metrics.inputTokens + metrics.outputTokens : 0;

  return (
    <div className={`space-y-5 ${className}`} {...props}>
      <section className="flex flex-wrap items-center justify-between gap-3 border border-border bg-surface p-4 sm:p-5">
        <Tabs
          variant="segment"
          items={PRESETS}
          activeKey={days}
          onTabChange={(key) => {
            setStatus('loading');
            setDays(key);
          }}
        />
        <p className="text-[12px] text-muted">
          Tokens reported by the Clio job of each analysis and group analysis run.
        </p>
      </section>

      {status === 'loading' ? <LoadingState label="Loading token usage" /> : null}
      {status === 'error' ? (
        <ErrorState title="Could not load token usage" error={error} onRetry={() => {
          setStatus('loading');
          setAttempt((value) => value + 1);
        }} />
      ) : null}

      {status === 'ready' && metrics ? (
        <>
          <section className="grid gap-px border border-border bg-border sm:grid-cols-2 lg:grid-cols-4">
            <StatCard className="border-0" accent value={formatTokens(total)} label="Total tokens" />
            <StatCard className="border-0" value={formatTokens(metrics.inputTokens)} label="Input tokens"
              delta={`${formatTokens(metrics.outputTokens)} output`} />
            <StatCard className="border-0" value={metrics.reportedRuns ? formatTokens(total / metrics.reportedRuns) : '—'}
              label="Average per analysis" />
            <StatCard className="border-0" value={metrics.reportedRuns.toLocaleString()} label="Analyses with usage"
              delta={`of ${metrics.runs.toLocaleString()} runs`} />
          </section>

          <Table title="By model" detail="A run that used several models counts once per model."
            columns={['Model', 'Runs', 'Input tokens', 'Output tokens']}>
            {metrics.models.length ? metrics.models.map((model) => (
              <tr key={model.model} className="border-t border-border">
                <td className={`${cellClass} font-mono text-fg`}>{model.model}</td>
                <td className={`${cellClass} font-mono text-fg`}>{model.runs.toLocaleString()}</td>
                <td className={`${cellClass} text-right font-mono text-fg`}>{model.inputTokens.toLocaleString()}</td>
                <td className={`${cellClass} text-right font-mono text-fg`}>{model.outputTokens.toLocaleString()}</td>
              </tr>
            )) : (
              <tr className="border-t border-border">
                <td colSpan={4} className={`${cellClass} text-muted`}>No usage reported in this period.</td>
              </tr>
            )}
          </Table>

          <Table title="Recent analyses" detail="Newest runs that reported usage."
            columns={['Run', 'Target', 'Invoked by', 'Harness', 'Status', 'Created', 'Input tokens', 'Output tokens']}>
            {metrics.recentRuns.map((run) => (
              <tr key={run.runId} className="border-t border-border">
                <td className={cellClass}>
                  <Link href={`/history?runId=${encodeURIComponent(run.runId)}`} className="font-mono text-accent hover:underline">
                    {run.kind === 'group_analysis' ? 'Group' : 'MR'} · {run.runId.slice(0, 8)}
                  </Link>
                </td>
                <td className={`${cellClass} font-mono text-fg`}>{run.target || '—'}</td>
                <td className={`${cellClass} text-muted`}>{run.invokedBy || '—'}</td>
                <td className={`${cellClass} font-mono text-muted`}>{run.harness || '—'}</td>
                <td className={`${cellClass} text-muted`}>{run.status || '—'}</td>
                <td className={`${cellClass} text-muted`}>{new Date(run.createdAt).toLocaleString()}</td>
                <td className={`${cellClass} text-right font-mono text-fg`}>{run.inputTokens.toLocaleString()}</td>
                <td className={`${cellClass} text-right font-mono text-fg`}>{run.outputTokens.toLocaleString()}</td>
              </tr>
            ))}
          </Table>
        </>
      ) : null}
    </div>
  );
}
