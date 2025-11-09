import { useState, useCallback, useMemo, useRef } from 'react';
import { GoogleMap, Marker, InfoWindow, useJsApiLoader } from '@react-google-maps/api';
import { uploadExcelFile, saveLocationsToDb } from '../services/api';
import './ExcelMapPage.css';

const libraries = ['places'];

const ExcelMapPage = () => {
  const [locations, setLocations] = useState([]);
  const [selectedMarker, setSelectedMarker] = useState(null);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);
  const [message, setMessage] = useState(null);
  const [messageType, setMessageType] = useState(null);
  const fileInputRef = useRef(null);

  const apiKey = import.meta.env.VITE_GOOGLE_MAPS_API_KEY;

  const { isLoaded, loadError } = useJsApiLoader({
    id: 'google-map-script',
    googleMapsApiKey: apiKey || '',
    libraries,
  });

  // 파일 업로드 처리
  const handleFileChange = useCallback(async (event) => {
    const file = event.target.files?.[0];
    if (!file) return;

    // 파일 형식 검증
    const validExtensions = ['.xlsx', '.xls'];
    const fileExtension = file.name.toLowerCase().slice(file.name.lastIndexOf('.'));
    if (!validExtensions.includes(fileExtension)) {
      setError('엑셀 파일(.xlsx, .xls)만 업로드 가능합니다.');
      setMessage('엑셀 파일(.xlsx, .xls)만 업로드 가능합니다.');
      setMessageType('error');
      return;
    }

    setLoading(true);
    setError(null);
    setMessage(null);
    setMessageType(null);

    try {
      const data = await uploadExcelFile(file);
      if (data && data.locations && Array.isArray(data.locations)) {
        setLocations(data.locations);
        setMessage(`${data.locations.length}개의 위치 정보를 불러왔습니다.`);
        setMessageType('success');
      } else {
        throw new Error('업로드된 데이터 형식이 올바르지 않습니다.');
      }
    } catch (err) {
      console.error('파일 업로드 실패:', err);
      const errorMessage = err.response?.data?.error || err.message || '파일 업로드 중 오류가 발생했습니다.';
      setError(errorMessage);
      setMessage(errorMessage);
      setMessageType('error');
      setLocations([]);
    } finally {
      setLoading(false);
      // 파일 input 초기화
      if (fileInputRef.current) {
        fileInputRef.current.value = '';
      }
    }
  }, []);

  // DB 저장 처리
  const handleSaveToDb = useCallback(async () => {
    if (!locations || locations.length === 0) {
      setMessage('저장할 위치 정보가 없습니다.');
      setMessageType('error');
      return;
    }

    setSaving(true);
    setMessage(null);
    setMessageType(null);

    try {
      const response = await saveLocationsToDb(locations);
      // 백엔드가 문자열을 반환하거나 객체일 수 있음
      const message = typeof response === 'string' ? response : response?.message || '데이터가 성공적으로 저장되었습니다.';
      setMessage(message);
      setMessageType('success');
    } catch (err) {
      console.error('DB 저장 실패:', err);
      const errorMessage = err.response?.data?.error || err.response?.data || err.message || '저장 중 오류가 발생했습니다.';
      setMessage(errorMessage);
      setMessageType('error');
    } finally {
      setSaving(false);
    }
  }, [locations]);

  // 지도 범위 계산 (모든 마커가 보이도록)
  const mapBounds = useMemo(() => {
    if (locations.length === 0) return null;

    const bounds = new window.google.maps.LatLngBounds();
    locations.forEach((location) => {
      if (location.lat && location.lng) {
        bounds.extend({ lat: location.lat, lng: location.lng });
      }
    });
    return bounds;
  }, [locations]);

  // 지도 중심 계산
  const mapCenter = useMemo(() => {
    if (locations.length === 0) {
      return { lat: 37.5665, lng: 126.9780 }; // 기본값: 서울
    }
    const firstLocation = locations[0];
    return { lat: firstLocation.lat, lng: firstLocation.lng };
  }, [locations]);

  // 지도 옵션
  const mapOptions = useMemo(
    () => ({
      mapTypeControl: false,
      streetViewControl: false,
      zoom: locations.length > 1 ? undefined : 17,
    }),
    [locations.length]
  );

  // 마커 클릭 핸들러
  const handleMarkerClick = useCallback((location, index) => {
    setSelectedMarker({ location, index });
  }, []);

  // InfoWindow 닫기
  const handleInfoWindowClose = useCallback(() => {
    setSelectedMarker(null);
  }, []);

  if (loadError) {
    return (
      <div className="excel-map-error">
        <p>Google Maps를 불러오는 중 오류가 발생했습니다.</p>
        <p>API 키를 확인해주세요.</p>
      </div>
    );
  }

  if (!isLoaded) {
    return (
      <div className="excel-map-loading">
        <p>Google Maps를 불러오는 중...</p>
      </div>
    );
  }

  return (
    <div className="excel-map-page">
      <div className="excel-map-wrap">
        <div className="excel-map-title">Google Map V2 - Excel Upload</div>

        {/* 파일 업로드 섹션 */}
        <div className="upload-section">
          <form onSubmit={(e) => e.preventDefault()}>
            <div className="form-group">
              <label className="form-label" htmlFor="file">
                엑셀 파일을 선택하세요 (주소가 포함된 파일)
              </label>
              <input
                type="file"
                id="file"
                name="file"
                accept=".xlsx,.xls"
                className="file-input"
                onChange={handleFileChange}
                disabled={loading}
                ref={fileInputRef}
              />
            </div>
            <div className="submit-note">
              파일을 선택하면 자동으로 업로드됩니다.
            </div>
          </form>
        </div>

        {/* 메시지 표시 */}
        {message && (
          <div className={`message ${messageType}`}>
            {message}
          </div>
        )}

        {/* 위치 정보 목록 */}
        {locations.length > 0 && (
          <div className="locations-info">
            <h3>업로드된 위치 정보 ({locations.length}개)</h3>
            <div className="locations-list">
              {locations.map((location, index) => (
                <div key={index} className="location-item">
                  <strong>{index + 1}.</strong>{' '}
                  {location.businessUnit && (
                    <span className="business-unit">{location.businessUnit}: </span>
                  )}
                  <span className="address">{location.address}</span>
                  <span className="coords">
                    {' '}- (위도: {location.lat?.toFixed(6)}, 경도: {location.lng?.toFixed(6)})
                  </span>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Google Maps 지도 */}
        {locations.length > 0 && isLoaded && (
          <GoogleMap
            mapContainerClassName="map-container"
            center={mapCenter}
            zoom={mapOptions.zoom}
            options={mapOptions}
            onLoad={(map) => {
              // 모든 마커가 보이도록 범위 조정
              if (mapBounds && locations.length > 1) {
                map.fitBounds(mapBounds);
              }
            }}
          >
            {locations.map((location, index) => {
              if (!location.lat || !location.lng) return null;
              
              return (
                <Marker
                  key={index}
                  position={{ lat: location.lat, lng: location.lng }}
                  label={(index + 1).toString()}
                  title={location.address}
                  onClick={() => handleMarkerClick(location, index)}
                />
              );
            })}

            {selectedMarker && (
              <InfoWindow
                position={{
                  lat: selectedMarker.location.lat,
                  lng: selectedMarker.location.lng,
                }}
                onCloseClick={handleInfoWindowClose}
              >
                <div className="info-window-content">
                  <b>
                    {selectedMarker.index + 1}.{' '}
                    {selectedMarker.location.businessUnit && (
                      <span>{selectedMarker.location.businessUnit}: </span>
                    )}
                    {selectedMarker.location.address}
                  </b>
                  <br />
                  ({selectedMarker.location.lat.toFixed(6)},{' '}
                  {selectedMarker.location.lng.toFixed(6)})
                </div>
              </InfoWindow>
            )}
          </GoogleMap>
        )}

        {/* DB 저장 버튼 */}
        {locations.length > 0 && (
          <div className="save-section">
            <button
              className="save-btn"
              onClick={handleSaveToDb}
              disabled={saving || locations.length === 0}
            >
              {saving ? '저장 중...' : '데이터베이스에 저장'}
            </button>
          </div>
        )}
      </div>
    </div>
  );
};

export default ExcelMapPage;

