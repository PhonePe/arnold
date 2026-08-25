'use client';

import React, { useEffect, useState } from 'react';
import { GitMerge, GitPullRequest, Timer, Users } from 'lucide-react';
import { Overline } from '@/components/ui/Overline';
import { Tabs } from '@/components/ui/Tabs';
import { StatCard } from '@/components/ui/StatCard';
import { LoadingState } from '@/components/ui/LoadingState';
import { ErrorState } from '@/components/ui/ErrorState';
import { EmptyState } from '@/components/ui/EmptyState';
import { useAppData } from '@/components/layout/AppLayout';
import { fetchStats } from '@/lib/api';

const scopes = [
  { key: 'personal', label: 'Personal' },
  { key: 'organization', label: 'Organisation' },
];

const heightClasses = [
  'h-[14%]', 'h-[24%]', 'h-[34%]', 'h-[44%]', 'h-[54%]',
  'h-[64%]', 'h-[74%]', 'h-[84%]', 'h-full',
];

function barHeight(value, maximum) {
  if (!value || !maximum) return heightClasses[0];
  return heightClasses[Math.min(heightClasses.length - 1, Math.ceil((value / maximum) * heightClasses.length) - 1)];
}

function duration(milliseconds) {
  if (!milliseconds) return '—';
  const minutes = Math.round(milliseconds / 60000);
  if (minutes < 60) return `${minutes}m`;
  return `${(minutes / 60).toFixed(minutes < 600 ? 1 : 0)}h`;
}

export default function StatsPage() {
  const appData = useAppData();
  const [scope, setScope] = useState('personal');
  const [stats, setStats] = useState(appData.productivity);
  const [status, setStatus] = useState(appData.productivity ? 'ready' : 'loading');
  const [error, setError] = useState(null);
  const [revision, setRevision] = useState(0);

  useEffect(() => {
    if (scope === 'personal' && appData.productivity && revision === 0) {
      return undefined;
    }
    const controller = new AbortController();
    fetchStats(scope, { signal: controller.signal })
      .then((response) => {
        if (!controller.signal.aborted) {
          setStats(response);
          setStatus('ready');
        }
      })
      .catch((nextError) => {
        if (!controller.signal.aborted) {
          setError(nextError);
          setStatus('error');
        }
      });
    return () => controller.abort();
  }, [appData.productivity, revision, scope]);

  const displayedStats = scope === 'personal' && revision === 0 && appData.productivity
    ? appData.productivity
    : stats;
  const displayedStatus = displayedStats && scope === 'personal' && revision === 0 ? 'ready' : status;
  const weeks = displayedStats?.weekly || [];
  const maximum = Math.max(1, ...weeks.flatMap((week) => [week.authoredMerged, week.reviewWorkCompleted]));
  const delta = displayedStats?.throughputDeltaPct;

  function changeScope(nextScope) {
    setScope(nextScope);
    setError(null);
    setStatus(nextScope === 'personal' && appData.productivity ? 'ready' : 'loading');
  }

  function retry() {
    setError(null);
    setStatus('loading');
    setRevision((value) => value + 1);
  }

  return (
    <div className="mx-auto max-w-[1280px]">
      <div className="mb-6">
        <Overline>Metrics</Overline>
        <h1 className="mt-1 font-display text-[clamp(30px,4vw,42px)] font-bold leading-[1.05] tracking-[-0.02em] text-fg">
          Stats &amp; productivity
        </h1>
        <p className="mt-2 max-w-[72ch] text-[15px] text-muted">
          Merge throughput and review load derived from synchronized GitLab history.
        </p>
      </div>

      <Tabs className="mb-5" items={scopes} activeKey={scope} onTabChange={changeScope} variant="segment" />

      {displayedStatus === 'loading' ? <LoadingState label={`Loading ${scope} stats`} /> : null}
      {displayedStatus === 'error' ? (
        <ErrorState title="Could not load statistics" error={error} onRetry={retry} />
      ) : null}

      {displayedStatus === 'ready' && displayedStats ? (
        <>
          <div className="mb-5 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
            <StatCard
              label="Open authored MRs"
              value={displayedStats.openAuthored}
              accent
              icon={<GitMerge className="h-4 w-4" />}
              delta={scope === 'organization' ? 'Across indexed Arnold data' : 'Owned by you'}
            />
            <StatCard
              label="Open review work"
              value={displayedStats.openReviewWork}
              icon={scope === 'organization' ? <Users className="h-4 w-4" /> : <GitPullRequest className="h-4 w-4" />}
              delta={scope === 'organization' ? 'Assignments and reviews' : 'Assigned or reviewing'}
            />
            <StatCard
              label="Merged in window"
              value={displayedStats.merged}
              delta={delta == null ? 'No previous-week baseline' : `${delta >= 0 ? '▲' : '▼'} ${Math.abs(delta).toFixed(0)}% vs last week`}
              deltaTone={delta == null ? 'neutral' : delta >= 0 ? 'up' : 'down'}
            />
            <StatCard
              label="Median time to merge"
              value={duration(displayedStats.medianTimeToMergeMs)}
              icon={<Timer className="h-4 w-4" />}
              delta="Created → merged"
            />
          </div>

          {weeks.length === 0 ? (
            <EmptyState title="Not enough synchronized history" description="Weekly productivity appears after merge history has been indexed." />
          ) : (
            <section className="border border-border bg-surface p-5 sm:p-6">
              <div className="mb-6 flex flex-wrap items-start justify-between gap-4">
                <div>
                  <h2 className="font-display text-[18px] font-bold text-fg">Six-week throughput</h2>
                  <p className="mt-1 text-[12.5px] text-muted">Authored merges and completed review work share the same weekly scale.</p>
                </div>
                <div className="flex gap-4 font-mono text-[10px] uppercase tracking-[0.05em] text-muted">
                  <span className="inline-flex items-center gap-1.5"><span className="h-2 w-2 bg-accent" /> Authored</span>
                  <span className="inline-flex items-center gap-1.5"><span className="h-2 w-2 bg-border-2" /> Reviewed</span>
                </div>
              </div>
              <div className="flex h-[260px] items-end gap-2 sm:gap-5">
                {weeks.map((week) => (
                  <div key={week.weekStart} className="flex h-full min-w-0 flex-1 flex-col justify-end">
                    <div className="flex h-[210px] items-end justify-center gap-1 sm:gap-2">
                      <div className={`w-3 bg-accent sm:w-5 ${barHeight(week.authoredMerged, maximum)}`} title={`${week.authoredMerged} authored merges`} />
                      <div className={`w-3 bg-border-2 sm:w-5 ${barHeight(week.reviewWorkCompleted, maximum)}`} title={`${week.reviewWorkCompleted} reviews completed`} />
                    </div>
                    <div className="mt-2 text-center">
                      <div className="font-mono text-[10px] text-fg">{week.authoredMerged + week.reviewWorkCompleted}</div>
                      <div className="mt-0.5 truncate font-mono text-[9px] uppercase text-faint">
                        {new Date(`${week.weekStart}T00:00:00`).toLocaleDateString(undefined, { month: 'short', day: 'numeric' })}
                      </div>
                    </div>
                  </div>
                ))}
              </div>
              <div className="mt-5 border-t border-border pt-3 font-mono text-[10px] text-muted">
                {displayedStats.dataThrough
                  ? `Indexed through ${new Date(displayedStats.dataThrough).toLocaleString()} · generated ${new Date(displayedStats.generatedAt).toLocaleString()}`
                  : 'Waiting for the first Elasticsearch projection pass'}
              </div>
            </section>
          )}
        </>
      ) : null}
    </div>
  );
}
