export type AttendanceStatus = 'SCHEDULED' | 'PRESENT' | 'ABSENT' | 'LATE' | 'EXCUSED';

export interface PlaceSummary {
  id: number;
  name: string;
  address: string;
  unitType: string;
  description: string;
  latitude: number;
  longitude: number;
  active: boolean;
}

export interface Attendee {
  attendId: number;
  memberId: number;
  memberName: string;
  status: AttendanceStatus;
  note: string | null;
  attendedAt: string | null;
}

export interface ScheduleAttendance {
  scheduleId: number;
  title: string;
  scheduleDate: string;
  startTime: string;
  endTime: string;
  placeName: string;
  attendees: Attendee[];
}
