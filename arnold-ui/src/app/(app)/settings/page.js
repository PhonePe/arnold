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

import React, { useState } from 'react';
import { Gitlab, ListChecks, LogOut, RefreshCw, RotateCcw, Unplug } from 'lucide-react';
import { GitHubConnectionCard } from '@/components/composite/GitHubConnectionCard';
import { CopilotConnectionCard } from '@/components/composite/CopilotConnectionCard';
import { Avatar } from '@/components/ui/Avatar';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { ErrorState } from '@/components/ui/ErrorState';
import { LoadingState } from '@/components/ui/LoadingState';
import { Overline } from '@/components/ui/Overline';
import { ThemeToggle } from '@/components/layout/ThemeToggle';
import { RETURN_PATH_KEY } from '@/components/layout/GitLabConnectionGate';
import { useAppData } from '@/components/layout/AppLayout';
import { disconnectGitLab, fetchGitLabOAuthAuthorization } from '@/lib/api';

function initialsFor(value) {
  return value ? value.split(/\s+/).map((word) => word[0]).join('').toUpperCase().slice(0, 2) : 'A';
}

function formatExpiry(value) {
  if (value == null) return null;
  const date = new Date(Number(value));
  return Number.isNaN(date.getTime()) ? null : date.toLocaleString();
}

