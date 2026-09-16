'use client';

import React, { useEffect, useState } from 'react';
import Link from 'next/link';
import { GitMerge, GitPullRequest, RefreshCw, Timer } from 'lucide-react';
import { Overline } from '@/components/ui/Overline';
import { EmptyState } from '@/components/ui/EmptyState';
import { ErrorState } from '@/components/ui/ErrorState';
import { LoadingState } from '@/components/ui/LoadingState';
import { Button } from '@/components/ui/Button';
import { StatCard } from '@/components/ui/StatCard';
import { Badge } from '@/components/ui/Badge';
import { useAppData } from '@/components/layout/AppLayout';

const heightClasses = [
  'h-[18%]', 'h-[28%]', 'h-[38%]', 'h-[48%]', 'h-[58%]',
  'h-[68%]', 'h-[78%]', 'h-[88%]', 'h-full',
];

function barHeight(value, maximum) {
  if (!value || !maximum) return heightClasses[0];
  return heightClasses[Math.min(heightClasses.length - 1, Math.ceil((value / maximum) * heightClasses.length) - 1)];
}

function formatDuration(milliseconds) {
  if (!milliseconds) return '—';
  const hours = milliseconds / 3600000;
  return hours < 1 ? `${Math.round(milliseconds / 60000)}m` : `${hours.toFixed(hours < 10 ? 1 : 0)}h`;
}

function mrHref(item) {
  const mergeRequest = item.mergeRequest;
  return `/review?provider=GITLAB&projectId=${encodeURIComponent(mergeRequest.repositoryId)}&iid=${mergeRequest.changeNumber}`;
}

function MergeRequestRow({ item }) {
  const mergeRequest = item.mergeRequest;
  return (
    <Link
      href={mrHref(item)}
      className={`block border border-border bg-surface px-4 py-3 transition-colors hover:border-accent ${
        item.blocked ? 'border-l-[3px] border-l-accent' : ''
      }`}
    >
      <div className="flex min-w-0 items-center gap-3">
        <span className="bg-fg px-2 py-1 font-mono text-[11px] font-semibold text-surface">
          !{mergeRequest.changeNumber}
        </span>
        <span className="min-w-0 flex-1 truncate text-[14px] font-semibold text-fg">
          {mergeRequest.title}
        </span>
        <Badge>{item.reviewState.replaceAll('_', ' ')}</Badge>
        {item.blocked ? <Badge variant="warning">Blocked</Badge> : null}
      </div>
      <div className="mt-1.5 flex flex-wrap gap-x-3 gap-y-1 font-mono text-[10.5px] text-muted">
        <span>{mergeRequest.repositoryPath}</span>
        <span>{mergeRequest.sourceBranch} → {mergeRequest.targetBranch}</span>
        <span>{item.roles.join(' · ') || 'synced'}</span>
      </div>
    </Link>
  );
}

function QueueRow({ item }) {
  if (item.targetType === 'GROUP') {
    return (
      <div className="border border-border-2 bg-[var(--ink-wash)]">
        <div className="flex items-center gap-3 px-4 py-3">
          <Badge variant="critical">Set</Badge>
          <div className="min-w-0 flex-1">
            <div className="truncate text-[14px] font-semibold text-fg">{item.group.name}</div>
            <div className="font-mono text-[10.5px] text-muted">
              {item.group.members.length} synchronized MRs · reviewed together
            </div>
          </div>
          {item.blocked ? <Badge variant="warning">Blocked</Badge> : null}
        </div>
      </div>
    );
  }
  return <MergeRequestRow item={item.mergeRequest} />;
}

