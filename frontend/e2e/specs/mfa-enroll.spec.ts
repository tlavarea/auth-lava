import { MFA_CODE } from '../support/fake-auth-backend';
import { expect, test } from '../support/fixtures';

test.describe('MFA enrollment', () => {
  test('enrolls, verifies the code, and confirms backup codes', async ({ page, backend, context }) => {
    backend.withAuthenticatedUser({ mfaEnabled: false });
    await context.grantPermissions(['clipboard-write'], { origin: 'http://localhost:4200' });
    await page.goto('/profile');

    await page.getByRole('button', { name: 'Update' }).click();

    const dialog = page.getByRole('dialog');
    await expect(dialog.getByAltText('QR code for authenticator app enrollment')).toBeVisible();
    await dialog.getByLabel('Verification code').pressSequentially(MFA_CODE);

    await expect(dialog.getByText(/Save these backup codes/)).toBeVisible();
    await dialog.getByRole('button', { name: 'Copy all' }).click();
    await expect(dialog.getByRole('button', { name: 'Continue' })).toBeEnabled();
    await dialog.getByRole('button', { name: 'Continue' }).click();

    await expect(dialog).toBeHidden();
    await expect(page.getByText('Two-factor verification is: ON', { exact: true })).toBeVisible();
  });

  test('shows an error for an incorrect verification code', async ({ page, backend }) => {
    backend.withAuthenticatedUser({ mfaEnabled: false });
    await page.goto('/profile');

    await page.getByRole('button', { name: 'Update' }).click();
    const dialog = page.getByRole('dialog');
    await dialog.getByLabel('Verification code').pressSequentially('000000');

    await expect(dialog.getByText('Invalid verification code')).toBeVisible();
  });
});
