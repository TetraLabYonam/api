import { useCallback, useEffect, useState } from 'react';
import { apiFetch } from '../../api/client';
import { useAuth } from '../auth/AuthContext';
import type { Period, UnitTypeAttendanceSummary } from '../../types/attendance';
import { PeriodSelector } from './PeriodSelector';
import { UnitTypeCard } from './UnitTypeCard';

export function LobbyPage() {
  const { logout } = useAuth();
  const [period, setPeriod] = useState<Period>('today');
  const [data, setData] = useState<UnitTypeAttendanceSummary[] | null>(null);
  const [error, setError] = useState(false);

  const load = useCallback(async () => {
    setError(false);
    try {
      const res = await apiFetch(`/api/admin/attendance/summary?period=${period}`);
      if (!res.ok) {
        if (res.status === 401) {
          logout();
          return;
        }
        setError(true);
        return;
      }
      setData(await res.json());
    } catch {
      setError(true);
    }
  }, [period, logout]);

  useEffect(() => {
    load();
  }, [load]);

  return (
    <div>
      <h1>사업단별 출석 현황</h1>
      <PeriodSelector value={period} onChange={setPeriod} />
      <button onClick={load}>새로고침</button>
      {error && (
        <div>
          <p>출석률을 불러오지 못했습니다</p>
          <button onClick={load}>재시도</button>
        </div>
      )}
      {!error && data && (
        <div>
          {data.map((summary) => (
            <UnitTypeCard key={summary.unitType} summary={summary} />
          ))}
        </div>
      )}
    </div>
  );
}
