export type ManifestStatusVariant = 'info' | 'success' | 'destructive' | 'muted';

// Mirrors Vektor's own color scheme for these 3 statuses (blue/green/red); planning/assigned/dispatched have no
// requested color and fall back to a neutral treatment.
export function manifestStatusVariant(status: string): ManifestStatusVariant {
  switch (status) {
    case 'manifest_in_progress':
      return 'info';
    case 'manifest_delivered':
      return 'success';
    case 'manifest_tonu':
      return 'destructive';
    default:
      return 'muted';
  }
}
