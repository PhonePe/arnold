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

import React, { useCallback, useEffect, useState } from 'react';
import { ExternalLink, Github, RotateCcw, Unplug } from 'lucide-react';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { disconnectGitHub, fetchGitHubAuthorization, fetchGitHubIntegrationStatus } from '@/lib/api';

export const GITHUB_RETURN_PATH_KEY = 'arnold.githubOAuth.returnPath';

export function GitHubConnectionCard({ onConnectionChange, className = '', ...props }) {
  const [status, setStatus] = useState(null);
  const [installationUrl, setInstallationUrl] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);

  const load = useCallback(async () => {
    try {
      setStatus(await fetchGitHubIntegrationStatus());
    } catch (loadError) {
      setError(loadError);
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  async function connect() {
    setBusy(true);
    setError(null);
    try {
      const response = await fetchGitHubAuthorization();
      if (!response?.authorizationUrl) throw new Error('GitHub authorization URL is unavailable');
      sessionStorage.setItem(GITHUB_RETURN_PATH_KEY, `${window.location.pathname}${window.location.search}`);
      window.location.assign(response.authorizationUrl);
    } catch (connectError) {
      setError(connectError);
      setBusy(false);
    }
  }

  async function disconnect() {
    setBusy(true);
    setError(null);
    try {
      setStatus(await disconnectGitHub());
      onConnectionChange?.();
    } catch (disconnectError) {
      setError(disconnectError);
    } finally {
      setBusy(false);
    }
  }

  async function loadInstallationUrl() {
    try {
      const response = await fetchGitHubAuthorization();
      setInstallationUrl(response?.installationUrl || null);
    } catch {
      setInstallationUrl(null);
    }
  }

  useEffect(() => { if (status?.connected) loadInstallationUrl(); }, [status?.connected]);

  const connected = Boolean(status?.connected);
  if (status?.status === 'unconfigured') {
    return (
      <Card className={`mt-3 flex flex-col gap-5 p-5 lg:flex-row lg:items-center ${className}`} {...props}>
        <div className="grid h-[46px] w-[46px] flex-none place-items-center border border-border-2 bg-surface-2 text-faint">
          <Github className="h-6 w-6" strokeWidth={1.8} aria-hidden="true" />
        </div>
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <h3 className="font-display text-[17px] font-bold text-fg">GitHub</h3>
            <Badge>Not configured</Badge>
          </div>
          <div className="mt-2 font-mono text-[11.5px] text-muted">Not configured by your administrator</div>
        </div>
      </Card>
    );
  }
  return (
    <Card className={`mt-3 flex flex-col gap-5 p-5 lg:flex-row lg:items-center ${className}`} {...props}>
      <div className="grid h-[46px] w-[46px] flex-none place-items-center bg-fg text-surface">
        <Github className="h-6 w-6" strokeWidth={1.8} aria-hidden="true" />
      </div>
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-2">
          <h3 className="font-display text-[17px] font-bold text-fg">GitHub</h3>
          <Badge variant={connected ? 'success' : 'warning'}>{connected ? 'Connected' : 'Not connected'}</Badge>
        </div>
        <div className="mt-2 font-mono text-[11.5px] text-muted">
          {status?.providerUsername ? `@${status.providerUsername}` : 'Connect to review pull requests through the Arnold GitHub App'}
        </div>
        {error ? <p className="mt-2 text-[13px] text-bad" role="alert">{error.message || 'The GitHub action failed.'}</p> : null}
      </div>
      <div className="flex flex-wrap gap-2">
        {connected && installationUrl ? (
          <Button size="sm" variant="secondary" onClick={() => window.open(installationUrl, '_blank', 'noopener')}>
            <ExternalLink className="h-3.5 w-3.5" aria-hidden="true" />
            Install on repositories
          </Button>
        ) : null}
        <Button size="sm" variant={connected ? 'secondary' : 'primary'} onClick={connect} disabled={busy}>
          <RotateCcw className="h-3.5 w-3.5" aria-hidden="true" />
          {connected ? 'Reconnect' : 'Connect GitHub'}
        </Button>
        {connected ? (
          <Button size="sm" variant="danger" onClick={disconnect} disabled={busy}>
            <Unplug className="h-3.5 w-3.5" aria-hidden="true" />
            Disconnect
          </Button>
        ) : null}
      </div>
    </Card>
  );
}
