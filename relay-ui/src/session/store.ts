import { useSyncExternalStore } from 'react';

/**
 * The one place credentials live. The API key is held in memory; only if the user ticks
 * "remember on this device" is it mirrored to sessionStorage (cleared when the tab closes).
 * It is never written to localStorage, URLs, logs or query keys.
 */
export type Session = {
  apiKey: string;
  /** '' means same-origin (the dev proxy / nginx forwards /api). */
  baseUrl: string;
  remember: boolean;
};

const STORAGE_KEY = 'relay-ui.session';

let current: Session | null = null;
let notice: string | null = null;
const listeners = new Set<() => void>();

function emit() {
  listeners.forEach((l) => l());
}

function readPersisted(): Session | null {
  try {
    const raw = window.sessionStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const parsed: unknown = JSON.parse(raw);
    if (
      typeof parsed === 'object' &&
      parsed !== null &&
      'apiKey' in parsed &&
      typeof parsed.apiKey === 'string' &&
      'baseUrl' in parsed &&
      typeof parsed.baseUrl === 'string'
    ) {
      return { apiKey: parsed.apiKey, baseUrl: parsed.baseUrl, remember: true };
    }
  } catch {
    // corrupted or blocked storage: start disconnected
  }
  return null;
}

function clearPersisted() {
  try {
    window.sessionStorage.removeItem(STORAGE_KEY);
  } catch {
    // storage blocked: nothing persisted anyway
  }
}

export const session = {
  /** Loads a remembered session from sessionStorage (call once at startup). */
  hydrate() {
    current = readPersisted();
    emit();
  },
  get(): Session | null {
    return current;
  },
  connect(next: Session) {
    current = next;
    notice = null;
    if (next.remember) {
      try {
        window.sessionStorage.setItem(
          STORAGE_KEY,
          JSON.stringify({ apiKey: next.apiKey, baseUrl: next.baseUrl }),
        );
      } catch {
        // storage blocked: keep the in-memory session only
      }
    } else {
      clearPersisted();
    }
    emit();
  },
  /** User-initiated: wipe everything. */
  disconnect() {
    current = null;
    clearPersisted();
    emit();
  },
  /** Server rejected the key (401): wipe it and leave a message for the Connect screen. */
  expire(message: string) {
    if (current === null) return;
    current = null;
    notice = message;
    clearPersisted();
    emit();
  },
  /** Returns and clears the message left by expire(). */
  takeNotice(): string | null {
    const n = notice;
    notice = null;
    return n;
  },
  peekNotice(): string | null {
    return notice;
  },
  subscribe(listener: () => void) {
    listeners.add(listener);
    return () => {
      listeners.delete(listener);
    };
  },
  /** Test helper: forget everything without emitting stale state. */
  reset() {
    current = null;
    notice = null;
    clearPersisted();
    emit();
  },
};

export function useSession(): Session | null {
  return useSyncExternalStore(session.subscribe, session.get, session.get);
}

/** Short, non-secret label for the connected key, like the backend's 12-char apiKeyPrefix. */
export function maskKey(apiKey: string): string {
  return `${apiKey.slice(0, 12)}…`;
}
