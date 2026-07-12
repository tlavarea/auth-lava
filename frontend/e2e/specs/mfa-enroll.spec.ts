import { MFA_CODE } from '../support/fake-auth-backend';
import { expect, test } from '../support/fixtures';

test.describe('MFA enrollment', () => {
  test('enrolls, verifies the code, and confirms backup codes', async ({ page, backend, context }) => {
    backend.withAuthenticatedUser();
    await context.grantPermissions(['clipboard-write'], { origin: 'http://localhost:4200' });
    await page.goto('/mfa/enroll');

    await expect(page.getByAltText('QR code for authenticator app enrollment')).toBeVisible();
    await page.getByLabel('Verification code').pressSequentially(MFA_CODE);

    await expect(page.getByText(/Save these backup codes/)).toBeVisible();
    await page.getByRole('button', { name: 'Copy all' }).click();
    await expect(page.getByRole('button', { name: 'Continue' })).toBeEnabled();
    await page.getByRole('button', { name: 'Continue' }).click();

    await expect(page).toHaveURL('/');
  });

  test('shows an error for an incorrect verification code', async ({ page, backend }) => {
    backend.withAuthenticatedUser();
    await page.goto('/mfa/enroll');

    await page.getByLabel('Verification code').pressSequentially('000000');

    await expect(page.getByText('Invalid verification code')).toBeVisible();
  });
});
