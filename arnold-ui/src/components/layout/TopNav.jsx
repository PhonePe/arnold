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
import { Bell, CheckCheck, PanelLeft, RefreshCw } from 'lucide-react';
import { ThemeToggle } from './ThemeToggle';
import { repositorySyncSummary } from '@/lib/setup-guide.mjs';

const relativeTime = new Intl.RelativeTimeFormat(undefined, { numeric: 'auto' });

function timeAgo(timestamp) {
  const seconds = Math.round((timestamp - Date.now()) / 1000);
  if (Math.abs(seconds) < 60) return relativeTime.format(seconds, 'second');
  const minutes = Math.round(seconds / 60);
  if (Math.abs(minutes) < 60) return relativeTime.format(minutes, 'minute');
  const hours = Math.round(minutes / 60);
  if (Math.abs(hours) < 24) return relativeTime.format(hours, 'hour');
  return relativeTime.format(Math.round(hours / 24), 'day');
}

function RepositorySyncControl({ summary, busy, error, onStart }) {
  if (!summary) return null;
  const failed = Boolean(error) || summary.tone === 'bad';
  const label = busy ? 'Syncing repositories…' : error ? `Sync failed · ${error.message}` : summary.label;
  const tone = failed
    ? 'border-bad bg-[color-mix(in_oklch,var(--bad)_8%,var(--surface))] text-bad'
    : 'border-border bg-surface text-muted';
  const content = (
    <>
      <RefreshCw className={`h-3.5 w-3.5 flex-none ${failed ? 'text-bad' : 'text-accent'} ${busy ? 'animate-spin' : ''}`} aria-hidden="true" />
      <span className="whitespace-nowrap font-mono text-[10.5px]">{label}</span>
    </>
  );
  const title = summary.detail || 'Open pull requests and merge requests from installed repositories';
  if (!onStart) {
    return (
      <span role="status" title={title} className={`hidden items-center gap-2 border px-3 py-1.5 sm:flex ${tone}`}>
        {content}
      </span>
    );
  }
  return (
    <button
      type="button"
      onClick={() => Promise.resolve(onStart()).catch(() => {})}
      disabled={busy}
      title={`${title} · Sync now`}
      aria-label={`${label}. Sync all repositories now`}
      className={`hidden items-center gap-2 border px-3 py-1.5 sm:flex ${tone} ${failed ? '' : 'hover:border-accent hover:text-fg'} focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-70`}
    >
      {content}
    </button>
  );
}

