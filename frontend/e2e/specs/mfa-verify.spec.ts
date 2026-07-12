import { MFA_CODE } from '../support/fake-auth-backend';
import { expect, test } from '../support/fixtures';

test.describe('MFA verification', () => {
  test('accepts the correct code and lands on the dashboard', async ({ page, backend }) => {
    backend.withMfaPendingUser();
    await page.goto('/mfa/verify');

    await page.getByLabel('Verification code').pressSequentially(MFA_CODE);

    await expect(page).toHaveURL('/');
  });

  test('shows an error for an incorrect code and stays on the page', async ({ page, backend }) => {
    backend.withMfaPendingUser();
    await page.goto('/mfa/verify');

    await page.getByLabel('Verification code').pressSequentially('000000');

    await expect(page.getByText('Invalid verification code')).toBeVisible();
    await expect(page).toHaveURL('/mfa/verify');
  });
});
