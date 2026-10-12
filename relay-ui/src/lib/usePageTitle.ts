import { useEffect } from 'react';

/** Sets document.title so screen-reader users and tab lists know which page this is (WCAG 2.4.2). */
export function usePageTitle(title: string) {
  useEffect(() => {
    document.title = `${title} · Relay Console`;
  }, [title]);
}
