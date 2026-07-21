import { useCallback, useEffect, useRef, useState } from 'react';
import { QRCodeSVG } from 'qrcode.react';
import { apiFetch } from '../../api/client';
import { useAuth } from '../auth/AuthContext';
import { AdminLayout } from '../../components/AdminLayout';
import type { PlaceSummary } from '../attend-management/types';
import type { MemberSummary, RegisterMemberResult } from './types';

export function MemberManagementPage() {
  const { logout } = useAuth();
  const [places, setPlaces] = useState<PlaceSummary[] | null>(null);
  const [placesError, setPlacesError] = useState(false);
  const [members, setMembers] = useState<MemberSummary[] | null>(null);
  const [membersError, setMembersError] = useState(false);

  const [name, setName] = useState('');
  const [phoneNumber, setPhoneNumber] = useState('');
  const [placeId, setPlaceId] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [registerError, setRegisterError] = useState(false);
  const [registerResult, setRegisterResult] = useState<RegisterMemberResult | null>(null);
  const [qrDownloadHref, setQrDownloadHref] = useState<string | null>(null);

  const qrRef = useRef<SVGSVGElement>(null);

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

  const loadMembers = useCallback(async () => {
    setMembersError(false);
    try {
      const res = await apiFetch('/api/admin/members');
      if (!res.ok) {
        if (res.status === 401) {
          logout();
          return;
        }
        setMembersError(true);
        return;
      }
      setMembers(await res.json());
    } catch {
      setMembersError(true);
    }
  }, [logout]);

  useEffect(() => {
    loadPlaces();
    loadMembers();
  }, [loadPlaces, loadMembers]);

  const handleRegister = useCallback(async () => {
    if (!placeId || !name.trim() || !phoneNumber.trim()) {
      return;
    }
    setSubmitting(true);
    setRegisterError(false);
    try {
      const res = await apiFetch('/api/admin/members', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name, phoneNumber, placeId: Number(placeId) }),
      });
      if (!res.ok) {
        if (res.status === 401) {
          logout();
          return;
        }
        setRegisterError(true);
        return;
      }
      const result: RegisterMemberResult = await res.json();
      setRegisterResult(result);
      setName('');
      setPhoneNumber('');
      await loadMembers();
    } catch {
      setRegisterError(true);
    } finally {
      setSubmitting(false);
    }
  }, [name, phoneNumber, placeId, logout, loadMembers]);

  const handleToggleActive = useCallback(
    async (employeeId: number, active: boolean) => {
      try {
        const res = await apiFetch(`/api/admin/members/${employeeId}`, {
          method: 'PATCH',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ active }),
        });
        if (!res.ok) {
          if (res.status === 401) {
            logout();
            return;
          }
          setMembersError(true);
          return;
        }
        await loadMembers();
      } catch {
        setMembersError(true);
      }
    },
    [loadMembers, logout]
  );

  useEffect(() => {
    if (!registerResult || !qrRef.current) {
      setQrDownloadHref(null);
      return;
    }
    const svgMarkup = new XMLSerializer().serializeToString(qrRef.current);
    setQrDownloadHref(`data:image/svg+xml;base64,${btoa(unescape(encodeURIComponent(svgMarkup)))}`);
  }, [registerResult]);

  return (
    <AdminLayout>
      <div>
        <h1 style={{ fontSize: 24 }}>회원 관리</h1>
        <p style={{ color: 'var(--color-text-muted)', fontSize: 14, marginTop: 4 }}>
          신규 회원을 등록하고 QR 출입증을 발급하세요
        </p>
      </div>

      <div className="card" style={{ display: 'flex', alignItems: 'flex-end', gap: 'var(--space-md)', flexWrap: 'wrap' }}>
        <div className="field">
          <label className="field-label" htmlFor="member-name-input">
            이름
          </label>
          <input
            id="member-name-input"
            className="input"
            value={name}
            onChange={(e) => setName(e.target.value)}
          />
        </div>
        <div className="field">
          <label className="field-label" htmlFor="member-phone-input">
            전화번호
          </label>
          <input
            id="member-phone-input"
            className="input"
            value={phoneNumber}
            onChange={(e) => setPhoneNumber(e.target.value)}
          />
        </div>
        <div className="field" style={{ minWidth: 220 }}>
          <label className="field-label" htmlFor="member-place-select">
            장소
          </label>
          <select
            id="member-place-select"
            className="input"
            value={placeId}
            onChange={(e) => setPlaceId(e.target.value)}
          >
            <option value="">장소 선택</option>
            {places?.map((place) => (
              <option key={place.id} value={place.id}>
                {place.name}
              </option>
            ))}
          </select>
        </div>
        <button
          className="btn btn-primary"
          onClick={handleRegister}
          disabled={!placeId || !name.trim() || !phoneNumber.trim() || submitting}
        >
          등록
        </button>
      </div>

      {placesError && <p className="alert-error">장소 목록을 불러오지 못했습니다</p>}
      {registerError && <p className="alert-error">회원 등록에 실패했습니다</p>}

      {registerResult && (
        <div className="card" style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-sm)', alignItems: 'flex-start' }}>
          <p>
            {registerResult.name}님 (사번 {registerResult.employeeId}) 등록 완료
          </p>
          <QRCodeSVG ref={qrRef} value={registerResult.qrPayload} title="회원 QR 출입증" size={200} />
          {qrDownloadHref && (
            <a
              className="btn btn-secondary btn-sm"
              href={qrDownloadHref}
              download={`member-${registerResult.employeeId}-qr.svg`}
            >
              QR 다운로드
            </a>
          )}
        </div>
      )}

      {membersError && <p className="alert-error">회원 목록을 불러오지 못했습니다</p>}

      <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
        <table className="data-table">
          <thead>
            <tr>
              <th>사번</th>
              <th>이름</th>
              <th>장소</th>
              <th>상태</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {members?.map((member) => (
              <tr key={member.employeeId}>
                <td>{member.employeeId}</td>
                <td>{member.name}</td>
                <td>{member.placeName}</td>
                <td>
                  <span className={`badge ${member.active ? 'badge-success' : 'badge-neutral'}`}>
                    {member.active ? '활성' : '비활성'}
                  </span>
                </td>
                <td>
                  <button
                    className="btn btn-secondary btn-sm"
                    onClick={() => handleToggleActive(member.employeeId, !member.active)}
                  >
                    {member.name} {member.active ? '비활성화' : '활성화'}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </AdminLayout>
  );
}
