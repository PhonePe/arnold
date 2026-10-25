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

import React, { useCallback, useEffect, useState } from 'react';
import {
  Crown,
  RadioTower,
  RefreshCcw,
  Save,
  ServerCog,
  Webhook,
} from 'lucide-react';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { ErrorState } from '@/components/ui/ErrorState';
import { LoadingState } from '@/components/ui/LoadingState';
import { Overline } from '@/components/ui/Overline';
import {
  fetchUserSyncControl,
  resetUserSyncCallbackObservation,
  updateUserSyncMode,
} from '@/lib/api';

const MODES = [
  {
    id: 'AUTO',
    label: 'Auto',
    eyebrow: 'Recommended',
    description: 'Keep leader-elected reconciliation active while callbacks apply incremental updates between syncs.',
    icon: ServerCog,
  },
  {
    id: 'POLLING',
    label: 'Polling',
    eyebrow: 'Always scheduled',
    description: 'Keep leader-elected background sync active. Callbacks are still accepted as incremental updates.',
    icon: RadioTower,
  },
  {
    id: 'CALLBACK_ONLY',
    label: 'Callback only',
    eyebrow: 'Event driven',
    description: 'Disable periodic sync and leader work. First-time and manually requested syncs still run on demand.',
    icon: Webhook,
  },
];

function formatTimestamp(value) {
  return value ? new Date(value).toLocaleString() : 'Not observed';
}

function compactId(value) {
  if (!value) return 'No active leader';
  return value.length > 34 ? `${value.slice(0, 16)}…${value.slice(-12)}` : value;
}

function modeLabel(mode) {
  return MODES.find((item) => item.id === mode)?.label || mode;
}

function RuntimeField({ label, value, detail }) {
  return (
    <div className="border-t border-border px-4 py-4 first:border-t-0 sm:px-5">
      <div className="font-mono text-[10px] font-semibold uppercase tracking-[0.12em] text-faint">{label}</div>
      <div className="mt-1 text-[14px] font-semibold text-fg" title={typeof value === 'string' ? value : undefined}>
        {value}
      </div>
      {detail ? <div className="mt-1 text-[12px] leading-5 text-muted">{detail}</div> : null}
    </div>
  );
}

