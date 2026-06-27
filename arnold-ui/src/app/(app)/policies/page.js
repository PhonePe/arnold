'use client';

import React from 'react';
import { Plus, Download } from 'lucide-react';
import { Overline } from '@/components/ui/Overline';
import { StatCard } from '@/components/ui/StatCard';
import { Card } from '@/components/ui/Card';
import { Tabs } from '@/components/ui/Tabs';

const FILTERS = [
  { key: 'all', label: 'All · 24' },
  { key: 'sec', label: 'Security' },
  { key: 'priv', label: 'Privacy' },
  { key: 'perf', label: 'Performance' },
  { key: 'maint', label: 'Maintainability' },
];

const POLICIES = [
  { cat: 'sec', name: 'Secret material in source or logs', code: 'SEC-014 · scans diff + 3 lines context', cls: 'security', clsTone: 'var(--sev-security)', sev: 'critical', sevTag: 'crit', action: 'blocks merge', solid: true, on: true },
  { cat: 'sec', name: 'Unsafe deserialization / injection sink', code: 'SEC-031 · taint trace to sink', cls: 'security', clsTone: 'var(--sev-security)', sev: 'critical', sevTag: 'crit', action: 'blocks merge', solid: true, on: true },
  { cat: 'priv', name: 'PII written without redaction', code: 'PII-02 · email / phone / token patterns', cls: 'privacy', clsTone: 'var(--sev-critical)', sev: 'high', sevTag: 'hi', action: 'blocks merge', solid: true, on: true },
  { cat: 'perf', name: 'N+1 query on a request path', code: 'PERF-08 · ORM call inside loop', cls: 'performance', clsTone: 'var(--sev-perf)', sev: 'high', sevTag: 'hi', action: 'advises', solid: false, on: true },
  { cat: 'perf', name: 'Blocking I/O on async path', code: 'PERF-12 · sync call in coroutine', cls: 'performance', clsTone: 'var(--sev-perf)', sev: 'medium', sevTag: 'med', action: 'advises', solid: false, on: true },
  { cat: 'maint', name: 'Cross-repo contract / schema drift', code: 'MAINT-05 · API + consumer mismatch', cls: 'maintainability', clsTone: 'var(--sev-maint)', sev: 'medium', sevTag: 'med', action: 'advises', solid: false, on: true },
  { cat: 'maint', name: 'Naming / style nit', code: 'MAINT-22 · 84% historic dismissal · auto-pruned', cls: 'style', clsTone: 'var(--sev-enh)', sev: 'nit', sevTag: 'low', action: 'silent', solid: false, on: false, dim: true },
];

const sevTagClasses = {
  crit: 'text-accent-ink border-[color-mix(in_oklch,var(--accent)_40%,transparent)]',
  hi: 'text-[var(--sev-security)] border-[color-mix(in_oklch,var(--sev-security)_40%,transparent)]',
  med: 'text-tertiary-ink border-[color-mix(in_oklch,var(--warn)_45%,transparent)]',
  low: 'text-faint border-border-2',
};

function Switch({ on, onClick }) {
  return (
    <button
      role="switch"
      aria-checked={on}
      onClick={onClick}
      className={`relative inline-block h-[19px] w-[34px] rounded-full align-middle transition-colors ${
        on ? 'bg-accent' : 'bg-border-2'
      }`}
    >
      <span
        className={`absolute left-0.5 top-0.5 h-[15px] w-[15px] rounded-full bg-surface shadow-[0_1px_2px_rgba(0,0,0,0.25)] transition-transform ${
          on ? 'translate-x-[15px]' : ''
        }`}
      />
    </button>
  );
}

