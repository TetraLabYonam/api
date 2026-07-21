export interface MemberSummary {
  employeeId: number;
  name: string;
  placeId: number;
  placeName: string;
  active: boolean;
}

export interface RegisterMemberResult {
  employeeId: number;
  name: string;
  placeId: number;
  qrPayload: string;
}
