'use client';

import React from 'react';
import { Clock } from 'lucide-react';

export default function HistoryPage() {
  return (
    <div className="flex flex-col items-center justify-center h-full min-h-[60vh] text-center">
      <Clock className="w-12 h-12 text-on-surface-faint mb-4" />
      <h1 className="text-2xl font-bold font-headline text-on-surface mb-2">Analysis History</h1>
      <p className="text-sm text-on-surface-muted font-body max-w-md">
        Past AI analysis results and review history will be listed here. Browse completed reviews and track remediation progress.
      </p>
    </div>
  );
}
