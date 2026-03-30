'use client';

import React, { useState, useEffect } from 'react';
import { Plus, FileText, Smile } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
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

// Mock sentiment data
const sentimentData = [
  { day: 'MON', value: 65 },
  { day: 'TUE', value: 55 },
  { day: 'WED', value: 70 },
  { day: 'THU', value: 80 },
  { day: 'FRI', value: 75 },
  { day: 'SAT', value: 45 },
];

// Placeholder recent analyses
const recentAnalyses = [
  { id: 'ANALYSIS #9921', title: 'Data Pipeline Refactor', status: 'in-progress' },
  { id: 'ANALYSIS #9918', title: 'Auth Middleware Audit', status: 'complete' },
  { id: 'ANALYSIS #9914', title: 'SQL Injection Vector Scan', status: 'critical-alert' },
];

function WelcomeBanner({ userName }) {
  return (
    <Card className="relative overflow-hidden">
      <div className="p-8 pr-48">
        <h1 className="text-3xl font-bold font-headline text-on-surface mb-2">
          Welcome back, {userName}.
        </h1>
        <p className="text-sm text-on-surface-muted font-body leading-relaxed max-w-xl">
          Your ledger currently contains 4 active merge requests and 12 pending AI
          analyses. Initialize a new project to start the Sentient Review process.
        </p>
        <div className="flex gap-3 mt-6">
          <Button variant="primary" size="md">
            <Plus className="w-4 h-4" />
            NEW PROJECT ONBOARDING
          </Button>
          <Button variant="outline" size="md">
            <FileText className="w-4 h-4" />
            VIEW DOCUMENTATION
          </Button>
        </div>
      </div>
      {/* Decorative arc */}
      <div className="absolute right-0 top-0 w-48 h-full flex items-center justify-center pointer-events-none">
        <svg viewBox="0 0 180 180" className="w-40 h-40 opacity-20">
          <circle
            cx="90"
            cy="90"
            r="70"
            fill="none"
            stroke="var(--primary-container)"
            strokeWidth="18"
            strokeDasharray="330 110"
            strokeLinecap="round"
          />
          <circle
            cx="90"
            cy="90"
            r="70"
            fill="none"
            stroke="var(--primary)"
            strokeWidth="18"
            strokeDasharray="110 330"
            strokeDashoffset="-330"
            strokeLinecap="round"
            opacity="0.5"
          />
        </svg>
      </div>
    </Card>
  );
}

function SentimentChart() {
  const max = Math.max(...sentimentData.map((d) => d.value));
  return (
    <Card className="p-5">
      <div className="flex items-center justify-between mb-4">
        <span className="text-[11px] font-semibold uppercase tracking-widest text-on-surface-muted font-body">
          TEAM SENTIMENT
        </span>
        <Smile className="w-5 h-5 text-status-success" />
      </div>
      <div className="flex items-end gap-2 h-24">
        {sentimentData.map((d) => (
          <div key={d.day} className="flex-1 flex flex-col items-center gap-1">
            <div
              className="w-full rounded-sm bg-primary-container/70 transition-all"
              style={{ height: `${(d.value / max) * 100}%` }}
            />
            <span className="text-[10px] text-on-surface-faint font-body">{d.day}</span>
          </div>
        ))}
      </div>
      <p className="text-xs text-on-surface-muted font-body mt-4">
        Sentiment has climbed <span className="text-status-success font-semibold">+12%</span> this
        week following the release of Arnold v2.4.
      </p>
    </Card>
  );
}

function MRSection({ mergeRequests, loading }) {
  return (
    <div>
      <div className="flex items-center justify-between mb-4">
        <div className="flex items-center gap-2">
          <span className="text-lg font-semibold font-headline text-on-surface">My Assigned MRs</span>
        </div>
        <button className="text-xs font-medium text-on-surface-muted hover:text-primary transition-colors font-body">
          VIEW ALL
        </button>
      </div>
      <div className="space-y-2">
        {loading && (
          <div className="text-sm text-on-surface-muted font-body py-8 text-center">
            Loading merge requests...
          </div>
        )}
        {!loading && mergeRequests.length === 0 && (
          <div className="text-sm text-on-surface-muted font-body py-8 text-center">
            No assigned merge requests found.
          </div>
        )}
        {mergeRequests.map((mr) => (
          <MRListItem
            key={mr.id}
            branch={mr.source_branch}
            mrNumber={`!${mr.iid}`}
            description={mr.title}
            status={statusFromMR(mr)}
            project={mr.references?.full?.split('!')[0]?.replace(/^\//, '') || ''}
            reviewers={
              (mr.reviewers || []).map((r) =>
                r.name?.split(' ').map((w) => w[0]).join('').slice(0, 2) || '?'
              )
            }
          />
        ))}
      </div>
    </div>
  );
}

function RecentAnalysesSection() {
  return (
    <div>
      <span className="text-[11px] font-semibold uppercase tracking-widest text-on-surface-muted font-body">
        RECENT ANALYSES
      </span>
      <div className="space-y-2 mt-3">
        {recentAnalyses.map((a) => (
          <AnalysisCard
            key={a.id}
            analysisId={a.id}
            title={a.title}
            status={a.status}
          />
        ))}
      </div>
    </div>
  );
}

export default function DashboardPage() {
  const user = useUser();
  const [mergeRequests, setMergeRequests] = useState([]);
  const [loadingMRs, setLoadingMRs] = useState(true);

  useEffect(() => {
    if (!user?.username) return;

    let cancelled = false;
    setLoadingMRs(true);

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

    return () => { cancelled = true; };
  }, [user?.username]);

  const displayName = user?.name || 'Architect';

  return (
    <div className="max-w-7xl mx-auto space-y-6">
      {/* Welcome Banner */}
      <WelcomeBanner userName={displayName} />

      {/* Main content grid */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Left column: MRs (2/3 width) */}
        <div className="lg:col-span-2">
          <MRSection mergeRequests={mergeRequests} loading={loadingMRs} />
        </div>

        {/* Right column: Sentiment + Recent Analyses (1/3 width) */}
        <div className="space-y-6">
          <SentimentChart />
          <RecentAnalysesSection />
        </div>
      </div>
    </div>
  );
}
