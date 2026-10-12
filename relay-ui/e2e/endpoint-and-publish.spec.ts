import { connect, expect, test } from './fixtures';

test('create an endpoint, see its secret once, publish with and without an Idempotency-Key', async ({
  page,
}) => {
  await connect(page);
  await page.getByRole('link', { name: 'Endpoints' }).click();
  await page.getByRole('button', { name: 'Add endpoint' }).first().click();
  const form = page.getByRole('dialog', { name: 'Add endpoint' });
  await form.getByLabel('URL').fill('https://e2e.example.com/hook');
  await form.getByLabel(/Event type filter/).fill('order.*');
  await form.getByRole('button', { name: 'Add endpoint' }).click();

  const secretDialog = page.getByRole('dialog', { name: /copy the signing secret/ });
  const secret = (await secretDialog.getByTestId('one-time-secret').textContent()) ?? '';
  expect(secret).toMatch(/^whsec_/);
  await secretDialog.getByRole('button', { name: 'I have stored the secret' }).click();
  await expect(page.getByText(secret)).toHaveCount(0);
  await expect(page.getByText('https://e2e.example.com/hook')).toBeVisible();
  expect(
    await page.evaluate(
      (s) => JSON.stringify({ ...localStorage, ...sessionStorage }).includes(s),
      secret,
    ),
  ).toBe(false);

  await page.getByRole('link', { name: 'Publish' }).click();
  await page.getByRole('button', { name: /Publish event/ }).click();
  const plain = page.getByRole('region', { name: 'Accepted · 202' });
  await expect(plain.getByTestId('delivery-count')).toHaveText('3');

  await page.getByRole('button', { name: 'Generate idempotency key' }).click();
  await expect(page.getByLabel(/Idempotency-Key/)).toHaveValue(/^[0-9a-f-]{36}$/);
  await page.getByRole('button', { name: /Publish event/ }).click();
  await expect(page.getByRole('region', { name: 'Accepted · 202' })).toBeVisible();
  await page.getByRole('button', { name: /Publish event/ }).click();
  const replay = page.getByRole('region', { name: /Accepted \(replayed\)/ });
  await expect(replay.getByText(/Idempotent replay/)).toBeVisible();

  await replay.getByRole('link', { name: /View event/ }).click();
  await expect(page.getByRole('heading', { name: 'order.paid' })).toBeVisible();
  await expect(
    page
      .getByRole('table', { name: 'Deliveries of this event' })
      .getByText('https://e2e.example.com/hook'),
  ).toBeVisible();
});