function Pill({ children, solid }) {
  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full border px-[9px] py-[3px] font-mono text-[11px] tracking-[0.03em] ${
        solid ? 'border-transparent bg-[var(--accent-soft)] text-accent-ink' : 'border-border-2 text-muted'
      }`}
    >
      {children}
    </span>
  );
}

export default function PoliciesPage() {
  const [filter, setFilter] = React.useState('all');
  const [states, setStates] = React.useState(() => POLICIES.map((p) => p.on));

  const toggle = (i) => setStates((s) => s.map((v, idx) => (idx === i ? !v : v)));
  const visible = POLICIES.map((p, i) => ({ ...p, i })).filter((p) => filter === 'all' || p.cat === filter);

  return (
    <div className="mx-auto max-w-[1680px]">
      <div className="mb-6">
        <Overline>Governance</Overline>
        <h1 className="mt-1 font-display text-[clamp(30px,4vw,42px)] font-bold leading-[1.05] tracking-[-0.02em] text-fg">
          Review policies
        </h1>
        <p className="mt-2 max-w-[72ch] text-[15px] text-muted">
          The rule set Arnold enforces on every MR. Each policy maps a finding class to a severity
          and an action — <span className="font-mono text-[0.92em] text-accent-ink">block</span>,
          advise, or stay silent. Org admins own the canon; reviewers can layer personal overrides.
        </p>
      </div>

      <div className="mb-5 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard value="24" accent label="Active policies" delta="6 categories" deltaTone="neutral" />
        <StatCard value="9" label="Blocking rules" delta="hard gates" deltaTone="neutral" />
        <StatCard value="312" label="Enforced this week" delta="▲ 18%" deltaTone="up" />
        <StatCard value="41%" label="NIT auto-pruned" delta="noise floor" deltaTone="neutral" />
      </div>

      <div className="mb-[18px] flex flex-wrap items-center justify-between gap-3">
        <Tabs variant="segment" items={FILTERS} activeKey={filter} onTabChange={setFilter} />
        <div className="flex items-center gap-2">
          <button className="inline-flex h-8 items-center gap-1.5 rounded-none border border-border-2 bg-surface px-3 text-xs font-semibold text-fg hover:border-fg">
            <Download className="h-[14px] w-[14px]" strokeWidth={2} />
            Export canon
          </button>
          <button className="inline-flex h-8 items-center gap-1.5 rounded-none bg-accent px-3 text-xs font-semibold text-on-accent hover:bg-accent-ink">
            <Plus className="h-[14px] w-[14px]" strokeWidth={2} />
            New policy
          </button>
        </div>
      </div>

      <Card className="overflow-hidden">
        <table className="w-full border-collapse text-[13.5px]">
          <thead>
            <tr className="text-left">
              <th className="border-b border-border px-4 py-[11px] font-mono text-[11px] font-medium uppercase tracking-[0.06em] text-muted">Policy</th>
              <th className="border-b border-border px-4 py-[11px] font-mono text-[11px] font-medium uppercase tracking-[0.06em] text-muted">Class</th>
              <th className="border-b border-border px-4 py-[11px] font-mono text-[11px] font-medium uppercase tracking-[0.06em] text-muted">Severity</th>
              <th className="border-b border-border px-4 py-[11px] font-mono text-[11px] font-medium uppercase tracking-[0.06em] text-muted">Action</th>
              <th className="border-b border-border px-4 py-[11px] text-right font-mono text-[11px] font-medium uppercase tracking-[0.06em] text-muted">State</th>
            </tr>
          </thead>
          <tbody>
            {visible.map((p) => (
              <tr key={p.name} className={p.dim ? 'opacity-60' : ''}>
                <td className="border-b border-border px-4 py-[13px]">
                  <div className="font-semibold text-fg">{p.name}</div>
                  <div className="font-mono text-[11px] text-muted">{p.code}</div>
                </td>
                <td className="border-b border-border px-4 py-[13px]">
                  <Pill>
                    <span className="h-[9px] w-[9px] rounded-full" style={{ background: p.clsTone }} />
                    {p.cls}
                  </Pill>
                </td>
                <td className="border-b border-border px-4 py-[13px]">
                  <span className={`inline-block rounded-full border px-2 py-[2px] font-mono text-[11px] tracking-[0.03em] ${sevTagClasses[p.sevTag]}`}>
                    {p.sev}
                  </span>
                </td>
                <td className="border-b border-border px-4 py-[13px]">
                  <Pill solid={p.solid}>{p.action}</Pill>
                </td>
                <td className="border-b border-border px-4 py-[13px] text-right">
                  <Switch on={states[p.i]} onClick={() => toggle(p.i)} />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </Card>
    </div>
  );
}
