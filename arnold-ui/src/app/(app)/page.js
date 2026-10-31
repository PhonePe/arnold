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
import { useRouter } from 'next/navigation';
import { ArrowRight, GitMerge, GitPullRequest, ListChecks, RefreshCw, Timer } from 'lucide-react';
import { Overline } from '@/components/ui/Overline';
import { EmptyState } from '@/components/ui/EmptyState';
import { ErrorState } from '@/components/ui/ErrorState';
import { LoadingState } from '@/components/ui/LoadingState';
import { Button } from '@/components/ui/Button';
import { StatCard } from '@/components/ui/StatCard';
import { Badge } from '@/components/ui/Badge';
import { useAppData } from '@/components/layout/AppLayout';
import { analysisEntry, reviewHref } from '@/lib/analysis-navigation.mjs';
import { barHeight } from '@/lib/bar-height';
import { changeReference, changeRequestNoun, repositorySyncChangeRequests } from '@/lib/setup-guide.mjs';

function formatDuration(milliseconds) {
  if (!milliseconds) return '—';
  const hours = milliseconds / 3600000;
  return hours < 1 ? `${Math.round(milliseconds / 60000)}m` : `${hours.toFixed(hours < 10 ? 1 : 0)}h`;
}

function mrHref(item) {
  const mergeRequest = item.mergeRequest;
  return reviewHref({ provider: mergeRequest.provider, projectId: mergeRequest.repositoryId, iid: mergeRequest.changeNumber });
}

function MergeRequestRow({ item }) {
  const router = useRouter();
  const mergeRequest = item.mergeRequest;
  const terminalState = ['closed', 'merged'].includes(mergeRequest.state?.toLowerCase())
    ? mergeRequest.state
    : item.reviewState;
  const action = analysisEntry(terminalState, mergeRequest.id);
  return (
    <div
      className={`relative border border-border bg-surface px-4 py-3 transition-colors hover:border-accent ${
        item.blocked ? 'border-l-[3px] border-l-accent' : ''
      }`}
    >
      <div className="flex min-w-0 items-center gap-3">
        <span className="bg-fg px-2 py-1 font-mono text-[11px] font-semibold text-surface">
          {changeReference(mergeRequest)}
        </span>
        <Link href={mrHref(item)} className="min-w-0 flex-1 truncate text-[14px] font-semibold text-fg after:absolute after:inset-0 hover:text-accent-ink">
          {mergeRequest.title}
        </Link>
        <Badge>{item.reviewState.replaceAll('_', ' ')}</Badge>
        {item.blocked ? <Badge variant="warning">Blocked</Badge> : null}
        {action ? <Button className="relative z-10" variant="secondary" size="xs" onClick={() => router.push(action.href)}>{action.label}</Button> : null}
      </div>
      <div className="mt-1.5 flex flex-wrap gap-x-3 gap-y-1 font-mono text-[10.5px] text-muted">
        <span>{mergeRequest.repositoryPath}</span>
        <span>{mergeRequest.sourceBranch} → {mergeRequest.targetBranch}</span>
        <span>{item.roles.join(' · ') || 'synced'}</span>
      </div>
    </div>
  );
}

function SetupCard({ guide, onOpen }) {
  const remaining = guide.steps.filter((step) => !step.done);
  return (
    <section className="mb-6 flex flex-col gap-4 border border-border border-l-[3px] border-l-accent bg-surface p-5 sm:flex-row sm:items-center" aria-labelledby="finish-setup-title">
      <div className="min-w-0 flex-1">
        <Overline>Setup · {guide.steps.length - remaining.length}/{guide.steps.length} complete</Overline>
        <h2 id="finish-setup-title" className="mt-1 font-display text-lg font-bold tracking-tight text-fg">Finish setting up Arnold</h2>
        <p className="mt-1 text-[13px] text-muted">Next: {remaining.map((step) => step.title).join(' · ')}</p>
      </div>
      <Button variant="primary" onClick={onOpen}>Finish setup <ArrowRight className="h-4 w-4" aria-hidden="true" /></Button>
    </section>
  );
}

function BrowseAllCta({ count, noun }) {
  return (
    <div className="flex flex-col items-start gap-3 border border-border bg-surface p-5 sm:flex-row sm:items-center">
      <ListChecks className="h-5 w-5 flex-none text-accent" strokeWidth={1.8} aria-hidden="true" />
      <div className="min-w-0 flex-1">
        <div className="text-[14px] font-semibold text-fg">Nothing assigned to you yet</div>
        <p className="mt-0.5 text-[13px] text-muted">
          <span className="font-mono">{count}</span> open {noun} are available across your organisation&apos;s repositories.
        </p>
      </div>
      <Link href="/queue?scope=all"><Button variant="secondary">Browse all open {noun} <ArrowRight className="h-4 w-4" aria-hidden="true" /></Button></Link>
    </div>
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
              {item.group.members.length} changes · reviewed together
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
  const syncRunning = appData.gitLabConfigured && appData.syncBusy;
  const noun = changeRequestNoun(appData.setup);
  const availableChanges = repositorySyncChangeRequests(appData.repositorySync);
  const guide = appData.setupGuide;

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
          Your {noun} are imported into Arnold automatically, ready to review.
        </p>
      </div>

      {guide?.pending ? <SetupCard guide={guide} onOpen={appData.openSetupGuide} /> : null}
      {syncRunning ? (
        <p role="status" className="mb-5 border border-border bg-surface px-4 py-3 text-[13px] text-muted">
          <RefreshCw className="mr-2 inline h-4 w-4 animate-spin text-accent" aria-hidden="true" />
          GitLab sync is running. Open merge requests will appear as they are imported.
        </p>
      ) : null}
      {stats ? (
        <div className="mb-6 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
          <StatCard label="Open review work" value={stats.openReviewWork} accent icon={<GitPullRequest className="h-4 w-4" />} />
          <StatCard label="Your open changes" value={stats.openAuthored} icon={<GitMerge className="h-4 w-4" />} />
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
            <h2 className="font-display text-[21px] font-bold text-fg">Your review queue</h2>
            <Link href="/queue"><Button variant="link">Open full queue →</Button></Link>
          </div>
          {queue.items.length === 0 && !syncRunning && availableChanges > 0 ? (
            <BrowseAllCta count={availableChanges} noun={noun} />
          ) : queue.items.length === 0 ? (
            <EmptyState
              title={syncRunning ? 'Building your queue' : 'Queue empty'}
              description={syncRunning ? 'Arnold is importing your open GitLab work.' : `Open ${noun} you author or review will appear here automatically.`}
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
              <p className="py-8 text-center text-[13px] text-muted">{appData.gitLabConfigured ? 'Productivity appears after the first completed sync.' : `Productivity appears once your ${noun} have review activity.`}</p>
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
