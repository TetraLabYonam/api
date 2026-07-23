import { useCallback, useEffect, useState } from 'react';
import { apiFetch } from '../../api/client';
import { useAuth } from '../auth/AuthContext';
import { AdminLayout } from '../../components/AdminLayout';
import { Modal } from '../../components/Modal';
import type { PlaceSummary } from '../attend-management/types';

const UNIT_TYPE_OPTIONS = [
  { value: 'PUBLIC_INTEREST', label: '공익형' },
  { value: 'MARKET', label: '시장형' },
  { value: 'SOCIAL_SERVICE', label: '사회서비스형' },
];

function unitTypeLabel(value: string): string {
  return UNIT_TYPE_OPTIONS.find((opt) => opt.value === value)?.label ?? value;
}

interface PlaceFormState {
  name: string;
  address: string;
  unitType: string;
  description: string;
  latitude: string;
  longitude: string;
}

const EMPTY_FORM: PlaceFormState = {
  name: '',
  address: '',
  unitType: '',
  description: '',
  latitude: '',
  longitude: '',
};

function formIsValid(f: PlaceFormState): boolean {
  return (
    f.name.trim() !== '' &&
    f.address.trim() !== '' &&
    f.unitType !== '' &&
    f.latitude.trim() !== '' &&
    f.longitude.trim() !== ''
  );
}

function toRequestBody(f: PlaceFormState) {
  return {
    name: f.name,
    address: f.address,
    unitType: f.unitType,
    description: f.description || null,
    latitude: Number(f.latitude),
    longitude: Number(f.longitude),
  };
}

