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

import React, { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { usePathname, useRouter } from 'next/navigation';
import { Settings2 } from 'lucide-react';
import { Sidebar, Home, ListChecks, Users, BarChart3, History } from './Sidebar';
import { TopNav } from './TopNav';
import { StatusBar } from './StatusBar';
import { LoadingState } from '@/components/ui/LoadingState';
import { Seal } from '@/components/ui/Seal';
import { SetupGuide } from './SetupGuide';
import {
  fetchAnalysisExecutionOptions,
  fetchAppBootstrap,
  fetchRepositorySyncStatus,
  fetchSetup,
  isBackendUnavailable,
  triggerRepositorySync,
  fetchSyncStatus,
  retrySync,
  fetchPermissions,
  fetchEnrichedMergeQueue,
  fetchGitLabIntegrationStatus,
  fetchNotifications,
  GITLAB_AUTH_REQUIRED_EVENT,
  SCM_RECONNECT_REQUIRED_EVENT,
  markAllNotificationsRead,
  markNotificationRead,
} from '@/lib/api';
import {
  computeSetupGuide, forgeSummary, isForgeConfigured, SETUP_GUIDE_DISMISSED_KEY, statusBarItems,
} from '@/lib/setup-guide.mjs';

const routeMap = { home: '/', queue: '/queue', analyze: '/analyze', review: '/review', personas: '/personas', stats: '/stats', history: '/history', admin: '/admin', settings: '/settings' };
const pathToKey = Object.fromEntries(Object.entries(routeMap).map(([key, path]) => [path, key]));
const crumbLabels = { home: 'Home', queue: 'Merge Requests', analyze: 'New Analysis', review: 'MR Review', personas: 'Personas', stats: 'Organisation Insights', history: 'Analysis History', admin: 'Admin', settings: 'Settings' };
const navBase = [
  { key: 'home', label: 'Home', icon: Home },
  { key: 'queue', label: 'Merge Requests', icon: ListChecks },
  { key: 'personas', label: 'Personas', icon: Users },
  { key: 'stats', label: 'Organisation Insights', icon: BarChart3 },
  { key: 'history', label: 'Analysis History', icon: History, group: 'governance' },
  { key: 'admin', label: 'Admin', icon: Settings2, group: 'governance' },
];

const AppDataContext = createContext(null);

export function useAppData() {
  return useContext(AppDataContext);
}

export function useUser() {
  return useAppData()?.user || null;
}

const INITIAL_RETRY_MS = 2000;
const MAX_RETRY_MS = 15000;
const OAUTH_CALLBACK_PATHS = new Set(['/gitlab/oauth/callback', '/github/oauth/callback']);

function nextRetryDelay(ref) {
  const delay = ref.current;
  ref.current = Math.min(delay * 2, MAX_RETRY_MS);
  return delay;
}

function StartingState() {
  return (
    <div className="mx-auto mt-[12vh] flex max-w-[420px] flex-col items-center border border-border bg-surface px-8 py-10 text-center" role="status" aria-live="polite">
      <Seal size={44} />
      <h1 className="mt-5 font-display text-[22px] font-bold tracking-[-0.01em] text-fg">Arnold is starting…</h1>
      <p className="mt-2 text-[13px] leading-6 text-muted">The review service is warming up. This page will load on its own in a few seconds.</p>
      <span className="mt-5 inline-flex items-center gap-2 font-mono text-[11px] uppercase tracking-[0.08em] text-faint">
        <span className="h-2 w-2 animate-pulse rounded-full bg-accent" aria-hidden="true" />
        Waiting for backend
      </span>
    </div>
  );
}

function initialsFor(value) {
  return value ? value.split(/\s+/).map((word) => word[0]).join('').toUpperCase().slice(0, 2) : 'A';
}

export function AppLayout({ children }) {
  const pathname = usePathname();
  const router = useRouter();
  const [collapsed, setCollapsed] = useState(false);
  const [mobileSidebarOpen, setMobileSidebarOpen] = useState(false);
  const [permissions, setPermissions] = useState(null);
  const [user, setUser] = useState(null);
  const [userError, setUserError] = useState(null);
  const [integration, setIntegration] = useState(null);
  const [integrationError, setIntegrationError] = useState(null);
  const [integrationExpired, setIntegrationExpired] = useState(false);
  const [integrationLoaded, setIntegrationLoaded] = useState(false);
  const [gitLabAuthRequired, setGitLabAuthRequired] = useState(false);
  const [reconnectProviders, setReconnectProviders] = useState([]);
  const [copilot, setCopilot] = useState(null);
  const [queue, setQueue] = useState(null);
  const [queueError, setQueueError] = useState(null);
  const [openMergeRequests, setOpenMergeRequests] = useState([]);
  const [productivity, setProductivity] = useState(null);
  const [notifications, setNotifications] = useState({ items: [], unreadCount: 0, nextCursor: null });
  const [sync, setSync] = useState(null);
  const [syncRequestPending, setSyncRequestPending] = useState(false);
  const [syncError, setSyncError] = useState(null);
  const syncBusy = syncRequestPending || ['QUEUED', 'RUNNING'].includes(sync?.status);
  const [bootstrapStarting, setBootstrapStarting] = useState(false);
  const [bootstrapAttempt, setBootstrapAttempt] = useState(0);
  const bootstrapRetry = useRef(INITIAL_RETRY_MS);
  const [setup, setSetup] = useState(null);
  const [setupStarting, setSetupStarting] = useState(false);
  const [setupAttempt, setSetupAttempt] = useState(0);
  const setupRetry = useRef(INITIAL_RETRY_MS);
  const [harnesses, setHarnesses] = useState([]);
  const [repositorySync, setRepositorySync] = useState(null);
  const [repositorySyncPending, setRepositorySyncPending] = useState(false);
  const [repositorySyncError, setRepositorySyncError] = useState(null);
  const [guideRequested, setGuideRequested] = useState(false);
  const [guideDismissed, setGuideDismissed] = useState(true);

  useEffect(() => {
    const controller = new AbortController();
    fetchPermissions({ signal: controller.signal })
      .then((value) => { if (!controller.signal.aborted) setPermissions(value); })
      .catch(() => { if (!controller.signal.aborted) setPermissions({}); });
    return () => controller.abort();
  }, []);

  const refreshQueue = useCallback(async () => {
    try {
      const nextQueue = await fetchEnrichedMergeQueue();
      setQueue(nextQueue);
      setQueueError(null);
      return nextQueue;
    } catch (error) {
      setQueueError(error);
      throw error;
    }
  }, []);

  const applyBootstrap = useCallback((value) => {
    setUser(value.user);
    setUserError(null);
    setIntegration(value.integration);
    setIntegrationError(null);
    setIntegrationExpired(value.integration?.expiresAt != null
      && Number(value.integration.expiresAt) < Date.now());
    setIntegrationLoaded(true);
    setGitLabAuthRequired(false);
    setCopilot(value.copilot || null);
    setQueue(value.queue);
    setQueueError(null);
    setOpenMergeRequests(value.openMergeRequests);
    setProductivity(value.productivity);
    setNotifications(value.notifications);
    setSync(value.sync);
    return value;
  }, []);

  const refreshBootstrap = useCallback(async () => {
    try {
      const value = applyBootstrap(await fetchAppBootstrap());
      setBootstrapStarting(false);
      return value;
    } catch (error) {
      if (isBackendUnavailable(error)) {
        setBootstrapStarting(true);
        bootstrapRetry.current = INITIAL_RETRY_MS;
        setBootstrapAttempt((value) => value + 1);
        throw error;
      }
      setUserError(error);
      setIntegrationError(error);
      setQueueError(error);
      setIntegrationLoaded(true);
      throw error;
    }
  }, [applyBootstrap]);

  const startSync = useCallback(async () => {
    setSyncRequestPending(true);
    setSyncError(null);
    try {
      const next = await retrySync();
      setSync(next);
      if (next.status === 'SUCCEEDED') await refreshBootstrap();
    } catch (error) {
      setSyncError(error);
    } finally {
      setSyncRequestPending(false);
    }
  }, [refreshBootstrap]);

  useEffect(() => {
    if (!['QUEUED', 'RUNNING'].includes(sync?.status)) return undefined;
    const controller = new AbortController();
    let timer;
    const poll = async () => {
      try {
        const next = await fetchSyncStatus({ signal: controller.signal });
        if (controller.signal.aborted) return;
        setSync(next);
        setSyncError(null);
        if (next.status === 'SUCCEEDED') {
          await refreshBootstrap();
          return;
        }
        if (next.status === 'FAILED') return;
      } catch (error) {
        if (controller.signal.aborted) return;
        setSyncError(error);
      }
      if (!controller.signal.aborted) timer = window.setTimeout(poll, 2000);
    };
    timer = window.setTimeout(poll, 2000);
    return () => {
      controller.abort();
      window.clearTimeout(timer);
    };
  }, [sync?.status, refreshBootstrap]);

  const refreshIntegration = useCallback(async () => {
    try {
      const value = await fetchGitLabIntegrationStatus();
      setIntegration(value);
      setIntegrationError(null);
      setIntegrationExpired(value?.expiresAt != null && Number(value.expiresAt) < Date.now());
      setGitLabAuthRequired(false);
      if (value?.connected) setReconnectProviders((current) => current.filter((provider) => provider !== 'GITLAB'));
      return value;
    } catch (error) {
      setIntegrationError(error);
      throw error;
    } finally {
      setIntegrationLoaded(true);
    }
  }, []);

  const refreshNotifications = useCallback(async (options = {}) => {
    const value = await fetchNotifications({ limit: 10, ...options });
    setNotifications(value);
    return value;
  }, []);

  useEffect(() => {
    let active = true;
    let timer;
    fetchAppBootstrap()
      .then((value) => {
        if (!active) return;
        bootstrapRetry.current = INITIAL_RETRY_MS;
        setBootstrapStarting(false);
        applyBootstrap(value);
      })
      .catch((error) => {
        if (!active) return;
        if (isBackendUnavailable(error)) {
          setBootstrapStarting(true);
          timer = window.setTimeout(() => setBootstrapAttempt((value) => value + 1), nextRetryDelay(bootstrapRetry));
          return;
        }
        setBootstrapStarting(false);
        setUserError(error);
        setIntegrationError(error);
        setQueueError(error);
        setIntegrationLoaded(true);
      });
    return () => {
      active = false;
      window.clearTimeout(timer);
    };
  }, [applyBootstrap, bootstrapAttempt]);

  const loadRepositorySync = useCallback(async (options = {}) => {
    try {
      const value = await fetchRepositorySyncStatus(options);
      setRepositorySync(value);
      setRepositorySyncError(null);
      return value;
    } catch (error) {
      if (!options.signal?.aborted) setRepositorySyncError(error);
      return null;
    }
  }, []);

  const refreshSetup = useCallback(() => {
    setupRetry.current = INITIAL_RETRY_MS;
    setSetupAttempt((value) => value + 1);
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    const { signal } = controller;
    let timer;
    fetchSetup({ signal })
      .then((value) => {
        if (signal.aborted) return;
        setupRetry.current = INITIAL_RETRY_MS;
        setSetupStarting(false);
        setSetup(value);
      })
      .catch((error) => {
        if (signal.aborted) return;
        if (isBackendUnavailable(error)) {
          setSetupStarting(true);
          timer = window.setTimeout(() => setSetupAttempt((value) => value + 1), nextRetryDelay(setupRetry));
          return;
        }
        setSetupStarting(false);
      });
    fetchAnalysisExecutionOptions({ signal })
      .then((value) => { if (!signal.aborted) setHarnesses(Array.isArray(value) ? value : []); })
      .catch(() => {});
    loadRepositorySync({ signal });
    return () => {
      controller.abort();
      window.clearTimeout(timer);
    };
  }, [pathname, setupAttempt, loadRepositorySync]);

  useEffect(() => {
    window.addEventListener('focus', refreshSetup);
    return () => window.removeEventListener('focus', refreshSetup);
  }, [refreshSetup]);

  const repositorySyncRunning = Boolean(repositorySync?.running
    || repositorySync?.providers?.some((provider) => provider.status === 'RUNNING'));
  useEffect(() => {
    if (!repositorySyncRunning) return undefined;
    const controller = new AbortController();
    const timer = window.setTimeout(() => loadRepositorySync({ signal: controller.signal }), 5000);
    return () => {
      controller.abort();
      window.clearTimeout(timer);
    };
  }, [repositorySync, repositorySyncRunning, loadRepositorySync]);

  const startRepositorySync = useCallback(async () => {
    setRepositorySyncPending(true);
    setRepositorySyncError(null);
    try {
      await triggerRepositorySync();
      await loadRepositorySync();
    } catch (error) {
      setRepositorySyncError(error);
    } finally {
      setRepositorySyncPending(false);
    }
  }, [loadRepositorySync]);

  const updateCopilot = useCallback((connection) => {
    setCopilot(connection);
    setSetup((current) => current ? { ...current, copilot: { ...current.copilot, ...connection } } : current);
    refreshSetup();
  }, [refreshSetup]);

  useEffect(() => {
    try {
      setGuideDismissed(window.localStorage.getItem(SETUP_GUIDE_DISMISSED_KEY) === 'true');
    } catch {
      setGuideDismissed(false);
    }
  }, []);

  useEffect(() => {
    const requireGitLabAuthorization = () => setGitLabAuthRequired(true);
    const requireReconnect = (event) => {
      const provider = event.detail?.provider;
      if (!provider) return;
      setReconnectProviders((current) => current.includes(provider) ? current : [...current, provider]);
      refreshSetup();
    };
    window.addEventListener(GITLAB_AUTH_REQUIRED_EVENT, requireGitLabAuthorization);
    window.addEventListener(SCM_RECONNECT_REQUIRED_EVENT, requireReconnect);
    return () => {
      window.removeEventListener(GITLAB_AUTH_REQUIRED_EVENT, requireGitLabAuthorization);
      window.removeEventListener(SCM_RECONNECT_REQUIRED_EVENT, requireReconnect);
    };
  }, [refreshSetup]);

  useEffect(() => {
    setMobileSidebarOpen(false);
  }, [pathname]);

  useEffect(() => {
    if (!user) return undefined;
    const controller = new AbortController();
    const refreshWhenVisible = () => {
      if (document.visibilityState !== 'visible') return;
      refreshNotifications({ signal: controller.signal }).catch(() => {});
    };
    const interval = window.setInterval(refreshWhenVisible, 60_000);
    document.addEventListener('visibilitychange', refreshWhenVisible);
    return () => {
      controller.abort();
      window.clearInterval(interval);
      document.removeEventListener('visibilitychange', refreshWhenVisible);
    };
  }, [refreshNotifications, user]);

  useEffect(() => {
    if (!mobileSidebarOpen) return undefined;
    const closeOnEscape = (event) => {
      if (event.key === 'Escape') setMobileSidebarOpen(false);
    };
    document.addEventListener('keydown', closeOnEscape);
    return () => document.removeEventListener('keydown', closeOnEscape);
  }, [mobileSidebarOpen]);

  const activeKey = pathToKey[pathname] || 'home';
  const displayName = user ? user.displayName || user.username || user.email : 'User details unavailable';
  const isAdminRoute = pathname === '/admin' || pathname.startsWith('/admin/');
  const canAdmin = permissions?.superadmin === true;
  const navItems = navBase.filter((item) => item.key !== 'admin' || canAdmin).map((item) => item.key === 'queue' && queue ? { ...item, badge: queue.items.length } : item);
  const gitLabConfigured = isForgeConfigured(setup, 'GITLAB');
  const starting = bootstrapStarting || setupStarting;
  const setupGuide = useMemo(() => computeSetupGuide({
    setup,
    harnesses,
    repositorySync,
    gitLabReauthRequired: gitLabAuthRequired || integrationExpired,
    reconnectProviders,
  }), [setup, harnesses, repositorySync, gitLabAuthRequired, integrationExpired, reconnectProviders]);
  const onCallbackRoute = OAUTH_CALLBACK_PATHS.has(pathname);
  const guideSuppressed = onCallbackRoute || isAdminRoute || pathname === '/settings';
  const showSetupGuide = Boolean(setup) && !starting && !onCallbackRoute && (
    guideRequested || (!guideSuppressed && (setupGuide.blocking || (setupGuide.pending && !guideDismissed)))
  );
  const guideClosable = !setupGuide.blocking || guideSuppressed;
  const connectionSummary = setup ? forgeSummary(setup) : undefined;
  const footerItems = statusBarItems({ setup, repositorySync, starting });

  const openSetupGuide = useCallback(() => setGuideRequested(true), []);
  const closeSetupGuide = useCallback(() => {
    setGuideRequested(false);
    setGuideDismissed(true);
    try {
      window.localStorage.setItem(SETUP_GUIDE_DISMISSED_KEY, 'true');
    } catch {
      // Without storage the guide simply reappears on the next visit.
    }
  }, []);

  const markNotification = useCallback(async (notification) => {
    try {
      if (!notification.read) {
        const updated = await markNotificationRead(notification.id);
        setNotifications((current) => ({
          ...current,
          unreadCount: Math.max(0, current.unreadCount - 1),
          items: current.items.map((item) => item.id === updated.id ? updated : item),
        }));
      }
    } catch {
      // The notification's destination remains useful if the read receipt fails.
    }
    if (notification.deepLink.startsWith('/')) {
      router.push(notification.deepLink);
    }
  }, [router]);

  const markEveryNotificationRead = useCallback(async () => {
    const updated = await markAllNotificationsRead();
    setNotifications(updated);
  }, []);

  const toggleSidebar = useCallback(() => {
    if (window.matchMedia('(max-width: 767px)').matches) {
      setMobileSidebarOpen((value) => !value);
      return;
    }
    setCollapsed((value) => !value);
  }, []);

  const navigate = useCallback((key) => {
    setMobileSidebarOpen(false);
    if (routeMap[key]) router.push(routeMap[key]);
  }, [router]);

  return (
    <AppDataContext value={{
      user,
      userError,
      integration,
      integrationError,
      integrationLoaded,
      copilot,
      setCopilot: updateCopilot,
      setup,
      setupGuide,
      openSetupGuide,
      refreshSetup,
      harnesses,
      repositorySync,
      gitLabConfigured,
      starting,
      queue,
      queueError,
      openMergeRequests,
      productivity,
      notifications,
      sync,
      syncBusy,
      syncError,
      startSync,
      refreshQueue,
      refreshIntegration,
      refreshNotifications,
      refreshBootstrap,
      setQueue,
      setNotifications,
    }}>
      <div className="flex h-screen overflow-hidden">
        <div className="hidden h-full shrink-0 md:block">
          <Sidebar
            navItems={navItems}
            activeKey={activeKey}
            collapsed={collapsed}
            onToggleCollapse={toggleSidebar}
            onNavigate={navigate}
            onRunAnalysis={() => router.push('/analyze')}
            user={{ name: displayName, initials: initialsFor(displayName), avatarUrl: user?.avatarUrl }}
            connection={connectionSummary}
            onOpenSetup={openSetupGuide}
            onOpenSettings={() => router.push('/settings')}
            onLogout={() => window.location.assign('/auth/logout')}
          />
        </div>
        {mobileSidebarOpen ? (
          <div className="fixed inset-0 z-50 flex md:hidden">
            <button
              type="button"
              aria-label="Close navigation"
              className="absolute inset-0 bg-[color-mix(in_oklch,var(--fg)_28%,transparent)]"
              onClick={() => setMobileSidebarOpen(false)}
            />
            <Sidebar
              className="relative z-10 shadow-xl"
              navItems={navItems}
              activeKey={activeKey}
              onNavigate={navigate}
              onRunAnalysis={() => {
                setMobileSidebarOpen(false);
                router.push('/analyze');
              }}
              user={{ name: displayName, initials: initialsFor(displayName), avatarUrl: user?.avatarUrl }}
              connection={connectionSummary}
            onOpenSetup={openSetupGuide}
              onOpenSettings={() => {
                setMobileSidebarOpen(false);
                router.push('/settings');
              }}
              onLogout={() => window.location.assign('/auth/logout')}
            />
          </div>
        ) : null}
        <div className="flex min-w-0 flex-1 flex-col overflow-hidden">
          <TopNav
            breadcrumbs={[{ label: 'Arnold' }, { label: crumbLabels[activeKey] || 'Home' }]}
            notifications={notifications}
            sync={gitLabConfigured ? sync : null}
            repositorySync={gitLabConfigured ? null : repositorySync}
            repositorySyncBusy={repositorySyncPending || repositorySyncRunning}
            repositorySyncError={repositorySyncError}
            onStartRepositorySync={canAdmin ? startRepositorySync : undefined}
            syncBusy={syncBusy}
            syncError={syncError}
            onStartSync={startSync}
            onNotificationsOpen={refreshNotifications}
            onNotificationClick={markNotification}
            onMarkAllNotificationsRead={markEveryNotificationRead}
            onToggleCollapse={toggleSidebar}
          />
          <main className={`${pathname.startsWith('/review') ? 'p-0' : pathname === '/queue' ? 'px-4 py-6' : 'px-4 py-6 sm:px-6 lg:px-10 lg:py-8'} ${pathname === '/history' ? 'flex flex-1 flex-col' : 'flex-1'} overflow-y-auto`}>{bootstrapStarting && !user
            ? <StartingState />
            : isAdminRoute && !canAdmin
            ? permissions === null
              ? <LoadingState label="Checking permissions" />
              : <p role="alert" className="text-muted">Access denied. Superadmin permission is required.</p>
            : children}</main>
          {!pathname.startsWith('/review') ? <StatusBar
            items={footerItems}
            version="Arnold · Lattice"
          /> : null}
        </div>
      </div>
      <SetupGuide
        open={showSetupGuide}
        guide={setupGuide}
        closable={guideClosable}
        onClose={closeSetupGuide}
        onCopilotConnected={updateCopilot}
      />
    </AppDataContext>
  );
}
