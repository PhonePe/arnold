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
import { Check, ExternalLink, Github, Gitlab, LoaderCircle, LockKeyhole, X } from 'lucide-react';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { GITHUB_RETURN_PATH_KEY } from '@/components/composite/GitHubConnectionCard';
import { fetchGitHubAuthorization, fetchGitLabOAuthAuthorization } from '@/lib/api';
import { CopilotDeviceFlow } from './CopilotDeviceFlow';
import { RETURN_PATH_KEY as GITLAB_RETURN_PATH_KEY } from './GitLabConnectionGate';

const PROVIDER_ICONS = { GITHUB: Github, GITLAB: Gitlab };
const FOCUSABLE = 'a[href], button:not([disabled]), input:not([disabled]), [tabindex]:not([tabindex="-1"])';

const CONNECTORS = {
  GITHUB: { fetch: fetchGitHubAuthorization, key: GITHUB_RETURN_PATH_KEY },
  GITLAB: { fetch: fetchGitLabOAuthAuthorization, key: GITLAB_RETURN_PATH_KEY },
};

function StepMarker({ index, done }) {
  return done ? (
    <span className="grid h-7 w-7 flex-none place-items-center bg-ok text-on-accent" aria-label="Done">
      <Check className="h-4 w-4" strokeWidth={2.2} aria-hidden="true" />
    </span>
  ) : (
    <span className="grid h-7 w-7 flex-none place-items-center border border-border-2 bg-surface-2 font-mono text-[12px] text-fg" aria-hidden="true">
      {index + 1}
    </span>
  );
}

function StepTag({ step }) {
  if (step.done) return <Badge variant="success">Done</Badge>;
  if (step.admin) return <Badge>Administrator</Badge>;
  return step.required ? <Badge variant="critical">Required</Badge> : <Badge>Recommended</Badge>;
}

function ForgeRow({ forge }) {
  const [connecting, setConnecting] = useState(false);
  const [error, setError] = useState(null);
  const Icon = PROVIDER_ICONS[forge.provider] || Github;

  async function connect() {
    const connector = CONNECTORS[forge.provider];
    if (!connector) return;
    setConnecting(true);
    setError(null);
    try {
      const response = await connector.fetch();
      if (!response?.authorizationUrl) throw new Error(`${forge.label} authorization URL is unavailable`);
      sessionStorage.setItem(connector.key, `${window.location.pathname}${window.location.search}`);
      window.location.assign(response.authorizationUrl);
    } catch (connectError) {
      setError(connectError.message || `Could not start ${forge.label} authorization`);
      setConnecting(false);
    }
  }

  return (
    <li className="border border-border bg-surface-2 px-3 py-2.5">
      <div className="flex items-center gap-3">
        <Icon className="h-4 w-4 flex-none text-fg" strokeWidth={1.8} aria-hidden="true" />
        <div className="min-w-0 flex-1">
          <div className="text-[13px] font-semibold text-fg">{forge.label}</div>
          <div className="truncate font-mono text-[11px] text-muted">
            {forge.reconnect
              ? 'Authorization expired — reconnect to continue'
              : forge.connected
                ? `Connected${forge.username ? ` as @${forge.username}` : ''}`
                : 'Not connected'}
          </div>
        </div>
        {forge.connected ? (
          <Check className="h-4 w-4 text-ok" aria-label="Connected" />
        ) : (
          <Button size="sm" onClick={connect} disabled={connecting}>
            {connecting ? <LoaderCircle className="h-3.5 w-3.5 animate-spin" aria-hidden="true" /> : null}
            {connecting ? 'Opening…' : forge.reconnect ? `Reconnect ${forge.label}` : `Connect ${forge.label}`}
          </Button>
        )}
      </div>
      {error ? <p role="alert" className="mt-2 text-[12px] text-bad">{error}</p> : null}
    </li>
  );
}

