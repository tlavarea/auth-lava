export type ShipmentStatusVariant = 'success' | 'info' | 'destructive' | 'secondary';

export function shipmentStatusVariant(status: string): ShipmentStatusVariant {
  switch (status.trim().toUpperCase()) {
    case 'OPEN':
    case 'ACCEPTED':
      return 'success';
    case 'AWAITING AWARD':
      return 'info';
    case 'EXPIRED':
      return 'destructive';
    default:
      return 'secondary';
  }
}
