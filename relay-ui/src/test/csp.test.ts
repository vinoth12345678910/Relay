import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';
import { CSP } from '../../csp';

describe('Content-Security-Policy', () => {
  it('e2e (vite preview) and nginx serve the identical policy', () => {
    const inc = readFileSync(resolve(__dirname, '../../security-headers.inc'), 'utf8');
    const nginxCsp = /Content-Security-Policy "([^"]+)"/.exec(inc)?.[1];
    expect(nginxCsp).toBe(CSP);
    expect(CSP).toContain("script-src 'self';");
    expect(CSP).not.toMatch(/script-src[^;]*unsafe/);
  });
});