function StepBody({ step, onCopilotConnected }) {
  const [copilotOpen, setCopilotOpen] = useState(false);

  if (step.id === 'source-control') {
    if (step.admin) {
      return (
        <p className="text-[13px] leading-6 text-muted">
          No source control provider is configured for this deployment. An administrator needs to set up GitHub
          (<code className="font-mono text-[12px] text-fg">docs/setup/github-app.md</code>) or GitLab
          (<code className="font-mono text-[12px] text-fg">docs/setup/gitlab.md</code>) before reviews can run.
        </p>
      );
    }
    return (
      <>
        <p className="mb-2.5 text-[13px] leading-6 text-muted">
          Arnold reads changes and publishes review comments as you. Connect at least one account.
        </p>
        <ul className="flex flex-col gap-2">
          {step.forges.map((forge) => <ForgeRow key={forge.provider} forge={forge} />)}
        </ul>
      </>
    );
  }

  if (step.id === 'install') {
    return (
      <>
        <p className="mb-2.5 text-[13px] leading-6 text-muted">
          Open pull requests from repositories where the Arnold GitHub App is installed are imported automatically.
        </p>
        <a
          href={step.installUrl}
          target="_blank"
          rel="noopener noreferrer"
          className="inline-flex items-center gap-1.5 text-[13px] font-semibold text-accent-ink underline-offset-2 hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        >
          Choose repositories on GitHub <ExternalLink className="h-3.5 w-3.5" aria-hidden="true" />
          <span className="sr-only"> (opens a new tab)</span>
        </a>
      </>
    );
  }

  if (step.id === 'copilot') {
    if (step.done) {
      return <p className="text-[13px] text-muted">Connected{step.username ? ` as @${step.username}` : ''}.</p>;
    }
    return (
      <>
        <p className="mb-2.5 text-[13px] leading-6 text-muted">
          {step.required
            ? 'Analyses run on your personal Copilot account — no other review engine is available.'
            : 'Optional: run analyses on your personal Copilot account instead of the shared review engine.'}
        </p>
        {copilotOpen ? (
          <CopilotDeviceFlow onConnected={onCopilotConnected} />
        ) : (
          <Button size="sm" variant={step.required ? 'primary' : 'secondary'} onClick={() => setCopilotOpen(true)}>
            <Github className="h-3.5 w-3.5" aria-hidden="true" />
            Connect Copilot
          </Button>
        )}
      </>
    );
  }

  return (
    <>
      <p className="mb-2 text-[13px] leading-6 text-muted">No review engine is available yet. Ask your administrator to enable one:</p>
      <ul className="flex flex-col gap-1 border-l-[3px] border-accent bg-surface-2 px-3 py-2 font-mono text-[11.5px] text-fg">
        {(step.messages.length ? step.messages : ['No analysis harness is enabled.']).map((message) => <li key={message}>{message}</li>)}
      </ul>
    </>
  );
}

export function SetupGuide({ open, guide, closable = true, onClose, onCopilotConnected, className = '', ...props }) {
  const dialogRef = useRef(null);

  useEffect(() => {
    if (!open) return undefined;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    const previousFocus = document.activeElement;
    dialogRef.current?.querySelector(FOCUSABLE)?.focus();
    const onKeyDown = (event) => {
      if (event.key === 'Escape' && closable) {
        event.preventDefault();
        onClose?.();
        return;
      }
      if (event.key !== 'Tab' || !dialogRef.current) return;
      const focusable = [...dialogRef.current.querySelectorAll(FOCUSABLE)];
      if (focusable.length === 0) return;
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (event.shiftKey && (document.activeElement === first || !dialogRef.current.contains(document.activeElement))) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    };
    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.body.style.overflow = previousOverflow;
      document.removeEventListener('keydown', onKeyDown);
      if (previousFocus instanceof HTMLElement) previousFocus.focus();
    };
  }, [open, closable, onClose]);

  if (!open || !guide) return null;
  const completed = guide.steps.filter((step) => step.done).length;

  return (
    <div className={`fixed inset-0 z-[100] grid place-items-center overflow-y-auto bg-fg/45 px-4 py-6 backdrop-blur-[2px] ${className}`} role="presentation" {...props}>
      <section
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby="setup-guide-title"
        aria-describedby="setup-guide-description"
        className="w-full max-w-[560px] border border-border-2 bg-surface shadow-xl"
      >
        <div className="flex items-center justify-between border-b border-border bg-surface-2 px-5 py-3">
          <span className="font-mono text-[11px] uppercase tracking-[0.12em] text-accent">
            {guide.reconnect ? 'Reconnect required' : 'Setup'} · {completed}/{guide.steps.length}
          </span>
          {closable ? (
            <button
              type="button"
              onClick={onClose}
              aria-label="Close setup guide"
              className="grid h-7 w-7 place-items-center text-muted transition-colors hover:bg-[var(--ink-wash)] hover:text-fg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            >
              <X className="h-4 w-4" aria-hidden="true" />
            </button>
          ) : (
            <LockKeyhole className="h-4 w-4 text-muted" aria-label="Required before continuing" />
          )}
        </div>
        <div className="p-6 sm:p-7">
          <h2 id="setup-guide-title" className="font-display text-[25px] font-bold tracking-[-0.02em] text-fg">
            {guide.reconnect ? 'Reconnect GitLab to continue' : 'Set up Arnold'}
          </h2>
          <p id="setup-guide-description" className="mt-2 text-[14px] leading-6 text-muted">
            {guide.blocking
              ? 'Connect your source control account to start reviewing. You will sign in and return here automatically.'
              : 'A few steps to get the most out of Arnold. You can reopen this guide from Home or Settings.'}
          </p>
          <ol className="mt-6 flex flex-col">
            {guide.steps.map((step, index) => (
              <li key={step.id} className="flex gap-3.5 border-t border-border py-4 first:border-t-0 first:pt-0">
                <StepMarker index={index} done={step.done} />
                <div className="min-w-0 flex-1">
                  <div className="mb-1.5 flex flex-wrap items-center gap-2">
                    <h3 className="font-display text-[16px] font-bold tracking-tight text-fg">{step.title}</h3>
                    <StepTag step={step} />
                  </div>
                  <StepBody step={step} onCopilotConnected={onCopilotConnected} />
                </div>
              </li>
            ))}
          </ol>
          {closable ? (
            <div className="mt-2 flex justify-end border-t border-border pt-4">
              <Button variant="secondary" size="sm" onClick={onClose}>{guide.pending ? 'Finish later' : 'Done'}</Button>
            </div>
          ) : null}
        </div>
      </section>
    </div>
  );
}
