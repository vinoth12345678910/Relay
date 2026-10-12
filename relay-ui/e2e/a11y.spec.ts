import AxeBuilder from '@axe-core/playwright';
import type { Page } from '@playwright/test';
import { connect, expect, test } from './fixtures';

const PAGES: Array<{ name: string; path: string; ready: (p: Page) => Promise<void> }> = [
  { name: 'overview', path: '/', ready: (p) => expect(p.getByTestId('count-DEAD')).toBeVisible() },
  {
    name: 'endpoints',
    path: '/endpoints',
    ready: (p) => expect(p.getByRole('table', { name: 'Endpoints' })).toBeVisible(),
  },
  {
    name: 'publish',
    path: '/events/new',
    ready: (p) => expect(p.getByRole('heading', { name: 'Publish an event' })).toBeVisible(),
  },
  {
    name: 'events',
    path: '/events',
    ready: (p) => expect(p.getByRole('heading', { name: 'Events' })).toBeVisible(),
  },
  {
    name: 'deliveries',
    path: '/deliveries',
    ready: (p) => expect(p.getByRole('table', { name: /Deliveries/ })).toBeVisible(),
  },
];

async function seriousViolations(page: Page) {
  const results = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
    .analyze();
  return results.violations
    .filter((v) => v.impact === 'serious' || v.impact === 'critical')
    .map(
      (v) =>
        `${v.id}: ${v.nodes
          .map((n) => n.target.join(' '))
          .slice(0, 3)
          .join(' | ')}`,
    );
}

for (const theme of ['light', 'dark'] as const) {
  test.describe(`axe (${theme})`, () => {
    test.use({ colorScheme: theme });

    test('connect page', async ({ page }) => {
      await page.goto('/connect');
      await expect(page.getByRole('heading', { name: 'Connect to Relay' })).toBeVisible();
      expect(await seriousViolations(page)).toEqual([]);
    });

    test('app pages, drawer and dialogs', async ({ page }) => {
      await connect(page, { remember: true });
      for (const p of PAGES) {
        await page.goto(p.path);
        await p.ready(page);
        expect(await seriousViolations(page), p.name).toEqual([]);
      }
      await page.goto('/deliveries?status=DEAD');
      await page
        .getByRole('button', { name: /Open details/ })
        .first()
        .click();
      await expect(
        page.getByRole('dialog', { name: /Delivery/ }).getByText('Attempt 1', { exact: true }),
      ).toBeVisible();
      expect(await seriousViolations(page), 'delivery drawer').toEqual([]);
      await page.keyboard.press('Escape');
      await page.goto('/endpoints');
      await page.getByRole('button', { name: 'Add endpoint' }).first().click();
      await expect(page.getByRole('dialog', { name: 'Add endpoint' })).toBeVisible();
      expect(await seriousViolations(page), 'endpoint dialog').toEqual([]);
    });
  });
}

test('works at 375px without horizontal page scroll', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 800 });
  await connect(page, { remember: true });
  for (const p of PAGES) {
    await page.goto(p.path);
    await p.ready(page);
    const overflow = await page.evaluate(
      () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
    );
    expect(overflow, `${p.name} overflows horizontally`).toBeLessThanOrEqual(0);
  }
});
