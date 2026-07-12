import { REGISTRATION_CODE } from '../support/fake-auth-backend';
import { expect, test } from '../support/fixtures';

test.describe('registration', () => {
  test('completes the full register flow and lands on login', async ({ page }) => {
    await page.goto('/register');

    await page.getByLabel('Email').fill('new-user@example.com');
    await page.getByRole('button', { name: 'Continue' }).click();

    await expect(page.getByLabel('Verification code')).toBeVisible();
    await page.getByLabel('Verification code').pressSequentially(REGISTRATION_CODE);

    await expect(page.getByLabel('Password', { exact: true })).toBeVisible();
    await page.getByLabel('Password', { exact: true }).fill('SuperSecret1!');
    await page.getByLabel('Confirm password').fill('SuperSecret1!');
    await page.getByRole('button', { name: 'Create account' }).click();

    await expect(page).toHaveURL('/login?registered=1');
    await expect(page.getByText('Account created. Sign in to continue.')).toBeVisible();
  });

  test('shows an error for an invalid verification code', async ({ page }) => {
    await page.goto('/register');

    await page.getByLabel('Email').fill('new-user@example.com');
    await page.getByRole('button', { name: 'Continue' }).click();
    await page.getByLabel('Verification code').pressSequentially('000000');

    await expect(page.getByText('Invalid or expired verification code')).toBeVisible();
  });
});
