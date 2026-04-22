'use client';

import React from 'react';
import { TrendingUp } from 'lucide-react';

export default function StatsPage() {
  return (
    <div className="flex flex-col items-center justify-center h-full min-h-[60vh] text-center">
      <TrendingUp className="w-12 h-12 text-on-surface-faint mb-4" />
      <h1 className="text-2xl font-bold font-headline text-on-surface mb-2">Stats</h1>
      <p className="text-sm text-on-surface-muted font-body max-w-md">
        Analytics and review metrics will be displayed here. Track team performance and AI analysis trends.
      </p>
    </div>
  );
}
