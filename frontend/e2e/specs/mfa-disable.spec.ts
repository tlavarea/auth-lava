import { MFA_CODE } from '../support/fake-auth-backend';
import { expect, test } from '../support/fixtures';

test.describe('MFA disable', () => {
  test('disables MFA with the correct code', async ({ page, backend }) => {
    backend.withAuthenticatedUser({ mfaEnabled: true });
    await page.goto('/mfa/disable');

    await page.getByLabel('Verification code').pressSequentially(MFA_CODE);

    await expect(page).toHaveURL('/');
  });

  test('shows an error for an incorrect code', async ({ page, backend }) => {
    backend.withAuthenticatedUser({ mfaEnabled: true });
    await page.goto('/mfa/disable');

    await page.getByLabel('Verification code').pressSequentially('000000');

    await expect(page.getByText('Invalid verification code')).toBeVisible();
    await expect(page).toHaveURL('/mfa/disable');
  });
});
