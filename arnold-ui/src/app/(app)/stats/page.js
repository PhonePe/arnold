'use client';

import React from 'react';
import { Overline } from '@/components/ui/Overline';
import { StatCard } from '@/components/ui/StatCard';
import { Card } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { Tabs } from '@/components/ui/Tabs';
import { ProgressBar } from '@/components/ui/ProgressBar';

const SCOPES = [
  { key: 'personal', label: 'Personal' },
  { key: 'org', label: 'Organisation' },
  { key: 'tool', label: 'Tool / admin' },
];

const findingsByClass = [
  { label: 'SEC', em: 'sec', height: 40, hi: true },
  { label: 'CRIT', em: 'crit', height: 30 },
  { label: 'PERF', em: 'perf', height: 55 },
  { label: 'MAINT', em: 'maint', height: 68 },
  { label: 'PRAC', em: 'prac', height: 50 },
  { label: 'NIT', em: 'nit', height: 90 },
];

const effectivenessTrend = [
  { label: 'Jan', em: '52%', height: 48 },
  { label: 'Feb', em: '57%', height: 54 },
  { label: 'Mar', em: '61%', height: 60 },
  { label: 'Apr', em: '65%', height: 66 },
  { label: 'May', em: '69%', height: 72 },
  { label: 'Jun', em: '71%', height: 78, hi: true },
];

const repoCoverage = [
  { repo: 'auth-service', coverage: 92, accept: '78%', status: 'mature', tone: 'var(--ok)' },
  { repo: 'ledger-service', coverage: 61, accept: '69%', status: 'growing', tone: 'var(--warn)' },
  { repo: 'web-app', coverage: 44, accept: '64%', status: 'growing', tone: 'var(--warn)' },
  { repo: 'platform-secrets', coverage: 81, accept: '74%', status: 'mature', tone: 'var(--ok)' },
];

// CSS bar chart matching the reference .bars
function BarChart({ data }) {
  return (
    <div className="mb-5 flex h-[140px] items-end gap-2.5 pt-2.5">
      {data.map((b) => (
        <div key={b.label} className="group relative flex h-full flex-1 items-end">
          <div
            className={`relative w-full rounded-[0_5px_0_0] transition-colors ${
              b.hi ? 'bg-accent' : 'bg-border-2 group-hover:bg-accent-ink'
            }`}
            style={{ height: `${b.height}%`, minHeight: 4 }}
          >
            <span
              className={`pointer-events-none absolute -top-[18px] left-0 right-0 text-center font-mono text-[10px] text-accent-ink transition-opacity ${
                b.hi ? 'opacity-100' : 'opacity-0 group-hover:opacity-100'
              }`}
            >
              {b.em}
            </span>
          </div>
          <span className="absolute -bottom-5 left-0 right-0 text-center font-mono text-[10px] text-faint">
            {b.label}
          </span>
        </div>
      ))}
    </div>
  );
}

export default function StatsPage() {
  const [scope, setScope] = React.useState('personal');

  return (
    <div className="mx-auto max-w-[1680px]">
      <div className="mb-6">
        <Overline>Metrics</Overline>
        <h1 className="mt-1 font-display text-[clamp(30px,4vw,42px)] font-bold leading-[1.05] tracking-[-0.02em] text-fg">
          Stats &amp; quality
        </h1>
        <p className="mt-2 max-w-[70ch] text-[15px] text-muted">
          Tool health for admins, organisation review quality for everyone, and your personal
          scorecard.
        </p>
      </div>

      <div className="mb-5">
        <Tabs variant="segment" items={SCOPES} activeKey={scope} onTabChange={setScope} />
      </div>

      <div className="mb-5 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard value="71%" accent label="Comments acted upon" delta="▲ 6 pts" deltaTone="up" />
        <StatCard value="14.2" label="MRs reviewed / week" delta="▲ 12%" deltaTone="up" />
        <StatCard value="38%" label="NIT noise pruned" delta="feedback loop" deltaTone="neutral" />
        <StatCard value="2.3h" label="Median time-to-merge" delta="▼ 19%" deltaTone="up" />
      </div>

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
        <Card className="p-5">
          <div className="mb-1 flex items-center justify-between">
            <h2 className="font-display text-[17px] font-bold text-fg">Findings by classification</h2>
            <Badge variant="legacy">this quarter</Badge>
          </div>
          <p className="mb-[18px] text-[12.5px] text-muted">
            Distribution across the org — security and critical down as knowledge base matures.
          </p>
          <BarChart data={findingsByClass} />
        </Card>

        <Card className="p-5">
          <div className="mb-1 flex items-center justify-between">
            <h2 className="font-display text-[17px] font-bold text-fg">Comment effectiveness trend</h2>
            <Badge variant="critical">feedback loop</Badge>
          </div>
          <p className="mb-[18px] text-[12.5px] text-muted">
            Share of posted comments that were addressed in the final diff.
          </p>
          <BarChart data={effectivenessTrend} />
        </Card>
      </div>

      <Card className="mt-5 overflow-hidden">
        <div className="flex items-center justify-between p-5 pb-3">
          <h2 className="font-display text-[17px] font-bold text-fg">Repository knowledge coverage</h2>
          <button className="font-mono text-[12px] text-muted hover:text-accent">Generate drafts →</button>
        </div>
        <table className="w-full border-collapse text-[13.5px]">
          <thead>
            <tr className="text-left">
              <th className="border-b border-border px-4 py-[11px] font-mono text-[11px] font-medium uppercase tracking-[0.06em] text-muted">Repository</th>
              <th className="border-b border-border px-4 py-[11px] font-mono text-[11px] font-medium uppercase tracking-[0.06em] text-muted">Knowledge base</th>
              <th className="border-b border-border px-4 py-[11px] font-mono text-[11px] font-medium uppercase tracking-[0.06em] text-muted">Accept rate</th>
              <th className="border-b border-border px-4 py-[11px] text-right font-mono text-[11px] font-medium uppercase tracking-[0.06em] text-muted">Status</th>
            </tr>
          </thead>
          <tbody>
            {repoCoverage.map((r) => (
              <tr key={r.repo}>
                <td className="border-b border-border px-4 py-[11px] font-mono text-[12.5px] text-fg">{r.repo}</td>
                <td className="border-b border-border px-4 py-[11px]">
                  <ProgressBar value={r.coverage} className="w-[120px]" />
                </td>
                <td className="border-b border-border px-4 py-[11px] font-mono text-fg">{r.accept}</td>
                <td className="border-b border-border px-4 py-[11px] text-right">
                  <span className="inline-flex items-center gap-1.5 font-mono text-[11px]" style={{ color: r.tone }}>
                    <span className="h-[7px] w-[7px] rounded-full" style={{ background: r.tone }} />
                    {r.status}
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </Card>
    </div>
  );
}
