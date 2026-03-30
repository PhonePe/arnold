'use client';

import React, { useState, useEffect, useCallback, createContext, useContext } from 'react';
import { usePathname, useRouter } from 'next/navigation';
import { Sidebar } from './Sidebar';
import { TopNav } from './TopNav';
import { StatusBar } from './StatusBar';
import { fetchCurrentUser, fetchGitLabUser } from '@/lib/api';

const routeMap = {
  dashboard: '/',
  projects: '/projects',
  stats: '/stats',
  knowledge: '/knowledge',
  history: '/history',
};

const pathToKey = Object.fromEntries(
  Object.entries(routeMap).map(([key, path]) => [path, key]),
);

const UserContext = createContext(null);

export function useUser() {
  return useContext(UserContext);
}

export function AppLayout({ children }) {
  const pathname = usePathname();
  const router = useRouter();
  const [collapsed, setCollapsed] = useState(false);
  const [user, setUser] = useState(null);

  const activeKey = pathToKey[pathname] || 'dashboard';

  useEffect(() => {
    let cancelled = false;

    async function loadUser() {
      try {
        const me = await fetchCurrentUser();
        console.log('[AppLayout] /api/me response:', me);
        if (cancelled) return;

        // Extract username from various possible fields in the Olympus response
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
            name: gitlabUser?.name || displayName || username || 'Architect',
            avatarUrl: gitlabUser?.avatar_url || null,
            gitlabId: gitlabUser?.id || null,
          });
        }
      } catch (err) {
        console.warn('Failed to fetch user info:', err.message);
        if (!cancelled) {
          setUser({ email: '', username: '', name: 'Architect', avatarUrl: null });
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
    : 'U';

  return (
    <UserContext value={user}>
      <div className="flex h-screen overflow-hidden bg-surface">
        <Sidebar
          activeKey={activeKey}
          collapsed={collapsed}
          onToggleCollapse={() => setCollapsed((c) => !c)}
          onNavigate={handleNavigate}
        />
        <div className="flex flex-col flex-1 overflow-hidden">
          <TopNav
            breadcrumbs={[
              { label: 'ARNOLD' },
              { label: 'INTELLIGENCE-CORE' },
            ]}
            tabs={[
              { key: 'projects', label: 'Projects' },
              { key: 'activity', label: 'Activity' },
              { key: 'issues', label: 'Issues' },
            ]}
            activeTab="projects"
            searchPlaceholder="Search resources..."
            userInitials={initials}
            userAvatar={user?.avatarUrl}
          />
          <main className="flex-1 overflow-y-auto p-6">
            {children}
          </main>
          <StatusBar
            items={[
              { status: 'success', label: 'NEURAL ENGINE ACTIVE' },
              { status: 'success', label: 'API LATENCY: 14MS' },
            ]}
            version="ARNOLD V2.4"
          />
        </div>
      </div>
    </UserContext>
  );
}
