import { MFA_CODE } from '../support/fake-auth-backend';
import { expect, test } from '../support/fixtures';

test.describe('MFA disable', () => {
  test('disables MFA via the profile dialog', async ({ page, backend }) => {
    backend.withAuthenticatedUser({ mfaEnabled: true });
    await page.goto('/profile');

    await page.getByRole('button', { name: 'Update' }).click();

    const dialog = page.getByRole('dialog');
    await expect(dialog.getByRole('heading', { name: 'Are you sure?' })).toBeVisible();
    await dialog.getByRole('button', { name: 'Turn it off' }).click();

    await dialog.getByLabel('Verification code').pressSequentially(MFA_CODE);

    await expect(dialog).toBeHidden();
    await expect(page.getByText('Two-factor verification is: OFF', { exact: true })).toBeVisible();
  });

  test('"Not now" closes the dialog without disabling MFA', async ({ page, backend }) => {
    backend.withAuthenticatedUser({ mfaEnabled: true });
    await page.goto('/profile');

    await page.getByRole('button', { name: 'Update' }).click();
    await page.getByRole('dialog').getByRole('button', { name: 'Not now' }).click();

    await expect(page.getByRole('dialog')).toBeHidden();
    await expect(page.getByText('Two-factor verification is: ON', { exact: true })).toBeVisible();
  });

  test('"Cancel" on the code step closes the dialog without disabling MFA', async ({ page, backend }) => {
    backend.withAuthenticatedUser({ mfaEnabled: true });
    await page.goto('/profile');

    await page.getByRole('button', { name: 'Update' }).click();
    const dialog = page.getByRole('dialog');
    await dialog.getByRole('button', { name: 'Turn it off' }).click();
    await dialog.getByRole('button', { name: 'Cancel' }).click();

    await expect(dialog).toBeHidden();
    await expect(page.getByText('Two-factor verification is: ON', { exact: true })).toBeVisible();
  });

  test('shows an error for an incorrect code and keeps the dialog open', async ({ page, backend }) => {
    backend.withAuthenticatedUser({ mfaEnabled: true });
    await page.goto('/profile');

    await page.getByRole('button', { name: 'Update' }).click();
    const dialog = page.getByRole('dialog');
    await dialog.getByRole('button', { name: 'Turn it off' }).click();
    await dialog.getByLabel('Verification code').pressSequentially('000000');

    await expect(dialog.getByText('Invalid verification code')).toBeVisible();
    await expect(dialog).toBeVisible();
  });
});
