import { useCallback, useEffect, useState } from 'react';
import { apiFetch } from '../../api/client';
import { useAuth } from '../auth/AuthContext';
import { AdminLayout } from '../../components/AdminLayout';
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
    <AdminLayout>
      <div>
        <h1 style={{ fontSize: 24 }}>사업단별 출석 현황</h1>
        <p style={{ color: 'var(--color-text-muted)', fontSize: 14, marginTop: 4 }}>
          오늘의 출석 현황을 한눈에 확인하세요
        </p>
      </div>

      <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-md)' }}>
        <PeriodSelector value={period} onChange={setPeriod} />
        <button type="button" className="btn btn-secondary btn-sm" onClick={load}>
          새로고침
        </button>
      </div>

      {error && (
        <div className="card" style={{ textAlign: 'center' }}>
          <p className="alert-error" style={{ display: 'inline-block' }}>
            출석률을 불러오지 못했습니다
          </p>
          <div>
            <button type="button" className="btn btn-primary btn-sm" onClick={load}>
              재시도
            </button>
          </div>
        </div>
      )}
      {!error && data && (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 'var(--space-md)' }}>
          {data.map((summary) => (
            <UnitTypeCard key={summary.unitType} summary={summary} />
          ))}
        </div>
      )}
    </AdminLayout>
  );
}
