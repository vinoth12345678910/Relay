import { connect, expect, test } from './fixtures';

test('filter deliveries, open the attempt timeline, replay a DEAD delivery', async ({ page }) => {
  await connect(page);
  await page.getByRole('link', { name: 'Deliveries' }).click();
  const table = page.getByRole('table', { name: /Deliveries/ });
  await expect(table.getByRole('row')).toHaveCount(7);

  await page.getByLabel('Status').selectOption('DEAD');
  await expect(page).toHaveURL(/\/deliveries\?status=DEAD$/);
  await expect(table.getByRole('row')).toHaveCount(3);

  await table
    .getByRole('button', { name: /Open details/ })
    .first()
    .click();
  await expect(page).toHaveURL(/delivery=/);
  const drawer = page.getByRole('dialog', { name: /Delivery/ });
  await expect(drawer.getByText('Attempt 1', { exact: true })).toBeVisible();
  // The receiver's HTML is shown as text, not rendered.
  await expect(drawer.getByRole('region', { name: 'Response snippet of attempt 1' })).toContainText(
    '<script>alert(1)</script>',
  );

  await drawer.getByRole('button', { name: 'Replay' }).click();
  await page
    .getByRole('dialog', { name: 'Replay this delivery?' })
    .getByRole('button', { name: 'Replay' })
    .click();
  await expect(drawer.getByRole('status').filter({ hasText: /Replay queued/ })).toBeVisible();
  await expect(drawer.getByText('Pending', { exact: true })).toBeVisible();
  await expect(drawer.getByRole('button', { name: 'Replay' })).toBeDisabled();

  await page.keyboard.press('Escape');
  await expect(page).toHaveURL(/\/deliveries\?status=DEAD$/);
});
