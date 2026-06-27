'use client';

import React, { useState, useEffect, useCallback, createContext, useContext } from 'react';
import { usePathname, useRouter } from 'next/navigation';
import { Sidebar, Home, ListChecks, FileCheck2, Users, BarChart3, ShieldCheck, History } from './Sidebar';
import { TopNav } from './TopNav';
import { StatusBar } from './StatusBar';
import { Splash } from './Splash';
import { fetchCurrentUser, fetchGitLabUser } from '@/lib/api';

const routeMap = {
  home: '/',
  queue: '/queue',
  analyze: '/analyze',
  review: '/review',
  personas: '/personas',
  stats: '/stats',
  policies: '/policies',
  history: '/history',
};

const pathToKey = Object.fromEntries(
  Object.entries(routeMap).map(([key, path]) => [path, key]),
);

// Sidebar nav model (Lattice labels; governance items grouped). Mirrors the
// reference navigation exactly.
const NAV_ITEMS = [
  { key: 'home', label: 'Home', icon: Home },
  { key: 'queue', label: 'Merge Queue', icon: ListChecks, badge: 7 },
  { key: 'review', label: 'MR Review', icon: FileCheck2 },
  { key: 'personas', label: 'Personas', icon: Users },
  { key: 'stats', label: 'Stats', icon: BarChart3 },
  { key: 'policies', label: 'Review Policies', icon: ShieldCheck, group: 'governance' },
  { key: 'history', label: 'Analysis History', icon: History, group: 'governance' },
];

const CRUMB_LABELS = {
  home: 'Home',
  queue: 'Merge Queue',
  analyze: 'New Analysis',
  review: 'MR Review · !482',
  personas: 'Personas',
  stats: 'Stats',
  policies: 'Review Policies',
  history: 'Analysis History',
};

const FALLBACK_TITLES = [
  'Adventurer', 'Alchemist', 'Architect', 'Artisan', 'Buccaneer',
  'Captain', 'Champion', 'Commander', 'Conjurer', 'Crusader',
  'Daredevil', 'Defector', 'Diplomat', 'Drifter', 'Enchanter',
  'Explorer', 'Firestarter', 'Freerunner', 'Gladiator', 'Guardian',
  'Harbinger', 'Heretic', 'Highflyer', 'Innovator', 'Insurgent',
  'Jedi', 'Juggernaut', 'Kingpin', 'Legend', 'Luminary',
  'Maverick', 'Mercenary', 'Mystic', 'Navigator', 'Nomad',
  'Oracle', 'Outlaw', 'Pathfinder', 'Phantom', 'Pioneer',
  'Renegade', 'Ronin', 'Sage', 'Sentinel', 'Sorcerer',
  'Trailblazer', 'Vanguard', 'Vigilante', 'Voyager', 'Warden',
];

function pickFallbackTitle() {
  return FALLBACK_TITLES[Math.floor(Math.random() * FALLBACK_TITLES.length)];
}

const UserContext = createContext(null);

export function useUser() {
  return useContext(UserContext);
}

export function AppLayout({ children }) {
  const pathname = usePathname();
  const router = useRouter();
  const [collapsed, setCollapsed] = useState(false);
  const [user, setUser] = useState(null);

  const activeKey = pathToKey[pathname] || 'home';
  const isReview = activeKey === 'review';

  useEffect(() => {
    let cancelled = false;

    async function loadUser() {
      try {
        const me = await fetchCurrentUser();
        console.log('[AppLayout] /api/me response:', me);
        if (cancelled) return;

        const raw = me.raw || {};
        const email = me.email || raw.email || raw.emailId || raw.userEmail || '';
        const username = me.username || email.split('@')[0] || raw.userId || '';
        const displayName = me.name || raw.name || raw.userName || raw.displayName || '';

        let gitlabUser = null;
        if (username) {
          try {
            const users = await fetchGitLabUser(username);
            if (!cancelled && Array.isArray(users) && users.length > 0) {
              gitlabUser = users[0];
            }
          } catch (err) {
            console.warn('Failed to fetch GitLab user:', err.message);
          }
        }

        if (!cancelled) {
          setUser({
            email,
            username,
            name: gitlabUser?.name || displayName || username || pickFallbackTitle(),
            avatarUrl: gitlabUser?.avatar_url || null,
            gitlabId: gitlabUser?.id || null,
          });
        }
      } catch (err) {
        console.warn('Failed to fetch user info:', err.message);
        if (!cancelled) {
          setUser({ email: '', username: '', name: pickFallbackTitle(), avatarUrl: null });
        }
      }
    }

    loadUser();
    return () => { cancelled = true; };
  }, []);

  const handleNavigate = useCallback((key) => {
    const path = routeMap[key];
    if (path) {
      router.push(path);
    }
  }, [router]);

  const initials = user?.name
    ? user.name.split(' ').map((w) => w[0]).join('').toUpperCase().slice(0, 2)
    : 'A';

  const sidebarUser = {
    name: user?.name || 'Signed in',
    subtitle: user?.username ? `@${user.username}` : 'Reviewer',
    initials,
  };

  return (
    <UserContext value={user}>
      <Splash />
      <div className="flex h-screen overflow-hidden">
        <Sidebar
          navItems={NAV_ITEMS}
          activeKey={activeKey}
          collapsed={collapsed}
          onToggleCollapse={() => setCollapsed((c) => !c)}
          onNavigate={handleNavigate}
          onRunAnalysis={() => router.push('/analyze')}
          user={sidebarUser}
        />
        <div className="flex flex-1 flex-col overflow-hidden">
          <TopNav
            breadcrumbs={[
              { label: 'Arnold' },
              { label: CRUMB_LABELS[activeKey] || 'Home' },
            ]}
            searchPlaceholder="Search MRs, findings, repos…"
            onToggleCollapse={() => setCollapsed((c) => !c)}
          />
          <main className={isReview ? 'flex-1 overflow-hidden' : 'flex-1 overflow-y-auto px-10 py-8'}>
            {children}
          </main>
          {!isReview && (
            <StatusBar
              items={[
                { status: 'success', label: 'Engine active' },
                { status: 'success', label: 'API latency · 14ms' },
              ]}
              version="Arnold · Lattice"
            />
          )}
        </div>
      </div>
    </UserContext>
  );
}
