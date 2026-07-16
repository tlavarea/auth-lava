export type ShipmentRankVariant = 'success' | 'warning' | 'destructive' | 'secondary';

export function shipmentRankVariant(rank: string): ShipmentRankVariant {
  const value = Number(rank);
  if (Number.isNaN(value)) {
    return 'secondary';
  }
  if (value <= 20) {
    return 'success';
  }
  if (value <= 70) {
    return 'warning';
  }
  return 'destructive';
}
