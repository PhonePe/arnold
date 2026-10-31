'use client';

/*
 * Copyright (c) 2026 Original Author(s), PhonePe India Pvt. Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import React, { useEffect, useState } from 'react';
import Link from 'next/link';
import { ErrorState } from '@/components/ui/ErrorState';
import { LoadingState } from '@/components/ui/LoadingState';
import { StatCard } from '@/components/ui/StatCard';
import { Tabs } from '@/components/ui/Tabs';
import { fetchTokenUsageMetrics } from '@/lib/api';
import { reviewHrefFromChangeRequestId } from '@/lib/analysis-navigation.mjs';

const DAY_MS = 24 * 60 * 60 * 1000;
// Flow-job usage records expire after 90 days, so longer ranges would under-report.
const PRESETS = [
  { key: '7', label: '7d' },
  { key: '30', label: '30d' },
  { key: '90', label: '90d' },
];
const formatTokens = (value) => new Intl.NumberFormat(undefined, { notation: 'compact', maximumFractionDigits: 1 })
  .format(value);
const FLOWS = { improve: 'MR analysis', group_review: 'Group analysis', smart_diff: 'Smart diff' };
const flowLabel = (flow) => FLOWS[flow] || flow;
const runHref = (job) => job.flow === 'smart_diff' && job.target
  ? reviewHrefFromChangeRequestId(job.target)
  : `/history?runId=${encodeURIComponent(job.runId)}`;
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
          Tokens reported by every Clio job Arnold triggers: MR analysis, group analysis and smart diff.
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
              delta={`${formatTokens(metrics.cacheReadTokens)} cached · ${formatTokens(metrics.outputTokens)} output`} />
            <StatCard className="border-0" value={metrics.jobs ? formatTokens(total / metrics.jobs) : '—'}
              label="Average per job" />
            <StatCard className="border-0" value={metrics.jobs.toLocaleString()} label="Clio jobs with usage" />
          </section>

          <Table title="By flow" detail="Each Clio job runs one flow."
            columns={['Flow', 'Jobs', 'Input tokens', 'Cached input', 'Output tokens']}>
            {metrics.flows.length ? metrics.flows.map((flow) => (
              <tr key={flow.flow} className="border-t border-border">
                <td className={`${cellClass} text-fg`}>{flowLabel(flow.flow)}</td>
                <td className={`${cellClass} font-mono text-fg`}>{flow.jobs.toLocaleString()}</td>
                <td className={`${cellClass} text-right font-mono text-fg`}>{flow.inputTokens.toLocaleString()}</td>
                <td className={`${cellClass} text-right font-mono text-muted`}>{flow.cacheReadTokens.toLocaleString()}</td>
                <td className={`${cellClass} text-right font-mono text-fg`}>{flow.outputTokens.toLocaleString()}</td>
              </tr>
            )) : (
              <tr className="border-t border-border">
                <td colSpan={5} className={`${cellClass} text-muted`}>No usage reported in this period.</td>
              </tr>
            )}
          </Table>

          <Table title="By model" detail="A job that used several models counts once per model."
            columns={['Model', 'Jobs', 'Input tokens', 'Cached input', 'Output tokens']}>
            {metrics.models.map((model) => (
              <tr key={model.model} className="border-t border-border">
                <td className={`${cellClass} font-mono text-fg`}>{model.model}</td>
                <td className={`${cellClass} font-mono text-fg`}>{model.jobs.toLocaleString()}</td>
                <td className={`${cellClass} text-right font-mono text-fg`}>{model.inputTokens.toLocaleString()}</td>
                <td className={`${cellClass} text-right font-mono text-muted`}>{model.cacheReadTokens.toLocaleString()}</td>
                <td className={`${cellClass} text-right font-mono text-fg`}>{model.outputTokens.toLocaleString()}</td>
              </tr>
            ))}
          </Table>

          <Table title="Recent flow jobs" detail="Newest Clio jobs that reported usage."
            columns={['Flow', 'Run', 'Target', 'Invoked by', 'Harness', 'Status', 'Models', 'Created', 'Clio job', 'Input tokens', 'Cached input', 'Output tokens']}>
            {metrics.recentJobs.map((job) => (
              <tr key={job.id} className="border-t border-border">
                <td className={`${cellClass} text-fg`}>{flowLabel(job.flow)}</td>
                <td className={cellClass}>
                  <Link href={runHref(job)} className="font-mono text-accent hover:underline">{job.runId.slice(0, 8)}</Link>
                </td>
                <td className={`${cellClass} font-mono text-fg`}>{job.target || '—'}</td>
                <td className={`${cellClass} text-muted`}>{job.invokedBy || '—'}</td>
                <td className={`${cellClass} font-mono text-muted`}>{job.harness || '—'}</td>
                <td className={`${cellClass} text-muted`}>{job.status || '—'}</td>
                <td className={`${cellClass} font-mono text-muted`}>{job.usage.models.map((model) => model.model).join(', ') || '—'}</td>
                <td className={`${cellClass} text-muted`}>{new Date(job.createdAt).toLocaleString()}</td>
                <td className={cellClass}>
                  {job.usage.jobUrl ? <a href={job.usage.jobUrl} className="font-mono text-accent hover:underline" target="_blank" rel="noreferrer">Open ↗</a> : '—'}
                </td>
                <td className={`${cellClass} text-right font-mono text-fg`}>{job.usage.inputTokens.toLocaleString()}</td>
                <td className={`${cellClass} text-right font-mono text-muted`}>{job.usage.cacheReadTokens?.toLocaleString() ?? '—'}</td>
                <td className={`${cellClass} text-right font-mono text-fg`}>{job.usage.outputTokens.toLocaleString()}</td>
              </tr>
            ))}
          </Table>
        </>
      ) : null}
    </div>
  );
}
