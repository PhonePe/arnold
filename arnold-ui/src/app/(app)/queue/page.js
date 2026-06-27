'use client';

import React, { useState, useCallback } from 'react';
import { useRouter } from 'next/navigation';
import { Plus, ChevronRight, GripVertical } from 'lucide-react';
import { Overline } from '@/components/ui/Overline';
import { Tabs } from '@/components/ui/Tabs';
import { Button } from '@/components/ui/Button';
import { Avatar } from '@/components/ui/Avatar';

// ── MR state machine ──────────────────────────────────────────────────────
const FLOW = {
  CREATED: { l: 'Created', dot: 'var(--muted)', blink: false, tone: 'idle' },
  ANALYSING: { l: 'Analysing', dot: 'var(--accent)', blink: true, tone: 'live' },
  ANALYSED: { l: 'Analysed', dot: 'var(--ok)', blink: false, tone: 'done' },
  REVIEW_READY: { l: 'Review ready', dot: 'var(--accent)', blink: false, tone: 'ready' },
  MERGED: { l: 'Merged', dot: 'var(--ok)', blink: false, tone: 'merged' },
  CLOSED: { l: 'Closed', dot: 'var(--bad)', blink: false, tone: 'closed' },
};

const toneText = {
  idle: 'text-muted',
  live: 'text-accent-ink',
  done: 'text-ok',
  ready: 'text-accent-ink',
  merged: 'text-ok',
  closed: 'text-bad',
};

const SEED = [
  { id: '!482', crit: true, role: 'reviewer', st: 'REVIEW_READY', title: 'feat: implement neural cache layer', meta: 'core-infrastructure · 12 files · JIRA-1442', sev: 'security', sevc: 'var(--sev-security)', who: ['JD', 'A'] },
  { id: '!479', crit: false, role: 'reviewer', st: 'REVIEW_READY', title: 'fix: prevent thread starvation in ledger', meta: 'ledger-service · 3 files', sev: 'performance', sevc: 'var(--sev-perf)', who: ['SK', 'A'] },
  { id: '!475', crit: true, role: 'author', st: 'ANALYSING', title: 'security: rotate API credentials Q4', meta: 'platform-secrets · 6 files · Netra audit', sev: 'critical', sevc: 'var(--sev-critical)', who: ['A'] },
  { id: '!468', crit: false, role: 'author', st: 'ANALYSED', title: 'refactor: extract pricing strategy', meta: 'billing-core · 8 files', sev: 'maintainability', sevc: 'var(--sev-maint)', who: ['MP', 'A'] },
  { id: '!461', crit: false, role: 'reviewer', st: 'REVIEW_READY', title: 'chore: bump grpc + regenerate stubs', meta: 'contracts · 21 files · downstream check', sev: 'best practice', sevc: 'var(--sev-practice)', who: ['RV', 'A'] },
  { id: '!455', crit: false, role: 'author', st: 'CREATED', title: 'feat: add idempotency keys to webhooks', meta: 'gateway · 5 files', sev: 'enhancement', sevc: 'var(--sev-enh)', who: ['A'] },
  { id: '!450', crit: false, role: 'author', st: 'REVIEW_READY', title: 'docs: clarify retry semantics', meta: 'gateway · 1 file', sev: 'typo', sevc: 'var(--sev-typo)', who: ['SK'] },
];

const GROUPS = [
  {
    id: 'grp-authv2', name: 'Auth V2 rollout', role: 'author', st: 'REVIEW_READY',
    note: 'reviewed together · shared review context · processed as a unit',
    members: [
      { id: '!401', st: 'ANALYSED', title: 'feat: session token schema', meta: 'ledger-service · model classes', sev: 'schema', sevc: 'var(--sev-maint)' },
      { id: '!493', st: 'REVIEW_READY', title: 'feat: oauth callback handler', meta: 'auth-gateway · 4 files', sev: 'security', sevc: 'var(--sev-security)' },
      { id: '!488', st: 'ANALYSING', title: 'security: refresh-token rotation', meta: 'platform-secrets · Netra audit', sev: 'critical', sevc: 'var(--sev-critical)' },
    ],
  },
];

