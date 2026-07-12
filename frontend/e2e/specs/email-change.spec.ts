import { EMAIL_CHANGE_CODE } from '../support/fake-auth-backend';
import { expect, test } from '../support/fixtures';

test.describe('email change', () => {
  test('changes email after verifying the code', async ({ page, backend }) => {
    backend.withAuthenticatedUser();
    await page.goto('/profile');

    await page.getByLabel('New email').fill('new-address@example.com');
    await page.getByRole('button', { name: 'Send verification code' }).click();

    await expect(page.getByText('Enter the code we sent to new-address@example.com.')).toBeVisible();
    await page.getByLabel('Verification code').pressSequentially(EMAIL_CHANGE_CODE);

    await expect(page.getByText('Email changed successfully.')).toBeVisible();
  });

  test('shows an error for an invalid verification code', async ({ page, backend }) => {
    backend.withAuthenticatedUser();
    await page.goto('/profile');

    await page.getByLabel('New email').fill('new-address@example.com');
    await page.getByRole('button', { name: 'Send verification code' }).click();
    await page.getByLabel('Verification code').pressSequentially('000000');

    await expect(page.getByText('Invalid or expired verification code')).toBeVisible();
  });
});
