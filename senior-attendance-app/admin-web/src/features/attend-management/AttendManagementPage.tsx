import { useCallback, useEffect, useState } from 'react';
import { apiFetch } from '../../api/client';
import { useAuth } from '../auth/AuthContext';
import { AdminLayout } from '../../components/AdminLayout';
import { AttendeeRow } from './AttendeeRow';
import type { AttendanceStatus, PlaceSummary, ScheduleAttendance } from './types';

function today(): string {
  const d = new Date();
  const year = d.getFullYear();
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

export function AttendManagementPage() {
  const { logout } = useAuth();
  const [places, setPlaces] = useState<PlaceSummary[] | null>(null);
  const [placesError, setPlacesError] = useState(false);
  const [placeId, setPlaceId] = useState('');
  const [date, setDate] = useState(today);
  const [schedule, setSchedule] = useState<ScheduleAttendance | null>(null);
  const [notFound, setNotFound] = useState(false);
  const [error, setError] = useState(false);

  const loadPlaces = useCallback(async () => {
    setPlacesError(false);
    try {
      const res = await apiFetch('/api/admin/places');
      if (!res.ok) {
        if (res.status === 401) {
          logout();
          return;
        }
        setPlacesError(true);
        return;
      }
      setPlaces(await res.json());
    } catch {
      setPlacesError(true);
    }
  }, [logout]);

  useEffect(() => {
    loadPlaces();
  }, [loadPlaces]);

  const loadSchedule = useCallback(async () => {
    if (!placeId) {
      return;
    }
    setError(false);
    setNotFound(false);
    try {
      const res = await apiFetch(`/api/admin/schedules?placeId=${placeId}&date=${date}`);
      if (res.status === 404) {
        setSchedule(null);
        setNotFound(true);
        return;
      }
      if (!res.ok) {
        if (res.status === 401) {
          logout();
          return;
        }
        setError(true);
        return;
      }
      setSchedule(await res.json());
    } catch {
      setError(true);
    }
  }, [placeId, date, logout]);

  const handleSaveAttendee = useCallback(
    async (attendId: number, status: AttendanceStatus, note: string) => {
      try {
        const res = await apiFetch(`/api/admin/attend/${attendId}`, {
          method: 'PATCH',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ status, note }),
        });
        if (!res.ok) {
          if (res.status === 401) {
            logout();
            return;
          }
          setError(true);
          return;
        }
        await loadSchedule();
      } catch {
        setError(true);
      }
    },
    [loadSchedule, logout]
  );

  return (
    <AdminLayout>
      <div>
        <h1 style={{ fontSize: 24 }}>일정별 출석 관리</h1>
        <p style={{ color: 'var(--color-text-muted)', fontSize: 14, marginTop: 4 }}>
          장소와 날짜를 선택해 일정별 출석 상태를 확인하고 수정하세요
        </p>
      </div>

      <div className="card" style={{ display: 'flex', alignItems: 'flex-end', gap: 'var(--space-md)' }}>
        <div className="field" style={{ minWidth: 220 }}>
          <label className="field-label" htmlFor="place-select">
            장소
          </label>
          <select
            id="place-select"
            className="input"
            value={placeId}
            onChange={(e) => setPlaceId(e.target.value)}
          >
            <option value="">장소 선택</option>
            {places?.filter((place) => place.active).map((place) => (
              <option key={place.id} value={place.id}>
                {place.name}
              </option>
            ))}
          </select>
        </div>
        <div className="field">
          <label className="field-label" htmlFor="date-input">
            날짜
          </label>
          <input
            id="date-input"
            className="input"
            type="date"
            value={date}
            onChange={(e) => setDate(e.target.value)}
          />
        </div>
        <button className="btn btn-primary" onClick={loadSchedule} disabled={!placeId}>
          조회
        </button>
      </div>

      {placesError && <p className="alert-error">장소 목록을 불러오지 못했습니다</p>}
      {error && <p className="alert-error">출석 정보를 불러오지 못했습니다</p>}
      {notFound && (
        <div className="card" style={{ textAlign: 'center', color: 'var(--color-text-muted)' }}>
          해당 날짜에 일정이 없습니다
        </div>
      )}
      {schedule && (
        <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
          <table className="data-table">
            <thead>
              <tr>
                <th>이름</th>
                <th>상태</th>
                <th>사유</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {schedule.attendees.map((attendee) => (
                <AttendeeRow key={attendee.attendId} attendee={attendee} onSave={handleSaveAttendee} />
              ))}
            </tbody>
          </table>
        </div>
      )}
    </AdminLayout>
  );
}
