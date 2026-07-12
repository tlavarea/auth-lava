import { TEST_PASSWORD } from '../support/fake-auth-backend';
import { expect, test } from '../support/fixtures';

test.describe('password change', () => {
  test('changes the password', async ({ page, backend }) => {
    backend.withAuthenticatedUser();
    await page.goto('/profile');

    await page.getByLabel('Current password').fill(TEST_PASSWORD);
    await page.getByLabel('New password', { exact: true }).fill('NewSecret2!');
    await page.getByLabel('Confirm new password').fill('NewSecret2!');
    await page.getByRole('button', { name: 'Change password' }).click();

    await expect(page.getByText('Password changed successfully.')).toBeVisible();
  });

  test('shows an error for an incorrect current password', async ({ page, backend }) => {
    backend.withAuthenticatedUser();
    await page.goto('/profile');

    await page.getByLabel('Current password').fill('wrong-password');
    await page.getByLabel('New password', { exact: true }).fill('NewSecret2!');
    await page.getByLabel('Confirm new password').fill('NewSecret2!');
    await page.getByRole('button', { name: 'Change password' }).click();

    await expect(page.getByText('Current password is incorrect')).toBeVisible();
  });
});