export function PlaceManagementPage() {
  const { logout } = useAuth();
  const [places, setPlaces] = useState<PlaceSummary[] | null>(null);
  const [placesError, setPlacesError] = useState(false);

  const [form, setForm] = useState<PlaceFormState>(EMPTY_FORM);
  const [submitting, setSubmitting] = useState(false);
  const [registerError, setRegisterError] = useState(false);

  const [editingPlace, setEditingPlace] = useState<PlaceSummary | null>(null);
  const [editForm, setEditForm] = useState<PlaceFormState>(EMPTY_FORM);
  const [editSubmitting, setEditSubmitting] = useState(false);
  const [editError, setEditError] = useState(false);

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

  const handleRegister = useCallback(async () => {
    if (!formIsValid(form)) {
      return;
    }
    setSubmitting(true);
    setRegisterError(false);
    try {
      const res = await apiFetch('/api/admin/places', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(toRequestBody(form)),
      });
      if (!res.ok) {
        if (res.status === 401) {
          logout();
          return;
        }
        setRegisterError(true);
        return;
      }
      setForm(EMPTY_FORM);
      await loadPlaces();
    } catch {
      setRegisterError(true);
    } finally {
      setSubmitting(false);
    }
  }, [form, logout, loadPlaces]);

  const openEdit = useCallback((place: PlaceSummary) => {
    setEditingPlace(place);
    setEditForm({
      name: place.name,
      address: place.address,
      unitType: place.unitType,
      description: place.description ?? '',
      latitude: String(place.latitude),
      longitude: String(place.longitude),
    });
    setEditError(false);
  }, []);

  const handleEditSave = useCallback(async () => {
    if (!editingPlace || !formIsValid(editForm)) {
      return;
    }
    setEditSubmitting(true);
    setEditError(false);
    try {
      const res = await apiFetch(`/api/admin/places/${editingPlace.id}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          name: editForm.name,
          address: editForm.address,
          unitType: editForm.unitType,
          description: editForm.description,
          latitude: Number(editForm.latitude),
          longitude: Number(editForm.longitude),
          active: editingPlace.active,
        }),
      });
      if (!res.ok) {
        if (res.status === 401) {
          logout();
          return;
        }
        setEditError(true);
        return;
      }
      setEditingPlace(null);
      await loadPlaces();
    } catch {
      setEditError(true);
    } finally {
      setEditSubmitting(false);
    }
  }, [editingPlace, editForm, logout, loadPlaces]);

  const handleToggleActive = useCallback(
    async (place: PlaceSummary, active: boolean) => {
      try {
        const res = await apiFetch(`/api/admin/places/${place.id}`, {
          method: 'PATCH',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            name: place.name,
            address: place.address,
            unitType: place.unitType,
            description: place.description,
            latitude: place.latitude,
            longitude: place.longitude,
            active,
          }),
        });
        if (!res.ok) {
          if (res.status === 401) {
            logout();
            return;
          }
          setPlacesError(true);
          return;
        }
        await loadPlaces();
      } catch {
        setPlacesError(true);
      }
    },
    [loadPlaces, logout]
  );

  return (
    <AdminLayout>
      <div>
        <h1 style={{ fontSize: 24 }}>장소 관리</h1>
        <p style={{ color: 'var(--color-text-muted)', fontSize: 14, marginTop: 4 }}>
          신규 장소를 등록하고 정보를 관리하세요
        </p>
      </div>

      <div className="card" style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-md)' }}>
        <div style={{ display: 'flex', alignItems: 'flex-end', gap: 'var(--space-md)', flexWrap: 'wrap' }}>
          <div className="field">
            <label className="field-label" htmlFor="place-name-input">
              이름
            </label>
            <input
              id="place-name-input"
              className="input"
              value={form.name}
              onChange={(e) => setForm({ ...form, name: e.target.value })}
            />
          </div>
          <div className="field">
            <label className="field-label" htmlFor="place-address-input">
              주소
            </label>
            <input
              id="place-address-input"
              className="input"
              value={form.address}
              onChange={(e) => setForm({ ...form, address: e.target.value })}
            />
          </div>
          <div className="field" style={{ minWidth: 160 }}>
            <label className="field-label" htmlFor="place-unittype-select">
              유형
            </label>
            <select
              id="place-unittype-select"
              className="input"
              value={form.unitType}
              onChange={(e) => setForm({ ...form, unitType: e.target.value })}
            >
              <option value="">유형 선택</option>
              {UNIT_TYPE_OPTIONS.map((opt) => (
                <option key={opt.value} value={opt.value}>
                  {opt.label}
                </option>
              ))}
            </select>
          </div>
          <div className="field">
            <label className="field-label" htmlFor="place-latitude-input">
              위도
            </label>
            <input
              id="place-latitude-input"
              className="input"
              type="number"
              step="any"
              value={form.latitude}
              onChange={(e) => setForm({ ...form, latitude: e.target.value })}
            />
          </div>
          <div className="field">
            <label className="field-label" htmlFor="place-longitude-input">
              경도
            </label>
            <input
              id="place-longitude-input"
              className="input"
              type="number"
              step="any"
              value={form.longitude}
              onChange={(e) => setForm({ ...form, longitude: e.target.value })}
            />
          </div>
        </div>
        <div className="field">
          <label className="field-label" htmlFor="place-description-input">
            설명
          </label>
          <input
            id="place-description-input"
            className="input"
            value={form.description}
            onChange={(e) => setForm({ ...form, description: e.target.value })}
          />
        </div>
        <div>
          <button
            className="btn btn-primary"
            onClick={handleRegister}
            disabled={!formIsValid(form) || submitting}
          >
            등록
          </button>
        </div>
      </div>

      {placesError && <p className="alert-error">장소 목록을 불러오지 못했습니다</p>}
      {registerError && <p className="alert-error">장소 등록에 실패했습니다</p>}

      <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
        <table className="data-table">
          <thead>
            <tr>
              <th>이름</th>
              <th>주소</th>
              <th>유형</th>
              <th>상태</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {places?.map((place) => (
              <tr key={place.id}>
                <td>{place.name}</td>
                <td>{place.address}</td>
                <td>{unitTypeLabel(place.unitType)}</td>
                <td>
                  <span className={`badge ${place.active ? 'badge-success' : 'badge-neutral'}`}>
                    {place.active ? '활성' : '비활성'}
                  </span>
                </td>
                <td style={{ display: 'flex', gap: 'var(--space-xs)' }}>
                  <button className="btn btn-secondary btn-sm" onClick={() => openEdit(place)}>
                    수정
                  </button>
                  <button
                    className="btn btn-secondary btn-sm"
                    onClick={() => handleToggleActive(place, !place.active)}
                    aria-label={`${place.name} ${place.active ? '비활성화' : '활성화'}`}
                  >
                    {place.active ? '비활성화' : '활성화'}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {editingPlace && (
        <Modal title="장소 수정" onClose={() => setEditingPlace(null)}>
          <div className="field">
            <label className="field-label" htmlFor="edit-name-input">
              이름
            </label>
            <input
              id="edit-name-input"
              className="input"
              value={editForm.name}
              onChange={(e) => setEditForm({ ...editForm, name: e.target.value })}
            />
          </div>
          <div className="field">
            <label className="field-label" htmlFor="edit-address-input">
              주소
            </label>
            <input
              id="edit-address-input"
              className="input"
              value={editForm.address}
              onChange={(e) => setEditForm({ ...editForm, address: e.target.value })}
            />
          </div>
          <div className="field">
            <label className="field-label" htmlFor="edit-unittype-select">
              유형
            </label>
            <select
              id="edit-unittype-select"
              className="input"
              value={editForm.unitType}
              onChange={(e) => setEditForm({ ...editForm, unitType: e.target.value })}
            >
              <option value="">유형 선택</option>
              {UNIT_TYPE_OPTIONS.map((opt) => (
                <option key={opt.value} value={opt.value}>
                  {opt.label}
                </option>
              ))}
            </select>
          </div>
          <div className="field">
            <label className="field-label" htmlFor="edit-description-input">
              설명
            </label>
            <input
              id="edit-description-input"
              className="input"
              value={editForm.description}
              onChange={(e) => setEditForm({ ...editForm, description: e.target.value })}
            />
          </div>
          <div className="field">
            <label className="field-label" htmlFor="edit-latitude-input">
              위도
            </label>
            <input
              id="edit-latitude-input"
              className="input"
              type="number"
              step="any"
              value={editForm.latitude}
              onChange={(e) => setEditForm({ ...editForm, latitude: e.target.value })}
            />
          </div>
          <div className="field">
            <label className="field-label" htmlFor="edit-longitude-input">
              경도
            </label>
            <input
              id="edit-longitude-input"
              className="input"
              type="number"
              step="any"
              value={editForm.longitude}
              onChange={(e) => setEditForm({ ...editForm, longitude: e.target.value })}
            />
          </div>
          {editError && <p className="alert-error">장소 수정에 실패했습니다</p>}
          <div>
            <button
              className="btn btn-primary"
              onClick={handleEditSave}
              disabled={!formIsValid(editForm) || editSubmitting}
            >
              저장
            </button>
          </div>
        </Modal>
      )}
    </AdminLayout>
  );
}
