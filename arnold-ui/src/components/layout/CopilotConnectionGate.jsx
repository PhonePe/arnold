'use client';

/*
 * Copyright (c) 2026 Original Author(s), PhonePe India Pvt. Ltd.
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

import React, { useEffect, useRef, useState } from 'react';
import { Copy, ExternalLink, Github, LoaderCircle, LockKeyhole } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import {
  cancelCopilotConnection, initiateCopilotConnection, pollCopilotConnection, verifyCopilotConnection,
} from '@/lib/api';

const PENDING = new Set(['pending', 'retrying']);
const OK = new Set(['connected', 'verification_failed']);

export function CopilotConnectionGate({ open, onConnected }) {
  const actionRef = useRef(null);
  const [attempt, setAttempt] = useState(null);
  const [connecting, setConnecting] = useState(false);
  const [copied, setCopied] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    if (!open) return;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    actionRef.current?.focus();
    return () => { document.body.style.overflow = previousOverflow; };
  }, [open]);

  useEffect(() => {
    if (!open || !attempt) return undefined;
    const controller = new AbortController();
    const timer = setTimeout(async () => {
      if (Date.now() >= attempt.expiresAt) {
        setAttempt(null);
        setError('The code expired. Connect again to get a new code.');
        return;
      }
      try {
        const result = await pollCopilotConnection(attempt.attemptId, { signal: controller.signal });
        if (controller.signal.aborted) return;
        setError(null);
        if (PENDING.has(result.status)) {
          setAttempt(result);
        } else {
          setAttempt(null);
          if (result.status === 'denied') setError('Authorization was declined. You can try again.');
          else if (result.status === 'expired') setError('The code expired. Connect again to get a new code.');
          else if (OK.has(result.status)) onConnected(result.connection);
        }
      } catch (failure) {
        if (controller.signal.aborted) return;
        if (failure.status === 404) {
          setAttempt(null);
          setError('This connection attempt is no longer active.');
        } else {
          setAttempt({ ...attempt, interval: Math.max(10, attempt.interval) });
        }
      }
    }, Math.min(Math.max(0, attempt.expiresAt - Date.now()), attempt.interval * 1000));
    return () => {
      clearTimeout(timer);
      controller.abort();
    };
  }, [open, attempt, onConnected]);

  if (!open) return null;

  async function connect() {
    setConnecting(true);
    setError(null);
    try {
      setAttempt(await initiateCopilotConnection());
      setCopied(false);
    } catch (connectError) {
      setError(connectError.message || 'Could not start Copilot connection');
    } finally {
      setConnecting(false);
    }
  }

  async function retest() {
    setConnecting(true);
    setError(null);
    try {
      const connection = await verifyCopilotConnection();
      if (connection.connected) onConnected(connection);
      else setError('Copilot access is still unavailable. Check your subscription or organization access, then try again.');
    } catch (verifyError) {
      setError(verifyError.message || 'Could not verify Copilot access');
    } finally {
      setConnecting(false);
    }
  }

  async function cancel() {
    try {
      await cancelCopilotConnection(attempt.attemptId);
    } catch {
      // The attempt expires on its own; leaving it active is harmless.
    }
    setAttempt(null);
  }

  async function copyCode() {
    try {
      await navigator.clipboard.writeText(attempt.userCode);
      setCopied(true);
    } catch {
      setError('Copy is unavailable. Select and copy the code below.');
    }
  }

  return (
    <div className="fixed inset-0 z-[110] grid place-items-center bg-fg/45 px-4 backdrop-blur-[2px]" role="presentation">
      <section
        role="dialog"
        aria-modal="true"
        aria-labelledby="copilot-connection-title"
        aria-describedby="copilot-connection-description"
        className="w-full max-w-[480px] border border-border-2 bg-surface shadow-xl"
      >
        <div className="flex items-center justify-between border-b border-border bg-surface-2 px-5 py-3">
          <span className="font-mono text-[11px] uppercase tracking-[0.12em] text-accent">Required connection</span>
          <LockKeyhole className="h-4 w-4 text-muted" aria-hidden="true" />
        </div>
        <div className="p-6 sm:p-7">
          <div className="mb-5 grid h-12 w-12 place-items-center border border-border-2 bg-[var(--accent-soft)] text-accent">
            <Github className="h-6 w-6" strokeWidth={1.8} aria-hidden="true" />
          </div>
          <h2 id="copilot-connection-title" className="font-display text-[25px] font-bold tracking-[-0.02em] text-fg">
            Connect GitHub Copilot to continue
          </h2>
          <p id="copilot-connection-description" className="mt-2 text-[14px] leading-6 text-muted">
            Arnold uses your personal Copilot account to run analyses on your merge requests.
          </p>
          {attempt ? (
            <div className="mt-6 border-l-[3px] border-accent bg-surface-2 px-4 py-3" aria-live="polite">
              <p className="text-[13px] text-fg">Enter this code on GitHub to authorize your account:</p>
              <div className="my-3 flex flex-wrap items-center gap-3">
                <code className="select-all border border-border bg-surface px-4 py-2 font-mono text-xl tracking-widest text-fg">{attempt.userCode}</code>
                <Button variant="secondary" size="sm" onClick={copyCode}><Copy className="h-4 w-4" aria-hidden="true" />{copied ? 'Copied' : 'Copy code'}</Button>
                <a href="https://github.com/login/device" target="_blank" rel="noopener noreferrer"
                  className="inline-flex items-center gap-1 text-[13px] font-semibold text-accent-ink underline">
                  Open GitHub <ExternalLink className="h-4 w-4" aria-hidden="true" /><span className="sr-only"> (opens a new tab)</span>
                </a>
              </div>
              <p className="mb-3 font-mono text-[11px] text-muted">Waiting for approval · Code expires {new Date(attempt.expiresAt).toLocaleTimeString()}</p>
              <Button variant="secondary" size="sm" onClick={cancel}>Cancel</Button>
            </div>
          ) : (
            <Button ref={actionRef} className="mt-6 w-full" size="lg" onClick={connect} disabled={connecting}>
              {connecting ? <LoaderCircle className="h-4 w-4 animate-spin" aria-hidden="true" /> : <Github className="h-4 w-4" aria-hidden="true" />}
              {connecting ? 'Opening GitHub…' : 'Connect Copilot'}
            </Button>
          )}
          {error ? <p role="alert" className="mt-4 text-[13px] text-bad">{error}</p> : null}
          <Button variant="secondary" className="mt-2 w-full" onClick={retest} disabled={connecting || Boolean(attempt)}>
            {connecting ? 'Checking…' : 'I have already authorized — test again'}
          </Button>
        </div>
      </section>
    </div>
  );
}
