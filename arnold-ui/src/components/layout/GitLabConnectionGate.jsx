'use client';

import React, { useEffect, useRef, useState } from 'react';
import { Gitlab, LoaderCircle, LockKeyhole } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { fetchGitLabOAuthAuthorization } from '@/lib/api';

const RETURN_PATH_KEY = 'arnold.gitlabOAuth.returnPath';

export function GitLabConnectionGate({ open, reason = 'required' }) {
  const actionRef = useRef(null);
  const [connecting, setConnecting] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    if (!open) return;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    actionRef.current?.focus();
    return () => { document.body.style.overflow = previousOverflow; };
  }, [open]);

  if (!open) return null;

  async function connect() {
    setConnecting(true);
    setError(null);
    try {
      const response = await fetchGitLabOAuthAuthorization();
      if (!response?.authorizationUrl) throw new Error('GitLab authorization URL is unavailable');
      const returnPath = `${window.location.pathname}${window.location.search}`;
      sessionStorage.setItem(RETURN_PATH_KEY, returnPath);
      window.location.assign(response.authorizationUrl);
    } catch (connectError) {
      setError(connectError.message || 'Could not start GitLab authorization');
      setConnecting(false);
    }
  }

  return (
    <div className="fixed inset-0 z-[100] grid place-items-center bg-fg/45 px-4 backdrop-blur-[2px]" role="presentation">
      <section
        role="dialog"
        aria-modal="true"
        aria-labelledby="gitlab-connection-title"
        aria-describedby="gitlab-connection-description"
        className="w-full max-w-[480px] border border-border-2 bg-surface shadow-xl"
      >
        <div className="flex items-center justify-between border-b border-border bg-surface-2 px-5 py-3">
          <span className="font-mono text-[11px] uppercase tracking-[0.12em] text-accent">Required connection</span>
          <LockKeyhole className="h-4 w-4 text-muted" aria-hidden="true" />
        </div>
        <div className="p-6 sm:p-7">
          <div className="mb-5 grid h-12 w-12 place-items-center border border-border-2 bg-[var(--accent-soft)] text-accent">
            <Gitlab className="h-6 w-6" strokeWidth={1.8} aria-hidden="true" />
          </div>
          <h2 id="gitlab-connection-title" className="font-display text-[25px] font-bold tracking-[-0.02em] text-fg">
            Connect GitLab to continue
          </h2>
          <p id="gitlab-connection-description" className="mt-2 text-[14px] leading-6 text-muted">
            {reason === 'expired'
              ? 'Your GitLab authorization is no longer valid. Reconnect to resolve merge requests and publish review discussions.'
              : 'Arnold uses your GitLab account to access merge requests and publish review discussions as you.'}
          </p>
          <div className="mt-6 border-l-[3px] border-accent bg-surface-2 px-4 py-3 font-mono text-[11px] leading-5 text-muted">
            You will sign in at gitlab.example.com and return here automatically.
          </div>
          {error ? <p role="alert" className="mt-4 text-[13px] text-bad">{error}</p> : null}
          <Button ref={actionRef} className="mt-6 w-full" size="lg" onClick={connect} disabled={connecting}>
            {connecting ? <LoaderCircle className="h-4 w-4 animate-spin" aria-hidden="true" /> : <Gitlab className="h-4 w-4" aria-hidden="true" />}
            {connecting ? 'Opening GitLab…' : reason === 'expired' ? 'Reconnect GitLab' : 'Connect GitLab'}
          </Button>
        </div>
      </section>
    </div>
  );
}

export { RETURN_PATH_KEY };
