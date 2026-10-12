import { connect, E2E_KEY, expect, test } from './fixtures';

test('connect with an API key, see endpoints, disconnect', async ({ page }) => {
  await connect(page);
  await expect(page.getByRole('heading', { name: 'Overview' })).toBeVisible();
  await page.getByRole('link', { name: 'Endpoints' }).click();
  await expect(page.getByRole('heading', { name: 'Endpoints' })).toBeVisible();
  const table = page.getByRole('table', { name: 'Endpoints' });
  await expect(table.getByText('https://hooks.example.com/orders')).toBeVisible();
  await expect(table.getByText('Paused')).toBeVisible();

  expect(await page.evaluate(() => window.sessionStorage.length)).toBe(0);
  await page.getByRole('button', { name: 'Disconnect' }).click();
  await expect(page.getByRole('heading', { name: 'Connect to Relay' })).toBeVisible();
});

test('a deep link survives the Connect detour', async ({ page }) => {
  await page.goto('/deliveries?status=DEAD');
  await expect(page.getByRole('heading', { name: 'Connect to Relay' })).toBeVisible();
  await page.getByLabel('API key').fill(E2E_KEY);
  await page.getByRole('button', { name: 'Connect' }).click();
  await expect(page).toHaveURL(/\/deliveries\?status=DEAD$/);
  await expect(page.getByLabel('Status')).toHaveValue('DEAD');
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
