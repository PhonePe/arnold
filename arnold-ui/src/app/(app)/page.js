'use client';

import React, { useState, useEffect } from 'react';
import { Card } from '@/components/ui/Card';
import { StatCard } from '@/components/ui/StatCard';
import { Overline } from '@/components/ui/Overline';
import { Badge } from '@/components/ui/Badge';
import { MRListItem } from '@/components/composite/MRListItem';
import { AnalysisCard } from '@/components/composite/AnalysisCard';
import { useUser } from '@/components/layout/AppLayout';
import { fetchAssignedMRs } from '@/lib/api';

const statusFromMR = (mr) => {
  if (mr.merge_status === 'cannot_be_merged') return 'issue-found';
  if (mr.draft) return 'analyzing';
  if (mr.merge_status === 'can_be_merged') return 'ready';
  return 'analyzing';
};

// Mock productivity data (MRs reviewed / week)
const productivity = [
  { week: 'W1', value: 11 },
  { week: 'W2', value: 13 },
  { week: 'W3', value: 12 },
  { week: 'W4', value: 16 },
  { week: 'W5', value: 14 },
  { week: 'W6', value: 18, hi: true },
];

// Placeholder recent analyses
const recentAnalyses = [
  { id: 'ANALYSIS #9921', title: 'Data pipeline refactor', status: 'in-progress' },
  { id: 'ANALYSIS #9918', title: 'Auth middleware audit', status: 'complete' },
  { id: 'ANALYSIS #9914', title: 'SQL injection scan', status: 'critical-alert' },
];

function SectionTitle({ children, action }) {
  return (
    <div className="mb-3.5 mt-1.5 flex items-center justify-between gap-4">
      <h2 className="font-display text-[21px] font-bold tracking-[-0.01em] text-fg">{children}</h2>
      {action}
    </div>
  );
}

function ProductivityChart() {
  const max = Math.max(...productivity.map((d) => d.value));
  return (
    <Card className="p-5">
      <div className="mb-1.5 flex items-center justify-between">
        <Overline tone="faint">MRs reviewed / week</Overline>
        <span className="font-mono text-[11px] text-accent-ink">avg 14.2 · ▲ 12%</span>
      </div>
      <div className="mb-5 flex h-[120px] items-end gap-2.5 pt-2.5">
        {productivity.map((d) => (
          <div key={d.week} className="relative flex h-full flex-1 flex-col justify-end">
            <div
              className={`w-full transition-colors ${d.hi ? 'bg-accent' : 'bg-border-2'}`}
              style={{ height: `${(d.value / max) * 100}%`, minHeight: 4 }}
            />
            <span className="absolute -bottom-5 left-0 right-0 text-center font-mono text-[10px] text-faint">{d.week}</span>
          </div>
        ))}
      </div>
    </Card>
  );
}

function OpenMRsCard() {
  const items = [
    { id: '!488', title: 'feat: streaming diff viewer', meta: '2 reviewers · awaiting approval · 3h', status: 'in-progress' },
    { id: '!486', title: 'chore: bump telemetry SDK', meta: '1 change requested · 1d', status: 'in-progress' },
    { id: '!483', title: 'refactor: extract token cache', meta: 'Approved · ready to merge · 5h', status: 'complete' },
  ];
  return (
    <Card className="p-5">
      <div className="mb-1.5 flex items-center justify-between">
        <h3 className="font-display text-[16px] font-bold text-fg">Your open MRs</h3>
        <button className="font-mono text-[12px] text-muted hover:text-accent">All →</button>
      </div>
      <div className="-mx-1">
        {items.map((m) => (
          <div key={m.id} className="flex items-start gap-3 border-t border-border px-1 py-3 first:border-t-0">
            <span className="mt-0.5 font-mono text-[12px] font-semibold text-fg">{m.id}</span>
            <div className="min-w-0">
              <div className="truncate text-[13px] font-medium text-fg">{m.title}</div>
              <div className="mt-0.5 font-mono text-[11px] text-muted">{m.meta}</div>
            </div>
          </div>
        ))}
      </div>
    </Card>
  );
}

