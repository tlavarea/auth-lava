import { patchState, signalStore, withMethods, withState } from '@ngrx/signals';

export type Theme = 'light' | 'dark';

const STORAGE_KEY = 'theme';

type ThemeState = {
  theme: Theme;
};

const initialState: ThemeState = {
  theme: 'light',
};

export const ThemeStore = signalStore(
  { providedIn: 'root' },
  withState(initialState),
  withMethods((store) => ({
    // Syncs state to whatever theme the inline script in index.html already applied to <html>,
    // rather than re-deciding it here and risking a flash/mismatch.
    initialize(): void {
      const theme: Theme = document.documentElement.classList.contains('dark') ? 'dark' : 'light';
      patchState(store, { theme });
    },

    toggle(): void {
      const theme: Theme = store.theme() === 'dark' ? 'light' : 'dark';
      document.documentElement.classList.toggle('dark', theme === 'dark');
      window.localStorage.setItem(STORAGE_KEY, theme);
      patchState(store, { theme });
    },
  }))
);

export type ThemeStoreType = InstanceType<typeof ThemeStore>;
