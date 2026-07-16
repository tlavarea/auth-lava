export type DutyStatusVariant = 'success' | 'warning' | 'secondary' | 'info' | 'outline';

// Raw values come from Samsara's /fleet/hos/clocks hosStatusType (see backend's SamsaraDriverDutyStatusSyncTasklet) -
// null means no HOS clock data has been synced yet, or the driver's Samsara Driver app is disconnected.
export function driverDutyStatusVariant(status: string | null): DutyStatusVariant {
  switch (status) {
    case 'driving':
      return 'success';
    case 'onDuty':
      return 'warning';
    case 'offDuty':
      return 'secondary';
    case 'sleeperBed':
      return 'info';
    case 'yardMove':
    case 'personalConveyance':
      return 'secondary';
    default:
      return 'outline';
  }
}

export function driverDutyStatusLabel(status: string | null): string {
  switch (status) {
    case 'driving':
      return 'Driving';
    case 'onDuty':
      return 'On Duty';
    case 'offDuty':
      return 'Off Duty';
    case 'sleeperBed':
      return 'Sleeper Berth';
    case 'yardMove':
      return 'Yard Move';
    case 'personalConveyance':
      return 'Personal Conveyance';
    default:
      return 'Unknown';
  }
}
