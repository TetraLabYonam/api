export type Period = 'today' | 'week' | 'month';

export interface UnitTypeAttendanceSummary {
  unitType: string;
  label: string;
  attendanceRate: number;
}
