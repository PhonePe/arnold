'use client';

import React, { createContext, useCallback, useContext, useEffect, useState } from 'react';
import { usePathname, useRouter } from 'next/navigation';
import { Sidebar, Home, ListChecks, FileCheck2, Users, BarChart3, ShieldCheck, History } from './Sidebar';
import { TopNav } from './TopNav';
import { StatusBar } from './StatusBar';
import { GitLabConnectionGate } from './GitLabConnectionGate';
import {
  fetchCurrentUser,
  fetchGitLabIntegrationStatus,
  fetchMergeQueue,
  GITLAB_AUTH_REQUIRED_EVENT,
} from '@/lib/api';

const routeMap = { home: '/', queue: '/queue', analyze: '/analyze', review: '/review', personas: '/personas', stats: '/stats', policies: '/policies', history: '/history', settings: '/settings' };
const pathToKey = Object.fromEntries(Object.entries(routeMap).map(([key, path]) => [path, key]));
const crumbLabels = { home: 'Home', queue: 'Merge Queue', analyze: 'New Analysis', review: 'MR Review', personas: 'Personas', stats: 'Stats', policies: 'Review Policies', history: 'Analysis History', settings: 'Settings' };
const navBase = [
  { key: 'home', label: 'Home', icon: Home },
  { key: 'queue', label: 'Merge Queue', icon: ListChecks },
  { key: 'review', label: 'MR Review', icon: FileCheck2 },
  { key: 'personas', label: 'Personas', icon: Users },
  { key: 'stats', label: 'Stats', icon: BarChart3 },
  { key: 'policies', label: 'Review Policies', icon: ShieldCheck, group: 'governance' },
  { key: 'history', label: 'Analysis History', icon: History, group: 'governance' },
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
  const [user, setUser] = useState(null);
  const [userError, setUserError] = useState(null);
  const [integration, setIntegration] = useState(null);
  const [integrationError, setIntegrationError] = useState(null);
  const [integrationExpired, setIntegrationExpired] = useState(false);
  const [integrationLoaded, setIntegrationLoaded] = useState(false);
  const [gitLabAuthRequired, setGitLabAuthRequired] = useState(false);
  const [queue, setQueue] = useState(null);
  const [queueError, setQueueError] = useState(null);

  const refreshQueue = useCallback(async () => {
    try {
      const nextQueue = await fetchMergeQueue();
      setQueue(nextQueue);
      setQueueError(null);
      return nextQueue;
    } catch (error) {
      setQueueError(error);
      throw error;
    }
  }, []);

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

  useEffect(() => {
    let active = true;
    const userRequest = fetchCurrentUser().then((value) => active && setUser(value)).catch((error) => active && setUserError(error));
    const integrationRequest = refreshIntegration().catch(() => null);
    const queueRequest = fetchMergeQueue().then((value) => active && setQueue(value)).catch((error) => active && setQueueError(error));
    Promise.allSettled([userRequest, integrationRequest, queueRequest]);
    return () => { active = false; };
  }, [refreshIntegration]);

  useEffect(() => {
    const requireGitLabAuthorization = () => setGitLabAuthRequired(true);
    window.addEventListener(GITLAB_AUTH_REQUIRED_EVENT, requireGitLabAuthorization);
    return () => window.removeEventListener(GITLAB_AUTH_REQUIRED_EVENT, requireGitLabAuthorization);
  }, []);

  const activeKey = pathToKey[pathname] || 'home';
  const displayName = user ? user.displayName || user.username || user.email : 'User details unavailable';
  const navItems = navBase.map((item) => item.key === 'queue' && queue ? { ...item, badge: queue.items.length } : item);
  const gitLabConnected = Boolean(integration?.connected && !integrationExpired && !gitLabAuthRequired);
  const showGitLabGate = pathname !== '/gitlab/oauth/callback'
    && (gitLabAuthRequired || integrationExpired || (integrationLoaded && !integrationError && !gitLabConnected));
  const integrationLabel = integrationError
    ? 'GitLab status unavailable'
    : integrationExpired
      ? 'Credential metadata expired'
      : integration?.connected
        ? 'GitLab configured'
        : 'GitLab setup required';

  return (
    <AppDataContext value={{ user, userError, integration, integrationError, integrationLoaded, queue, queueError, refreshQueue, refreshIntegration, setQueue }}>
      <div className="flex h-screen overflow-hidden">
        <Sidebar
          navItems={navItems}
          activeKey={activeKey}
          collapsed={collapsed}
          onToggleCollapse={() => setCollapsed((value) => !value)}
          onNavigate={(key) => routeMap[key] && router.push(routeMap[key])}
          onRunAnalysis={() => router.push('/analyze')}
          user={{ name: displayName, initials: initialsFor(displayName), avatarUrl: user?.avatarUrl }}
          gitLabConnected={gitLabConnected}
          onOpenSettings={() => router.push('/settings')}
          onLogout={() => window.location.assign('/auth/logout')}
        />
        <div className="flex min-w-0 flex-1 flex-col overflow-hidden">
          <TopNav breadcrumbs={[{ label: 'Arnold' }, { label: crumbLabels[activeKey] || 'Home' }]} onToggleCollapse={() => setCollapsed((value) => !value)} />
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
