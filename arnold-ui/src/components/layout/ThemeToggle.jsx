'use client';

import React, { useSyncExternalStore } from 'react';
import { Sun, Moon } from 'lucide-react';

// Subscribe to <html data-theme> mutations so the icon mirrors the live theme.
function subscribe(callback) {
  if (typeof document === 'undefined') return () => {};
  const observer = new MutationObserver(callback);
  observer.observe(document.documentElement, {
    attributes: true,
    attributeFilter: ['data-theme'],
  });
  return () => observer.disconnect();
}

function getSnapshot() {
  return document.documentElement.getAttribute('data-theme') || 'light';
}

function getServerSnapshot() {
  return 'light';
}

/**
 * ThemeToggle — flips the Lattice theme (light ⇄ dark) on <html data-theme>,
 * persisting the choice to localStorage. The initial theme is applied before
 * paint by the bootstrap script in the root layout, so this only mirrors and
 * mutates that state.
 */
export function ThemeToggle({ className = '', ...props }) {
  const theme = useSyncExternalStore(subscribe, getSnapshot, getServerSnapshot);

  const toggle = () => {
    const next = (document.documentElement.getAttribute('data-theme') || 'light') === 'dark' ? 'light' : 'dark';
    document.documentElement.setAttribute('data-theme', next);
    try {
      localStorage.setItem('arnold.theme', next);
    } catch (e) {
      /* ignore */
    }
    // useSyncExternalStore picks up the data-theme mutation via the observer.
  };

  return (
    <button
      onClick={toggle}
      title={`Switch to ${theme === 'dark' ? 'light' : 'dark'} theme`}
      aria-label={`Switch to ${theme === 'dark' ? 'light' : 'dark'} theme`}
      className={`grid h-[34px] w-[34px] place-items-center rounded-none border border-transparent text-muted transition-colors hover:bg-[var(--ink-wash)] hover:text-fg ${className}`}
      {...props}
    >
      {theme === 'dark' ? (
        <Sun className="h-[18px] w-[18px]" strokeWidth={1.8} />
      ) : (
        <Moon className="h-[18px] w-[18px]" strokeWidth={1.8} />
      )}
    </button>
  );
}
