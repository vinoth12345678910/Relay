import { useCallback, useEffect, useState } from 'react';

/*
 * The ONLY localStorage user in the app (ESLint forbids it elsewhere): a non-sensitive
 * light/dark preference under one key. public/theme-init.js applies it before first paint.
 */
export type ThemePreference = 'light' | 'dark' | 'system';
export const THEME_STORAGE_KEY = 'relay-ui.theme';

export function readThemePreference(): ThemePreference {
  try {
    const v = localStorage.getItem(THEME_STORAGE_KEY);
    return v === 'light' || v === 'dark' ? v : 'system';
  } catch {
    return 'system';
  }
}

function writeThemePreference(p: ThemePreference) {
  try {
    if (p === 'system') localStorage.removeItem(THEME_STORAGE_KEY);
    else localStorage.setItem(THEME_STORAGE_KEY, p);
  } catch {
    // storage blocked: the choice lasts for this page only
  }
}

function systemPrefersDark(): boolean {
  return (
    typeof window.matchMedia === 'function' &&
    window.matchMedia('(prefers-color-scheme: dark)').matches
  );
}

export function applyTheme(p: ThemePreference) {
  const dark = p === 'dark' || (p === 'system' && systemPrefersDark());
  document.documentElement.classList.toggle('dark', dark);
}

/** Cycles system -> light -> dark, follows OS changes while on "system". */
export function useTheme() {
  const [pref, setPref] = useState<ThemePreference>(readThemePreference);

  useEffect(() => {
    applyTheme(pref);
    if (pref !== 'system' || typeof window.matchMedia !== 'function') return;
    const mq = window.matchMedia('(prefers-color-scheme: dark)');
    const onChange = () => applyTheme('system');
    mq.addEventListener('change', onChange);
    return () => mq.removeEventListener('change', onChange);
  }, [pref]);

  const cycle = useCallback(() => {
    setPref((p) => {
      const next: ThemePreference = p === 'system' ? 'light' : p === 'light' ? 'dark' : 'system';
      writeThemePreference(next);
      return next;
    });
  }, []);

  return { preference: pref, cycle };
}
