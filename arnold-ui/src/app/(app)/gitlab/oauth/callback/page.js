'use client';

/*
 * Copyright (c) 2026 Original Author(s), PhonePe Limited
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

import React, { Suspense, useEffect, useRef, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { Check, Gitlab, LoaderCircle } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { completeGitLabOAuth, fetchGitLabOAuthAuthorization } from '@/lib/api';
import { RETURN_PATH_KEY } from '@/components/layout/GitLabConnectionGate';
import { useAppData } from '@/components/layout/AppLayout';

function CallbackContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { refreshIntegration } = useAppData();
  const started = useRef(false);
  const [status, setStatus] = useState('connecting');
  const [error, setError] = useState(null);

  useEffect(() => {
    if (started.current) return;
    started.current = true;
    const code = searchParams.get('code');
    const state = searchParams.get('state');
    const providerError = searchParams.get('error_description') || searchParams.get('error');

    if (providerError || !code || !state) {
      setStatus('error');
      setError(providerError || 'GitLab did not return the required authorization details.');
      return;
    }

    completeGitLabOAuth(code, state)
      .then(() => refreshIntegration())
      .then(() => {
        setStatus('connected');
        const storedReturnPath = sessionStorage.getItem(RETURN_PATH_KEY);
        const returnPath = storedReturnPath?.startsWith('/') && !storedReturnPath.startsWith('//')
          ? storedReturnPath
          : '/';
        sessionStorage.removeItem(RETURN_PATH_KEY);
        window.setTimeout(() => router.replace(returnPath), 450);
      })
      .catch((callbackError) => {
        setStatus('error');
        setError(callbackError.message || 'GitLab authorization could not be completed.');
      });
  }, [refreshIntegration, router, searchParams]);

  async function retry() {
    setStatus('connecting');
    setError(null);
    try {
      const response = await fetchGitLabOAuthAuthorization();
      if (!response?.authorizationUrl) throw new Error('GitLab authorization URL is unavailable');
      window.location.assign(response.authorizationUrl);
    } catch (retryError) {
      setStatus('error');
      setError(retryError.message || 'Could not restart GitLab authorization.');
    }
  }

  return (
    <div className="mx-auto grid min-h-[60vh] max-w-[620px] place-items-center">
      <section className="w-full border border-border bg-surface p-7 sm:p-9">
        <div className="mb-6 flex h-12 w-12 items-center justify-center border border-border-2 bg-surface-2 text-accent">
          {status === 'connected'
            ? <Check className="h-6 w-6 text-ok" aria-hidden="true" />
            : status === 'connecting'
              ? <LoaderCircle className="h-6 w-6 animate-spin" aria-hidden="true" />
              : <Gitlab className="h-6 w-6" aria-hidden="true" />}
        </div>
        <div className="font-mono text-[11px] uppercase tracking-[0.12em] text-accent">GitLab connection</div>
        <h1 className="mt-2 font-display text-[30px] font-bold tracking-[-0.02em] text-fg">
          {status === 'connected' ? 'GitLab connected' : status === 'connecting' ? 'Linking your account' : 'Connection not completed'}
        </h1>
        <p className="mt-3 text-[14px] leading-6 text-muted">
          {status === 'connected'
            ? 'Your GitLab account is ready. Returning to your workspace.'
            : status === 'connecting'
              ? 'Arnold is validating the authorization and storing your connection.'
              : error}
        </p>
        {status === 'error' ? <Button className="mt-6" onClick={retry}>Try GitLab login again</Button> : null}
      </section>
    </div>
  );
}

export default function GitLabOAuthCallbackPage() {
  return <Suspense fallback={null}><CallbackContent /></Suspense>;
}