const FILTERS = [
  { key: 'all', label: 'All · 8' },
  { key: 'mine', label: 'Assigned' },
  { key: 'watch', label: 'Watching' },
  { key: 'blocked', label: 'Blocked' },
];

const isGroup = (id) => id.startsWith('grp-');

function actionsFor(it) {
  const s = it.st;
  const role = it.role || 'author';
  const A = [];
  if (role === 'author') {
    if (s === 'CREATED') A.push({ l: 'Invoke analysis', to: 'ANALYSING', k: 'primary' });
    else if (s === 'ANALYSED') A.push({ l: 'Mark ready', to: 'REVIEW_READY', k: 'primary' });
    else if (s === 'REVIEW_READY') {
      A.push({ l: 'Merge', to: 'MERGED', k: 'secondary', sync: 1 });
      A.push({ l: 'Close', to: 'CLOSED', k: 'ghost', sync: 1 });
    }
  } else if (s === 'REVIEW_READY') {
    A.push({ l: 'Re-run', to: 'ANALYSING', k: 'secondary' });
    A.push({ l: 'Merge', to: 'MERGED', k: 'ghost', sync: 1 });
  }
  return A;
}

function StateChip({ st }) {
  const f = FLOW[st] || FLOW.CREATED;
  return (
    <span className={`inline-flex min-w-[104px] items-center gap-1.5 whitespace-nowrap font-mono text-[11px] ${toneText[f.tone]}`}>
      <span className={`h-2 w-2 rounded-full ${f.blink ? 'animate-pulse' : ''}`} style={{ background: f.dot }} />
      {f.l}
    </span>
  );
}

function RoleTag({ role }) {
  return role === 'reviewer' ? (
    <span className="whitespace-nowrap rounded-none bg-[var(--ink-wash)] px-2 py-[3px] font-mono text-[10px] uppercase tracking-[0.04em] text-muted">
      Reviewer
    </span>
  ) : (
    <span className="whitespace-nowrap rounded-none bg-[var(--accent-soft)] px-2 py-[3px] font-mono text-[10px] uppercase tracking-[0.04em] text-accent-ink">
      You · author
    </span>
  );
}

function Chip({ children }) {
  return (
    <span className="inline-flex items-center gap-1.5 whitespace-nowrap bg-[var(--ink-wash)] px-2 py-[3px] font-mono text-[10.5px] text-muted">
      {children}
    </span>
  );
}

function Actions({ it, onTransition }) {
  return (
    <>
      {actionsFor(it).map((a) => (
        <Button
          key={a.l}
          variant={a.k}
          size="sm"
          className="min-w-[76px] justify-center"
          onClick={(e) => {
            e.stopPropagation();
            onTransition(it.id, a.to, a.sync);
          }}
        >
          {a.l}
        </Button>
      ))}
    </>
  );
}

