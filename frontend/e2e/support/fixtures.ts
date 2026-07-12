import { test as base } from '@playwright/test';

import { FakeAuthBackend } from './fake-auth-backend';

export const test = base.extend<{ backend: FakeAuthBackend }>({
  // auto: true so routes are installed even when a spec doesn't need to configure
  // scenario state and so never destructures `backend` from the test args.
  backend: [
    async ({ page }, use) => {
      const backend = new FakeAuthBackend();
      await backend.install(page);
      await use(backend);
    },
    { auto: true },
  ],
});

export { expect } from '@playwright/test';
