'use client';

import React, { Suspense } from 'react';
import { LoadingState } from '@/components/ui/LoadingState';
import { ReviewWorkspace } from '@/components/composite/ReviewWorkspace';

export default function ReviewPage() {
  return <Suspense fallback={<LoadingState label="Loading review" />}><ReviewWorkspace /></Suspense>;
}
