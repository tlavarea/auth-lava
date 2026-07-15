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

export type EquipmentItemDetail = {
  description: string | null;
  packType: string | null;
  pieces: number | null;
  quantity: number | null;
  quantityUom: string | null;
  length: number | null;
  width: number | null;
  height: number | null;
  cubicFeet: number | null;
};

export type EquipmentUnit = {
  ciic: string | null;
  commodityCode: string | null;
  commodityDesc: string | null;
  nsn: string | null;
  quantity: number | null;
  quantityUom: string | null;
  items: EquipmentItemDetail[];
};

export type ShipperRequestedService = {
  description: string | null;
  code: string | null;
  cost: number | null;
  // Shape unconfirmed against real data - see GfmBidDetailMapper's Javadoc. Passed through as raw key/value pairs.
  params: Record<string, unknown>[];
};

// Every field beyond the 9 typed columns ShipmentDetailResponse's other fields already persist - derived at read
// time from the same raw_response JSON (see the backend's GfmBidDetailMapper), null exactly when rawResponse is null.
export type GfmBidDetail = {
  bidRank: number | null;
  requestorPhone: string | null;
  originAddress: string | null;
  destinationAddress: string | null;
  earliestPickupDisplay: string | null;
  latestPickupDisplay: string | null;
  latestDeliveryDisplay: string | null;
  offerExpirationDisplay: string | null;
  quantity: number | null;
  quantityUom: string | null;
  commodityCode: string | null;
  ratedCommodityCode: string | null;
  numberOfConveyances: number | null;
  shipmentMode: string | null;
  remarks: string | null;
  sdg3Remarks: string | null;
  contractNumber: string | null;
  carrierPhone: string | null;
  tenderEffectiveDate: string | null;
  tenderExpirationDate: string | null;
  ratedMiles: number | null;
  rateQualifier: string | null;
  ratedQuantityLimits: string | null;
  serviceCost: number | null;
  miscCost: number | null;
  fuelAdjustment: number | null;
  rins: string | null;
  shipperRequestedServices: ShipperRequestedService[];
  equipmentUnits: EquipmentUnit[];
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
  bidDetail: GfmBidDetail | null;
};

export type OfferResponseType = 'ACCEPT' | 'DECLINE';

export type OfferResponseRequest = {
  response: OfferResponseType;
  conveyancesAvailable: number;
};
