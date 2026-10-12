import { useSyncExternalStore } from 'react';

/*
 * The ONLY localStorage user in the app (ESLint forbids it elsewhere): a non-sensitive
 * light/dark preference under one key. public/theme-init.js applies it before first paint.
 * One module-level store, so every toggle on screen shows the same value and only one
 * prefers-color-scheme listener exists.
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

function systemQuery(): MediaQueryList | null {
  return typeof window.matchMedia === 'function'
    ? window.matchMedia('(prefers-color-scheme: dark)')
    : null;
}

export function applyTheme(p: ThemePreference) {
  const dark = p === 'dark' || (p === 'system' && (systemQuery()?.matches ?? false));
  document.documentElement.classList.toggle('dark', dark);
}

let preference: ThemePreference | null = null;
const listeners = new Set<() => void>();
const onSystemChange = () => {
  if (preference === 'system') applyTheme('system');
};

function current(): ThemePreference {
  if (preference === null) preference = readThemePreference();
  return preference;
}

export const themeStore = {
  get: current,
  subscribe(l: () => void) {
    if (listeners.size === 0) systemQuery()?.addEventListener('change', onSystemChange);
    listeners.add(l);
    return () => {
      listeners.delete(l);
      if (listeners.size === 0) systemQuery()?.removeEventListener('change', onSystemChange);
    };
  },
  set(p: ThemePreference) {
    preference = p;
    writeThemePreference(p);
    applyTheme(p);
    listeners.forEach((l) => l());
  },
  /** Test helper: forget the cached value (storage is cleared separately). */
  reset() {
    preference = null;
  },
};

const NEXT: Record<ThemePreference, ThemePreference> = {
  system: 'light',
  light: 'dark',
  dark: 'system',
};

/** Cycles system -> light -> dark; follows OS changes while on "system". */
export function useTheme() {
  const pref = useSyncExternalStore(themeStore.subscribe, themeStore.get, themeStore.get);
  return { preference: pref, cycle: () => themeStore.set(NEXT[themeStore.get()]) };
}
