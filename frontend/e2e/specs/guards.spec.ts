import { expect, test } from '../support/fixtures';

test.describe('route guards', () => {
  test('anonymous users hitting a protected route are redirected to /login', async ({ page }) => {
    await page.goto('/');
    await expect(page).toHaveURL('/login');
  });

  test('anonymous users hitting /mfa/verify are redirected to /login', async ({ page }) => {
    await page.goto('/mfa/verify');
    await expect(page).toHaveURL('/login');
  });

  test('authenticated users hitting /login are redirected to /', async ({ page, backend }) => {
    backend.withAuthenticatedUser();
    await page.goto('/login');
    await expect(page).toHaveURL('/');
  });

  test('authenticated users hitting /mfa/verify are redirected to /', async ({ page, backend }) => {
    backend.withAuthenticatedUser();
    await page.goto('/mfa/verify');
    await expect(page).toHaveURL('/');
  });

  test('mfa-pending users hitting / are redirected to /mfa/verify', async ({ page, backend }) => {
    backend.withMfaPendingUser();
    await page.goto('/');
    await expect(page).toHaveURL('/mfa/verify');
  });

  test('mfa-pending users hitting /login are redirected to /mfa/verify', async ({ page, backend }) => {
    backend.withMfaPendingUser();
    await page.goto('/login');
    await expect(page).toHaveURL('/mfa/verify');
  });
});
