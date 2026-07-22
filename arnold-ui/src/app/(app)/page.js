'use client';

import React, { useEffect, useState } from 'react';
import Link from 'next/link';
import { Overline } from '@/components/ui/Overline';
import { EmptyState } from '@/components/ui/EmptyState';
import { ErrorState } from '@/components/ui/ErrorState';
import { LoadingState } from '@/components/ui/LoadingState';
import { Button } from '@/components/ui/Button';
import { useAppData } from '@/components/layout/AppLayout';
import { parseChangeRequestId } from '@/lib/contracts';
import { hydrateMergeQueue } from '@/lib/merge-queue';

export default function DashboardPage() {
  const appData = useAppData();
  const [details, setDetails] = useState({});
  const [currentTime, setCurrentTime] = useState(null);
  const queue = appData.queue;

  useEffect(() => {
    const updateTime = () => setCurrentTime(new Date());
    const initialTimer = window.setTimeout(updateTime, 0);
    const interval = window.setInterval(updateTime, 60000);
    return () => {
      window.clearTimeout(initialTimer);
      window.clearInterval(interval);
    };
  }, []);

  useEffect(() => {
    if (!queue) return undefined;
    const controller = new AbortController();
    hydrateMergeQueue({ ...queue, items: queue.items.slice(0, 5) }, { signal: controller.signal })
      .then((result) => !controller.signal.aborted && setDetails(result.detailsById));
    return () => controller.abort();
  }, [queue]);

  const name = appData.user?.displayName || appData.user?.username || appData.user?.email || 'reviewer';
  return (
    <div className="mx-auto max-w-[1100px]">
      <div className="mb-7">
        <Overline>{currentTime ? new Intl.DateTimeFormat(undefined, { dateStyle: 'full', timeStyle: 'short' }).format(currentTime) : 'Current session'}</Overline>
        <h1 className="mt-1 font-display text-[clamp(30px,4vw,42px)] font-bold leading-[1.05] tracking-[-0.02em] text-fg">Welcome, {name.split(/\s+/)[0]}.</h1>
        <p className="mt-2 text-[15px] text-muted">Review synchronized merge requests in your persisted Arnold queue.</p>
      </div>
      <div className="mb-4 flex items-center justify-between gap-4">
        <h2 className="font-display text-[21px] font-bold text-fg">Your merge queue</h2>
        <Link href="/queue"><Button variant="link">Open full queue →</Button></Link>
      </div>
      {!queue && !appData.queueError ? <LoadingState label="Loading queue" /> : null}
      {appData.queueError ? <ErrorState title="Could not load queue" error={appData.queueError} onRetry={() => appData.refreshQueue().catch(() => {})} /> : null}
      {queue?.items.length === 0 ? <EmptyState title="Queue empty" description="Add a synchronized merge request from the queue page." action={<Link href="/queue"><Button>Open queue</Button></Link>} /> : null}
      <div className="flex flex-col gap-2.5">
        {queue?.items.slice(0, 5).map((item) => {
          const identity = parseChangeRequestId(item.changeRequestId);
          const detail = details[item.changeRequestId];
          return (
            <Link key={item.id} href={`/review?provider=GITLAB&projectId=${encodeURIComponent(identity.repositoryId)}&iid=${identity.number}`} className="border border-border bg-surface px-4 py-3 hover:border-accent">
              <div className="flex items-center gap-3"><span className="font-mono text-xs font-semibold text-accent-ink">!{identity.number}</span><span className="truncate text-sm font-semibold text-fg">{detail?.title || 'MR details unavailable'}</span><span className="ml-auto font-mono text-[10px] uppercase text-muted">{item.role}</span></div>
              <div className="mt-1 font-mono text-[11px] text-muted">{detail?.repositoryPath || item.changeRequestId}</div>
            </Link>
          );
        })}
      </div>
    </div>
  );
}
