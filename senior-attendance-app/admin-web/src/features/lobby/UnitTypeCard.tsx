import type { UnitTypeAttendanceSummary } from '../../types/attendance';

export function UnitTypeCard({ summary }: { summary: UnitTypeAttendanceSummary }) {
  return (
    <div>
      <h2>{summary.label}</h2>
      <p>{summary.attendanceRate.toFixed(1)}%</p>
    </div>
  );
}
