import { act, renderHook } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { THEME_STORAGE_KEY, useTheme } from './theme';

describe('theme', () => {
  it('cycles system -> light -> dark -> system and stores only the theme key', () => {
    const { result } = renderHook(() => useTheme());
    expect(result.current.preference).toBe('system');
    act(() => result.current.cycle());
    expect(result.current.preference).toBe('light');
    expect(document.documentElement.classList.contains('dark')).toBe(false);
    act(() => result.current.cycle());
    expect(result.current.preference).toBe('dark');
    expect(document.documentElement.classList.contains('dark')).toBe(true);
    expect(Object.keys({ ...localStorage })).toEqual([THEME_STORAGE_KEY]);
    act(() => result.current.cycle());
    expect(localStorage.length).toBe(0);
  });
});
