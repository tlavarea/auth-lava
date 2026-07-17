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

export type DutyRow = 'OFF' | 'SB' | 'D' | 'ON';

// Collapses Samsara's 6 raw duty statuses onto the FMCSA ELD grid-graph's 4 canonical rows - yardMove folds into ON
// (on-duty, not driving) and personalConveyance folds into OFF, matching how carriers annotate those special
// statuses on a paper/electronic log's ON and OFF rows rather than giving them their own row.
export function dutyStatusRow(status: string | null): DutyRow {
  switch (status) {
    case 'driving':
      return 'D';
    case 'onDuty':
    case 'yardMove':
      return 'ON';
    case 'sleeperBed':
      return 'SB';
    case 'offDuty':
    case 'personalConveyance':
    default:
      return 'OFF';
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