export default function QueuePage() {
  const router = useRouter();
  const [filter, setFilter] = useState('all');
  const [stateStore, setStateStore] = useState(() => {
    const m = {};
    SEED.forEach((s) => { m[s.id] = s.st; });
    GROUPS.forEach((g) => { m[g.id] = g.st; g.members.forEach((mm) => { m[mm.id] = mm.st; }); });
    return m;
  });
  const [order, setOrder] = useState(['!482', 'grp-authv2', '!479', '!475', '!468', '!461', '!455', '!450']);
  const [openGroups, setOpenGroups] = useState(new Set());
  const [dragId, setDragId] = useState(null);
  const [dropId, setDropId] = useState(null);

  const bySingle = Object.fromEntries(SEED.map((s) => [s.id, { ...s, st: stateStore[s.id] || s.st }]));
  const byGroup = Object.fromEntries(GROUPS.map((g) => [g.id, { ...g, st: stateStore[g.id] || g.st }]));

  const transition = useCallback((id, to) => {
    setStateStore((prev) => {
      const next = { ...prev, [id]: to };
      if (to === 'ANALYSING') {
        setTimeout(() => setStateStore((p) => ({ ...p, [id]: 'ANALYSED' })), 2600);
      }
      return next;
    });
  }, []);

  const move = (id, dir) => {
    setOrder((prev) => {
      const i = prev.indexOf(id);
      const j = i + dir;
      if (j < 0 || j >= prev.length) return prev;
      const next = [...prev];
      [next[i], next[j]] = [next[j], next[i]];
      return next;
    });
  };

  const reorder = (from, to) => {
    setOrder((prev) => {
      const next = prev.filter((x) => x !== from);
      next.splice(next.indexOf(to), 0, from);
      return next;
    });
  };

  const toggleGroup = (id) => {
    setOpenGroups((prev) => {
      const next = new Set(prev);
      next.has(id) ? next.delete(id) : next.add(id);
      return next;
    });
  };

  const dragProps = (id) => ({
    draggable: true,
    onDragStart: () => setDragId(id),
    onDragEnd: () => { setDragId(null); setDropId(null); },
    onDragOver: (e) => { e.preventDefault(); setDropId(id); },
    onDragLeave: () => setDropId((d) => (d === id ? null : d)),
    onDrop: (e) => { e.preventDefault(); if (dragId && dragId !== id) reorder(dragId, id); },
  });

  const rowCls = (id) =>
    `transition-[border-color,box-shadow,opacity] ${dragId === id ? 'opacity-40' : ''} ${
      dropId === id ? 'border-accent border-dashed' : ''
    }`;

  const OrderBtns = ({ id }) => (
    <span className="flex flex-none flex-col">
      <button onClick={(e) => { e.stopPropagation(); move(id, -1); }} title="Up" className="px-0.5 leading-[0.8] text-faint hover:text-accent">▲</button>
      <button onClick={(e) => { e.stopPropagation(); move(id, 1); }} title="Down" className="px-0.5 leading-[0.8] text-faint hover:text-accent">▼</button>
    </span>
  );

  const Grip = () => (
    <span className="grid flex-none cursor-grab place-items-center text-faint active:cursor-grabbing" onClick={(e) => e.stopPropagation()}>
      <GripVertical className="h-4 w-4" strokeWidth={2} />
    </span>
  );

  return (
    <div className="mx-auto max-w-[1680px]">
      <div className="mb-6">
        <Overline>Personal merge queue</Overline>
        <h1 className="mt-1 font-display text-[clamp(30px,4vw,42px)] font-bold leading-[1.05] tracking-[-0.02em] text-fg">
          Queue &amp; prioritise
        </h1>
        <p className="mt-2 max-w-[72ch] text-[15px] text-muted">
          Drag to reorder, track each MR through its review state, and act on it by your role. Start
          a fresh analysis any time — order persists locally.
        </p>
      </div>

      <div className="mb-[18px] flex flex-wrap items-center justify-between gap-3">
        <Tabs variant="segment" items={FILTERS} activeKey={filter} onTabChange={setFilter} />
        <Button variant="primary" size="sm" onClick={() => router.push('/analyze')}>
          <Plus className="h-4 w-4" strokeWidth={2} />
          New analysis
        </Button>
      </div>

      <div className="flex flex-col gap-2.5">
        {order.map((id) => {
          if (isGroup(id)) {
            const g = byGroup[id];
            if (!g) return null;
            const open = openGroups.has(id);
            return (
              <div
                key={id}
                {...dragProps(id)}
                className={`rounded-none border border-border-2 bg-[var(--ink-wash)] hover:border-accent hover:shadow-[0_4px_16px_rgba(28,26,22,0.05)] ${rowCls(id)}`}
              >
                <div
                  className="flex cursor-pointer items-center gap-3.5 border-0 bg-transparent px-4 py-3"
                  role="button"
                  tabIndex={0}
                >
                  <Grip />
                  <span
                    className="grid flex-none cursor-pointer place-items-center p-1 text-muted hover:text-accent"
                    onClick={(e) => { e.stopPropagation(); toggleGroup(id); }}
                    style={{ transform: open ? 'rotate(90deg)' : 'none', transition: 'transform .18s' }}
                  >
                    <ChevronRight className="h-4 w-4" strokeWidth={2} />
                  </span>
                  <div className="flex w-[116px] flex-none flex-col gap-2">
                    <span className="w-max rounded-none bg-[var(--accent-soft)] px-[9px] py-1 font-mono text-[12px] font-bold text-accent-ink">SET</span>
                    <StateChip st={g.st} />
                  </div>
                  <div className="min-w-0 flex-1">
                    <div className="text-[14.5px] font-semibold text-fg">
                      {g.name}
                      <span className="ml-2 font-mono text-[11px] font-medium text-muted">{g.members.length} MRs</span>
                    </div>
                    <div className="mt-2 flex flex-wrap items-center gap-[7px]">
                      <Chip>{g.note}</Chip>
                      <RoleTag role={g.role} />
                    </div>
                  </div>
                  <div className="flex flex-none items-center gap-3">
                    <Actions it={g} onTransition={transition} />
                  </div>
                  <OrderBtns id={id} />
                </div>
                {open && (
                  <div className="grid gap-2 px-3 pb-3">
                    {g.members.map((mm) => {
                      const m = { ...mm, st: stateStore[mm.id] || mm.st };
                      return (
                        <div key={m.id} className="flex items-center gap-3.5 rounded-none border border-border bg-surface-2 px-[13px] py-2.5">
                          <div className="flex w-[116px] flex-none flex-col gap-2">
                            <span className="w-max rounded-none bg-fg px-2 py-1 font-mono text-[12px] font-semibold text-surface">{m.id}</span>
                            <StateChip st={m.st} />
                          </div>
                          <div className="min-w-0 flex-1">
                            <div className="text-[14.5px] font-semibold text-fg">{m.title}</div>
                            <div className="mt-2 flex flex-wrap items-center gap-[7px]">
                              <Chip>{m.meta}</Chip>
                              <Chip>
                                <span className="h-[7px] w-[7px] flex-none rounded-full" style={{ background: m.sevc }} />
                                {m.sev}
                              </Chip>
                            </div>
                          </div>
                        </div>
                      );
                    })}
                  </div>
                )}
              </div>
            );
          }

          const m = bySingle[id];
          if (!m) return null;
          return (
            <div
              key={id}
              {...dragProps(id)}
              role="button"
              tabIndex={0}
              className={`flex cursor-pointer items-center gap-3.5 rounded-none border border-border bg-surface px-4 py-3 hover:border-accent hover:shadow-[0_4px_16px_rgba(28,26,22,0.05)] ${rowCls(id)}`}
            >
              <Grip />
              <div className="flex w-[124px] flex-none flex-col gap-[7px] self-stretch justify-center border-r border-border pr-3.5">
                <span className={`w-max rounded-none px-2 py-1 font-mono text-[12px] font-semibold ${m.crit ? 'bg-accent text-on-accent' : 'bg-fg text-surface'}`}>{m.id}</span>
                <StateChip st={m.st} />
              </div>
              <div className="min-w-0 flex-1">
                <div className="text-[14.5px] font-semibold text-fg">{m.title}</div>
                <div className="mt-2 flex flex-wrap items-center gap-[7px]">
                  <Chip>{m.meta}</Chip>
                  <Chip>
                    <span className="h-[7px] w-[7px] flex-none rounded-full" style={{ background: m.sevc }} />
                    {m.sev}
                  </Chip>
                  <RoleTag role={m.role} />
                </div>
              </div>
              <div className="flex flex-none items-center gap-3">
                <div className="flex">
                  {m.who.map((w, i) => (
                    <Avatar
                      key={i}
                      initials={w}
                      size="md"
                      variant={w === 'A' ? 'ai' : 'default'}
                      className={`border-2 border-surface ${i > 0 ? '-ml-[7px]' : ''}`}
                    />
                  ))}
                </div>
                <Actions it={m} onTransition={transition} />
              </div>
              <OrderBtns id={id} />
            </div>
          );
        })}
      </div>
    </div>
  );
}