export default function DashboardPage() {
  const appData = useAppData();
  const [currentTime, setCurrentTime] = useState(null);

  useEffect(() => {
    const updateTime = () => setCurrentTime(new Date());
    updateTime();
    const interval = window.setInterval(updateTime, 60000);
    return () => window.clearInterval(interval);
  }, []);

  const name = appData.user?.displayName || appData.user?.username || appData.user?.email || 'reviewer';
  const queue = appData.queue;
  const stats = appData.productivity;
  const weeks = stats?.weekly || [];
  const weeklyTotals = weeks.map((week) => week.authoredMerged + week.reviewWorkCompleted);
  const maximum = Math.max(1, ...weeklyTotals);
  const syncRunning = appData.syncBusy;

  return (
    <div className="mx-auto max-w-[1280px]">
      <div className="mb-7">
        <Overline>
          {currentTime
            ? new Intl.DateTimeFormat(undefined, { dateStyle: 'full', timeStyle: 'short' }).format(currentTime)
            : 'Current session'}
        </Overline>
        <h1 className="mt-1 font-display text-[clamp(30px,4vw,42px)] font-bold leading-[1.05] tracking-[-0.02em] text-fg">
          Welcome, {name.split(/\s+/)[0]}.
        </h1>
        <p className="mt-2 max-w-[72ch] text-[15px] text-muted">
          Your GitLab work is synchronized into Arnold, ready to review without per-row API calls.
        </p>
      </div>

      {appData.sync && appData.integration?.connected ? (
        <div className={`mb-5 flex flex-wrap items-center gap-3 border px-4 py-3 ${
          (appData.syncError || appData.sync.status === 'FAILED')
            ? 'border-bad bg-[color-mix(in_oklch,var(--bad)_8%,var(--surface))]'
            : 'border-border bg-surface'
        }`}>
          <RefreshCw className={`h-4 w-4 text-accent ${syncRunning ? 'animate-spin' : ''}`} aria-hidden="true" />
          <div className="min-w-0 flex-1">
            <div className="text-[13px] font-semibold text-fg">
              {syncRunning ? 'GitLab sync is running' : appData.syncError || appData.sync.status === 'FAILED' ? 'GitLab sync needs attention' : 'GitLab synchronization'}
            </div>
            <div className="font-mono text-[10.5px] text-muted">
              {appData.sync.lastSucceededAt
                ? `Last synced ${new Date(appData.sync.lastSucceededAt).toLocaleString()}`
                : 'GitLab has not been synced yet'}
              {appData.syncError?.message || appData.sync.failure ? ` · ${appData.syncError?.message || appData.sync.failure}` : ''}
            </div>
          </div>
          <Button variant="secondary" size="sm" disabled={syncRunning} onClick={appData.startSync}>
            {syncRunning ? 'Syncing…' : 'Sync now'}
          </Button>
        </div>
      ) : null}

      {stats ? (
        <div className="mb-6 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
          <StatCard label="Open review work" value={stats.openReviewWork} accent icon={<GitPullRequest className="h-4 w-4" />} />
          <StatCard label="Your open MRs" value={stats.openAuthored} icon={<GitMerge className="h-4 w-4" />} />
          <StatCard label="Merged in window" value={stats.merged} delta={stats.throughputDeltaPct == null ? 'No prior-week baseline' : `${stats.throughputDeltaPct >= 0 ? '▲' : '▼'} ${Math.abs(stats.throughputDeltaPct).toFixed(0)}% vs last week`} deltaTone={stats.throughputDeltaPct >= 0 ? 'up' : 'down'} />
          <StatCard label="Median time to merge" value={formatDuration(stats.medianTimeToMergeMs)} icon={<Timer className="h-4 w-4" />} />
        </div>
      ) : null}

      {!queue && !appData.queueError ? <LoadingState label="Loading workspace" /> : null}
      {appData.queueError ? (
        <ErrorState title="Could not load your workspace" error={appData.queueError} onRetry={() => appData.refreshBootstrap().catch(() => {})} />
      ) : null}

      {queue ? (
        <div>
          <div className="mb-3 flex items-center justify-between gap-4">
            <h2 className="font-display text-[21px] font-bold text-fg">Your merge request queue</h2>
            <Link href="/queue"><Button variant="link">Open full queue →</Button></Link>
          </div>
          {queue.items.length === 0 ? (
            <EmptyState
              title={syncRunning ? 'Building your queue' : 'Queue empty'}
              description={syncRunning ? 'Arnold is importing your open GitLab work.' : 'Open merge requests will be added automatically during sync.'}
            />
          ) : (
            <div className="flex flex-col gap-2.5">
              {queue.items.slice(0, 5).map((item) => <QueueRow item={item} key={item.id} />)}
            </div>
          )}

          <div className="mb-3 mt-8 flex items-center justify-between gap-4">
            <h2 className="font-display text-[21px] font-bold text-fg">Your productivity</h2>
            <span className="border border-border-2 px-2.5 py-1 font-mono text-[10px] uppercase text-muted">Last 6 weeks</span>
          </div>
          <div className="border border-border bg-surface p-5">
            {weeks.length === 0 ? (
              <p className="py-8 text-center text-[13px] text-muted">Productivity appears after the first completed sync.</p>
            ) : (
              <div className="flex h-[180px] items-end gap-2 sm:gap-4">
                {weeks.map((week, index) => {
                  const total = weeklyTotals[index];
                  return (
                    <div key={week.weekStart} className="flex h-full min-w-0 flex-1 flex-col justify-end">
                      <span className="mb-2 text-center font-mono text-[10px] text-muted">{total}</span>
                      <div className={`mx-auto w-full max-w-10 ${index === weeks.length - 1 ? 'bg-accent' : 'bg-border-2'} ${barHeight(total, maximum)}`} />
                      <span className="mt-2 truncate text-center font-mono text-[9px] uppercase text-faint">
                        {new Date(`${week.weekStart}T00:00:00`).toLocaleDateString(undefined, { month: 'short', day: 'numeric' })}
                      </span>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        </div>
      ) : null}
    </div>
  );
}