export default function AdminPage() {
  const [control, setControl] = useState(null);
  const [selectedMode, setSelectedMode] = useState('AUTO');
  const [status, setStatus] = useState('loading');
  const [error, setError] = useState(null);
  const [saving, setSaving] = useState(false);
  const [notice, setNotice] = useState('');

  const load = useCallback(async (signal) => {
    setStatus('loading');
    setError(null);
    try {
      const response = await fetchUserSyncControl(signal ? { signal } : {});
      setControl(response);
      setSelectedMode(response.configuredMode);
      setStatus('ready');
    } catch (nextError) {
      if (!signal?.aborted) {
        setError(nextError);
        setStatus('error');
      }
    }
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    load(controller.signal);
    return () => controller.abort();
  }, [load]);

  async function saveMode() {
    if (!control || selectedMode === control.configuredMode) return;
    setSaving(true);
    setError(null);
    setNotice('');
    try {
      const response = await updateUserSyncMode(selectedMode);
      setControl(response);
      setSelectedMode(response.configuredMode);
      setNotice(`Sync mode changed to ${modeLabel(response.configuredMode)}.`);
    } catch (nextError) {
      setError(nextError);
    } finally {
      setSaving(false);
    }
  }

  async function resetCallbackObservation() {
    setSaving(true);
    setError(null);
    setNotice('');
    try {
      const response = await resetUserSyncCallbackObservation();
      setControl(response);
      setSelectedMode(response.configuredMode);
      setNotice('Callback observation reset. Background reconciliation is unchanged.');
    } catch (nextError) {
      setError(nextError);
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="mx-auto max-w-[1280px]">
      <div className="mb-6">
        <Overline>Cluster operations</Overline>
        <h1 className="mt-1 font-display text-[clamp(30px,4vw,42px)] font-bold leading-[1.05] tracking-[-0.02em] text-fg">
          Admin
        </h1>
        <p className="mt-2 max-w-[72ch] text-[15px] text-muted">
          Control how GitLab merge requests stay synchronized across this Arnold cluster.
        </p>
      </div>

      {status === 'loading' ? <LoadingState label="Loading cluster sync controls" /> : null}
      {status === 'error' ? (
        <ErrorState title="Could not load sync controls" error={error} onRetry={() => load()} />
      ) : null}

      {status === 'ready' && control ? (
        <div className="space-y-5">
          <section className="grid border border-border bg-surface md:grid-cols-[1fr_auto_1fr_auto_1fr]">
            <div className="flex items-center gap-3 p-4 sm:p-5">
              <Webhook className="h-5 w-5 text-accent" strokeWidth={1.8} aria-hidden="true" />
              <div>
                <div className="font-mono text-[10px] uppercase tracking-[0.12em] text-faint">Signal</div>
                <div className="mt-0.5 text-[13px] font-semibold text-fg">
                  {control.callbackObserved ? 'GitLab callback observed' : 'Waiting for callback'}
                </div>
              </div>
            </div>
            <div className="hidden w-px bg-border md:block" aria-hidden="true" />
            <div className="flex items-center gap-3 border-t border-border p-4 sm:p-5 md:border-t-0">
              <ServerCog className="h-5 w-5 text-muted" strokeWidth={1.8} aria-hidden="true" />
              <div>
                <div className="font-mono text-[10px] uppercase tracking-[0.12em] text-faint">Configured policy</div>
                <div className="mt-0.5 text-[13px] font-semibold text-fg">{modeLabel(control.configuredMode)}</div>
              </div>
            </div>
            <div className="hidden w-px bg-border md:block" aria-hidden="true" />
            <div className="flex items-center gap-3 border-t border-border p-4 sm:p-5 md:border-t-0">
              {control.pollingEnabled
                ? <RadioTower className="h-5 w-5 text-accent" strokeWidth={1.8} aria-hidden="true" />
                : <Webhook className="h-5 w-5 text-ok" strokeWidth={1.8} aria-hidden="true" />}
              <div>
                <div className="font-mono text-[10px] uppercase tracking-[0.12em] text-faint">Effective now</div>
                <div className="mt-0.5 flex items-center gap-2 text-[13px] font-semibold text-fg">
                  {modeLabel(control.effectiveMode)}
                  <Badge variant={control.pollingEnabled ? 'warning' : 'success'}>Live</Badge>
                </div>
              </div>
            </div>
          </section>

          <section className="border border-border bg-surface">
            <div className="border-b border-border px-4 py-4 sm:px-5">
              <h2 className="font-display text-[18px] font-bold text-fg">Merge request sync mode</h2>
              <p className="mt-1 text-[12.5px] text-muted">
                This persisted setting is shared by every node in the cluster.
              </p>
            </div>

            <div className="grid gap-px bg-border lg:grid-cols-3" role="radiogroup" aria-label="Merge request sync mode">
              {MODES.map((mode) => {
                const Icon = mode.icon;
                const selected = selectedMode === mode.id;
                return (
                  <button
                    key={mode.id}
                    type="button"
                    role="radio"
                    aria-checked={selected}
                    disabled={saving}
                    onClick={() => {
                      setSelectedMode(mode.id);
                      setNotice('');
                    }}
                    className={`min-h-[176px] bg-surface p-5 text-left transition-colors focus-visible:z-10 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:opacity-60 ${
                      selected ? 'bg-[var(--accent-soft)] shadow-[inset_4px_0_0_var(--accent)]' : 'hover:bg-[var(--ink-wash)]'
                    }`}
                  >
                    <div className="flex items-start justify-between gap-3">
                      <Icon className={`h-5 w-5 ${selected ? 'text-accent' : 'text-muted'}`} strokeWidth={1.8} aria-hidden="true" />
                      <span className={`h-3.5 w-3.5 border ${selected ? 'border-accent bg-accent' : 'border-border-2 bg-surface'}`} aria-hidden="true" />
                    </div>
                    <div className="mt-5 font-mono text-[10px] font-semibold uppercase tracking-[0.12em] text-faint">
                      {mode.eyebrow}
                    </div>
                    <div className="mt-1 font-display text-[18px] font-bold text-fg">{mode.label}</div>
                    <p className="mt-2 text-[12.5px] leading-5 text-muted">{mode.description}</p>
                  </button>
                );
              })}
            </div>

            <div className="flex flex-wrap items-center justify-between gap-3 border-t border-border px-4 py-4 sm:px-5">
              <div className="min-h-5 text-[12px] text-muted" aria-live="polite">
                {error ? <span className="text-bad">{error.message}</span> : notice}
              </div>
              <Button
                size="sm"
                disabled={saving || selectedMode === control.configuredMode}
                onClick={saveMode}
              >
                <Save className="h-3.5 w-3.5" strokeWidth={2} aria-hidden="true" />
                {saving ? 'Saving…' : 'Save mode'}
              </Button>
            </div>
          </section>

          <section className="grid border border-border bg-surface lg:grid-cols-[1fr_1px_1fr]">
            <div>
              <div className="flex items-center justify-between gap-3 border-b border-border px-4 py-4 sm:px-5">
                <div>
                  <h2 className="font-display text-[18px] font-bold text-fg">Callback state</h2>
                  <p className="mt-1 text-[12.5px] text-muted">Callback health is observed independently of background reconciliation.</p>
                </div>
                <Badge variant={control.callbackObserved ? 'success' : 'default'}>
                  {control.callbackObserved ? 'Observed' : 'Not observed'}
                </Badge>
              </div>
              <RuntimeField label="Last valid callback" value={formatTimestamp(control.lastCallbackAt)} />
              <div className="border-t border-border px-4 py-4 sm:px-5">
                <Button
                  variant="secondary"
                  size="sm"
                  disabled={saving || !control.callbackObserved}
                  onClick={resetCallbackObservation}
                >
                  <RefreshCcw className="h-3.5 w-3.5" strokeWidth={2} aria-hidden="true" />
                  Reset detection
                </Button>
              </div>
            </div>

            <div className="hidden bg-border lg:block" aria-hidden="true" />

            <div className="border-t border-border lg:border-t-0">
              <div className="flex items-center justify-between gap-3 border-b border-border px-4 py-4 sm:px-5">
                <div>
                  <h2 className="font-display text-[18px] font-bold text-fg">Cluster runtime</h2>
                  <p className="mt-1 text-[12.5px] text-muted">Lease ownership for the periodic sync worker.</p>
                </div>
                <Badge variant={control.pollingEnabled ? control.currentNodeLeader ? 'success' : 'warning' : 'default'}>
                  {control.pollingEnabled ? control.currentNodeLeader ? 'Leader' : 'Standby' : 'Off'}
                </Badge>
              </div>
              <RuntimeField
                label="Active leader"
                value={compactId(control.leaderId)}
                detail={control.leaderLeaseUntil ? `Lease valid until ${formatTimestamp(control.leaderLeaseUntil)}` : 'No polling lease is active.'}
              />
              <RuntimeField
                label="Current node"
                value={compactId(control.currentNodeId)}
                detail={control.currentNodeLeader ? 'This node owns the periodic sync lease.' : 'This node is not running periodic sync.'}
              />
            </div>
          </section>

          <div className="flex items-start gap-3 border border-border bg-[var(--ink-wash)] px-4 py-3 text-[12px] leading-5 text-muted">
            <Crown className="mt-0.5 h-4 w-4 flex-none text-accent" strokeWidth={1.8} aria-hidden="true" />
            One-time sync for a new user and manual retries remain available in every mode. Leader election governs only the periodic background scan.
          </div>
        </div>
      ) : null}
    </div>
  );
}
