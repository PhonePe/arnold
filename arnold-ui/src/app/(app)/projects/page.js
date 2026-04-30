'use client';

import React from 'react';
import { FolderKanban } from 'lucide-react';

export default function ProjectsPage() {
  return (
    <div className="flex flex-col items-center justify-center h-full min-h-[60vh] text-center">
      <FolderKanban className="w-12 h-12 text-on-surface-faint mb-4" />
      <h1 className="text-2xl font-bold font-headline text-on-surface mb-2">Projects</h1>
      <p className="text-sm text-on-surface-muted font-body max-w-md">
        Project management and onboarding will appear here. Configure your repositories and review policies.
      </p>
    </div>
  );
}
