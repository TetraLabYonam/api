import { useEffect, useState } from 'react';
import type { Attendee, AttendanceStatus } from './types';

const STATUS_OPTIONS: { value: AttendanceStatus; label: string }[] = [
  { value: 'SCHEDULED', label: '예정' },
  { value: 'PRESENT', label: '출석' },
  { value: 'ABSENT', label: '결석' },
  { value: 'LATE', label: '지각' },
  { value: 'EXCUSED', label: '사유결석' },
];

interface AttendeeRowProps {
  attendee: Attendee;
  onSave: (attendId: number, status: AttendanceStatus, note: string) => void | Promise<void>;
}

export function AttendeeRow({ attendee, onSave }: AttendeeRowProps) {
  const [status, setStatus] = useState<AttendanceStatus>(attendee.status);
  const [note, setNote] = useState(attendee.note ?? '');

  useEffect(() => {
    setStatus(attendee.status);
    setNote(attendee.note ?? '');
  }, [attendee]);

  return (
    <tr>
      <td>{attendee.memberName}</td>
      <td>
        <select
          aria-label={`${attendee.memberName} 상태`}
          value={status}
          onChange={(e) => setStatus(e.target.value as AttendanceStatus)}
        >
          {STATUS_OPTIONS.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
      </td>
      <td>
        <input
          aria-label={`${attendee.memberName} 사유`}
          value={note}
          onChange={(e) => setNote(e.target.value)}
        />
      </td>
      <td>
        <button onClick={() => onSave(attendee.attendId, status, note)}>저장</button>
      </td>
    </tr>
  );
}
