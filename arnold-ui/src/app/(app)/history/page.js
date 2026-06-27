'use client';

import React from 'react';
import { Search } from 'lucide-react';
import { Overline } from '@/components/ui/Overline';
import { StatCard } from '@/components/ui/StatCard';
import { Card } from '@/components/ui/Card';
import { Tabs } from '@/components/ui/Tabs';

const FILTERS = [
  { key: 'all', label: 'All runs' },
  { key: 'mine', label: 'Yours' },
  { key: 'flag', label: 'With findings' },
  { key: 'live', label: 'In progress' },
];

const RUNS = [
  { run: '#R-2048', ago: '2m ago', mr: '!482 · neural cache layer', repo: 'core-infrastructure', persona: 'Netra', personaTone: 'var(--sev-security)', findings: '3', note: '· 1 crit', dur: '2.6s', result: 'analysing', resultTone: 'var(--accent)', blink: true, kind: 'live' },
  { run: '#R-2047', ago: '14m ago', mr: '!475 · rotate API credentials', repo: 'platform-secrets', persona: 'Netra', personaTone: 'var(--sev-security)', findings: '5', note: '· 2 crit', dur: '3.1s', result: 'findings', resultTone: 'var(--warn)', kind: 'flag' },
  { run: '#R-2046', ago: '1h ago', mr: '!401 · session token schema', repo: 'ledger-service', persona: 'Kōzō', personaTone: 'var(--sev-maint)', findings: '2', note: '· schema', dur: '1.4s', result: 'merged', resultTone: 'var(--ok)', kind: 'flag' },
  { run: '#R-2045', ago: '3h ago', mr: '!468 · payout retry backoff', repo: 'ledger-service', persona: 'Hayai', personaTone: 'var(--sev-perf)', findings: '1', note: '· perf', dur: '0.9s', result: 'merged', resultTone: 'var(--ok)', kind: 'flag' },
  { run: '#R-2044', ago: 'yesterday', mr: '!460 · feature flag cleanup', repo: 'web-app', persona: 'Socratic', personaTone: 'var(--sev-enh)', findings: '0', note: '· clean', dur: '1.2s', result: 'merged', resultTone: 'var(--ok)', kind: 'all' },
  { run: '#R-2043', ago: 'yesterday', mr: '!455 · auth middleware refactor', repo: 'auth-service', persona: 'Mamoru', personaTone: 'var(--sev-critical)', findings: '4', note: '· 1 regression', dur: '2.0s', result: 'merged', resultTone: 'var(--ok)', kind: 'flag' },
];

function Pill({ tone, blink, children }) {
  return (
    <span className="inline-flex items-center gap-1.5 rounded-full border border-border-2 px-[9px] py-[3px] font-mono text-[11px] tracking-[0.03em]" style={{ color: tone }}>
      <span className={`h-[7px] w-[7px] rounded-full ${blink ? 'animate-pulse' : ''}`} style={{ background: tone }} />
      {children}
    </span>
  );
}

export default function HistoryPage() {
  const [filter, setFilter] = React.useState('all');
  const visible = RUNS.filter((r) => filter === 'all' || r.kind === filter || (filter === 'mine'));

  return (
    <div className="mx-auto max-w-[1680px]">
      <div className="mb-6">
        <Overline>Audit trail</Overline>
        <h1 className="mt-1 font-display text-[clamp(30px,4vw,42px)] font-bold leading-[1.05] tracking-[-0.02em] text-fg">
          Analysis history
        </h1>
        <p className="mt-2 max-w-[72ch] text-[15px] text-muted">
          Every Arnold run, persisted with its persona, findings and post-merge effectiveness.
          Replay any run to see the exact comments generated — and which ones the author acted on.
        </p>
      </div>

      <div className="mb-5 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard value="68" accent label="Runs this week" delta="▲ 9" deltaTone="up" />
        <StatCard value="214" label="Findings surfaced" delta="71% acted on" deltaTone="neutral" />
        <StatCard value="1.8s" label="Median run time" delta="▼ 14%" deltaTone="up" />
        <StatCard value="3" label="Runs in progress" delta="live queue" deltaTone="neutral" />
      </div>

      <div className="mb-[18px] flex flex-wrap items-center justify-between gap-3">
        <Tabs variant="segment" items={FILTERS} activeKey={filter} onTabChange={setFilter} />
        <div className="relative w-[280px]">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-faint" />
          <input
            className="h-[34px] w-full rounded-none border border-border bg-surface pl-9 pr-3 text-[13px] text-fg placeholder:text-faint focus-visible:border-accent focus-visible:outline-none focus-visible:ring-[3px] focus-visible:ring-[var(--accent-soft)]"
            placeholder="Filter by MR or repo…"
          />
        </div>
      </div>

      <Card className="overflow-hidden">
        <table className="w-full border-collapse text-[13.5px]">
          <thead>
            <tr className="text-left">
              <th className="border-b border-border px-4 py-[11px] font-mono text-[11px] font-medium uppercase tracking-[0.06em] text-muted">Run</th>
              <th className="border-b border-border px-4 py-[11px] font-mono text-[11px] font-medium uppercase tracking-[0.06em] text-muted">Merge request</th>
              <th className="border-b border-border px-4 py-[11px] font-mono text-[11px] font-medium uppercase tracking-[0.06em] text-muted">Persona</th>
              <th className="border-b border-border px-4 py-[11px] font-mono text-[11px] font-medium uppercase tracking-[0.06em] text-muted">Findings</th>
              <th className="border-b border-border px-4 py-[11px] font-mono text-[11px] font-medium uppercase tracking-[0.06em] text-muted">Duration</th>
              <th className="border-b border-border px-4 py-[11px] text-right font-mono text-[11px] font-medium uppercase tracking-[0.06em] text-muted">Result</th>
            </tr>
          </thead>
          <tbody>
            {visible.map((r) => (
              <tr key={r.run} className="cursor-pointer transition-colors hover:bg-[var(--ink-wash)]">
                <td className="border-b border-border px-4 py-[13px]">
                  <div className="font-mono text-[12px] text-fg">{r.run}</div>
                  <div className="font-mono text-[11px] text-muted">{r.ago}</div>
                </td>
                <td className="border-b border-border px-4 py-[13px]">
                  <div className="font-semibold text-fg">{r.mr}</div>
                  <div className="font-mono text-[11px] text-muted">{r.repo}</div>
                </td>
                <td className="border-b border-border px-4 py-[13px]">
                  <Pill tone={r.personaTone}>{r.persona}</Pill>
                </td>
                <td className="border-b border-border px-4 py-[13px]">
                  <span className="font-mono text-fg">{r.findings}</span>{' '}
                  <span className="font-mono text-[11px] text-muted">{r.note}</span>
                </td>
                <td className="border-b border-border px-4 py-[13px] font-mono text-fg">{r.dur}</td>
                <td className="border-b border-border px-4 py-[13px] text-right">
                  <Pill tone={r.resultTone} blink={r.blink}>{r.result}</Pill>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </Card>
    </div>
  );
}
