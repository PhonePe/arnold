'use client';

import React, { createContext, useCallback, useContext, useEffect, useState } from 'react';
import { usePathname, useRouter } from 'next/navigation';
import { GitPullRequest, Settings2 } from 'lucide-react';
import { Sidebar, Home, ListChecks, FileCheck2, Users, BarChart3, ShieldCheck, History } from './Sidebar';
import { TopNav } from './TopNav';
import { StatusBar } from './StatusBar';
import { GitLabConnectionGate } from './GitLabConnectionGate';
import {
  fetchAppBootstrap,
  fetchEnrichedMergeQueue,
  fetchGitLabIntegrationStatus,
  fetchNotifications,
  GITLAB_AUTH_REQUIRED_EVENT,
  markAllNotificationsRead,
  markNotificationRead,
} from '@/lib/api';

const routeMap = { home: '/', mergeRequests: '/merge-requests', queue: '/queue', analyze: '/analyze', review: '/review', personas: '/personas', stats: '/stats', policies: '/policies', history: '/history', admin: '/admin', settings: '/settings' };
const pathToKey = Object.fromEntries(Object.entries(routeMap).map(([key, path]) => [path, key]));
const crumbLabels = { home: 'Home', mergeRequests: 'Open MRs', queue: 'Merge Queue', analyze: 'New Analysis', review: 'MR Review', personas: 'Personas', stats: 'Stats', policies: 'Review Policies', history: 'Analysis History', admin: 'Admin', settings: 'Settings' };
const navBase = [
  { key: 'home', label: 'Home', icon: Home },
  { key: 'mergeRequests', label: 'Open MRs', icon: GitPullRequest },
  { key: 'queue', label: 'Merge Queue', icon: ListChecks },
  { key: 'review', label: 'MR Review', icon: FileCheck2 },
  { key: 'personas', label: 'Personas', icon: Users },
  { key: 'stats', label: 'Stats', icon: BarChart3 },
  { key: 'policies', label: 'Review Policies', icon: ShieldCheck, group: 'governance' },
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

function initialsFor(value) {
  return value ? value.split(/\s+/).map((word) => word[0]).join('').toUpperCase().slice(0, 2) : 'A';
}

export function AppLayout({ children }) {
  const pathname = usePathname();
  const router = useRouter();
  const [collapsed, setCollapsed] = useState(false);
  const [mobileSidebarOpen, setMobileSidebarOpen] = useState(false);
  const [user, setUser] = useState(null);
  const [userError, setUserError] = useState(null);
  const [integration, setIntegration] = useState(null);
  const [integrationError, setIntegrationError] = useState(null);
  const [integrationExpired, setIntegrationExpired] = useState(false);
  const [integrationLoaded, setIntegrationLoaded] = useState(false);
  const [gitLabAuthRequired, setGitLabAuthRequired] = useState(false);
  const [queue, setQueue] = useState(null);
  const [queueError, setQueueError] = useState(null);
  const [openMergeRequests, setOpenMergeRequests] = useState([]);
  const [productivity, setProductivity] = useState(null);
  const [notifications, setNotifications] = useState({ items: [], unreadCount: 0, nextCursor: null });
  const [sync, setSync] = useState(null);

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
      return applyBootstrap(await fetchAppBootstrap());
    } catch (error) {
      setUserError(error);
      setIntegrationError(error);
      setQueueError(error);
      setIntegrationLoaded(true);
      throw error;
    }
  }, [applyBootstrap]);

  const refreshIntegration = useCallback(async () => {
    try {
      const value = await fetchGitLabIntegrationStatus();
      setIntegration(value);
      setIntegrationError(null);
      setIntegrationExpired(value?.expiresAt != null && Number(value.expiresAt) < Date.now());
      setGitLabAuthRequired(false);
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
    fetchAppBootstrap()
      .then((value) => active && applyBootstrap(value))
      .catch((error) => {
        if (!active) return;
        setUserError(error);
        setIntegrationError(error);
        setQueueError(error);
        setIntegrationLoaded(true);
      });
    return () => { active = false; };
  }, [applyBootstrap]);

  useEffect(() => {
    const requireGitLabAuthorization = () => setGitLabAuthRequired(true);
    window.addEventListener(GITLAB_AUTH_REQUIRED_EVENT, requireGitLabAuthorization);
    return () => window.removeEventListener(GITLAB_AUTH_REQUIRED_EVENT, requireGitLabAuthorization);
  }, []);

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
  const navItems = navBase.map((item) => item.key === 'queue' && queue ? { ...item, badge: queue.items.length } : item);
  const gitLabConnected = Boolean(integration?.connected && !integrationExpired && !gitLabAuthRequired);
  const showGitLabGate = pathname !== '/gitlab/oauth/callback'
    && pathname !== '/admin'
    && (gitLabAuthRequired || integrationExpired || (integrationLoaded && !integrationError && !gitLabConnected));
  const integrationLabel = integrationError
    ? 'GitLab status unavailable'
    : integrationExpired
      ? 'Credential metadata expired'
      : integration?.connected
        ? 'GitLab configured'
        : 'GitLab setup required';

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
      queue,
      queueError,
      openMergeRequests,
      productivity,
      notifications,
      sync,
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
            gitLabConnected={gitLabConnected}
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
              className="relative z-10 shadow-[12px_0_36px_rgba(28,26,22,0.18)]"
              navItems={navItems}
              activeKey={activeKey}
              onNavigate={navigate}
              onRunAnalysis={() => {
                setMobileSidebarOpen(false);
                router.push('/analyze');
              }}
              user={{ name: displayName, initials: initialsFor(displayName), avatarUrl: user?.avatarUrl }}
              gitLabConnected={gitLabConnected}
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
            onNotificationsOpen={refreshNotifications}
            onNotificationClick={markNotification}
            onMarkAllNotificationsRead={markEveryNotificationRead}
            onToggleCollapse={toggleSidebar}
          />
          <main className="flex-1 overflow-y-auto px-4 py-6 sm:px-6 lg:px-10 lg:py-8">{children}</main>
          <StatusBar
            items={[{ status: integration?.connected && !integrationExpired ? 'success' : integrationError ? 'neutral' : 'warning', label: integrationLabel }]}
            version="Arnold · Lattice"
          />
        </div>
      </div>
      <GitLabConnectionGate open={showGitLabGate} reason={gitLabAuthRequired || integrationExpired ? 'expired' : 'required'} />
    </AppDataContext>
  );
}
