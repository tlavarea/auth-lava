import { BadgeVariants } from '@spartan-ng/helm/badge';

// Samsara's engineState stat type (see backend's SamsaraVehicleDiagnosticsRow javadoc) is Off/On/Idle. "Moving"
// isn't a Samsara engine state - this app derives it the same way Samsara's own dashboard does: engine On plus a
// nonzero ecuSpeedMph. Both fields are null when the truck isn't VIN-matched to a Samsara vehicle yet.
export type TruckStatus = 'moving' | 'on' | 'idle' | 'off' | 'unknown';

export function truckStatus(engineState: string | null, ecuSpeedMph: number | null): TruckStatus {
  if (engineState === 'On') {
    return ecuSpeedMph !== null && ecuSpeedMph !== 0 ? 'moving' : 'on';
  }
  if (engineState === 'Idle') {
    return 'idle';
  }
  if (engineState === 'Off') {
    return 'off';
  }
  return 'unknown';
}

export const TRUCK_STATUS_LABELS: Record<TruckStatus, string> = {
  moving: 'Moving',
  on: 'On',
  idle: 'Idle',
  off: 'Off',
  unknown: 'Unknown',
};

export type TruckStatusBadge = { label: string; variant: BadgeVariants['variant']; class: string };

// Mirrors Samsara's own status pills: a light-green "On" and solid-green "Moving" (both the success badge variant,
// "Moving" additionally overridden to a solid fill), gray "Off", amber "Idle" (this app's own convention - Samsara's
// dashboard doesn't call out idling specially, but it's normally flagged since it burns fuel for no distance).
const TRUCK_STATUS_BADGES: Record<TruckStatus, TruckStatusBadge> = {
  moving: { label: 'Moving', variant: 'success', class: 'bg-success text-success-foreground' },
  on: { label: 'On', variant: 'success', class: '' },
  idle: { label: 'Idle', variant: 'warning', class: '' },
  off: { label: 'Off', variant: 'secondary', class: '' },
  unknown: { label: 'Unknown', variant: 'outline', class: '' },
};

export function truckStatusBadge(engineState: string | null, ecuSpeedMph: number | null): TruckStatusBadge {
  return TRUCK_STATUS_BADGES[truckStatus(engineState, ecuSpeedMph)];
}
