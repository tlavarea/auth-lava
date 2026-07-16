export function shipmentLocationState(location: string): string {
  const parts = location.split(',');
  return parts[parts.length - 1].trim();
}