export function TopNav({
  breadcrumbs = [],
  notifications = { items: [], unreadCount: 0 },
  sync = null,
  syncBusy = false,
  syncError = null,
  onStartSync,
  repositorySync = null,
  repositorySyncBusy = false,
  repositorySyncError = null,
  onStartRepositorySync,
  onNotificationsOpen,
  onNotificationClick,
  onMarkAllNotificationsRead,
  onToggleCollapse,
  className = '',
  ...props
}) {
  const [notificationsOpen, setNotificationsOpen] = useState(false);
  const [markingAll, setMarkingAll] = useState(false);
  const popoverRef = useRef(null);

  useEffect(() => {
    if (!notificationsOpen) return undefined;
    const close = (event) => {
      if (event.key === 'Escape' || !popoverRef.current?.contains(event.target)) {
        setNotificationsOpen(false);
      }
    };
    document.addEventListener('keydown', close);
    document.addEventListener('pointerdown', close);
    return () => {
      document.removeEventListener('keydown', close);
      document.removeEventListener('pointerdown', close);
    };
  }, [notificationsOpen]);

  async function markAllRead() {
    setMarkingAll(true);
    try {
      await onMarkAllNotificationsRead?.();
    } catch {
      // Keep the popover usable; the unread state remains unchanged on failure.
    } finally {
      setMarkingAll(false);
    }
  }

  return (
    <header
      className={`sticky top-0 z-30 flex h-[60px] items-center gap-4 border-b border-border bg-[color-mix(in_oklch,var(--bg)_86%,transparent)] px-6 backdrop-blur-[10px] ${className}`}
      {...props}
    >
      {onToggleCollapse && (
        <button
          onClick={onToggleCollapse}
          aria-label="Toggle sidebar"
          title="Toggle sidebar"
          className="grid h-[34px] w-[34px] place-items-center rounded-none border border-transparent text-muted transition-colors hover:bg-[var(--ink-wash)] hover:text-fg active:scale-95"
        >
          <PanelLeft className="h-[18px] w-[18px]" strokeWidth={1.8} />
        </button>
      )}

      {breadcrumbs.length > 0 && (
        <div className="font-mono text-[12px] tracking-[0.02em] text-muted">
          {breadcrumbs.map((crumb, i) => (
            <React.Fragment key={i}>
              {i > 0 && <span className="px-1.5 opacity-50">/</span>}
              {i === breadcrumbs.length - 1 ? (
                <span className="font-semibold text-fg">{crumb.label}</span>
              ) : crumb.href ? (
                <a href={crumb.href} className="transition-colors hover:text-accent">
                  {crumb.label}
                </a>
              ) : (
                <span>{crumb.label}</span>
              )}
            </React.Fragment>
          ))}
        </div>
      )}

      <div className="ml-auto flex items-center gap-2">
        <button
          disabled
          className="hidden h-[34px] w-[min(260px,30vw)] border border-border-2 bg-surface-2 px-3 text-left text-[13px] text-faint disabled:cursor-not-allowed sm:block"
          aria-label="Global search unavailable"
          title="Global search is unavailable"
        >
          Search unavailable
        </button>

        {sync ? (
          <button
            type="button"
            onClick={() => Promise.resolve(onStartSync?.()).catch(() => {})}
            disabled={syncBusy || !onStartSync}
            role="status"
            title={sync.failure || syncError?.message || 'Sync GitLab now'}
            className={`hidden items-center gap-2 border px-3 py-1.5 sm:flex ${
              syncError || sync.status === 'FAILED'
                ? 'border-bad bg-[color-mix(in_oklch,var(--bad)_8%,var(--surface))] text-bad'
                : 'border-border bg-surface text-muted hover:border-accent hover:text-fg'
            } focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-70`}
          >
            <RefreshCw
              className={`h-3.5 w-3.5 flex-none ${syncError || sync.status === 'FAILED' ? 'text-bad' : 'text-accent'} ${syncBusy ? 'animate-spin' : ''}`}
              aria-hidden="true"
            />
            <span className="whitespace-nowrap font-mono text-[10.5px]" aria-label={syncBusy ? 'Syncing' : 'Sync GitLab now'}>
              {syncBusy
                ? 'Syncing…'
                : syncError || sync.status === 'FAILED'
                  ? `Sync failed${sync.failure || syncError?.message ? ` · ${sync.failure || syncError.message}` : ''}`
                  : sync.lastSucceededAt
                    ? `Synced ${timeAgo(sync.lastSucceededAt * 1)}`
                    : 'Not synced yet'}
            </span>
          </button>
        ) : null}

        {!sync ? (
          <RepositorySyncControl
            summary={repositorySyncSummary(repositorySync)}
            busy={repositorySyncBusy}
            error={repositorySyncError}
            onStart={onStartRepositorySync}
          />
        ) : null}

        <div className="relative" ref={popoverRef}>
          <button
            type="button"
            className="relative grid h-[34px] w-[34px] place-items-center rounded-none border border-transparent text-muted transition-colors hover:bg-[var(--ink-wash)] hover:text-fg active:scale-95 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            aria-label={`${notifications.unreadCount || 0} unread notifications`}
            aria-haspopup="dialog"
            aria-expanded={notificationsOpen}
            onClick={() => {
              const opening = !notificationsOpen;
              setNotificationsOpen(opening);
              if (opening) Promise.resolve(onNotificationsOpen?.()).catch(() => {});
            }}
          >
            <Bell className="h-[18px] w-[18px]" strokeWidth={1.8} />
            {notifications.unreadCount > 0 ? (
              <span className="absolute right-[6px] top-[5px] h-[7px] w-[7px] rounded-full bg-accent" />
            ) : null}
          </button>
          {notificationsOpen ? (
            <section
              role="dialog"
              aria-label="Notifications"
              className="absolute right-0 top-[calc(100%+9px)] z-50 w-[min(360px,calc(100vw-32px))] border border-border-2 bg-surface shadow-lg"
            >
              <div className="flex items-center justify-between border-b border-border px-4 py-3">
                <div>
                  <h2 className="font-display text-[15px] font-bold text-fg">Notifications</h2>
                  <p className="font-mono text-[10px] uppercase tracking-[0.08em] text-muted">
                    {notifications.unreadCount || 0} unread
                  </p>
                </div>
                <button
                  type="button"
                  disabled={markingAll || notifications.unreadCount === 0}
                  onClick={markAllRead}
                  className="inline-flex items-center gap-1.5 font-mono text-[11px] text-accent-ink hover:text-accent disabled:text-faint"
                >
                  <CheckCheck className="h-3.5 w-3.5" aria-hidden="true" />
                  {markingAll ? 'Marking…' : 'Mark all read'}
                </button>
              </div>
              <div className="max-h-[360px] overflow-y-auto">
                {notifications.items.length === 0 ? (
                  <p className="px-4 py-8 text-center text-[13px] text-muted">
                    No activity yet. Sync updates will appear here.
                  </p>
                ) : notifications.items.map((notification) => (
                  <button
                    type="button"
                    key={notification.id}
                    onClick={() => {
                      setNotificationsOpen(false);
                      void onNotificationClick?.(notification);
                    }}
                    className={`grid w-full grid-cols-[8px_1fr] gap-3 border-b border-border px-4 py-3 text-left last:border-b-0 hover:bg-[var(--ink-wash)] ${
                      notification.read ? 'text-muted' : 'text-fg'
                    }`}
                  >
                    <span className={`mt-1.5 h-2 w-2 rounded-full ${notification.read ? 'bg-border-2' : 'bg-accent'}`} />
                    <span className="min-w-0">
                      <span className="block truncate text-[13px] font-semibold">{notification.title}</span>
                      {notification.body ? <span className="mt-0.5 block text-[12px] leading-5 text-muted">{notification.body}</span> : null}
                      <span className="mt-1 block font-mono text-[10px] uppercase tracking-[0.04em] text-faint">
                        {timeAgo(notification.createdAt)}
                      </span>
                    </span>
                  </button>
                ))}
              </div>
            </section>
          ) : null}
        </div>

        <ThemeToggle />
      </div>
    </header>
  );
}
