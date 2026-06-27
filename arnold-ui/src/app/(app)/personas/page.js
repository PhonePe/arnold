'use client';

import React from 'react';
import { Plus, TrendingUp } from 'lucide-react';
import { Overline } from '@/components/ui/Overline';
import { Button } from '@/components/ui/Button';
import { Tabs } from '@/components/ui/Tabs';

const PERSONAS = [
  {
    glyph: '網', tone: 'var(--sev-security)', name: 'Netra', by: '@security-guild · official',
    desc: 'Compliance & audit-focused. Maps every finding to org policy IDs (SEC-014, PII-02) and produces an auditor-ready trail.',
    reviewers: '1,204 reviewers', cta: 'Use', published: false,
  },
  {
    glyph: '速', tone: 'var(--sev-perf)', name: 'Hayai', by: '@perf-core',
    desc: 'Performance-obsessed. Hunts O(n²), N+1 queries and blocking I/O on hot paths. Quiet on style — loud on latency.',
    reviewers: '867 reviewers', cta: 'Use', published: false,
  },
  {
    glyph: '問', tone: 'var(--sev-enh)', name: 'Socratic', by: '@tushar.n · yours',
    desc: "Doesn't fix — asks. Generates understanding-check questions to gauge whether the author actually grasps generated code.",
    reviewers: '412 reviewers', cta: 'Published', published: true,
  },
  {
    glyph: '構', tone: 'var(--sev-maint)', name: 'Kōzō', by: '@architecture',
    desc: 'Structural reviewer. Flags excessive nesting, missing abstractions and cross-repo contract drift in API/schema changes.',
    reviewers: '633 reviewers', cta: 'Use', published: false,
  },
  {
    glyph: '護', tone: 'var(--sev-critical)', name: 'Mamoru', by: '@platform',
    desc: 'Regression guardian. Cross-references previous MRs in the same code area and warns when a pattern broke before.',
    reviewers: '521 reviewers', cta: 'Use', published: false,
  },
  {
    glyph: '＋', tone: 'var(--faint)', name: 'Your next persona', by: 'draft from repo knowledge',
    desc: "Generate a starter persona from a repo's conventions and past review feedback — then refine and evangelise it.",
    reviewers: 'draft', cta: 'Generate', published: false, draft: true,
  },
];

const TABS = [
  { key: 'popular', label: 'Popular' },
  { key: 'language', label: 'By language' },
  { key: 'yours', label: 'Yours' },
  { key: 'compliance', label: 'Compliance' },
];

function PersonaCard({ glyph, tone, name, by, desc, reviewers, cta, published, draft }) {
  return (
    <div className="flex flex-col gap-3 rounded-none border border-border bg-surface p-[18px]">
      <div className="flex items-center gap-3">
        <div
          className="grid h-[42px] w-[42px] flex-none place-items-center font-display text-[18px] font-bold text-white"
          style={{ background: tone }}
        >
          {glyph}
        </div>
        <div>
          <div className="text-[15px] font-bold text-fg">{name}</div>
          <div className="font-mono text-[11px] text-faint">{by}</div>
        </div>
      </div>
      <p className="flex-1 text-[13px] leading-[1.5] text-muted">{desc}</p>
      <div className="flex items-center justify-between gap-2.5">
        <span className={`flex items-center gap-1.5 font-mono text-[12px] ${draft ? 'text-faint' : 'text-muted'}`}>
          {!draft && <TrendingUp className="h-[13px] w-[13px]" strokeWidth={2} />}
          {reviewers}
        </span>
        <Button variant={published ? 'primary' : 'secondary'} size="sm">
          {cta}
        </Button>
      </div>
    </div>
  );
}

export default function PersonasPage() {
  const [tab, setTab] = React.useState('popular');

  return (
    <div className="mx-auto max-w-[1680px]">
      <div className="mb-6">
        <Overline>Marketplace</Overline>
        <h1 className="mt-1 font-display text-[clamp(30px,4vw,42px)] font-bold leading-[1.05] tracking-[-0.02em] text-fg">
          Review personas
        </h1>
        <p className="mt-2 max-w-[70ch] text-[15px] text-muted">
          Specialised review agents with dedicated context windows. Build your own, publish it, and
          let the org adopt it. Ranked by active reviewers this month.
        </p>
      </div>

      <div className="mb-[18px] flex flex-wrap items-center justify-between gap-3">
        <Tabs variant="segment" items={TABS} activeKey={tab} onTabChange={setTab} />
        <Button variant="primary" size="sm">
          <Plus className="h-4 w-4" strokeWidth={2} />
          Create persona
        </Button>
      </div>

      <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3">
        {PERSONAS.map((p) => (
          <PersonaCard key={p.name} {...p} />
        ))}
      </div>
    </div>
  );
}
