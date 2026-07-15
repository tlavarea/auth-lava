export type ShipmentListingRow = {
  offerId: number;
  status: string;
  expirationDate: string | null;
  shipmentId: string;
  shipmentType: string;
  rank: string;
  gbloc: string;
  origin: string;
  destination: string;
  equipType: string;
  conveyancesOffered: number;
  conveyancesAccepted: number;
  pickupDate: string | null;
  requiredDeliveryDate: string | null;
  syncedAt: string | null;
};

export type ShipmentDetailResponse = {
  listing: ShipmentListingRow;
  totalAmount: number | null;
  lineHaulCost: number | null;
  rateUsed: number | null;
  scac: string | null;
  scacName: string | null;
  tenderNumber: string | null;
  equipmentDesc: string | null;
  requestorName: string | null;
  requestorEmail: string | null;
  rawResponse: string | null;
  syncedAt: string | null;
};
