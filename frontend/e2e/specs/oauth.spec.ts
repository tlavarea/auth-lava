import type { Route } from '@playwright/test';

import { expect, test } from '../support/fixtures';

test.describe('OAuth sign-in', () => {
  test('clicking Google redirects to the Google authorization endpoint', async ({ page }) => {
    await page.route('**/oauth2/authorization/**', (route: Route) =>
      route.fulfill({ status: 200, contentType: 'text/plain', body: 'stub' })
    );
    await page.goto('/login');

    await page.getByRole('button', { name: 'Google' }).click();

    await expect(page).toHaveURL(/\/oauth2\/authorization\/google$/);
  });

  test('clicking GitHub redirects to the GitHub authorization endpoint', async ({ page }) => {
    await page.route('**/oauth2/authorization/**', (route: Route) =>
      route.fulfill({ status: 200, contentType: 'text/plain', body: 'stub' })
    );
    await page.goto('/login');

    await page.getByRole('button', { name: 'GitHub' }).click();

    await expect(page).toHaveURL(/\/oauth2\/authorization\/github$/);
  });
});
