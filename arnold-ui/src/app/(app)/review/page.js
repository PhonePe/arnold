'use client';

import React, { Suspense, useEffect } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { LoadingState } from '@/components/ui/LoadingState';

function RedirectContent() {
  const router = useRouter();
  const params = useSearchParams();
  useEffect(() => {
    const provider = (params.get('provider') || 'GITLAB').toUpperCase();
    const projectId = params.get('projectId') || params.get('repositoryId');
    const iid = params.get('iid') || params.get('number');
    const runId = params.get('runId');
    if (!projectId || !/^\d+$/.test(iid || '')) {
      router.replace('/queue');
      return;
    }
    const base = `/review/${provider}/${projectId}/${iid}`;
    router.replace(runId ? `${base}/runs/${encodeURIComponent(runId)}` : base);
  }, [params, router]);
  return <LoadingState className="m-5" label="Opening review" />;
}

export default function ReviewRedirectPage() {
  return <Suspense fallback={<LoadingState label="Loading review" />}><RedirectContent /></Suspense>;
}
