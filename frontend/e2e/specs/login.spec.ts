import { TEST_PASSWORD } from '../support/fake-auth-backend';
import { expect, test } from '../support/fixtures';

test.describe('login', () => {
  test('signs in and redirects to the dashboard', async ({ page }) => {
    await page.goto('/login');

    await page.getByLabel('Email').fill('jane@example.com');
    await page.getByLabel('Password').fill(TEST_PASSWORD);
    await page.getByRole('button', { name: 'Sign in' }).click();

    await expect(page).toHaveURL('/');
    await expect(page.getByText('Welcome back, jane@example.com.')).toBeVisible();
  });

  test('shows an error for invalid credentials', async ({ page }) => {
    await page.goto('/login');

    await page.getByLabel('Email').fill('jane@example.com');
    await page.getByLabel('Password').fill('wrong-password');
    await page.getByRole('button', { name: 'Sign in' }).click();

    await expect(page.getByText('Invalid email or password')).toBeVisible();
    await expect(page).toHaveURL('/login');
  });

  test('redirects an MFA-enabled user to the verification step', async ({ page, backend }) => {
    backend.withRegisteredUser({ mfaEnabled: true });
    await page.goto('/login');

    await page.getByLabel('Email').fill('jane@example.com');
    await page.getByLabel('Password').fill(TEST_PASSWORD);
    await page.getByRole('button', { name: 'Sign in' }).click();

    await expect(page).toHaveURL('/mfa/verify');
  });
});