function MRSection({ mergeRequests, loading }) {
  return (
    <div>
      <SectionTitle
        action={
          <button className="font-mono text-[12px] text-muted hover:text-accent">Open full queue →</button>
        }
      >
        Your merge queue
      </SectionTitle>
      <div className="flex flex-col gap-2.5">
        {loading && (
          <div className="py-8 text-center text-sm text-muted">Loading merge requests…</div>
        )}
        {!loading && mergeRequests.length === 0 && (
          <div className="rounded-none border border-border bg-surface py-8 text-center text-sm text-muted">
            No assigned merge requests found.
          </div>
        )}
        {mergeRequests.map((mr) => (
          <MRListItem
            key={mr.id}
            branch={mr.title}
            mrNumber={`!${mr.iid}`}
            status={statusFromMR(mr)}
            critical={mr.merge_status === 'cannot_be_merged'}
            meta={[
              mr.references?.full?.split('!')[0]?.replace(/^\//, ''),
              mr.source_branch,
            ]
              .filter(Boolean)
              .join(' · ')}
            reviewers={(mr.reviewers || []).map(
              (r) => r.name?.split(' ').map((w) => w[0]).join('').slice(0, 2) || '?',
            )}
          />
        ))}
      </div>

      <SectionTitle action={<Badge variant="legacy">last 6 weeks</Badge>}>
        Your productivity
      </SectionTitle>
      <ProductivityChart />
    </div>
  );
}

function RecentAnalysesCard() {
  return (
    <Card className="p-5">
      <Overline tone="faint" className="mb-3 block">
        Recent analyses
      </Overline>
      <div className="space-y-2.5">
        {recentAnalyses.map((a) => (
          <AnalysisCard key={a.id} analysisId={a.id} title={a.title} status={a.status} />
        ))}
      </div>
    </Card>
  );
}

export default function DashboardPage() {
  const user = useUser();
  const [mergeRequests, setMergeRequests] = useState([]);
  const [loadingMRs, setLoadingMRs] = useState(true);

  useEffect(() => {
    if (!user?.username) return;

    let cancelled = false;

    // Note: loadingMRs starts true and is cleared in .finally; we avoid a
    // synchronous setState at the top of the effect (cascading renders).
    fetchAssignedMRs(user.username, { perPage: 5 })
      .then((mrs) => {
        if (!cancelled) setMergeRequests(mrs);
      })
      .catch((err) => {
        console.warn('Failed to fetch MRs:', err.message);
      })
      .finally(() => {
        if (!cancelled) setLoadingMRs(false);
      });

    return () => {
      cancelled = true;
    };
  }, [user?.username]);

  const firstName = (user?.name || 'there').split(' ')[0];

  return (
    <div className="mx-auto max-w-[1680px]">
      {/* Page head */}
      <div className="mb-6">
        <Overline>Today · 09:14</Overline>
        <h1 className="mt-1 font-display text-[clamp(30px,4vw,42px)] font-bold leading-[1.05] tracking-[-0.02em] text-fg">
          Welcome back, {firstName}.
        </h1>
        <p className="mt-2 max-w-[70ch] text-[15px] text-muted">
          Arnold analysed <b className="text-fg">12 merge requests</b> since your last session.
          High-risk patterns detected in{' '}
          <span className="font-mono text-[0.92em] text-accent-ink">!482 auth-service</span>. Your
          queue is <b className="text-fg">reviewer-balanced</b> today.
        </p>
      </div>

      {/* Stats */}
      <div className="mb-6 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard value="7" accent label="MRs assigned to you" delta="▲ 2 since yesterday" deltaTone="up" />
        <StatCard value="23" label="Open findings to triage" delta="9 critical · 14 nit" deltaTone="warn" />
        <StatCard value="22m" label="Est. review time left" delta="▼ 31% vs last week" deltaTone="up" />
        <StatCard value="5" label="Open MRs awaiting merge" delta="2 blocked on review" deltaTone="warn" />
      </div>

      {/* Main rail */}
      <div className="grid grid-cols-1 items-start gap-7 xl:grid-cols-[minmax(0,1fr)_360px]">
        <MRSection mergeRequests={mergeRequests} loading={loadingMRs} />
        <div className="space-y-4">
          <OpenMRsCard />
          <RecentAnalysesCard />
        </div>
      </div>
    </div>
  );
}
