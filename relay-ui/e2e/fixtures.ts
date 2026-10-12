import { test as base, expect, type Page } from '@playwright/test';

export const E2E_KEY = 'rl_live_e2e_demo_key_0001';

/** Connects through the real Connect screen against the in-browser mock backend. */
export async function connect(page: Page, opts: { remember?: boolean } = {}) {
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Connect to Relay' })).toBeVisible();
  await page.getByLabel('API key').fill(E2E_KEY);
  if (opts.remember) await page.getByRole('checkbox', { name: /Remember on this device/ }).check();
  await page.getByRole('button', { name: 'Connect' }).click();
}

/** Fails the test if the API key ever shows up in a request URL or the console. */
// eslint-disable-next-line @typescript-eslint/no-invalid-void-type -- Playwright's idiom for a value-less auto fixture
export const test = base.extend<{ leakGuard: void }>({
  leakGuard: [
    async ({ page }, use) => {
      const leaks: string[] = [];
      page.on('request', (r) => {
        if (r.url().includes(E2E_KEY)) leaks.push(`url: ${r.url()}`);
      });
      page.on('console', (m) => {
        if (m.text().includes(E2E_KEY)) leaks.push(`console: ${m.text()}`);
        if (/Content Security Policy|Refused to/i.test(m.text())) leaks.push(`CSP: ${m.text()}`);
      });
      await use();
      const local = await page
        .evaluate(() => JSON.stringify({ ...window.localStorage }))
        .catch(() => '{}');
      if (local.includes(E2E_KEY)) leaks.push('localStorage');
      expect(leaks, 'API key leak or CSP violation').toEqual([]);
    },
    { auto: true },
  ],
});
export { expect };
