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

import { useEffect, useState } from 'react';
import { Github, Copy, ExternalLink, RefreshCw, RotateCcw, Unplug } from 'lucide-react';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { ErrorState } from '@/components/ui/ErrorState';
import { LoadingState } from '@/components/ui/LoadingState';
import {
  fetchCopilotStatus, initiateCopilotConnection, pollCopilotConnection,
  cancelCopilotConnection, verifyCopilotConnection, disconnectCopilot,
} from '@/lib/api';

const STATUS_LABELS = {
  disabled: 'Not configured',
  not_connected: 'Not connected',
  connected: 'Copilot access verified',
  copilot_unavailable: 'GitHub connected; Copilot unavailable',
  verification_failed: 'Could not verify Copilot access',
  reconnect_required: 'Reconnect required',
};

export function CopilotConnectionCard({ className = '', connection: syncedConnection, onConnectionChange, ...props }) {
  const [connection, setConnection] = useState(null);
  const [attempt, setAttempt] = useState(null);
  const [action, setAction] = useState(null);
  const [error, setError] = useState(null);
  const [notice, setNotice] = useState('');
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    if (syncedConnection) setConnection(syncedConnection);
  }, [syncedConnection]);

  useEffect(() => {
    const controller = new AbortController();
    fetchCopilotStatus({ signal: controller.signal }).then((result) => {
      if (!controller.signal.aborted) setConnection(result);
    }).catch((failure) => {
      if (!controller.signal.aborted) setError(failure);
    });
    return () => controller.abort();
  }, []);

  useEffect(() => {
    if (!attempt || action) return;
    const controller = new AbortController();
    const delay = Math.min(Math.max(0, attempt.expiresAt - Date.now()), attempt.interval * 1000);
    const timeout = setTimeout(async () => {
      if (Date.now() >= attempt.expiresAt) {
        setAttempt(null);
        setNotice('The code expired. Connect again to get a new code.');
        return;
      }
      try {
        const result = await pollCopilotConnection(attempt.attemptId, { signal: controller.signal });
        if (controller.signal.aborted) return;
        setError(null);
        if (result.status === 'pending' || result.status === 'retrying') {
          setNotice(result.status === 'retrying' ? 'GitHub is temporarily unavailable. Retrying until the code expires.' : '');
          setAttempt(result);
        } else {
          setAttempt(null);
          update(result.connection);
          setNotice(result.status === 'denied' ? 'Authorization was declined. You can try again.'
            : result.status === 'expired' ? 'The code expired. Connect again to get a new code.' : '');
        }
      } catch (failure) {
        if (controller.signal.aborted) return;
        setError(failure);
        if (failure.status === 404) {
          setAttempt(null);
          setNotice('This connection attempt is no longer active.');
        } else {
          setAttempt({ ...attempt, interval: Math.max(10, attempt.interval) });
        }
      }
    }, delay);
    return () => {
      clearTimeout(timeout);
      controller.abort();
    };
  // eslint-disable-next-line react-hooks/exhaustive-deps -- update is a stable wrapper over setState + optional callback
  }, [attempt, action]);

  function update(next) {
    setConnection(next);
    onConnectionChange?.(next);
  }

  async function run(name, task) {
    setAction(name);
    setError(null);
    setNotice('');
    try {
      await task();
    } catch (failure) {
      setError(failure);
    } finally {
      setAction(null);
    }
  }

  function connect() {
    return run('connect', async () => {
      setAttempt(await initiateCopilotConnection());
      setCopied(false);
    });
  }

  function cancel() {
    return run('cancel', async () => {
      update(await cancelCopilotConnection(attempt.attemptId));
      setAttempt(null);
    });
  }

  function disconnect() {
    if (!window.confirm('Remove your Copilot connection from Arnold? This does not revoke authorization on GitHub.')) return;
    return run('disconnect', async () => {
      update(await disconnectCopilot());
      setAttempt(null);
    });
  }

  async function copyCode() {
    try {
      await navigator.clipboard.writeText(attempt.userCode);
      setCopied(true);
    } catch {
      setNotice('Copy is unavailable. Select and copy the code below.');
    }
  }

  const saved = connection && !['disabled', 'not_connected'].includes(connection.status);

  return (
    <Card className={`mt-4 p-5 ${className}`} {...props}>
      <div className="flex flex-col gap-5 lg:flex-row lg:items-center">
        <div className="grid h-[46px] w-[46px] flex-none place-items-center bg-surface-2 text-fg">
          <Github className="h-6 w-6" strokeWidth={1.8} aria-hidden="true" />
        </div>
        <div className="min-w-0 flex-1">
          <h3 className="font-display text-[17px] font-bold text-fg">GitHub Copilot</h3>
          {connection ? (
            <>
              <div className="mt-2"><Badge variant={connection.connected ? 'success' : 'warning'}>
                {STATUS_LABELS[connection.status] || 'Connection status unavailable'}
              </Badge></div>
              <p className="mt-2 font-mono text-[11.5px] text-muted">
                {connection.username ? `github.com · @${connection.username}` : 'Connect your personal Copilot account'}
              </p>
              {connection.status === 'disabled' ? <p className="mt-2 text-[12px] text-muted">Copilot connections have not been enabled by your administrator.</p> : null}
              {connection.status === 'copilot_unavailable' ? <p className="mt-2 text-[12px] text-muted">Check this account’s Copilot subscription or organization access, then test again.</p> : null}
              {connection.status === 'verification_failed' ? <p className="mt-2 text-[12px] text-muted">Your credentials are saved. Test the connection again when GitHub is available.</p> : null}
            </>
          ) : !error ? <LoadingState label="Checking Copilot connection" /> : null}
        </div>
        {connection?.enabled && !attempt ? (
          <div className="flex flex-wrap gap-2">
            <Button size="sm" variant={saved ? 'secondary' : 'primary'} disabled={action != null} onClick={connect}>
              {saved ? <RotateCcw className="h-3.5 w-3.5" aria-hidden="true" /> : <Github className="h-3.5 w-3.5" aria-hidden="true" />}
              {action === 'connect' ? 'Connecting…' : saved ? 'Reconnect' : 'Connect Copilot'}
            </Button>
            {saved ? <>
              <Button size="sm" variant="secondary" disabled={action != null}
                onClick={() => run('verify', async () => update(await verifyCopilotConnection()))}>
                <RefreshCw className={`h-3.5 w-3.5 ${action === 'verify' ? 'animate-spin' : ''}`} aria-hidden="true" />
                {action === 'verify' ? 'Checking…' : 'Test connection'}
              </Button>
              <Button size="sm" variant="danger" disabled={action != null} onClick={disconnect}>
                <Unplug className="h-3.5 w-3.5" aria-hidden="true" />
                Disconnect
              </Button>
            </> : null}
          </div>
        ) : null}
      </div>
      {attempt ? (
        <div className="mt-5 border-t border-border pt-4" aria-live="polite">
          <p className="text-[13px] text-fg">Enter this code on GitHub to authorize your account:</p>
          <div className="my-3 flex flex-wrap items-center gap-3">
            <code className="select-all border border-border bg-surface-2 px-4 py-2 font-mono text-xl tracking-widest text-fg">{attempt.userCode}</code>
            <Button variant="secondary" size="sm" onClick={copyCode}><Copy className="h-4 w-4" aria-hidden="true" />{copied ? 'Copied' : 'Copy code'}</Button>
            <a href="https://github.com/login/device" target="_blank" rel="noopener noreferrer"
              className="inline-flex items-center gap-1 text-[13px] font-semibold text-accent-ink underline">
              Open GitHub <ExternalLink className="h-4 w-4" aria-hidden="true" /><span className="sr-only"> (opens a new tab)</span>
            </a>
          </div>
          <p className="mb-3 font-mono text-[11px] text-muted">Waiting for approval · Code expires {new Date(attempt.expiresAt).toLocaleTimeString()}</p>
          <Button variant="secondary" size="sm" disabled={action != null} onClick={cancel}>Cancel</Button>
        </div>
      ) : null}
      {notice ? <p className="mt-3 text-[13px] text-muted" role="status">{notice}</p> : null}
      {error ? <div className="mt-3"><ErrorState title="Copilot connection action failed" error={error}
        onRetry={!connection ? () => run('load', async () => setConnection(await fetchCopilotStatus())) : undefined} /></div> : null}
    </Card>
  );
}
