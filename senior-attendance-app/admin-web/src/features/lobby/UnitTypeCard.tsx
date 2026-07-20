import type { UnitTypeAttendanceSummary } from '../../types/attendance';

export function UnitTypeCard({ summary }: { summary: UnitTypeAttendanceSummary }) {
  return (
    <div className="card">
      <h2 style={{ fontSize: 14, fontWeight: 500, color: 'var(--color-text-muted)' }}>{summary.label}</h2>
      <p style={{ fontSize: 32, fontWeight: 700, color: 'var(--color-primary-dark)', marginTop: 8 }}>
        {summary.attendanceRate.toFixed(1)}%
      </p>
    </div>
  );
}