export default function SettingsPage() {
  const {
    user,
    userError,
    integration,
    integrationError,
    integrationLoaded,
    copilot,
    setCopilot,
    setup,
    openSetupGuide,
    refreshSetup,
    refreshIntegration,
  } = useAppData();
  const gitLabForge = setup?.forges?.find((forge) => forge.provider === 'GITLAB');
  const gitLabUnconfigured = integration?.status === 'unconfigured' || (setup != null && !gitLabForge?.configured);
  const [action, setAction] = useState(null);
  const [actionError, setActionError] = useState(null);
  const displayName = user?.displayName || user?.username || user?.email || 'Arnold user';
  const connected = Boolean(integration?.connected);
  const expiry = formatExpiry(integration?.expiresAt);

  async function refresh() {
    setAction('refresh');
    setActionError(null);
    try {
      await refreshIntegration();
    } catch (error) {
      setActionError(error);
    } finally {
      setAction(null);
    }
  }

  async function connect() {
    setAction('connect');
    setActionError(null);
    try {
      const response = await fetchGitLabOAuthAuthorization();
      if (!response?.authorizationUrl) throw new Error('GitLab authorization URL is unavailable');
      sessionStorage.setItem(RETURN_PATH_KEY, '/settings');
      window.location.assign(response.authorizationUrl);
    } catch (error) {
      setActionError(error);
      setAction(null);
    }
  }

  async function disconnect() {
    if (!window.confirm('Disconnect GitLab? Arnold will pause GitLab-backed review actions until you reconnect.')) return;
    setAction('disconnect');
    setActionError(null);
    try {
      await disconnectGitLab();
      await refreshIntegration();
      refreshSetup();
    } catch (error) {
      setActionError(error);
    } finally {
      setAction(null);
    }
  }

  return (
    <div className="mx-auto max-w-[1000px]">
      <header className="mb-8 max-w-[760px]">
        <Overline>Account</Overline>
        <h1 className="mt-1 font-display text-[clamp(30px,4vw,42px)] font-bold leading-[1.05] tracking-[-0.02em] text-fg">Settings</h1>
        <p className="mt-3 text-[14px] leading-6 text-muted">
          Manage your Arnold session, appearance, source control accounts and Copilot connection.
        </p>
      </header>

      <section className="mb-8" aria-labelledby="account-heading">
        <div className="mb-3">
          <h2 id="account-heading" className="font-display text-[19px] font-bold tracking-[-0.01em] text-fg">Account</h2>
          <p className="mt-1 font-mono text-[11px] text-muted">Identity supplied by your sign-in provider</p>
        </div>
        {userError ? <ErrorState title="Account details unavailable" error={userError} /> : !user ? <LoadingState label="Loading account details" /> : (
          <Card className="flex flex-col gap-5 p-5 sm:flex-row sm:items-center">
            <Avatar src={user.avatarUrl} alt={displayName} initials={initialsFor(displayName)} size="lg" />
            <div className="min-w-0 flex-1">
              <div className="font-display text-lg font-bold text-fg">{displayName}</div>
              <div className="mt-1 break-all font-mono text-[11.5px] text-muted">{user.email}</div>
              {user.username ? <div className="mt-1 font-mono text-[11px] text-faint">@{user.username}</div> : null}
            </div>
            {user.roles.length ? <div className="flex flex-wrap gap-1.5">{user.roles.map((role) => <Badge key={role}>{role}</Badge>)}</div> : null}
          </Card>
        )}
      </section>

      <section className="mb-8" aria-labelledby="connections-heading">
        <div className="mb-3 flex flex-wrap items-end justify-between gap-3">
          <div>
            <h2 id="connections-heading" className="font-display text-[19px] font-bold tracking-[-0.01em] text-fg">Connected accounts</h2>
            <p className="mt-1 font-mono text-[11px] text-muted">GitLab or GitHub for reviews · Copilot for your AI account</p>
          </div>
          <div className="flex flex-wrap gap-2">
            {setup ? (
              <Button variant="secondary" size="sm" onClick={openSetupGuide}>
                <ListChecks className="h-3.5 w-3.5" aria-hidden="true" />
                Open setup guide
              </Button>
            ) : null}
            {!gitLabUnconfigured ? (
              <Button variant="secondary" size="sm" onClick={refresh} disabled={action != null}>
                <RefreshCw className={`h-3.5 w-3.5 ${action === 'refresh' ? 'animate-spin' : ''}`} aria-hidden="true" />
                Test GitLab connection
              </Button>
            ) : null}
          </div>
        </div>

        {gitLabUnconfigured ? (
          <Card className="flex flex-col gap-5 p-5 lg:flex-row lg:items-center">
            <div className="grid h-[46px] w-[46px] flex-none place-items-center border border-border-2 bg-surface-2 text-faint">
              <Gitlab className="h-6 w-6" strokeWidth={1.8} aria-hidden="true" />
            </div>
            <div className="min-w-0 flex-1">
              <div className="flex flex-wrap items-center gap-2">
                <h3 className="font-display text-[17px] font-bold text-fg">GitLab</h3>
                <Badge>Not configured</Badge>
              </div>
              <div className="mt-2 font-mono text-[11.5px] text-muted">Not configured by your administrator</div>
            </div>
          </Card>
        ) : integrationError ? <ErrorState title="GitLab status unavailable" error={integrationError} onRetry={refresh} /> : !integrationLoaded ? <LoadingState label="Checking GitLab connection" /> : (
          <Card className="flex flex-col gap-5 p-5 lg:flex-row lg:items-center">
            <div className="grid h-[46px] w-[46px] flex-none place-items-center bg-accent text-on-accent">
              <Gitlab className="h-6 w-6" strokeWidth={1.8} aria-hidden="true" />
            </div>
            <div className="min-w-0 flex-1">
              <div className="flex flex-wrap items-center gap-2">
                <h3 className="font-display text-[17px] font-bold text-fg">GitLab</h3>
                <Badge variant={connected ? 'success' : 'warning'}>{connected ? 'Connected' : 'Not connected'}</Badge>
                {integration?.credentialType ? <Badge>{integration.credentialType}</Badge> : null}
              </div>
              <div className="mt-2 font-mono text-[11.5px] text-muted">
                {integration?.providerUsername ? `@${integration.providerUsername}` : 'No GitLab account linked'}
              </div>
              {integration?.scopes?.length ? <div className="mt-1 font-mono text-[11px] text-faint">Scopes: {integration.scopes.join(', ')}</div> : null}
              {expiry ? <div className="mt-1 font-mono text-[11px] text-faint">Token expires {expiry}</div> : null}
            </div>
            <div className="flex flex-wrap gap-2">
              <Button size="sm" variant={connected ? 'secondary' : 'primary'} onClick={connect} disabled={action != null}>
                <RotateCcw className="h-3.5 w-3.5" aria-hidden="true" />
                {connected ? 'Reconnect' : 'Connect GitLab'}
              </Button>
              <Button size="sm" variant="secondary" onClick={refresh} disabled={action != null}>
                <RefreshCw className={`h-3.5 w-3.5 ${action === 'refresh' ? 'animate-spin' : ''}`} aria-hidden="true" />
                {action === 'refresh' ? 'Checking…' : 'Test connection'}
              </Button>
              {connected ? (
                <Button size="sm" variant="danger" onClick={disconnect} disabled={action != null}>
                  <Unplug className="h-3.5 w-3.5" aria-hidden="true" />
                  Disconnect
                </Button>
              ) : null}
            </div>
          </Card>
        )}
        {actionError ? <p className="mt-3 text-[13px] text-bad" role="alert">{actionError.message || 'The connection action failed.'}</p> : null}
        <GitHubConnectionCard onConnectionChange={refreshSetup} />
        <CopilotConnectionCard connection={copilot} onConnectionChange={setCopilot} />
      </section>

      <section className="mb-8" aria-labelledby="appearance-heading">
        <div className="mb-3">
          <h2 id="appearance-heading" className="font-display text-[19px] font-bold tracking-[-0.01em] text-fg">Appearance</h2>
          <p className="mt-1 font-mono text-[11px] text-muted">Stored in this browser</p>
        </div>
        <Card className="flex items-center justify-between gap-4 p-5">
          <div>
            <div className="text-[14px] font-semibold text-fg">Color theme</div>
            <div className="mt-1 text-[12px] text-muted">Switch between the paper and charcoal canvases.</div>
          </div>
          <ThemeToggle className="border-border-2 bg-surface-2" />
        </Card>
      </section>

      <section aria-labelledby="session-heading">
        <div className="mb-3">
          <h2 id="session-heading" className="font-display text-[19px] font-bold tracking-[-0.01em] text-fg">Session</h2>
        </div>
        <Button variant="danger" size="sm" onClick={() => window.location.assign('/auth/logout')}>
          <LogOut className="h-4 w-4" aria-hidden="true" />
          Sign out of Arnold
        </Button>
      </section>
    </div>
  );
}
