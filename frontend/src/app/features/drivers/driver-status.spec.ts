import { driverDutyStatusLabel, driverDutyStatusVariant } from './driver-status';

describe('driverDutyStatusVariant', () => {
  it('maps driving to success', () => {
    expect(driverDutyStatusVariant('driving')).toBe('success');
  });

  it('maps onDuty to warning', () => {
    expect(driverDutyStatusVariant('onDuty')).toBe('warning');
  });

  it('maps offDuty to secondary', () => {
    expect(driverDutyStatusVariant('offDuty')).toBe('secondary');
  });

  it('maps sleeperBed to info', () => {
    expect(driverDutyStatusVariant('sleeperBed')).toBe('info');
  });

  it('maps yardMove and personalConveyance to secondary', () => {
    expect(driverDutyStatusVariant('yardMove')).toBe('secondary');
    expect(driverDutyStatusVariant('personalConveyance')).toBe('secondary');
  });

  it('maps null and unrecognized values to outline', () => {
    expect(driverDutyStatusVariant(null)).toBe('outline');
    expect(driverDutyStatusVariant('somethingNew')).toBe('outline');
  });
});

describe('driverDutyStatusLabel', () => {
  it('humanizes known statuses', () => {
    expect(driverDutyStatusLabel('driving')).toBe('Driving');
    expect(driverDutyStatusLabel('onDuty')).toBe('On Duty');
    expect(driverDutyStatusLabel('offDuty')).toBe('Off Duty');
    expect(driverDutyStatusLabel('sleeperBed')).toBe('Sleeper Berth');
    expect(driverDutyStatusLabel('yardMove')).toBe('Yard Move');
    expect(driverDutyStatusLabel('personalConveyance')).toBe('Personal Conveyance');
  });

  it('maps null and unrecognized values to Unknown', () => {
    expect(driverDutyStatusLabel(null)).toBe('Unknown');
    expect(driverDutyStatusLabel('somethingNew')).toBe('Unknown');
  });
});
