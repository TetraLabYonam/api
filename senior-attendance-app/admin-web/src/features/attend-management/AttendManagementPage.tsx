import { useCallback, useEffect, useState } from 'react';
import { apiFetch } from '../../api/client';
import { useAuth } from '../auth/AuthContext';
import { AttendeeRow } from './AttendeeRow';
import type { AttendanceStatus, PlaceSummary, ScheduleAttendance } from './types';

function today(): string {
  return new Date().toISOString().slice(0, 10);
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
    <div>
      <h1>일정별 출석 관리</h1>
      <div>
        <label>
          장소
          <select value={placeId} onChange={(e) => setPlaceId(e.target.value)}>
            <option value="">장소 선택</option>
            {places?.map((place) => (
              <option key={place.id} value={place.id}>
                {place.name}
              </option>
            ))}
          </select>
        </label>
        <label>
          날짜
          <input type="date" value={date} onChange={(e) => setDate(e.target.value)} />
        </label>
        <button onClick={loadSchedule} disabled={!placeId}>
          조회
        </button>
      </div>
      {placesError && <p>장소 목록을 불러오지 못했습니다</p>}
      {error && <p>출석 정보를 불러오지 못했습니다</p>}
      {notFound && <p>해당 날짜에 일정이 없습니다</p>}
      {schedule && (
        <table>
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
      )}
    </div>
  );
}
