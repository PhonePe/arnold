'use client';

import React from 'react';
import { BookOpen } from 'lucide-react';

export default function KnowledgePage() {
  return (
    <div className="flex flex-col items-center justify-center h-full min-h-[60vh] text-center">
      <BookOpen className="w-12 h-12 text-on-surface-faint mb-4" />
      <h1 className="text-2xl font-bold font-headline text-on-surface mb-2">Knowledge Base</h1>
      <p className="text-sm text-on-surface-muted font-body max-w-md">
        Review policies and learned rules will appear here. Manage organization-wide coding standards and AI-generated insights.
      </p>
    </div>
  );
}
