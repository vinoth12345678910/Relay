import { connect, E2E_KEY, expect, test } from './fixtures';

test('connect with an API key, see endpoints, disconnect', async ({ page }) => {
  await page.goto('/endpoints');
  await connect(page);
  await expect(page.getByRole('heading', { name: 'Endpoints' })).toBeVisible();
  const table = page.getByRole('table', { name: 'Endpoints' });
  await expect(table.getByText('https://hooks.example.com/orders')).toBeVisible();
  await expect(table.getByText('Paused')).toBeVisible();

  expect(await page.evaluate(() => window.sessionStorage.length)).toBe(0);
  await page.getByRole('button', { name: 'Disconnect' }).click();
  await expect(page.getByRole('heading', { name: 'Connect to Relay' })).toBeVisible();
});

test('remember-on-this-device survives a reload via sessionStorage only', async ({ page }) => {
  await connect(page, { remember: true });
  await expect(page.getByRole('heading', { name: /Endpoints|Overview/ })).toBeVisible();
  expect(await page.evaluate(() => window.sessionStorage.getItem('relay-ui.session'))).toContain(
    E2E_KEY,
  );
  await page.reload();
  await expect(page.getByRole('heading', { name: /Endpoints|Overview/ })).toBeVisible();
  await page.getByRole('button', { name: 'Disconnect' }).click();
  expect(await page.evaluate(() => window.sessionStorage.length)).toBe(0);
});
