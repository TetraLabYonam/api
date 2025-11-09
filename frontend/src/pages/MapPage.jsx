import { useState, useEffect, useCallback, useMemo } from 'react';
import { useSearchParams } from 'react-router-dom';
import { GoogleMap, Marker, InfoWindow, useJsApiLoader } from '@react-google-maps/api';
import { getLocationData } from '../services/api';
import './MapPage.css';

const libraries = ['places'];

const MapPage = () => {
  const [searchParams] = useSearchParams();
  const [location, setLocation] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [infoWindowOpen, setInfoWindowOpen] = useState(true);

  const apiKey = import.meta.env.VITE_GOOGLE_MAPS_API_KEY;

  const { isLoaded, loadError } = useJsApiLoader({
    id: 'google-map-script',
    googleMapsApiKey: apiKey || '',
    libraries,
  });

  // API 키가 없을 때 처리
  useEffect(() => {
    if (!apiKey) {
      setError('Google Maps API 키가 설정되지 않았습니다. .env 파일을 확인해주세요.');
      setLoading(false);
    }
  }, [apiKey]);

  // URL 파라미터에서 위치 정보 가져오기
  const getLocationFromParams = useCallback(() => {
    const lat = searchParams.get('lat');
    const lng = searchParams.get('lng');
    const address = searchParams.get('address');

    if (lat && lng) {
      return {
        lat: parseFloat(lat),
        lng: parseFloat(lng),
        address: address || 'Selected Location',
      };
    }
    return null;
  }, [searchParams]);

  // API에서 위치 정보 가져오기
  const fetchLocationData = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await getLocationData();
      if (data) {
        setLocation({
          lat: data.lat,
          lng: data.lng,
          address: data.address || 'Selected Location',
        });
      } else {
        setError('위치 정보를 가져올 수 없습니다.');
      }
    } catch (err) {
      console.error('위치 데이터 로드 실패:', err);
      setError('위치 정보를 불러오는 중 오류가 발생했습니다.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    // 먼저 URL 파라미터에서 확인
    const paramsLocation = getLocationFromParams();
    if (paramsLocation) {
      setLocation(paramsLocation);
      setLoading(false);
    } else {
      // URL 파라미터가 없으면 API에서 가져오기
      fetchLocationData();
    }
  }, [getLocationFromParams, fetchLocationData]);

  const mapCenter = useMemo(() => {
    if (location) {
      return { lat: location.lat, lng: location.lng };
    }
    return { lat: 37.5665, lng: 126.9780 }; // 기본값: 서울
  }, [location]);

  const mapOptions = useMemo(
    () => ({
      mapTypeControl: false,
      streetViewControl: false,
      zoom: 17,
    }),
    []
  );

  const handleMarkerClick = () => {
    setInfoWindowOpen(true);
  };

  if (loadError) {
    return (
      <div className="map-error">
        <p>Google Maps를 불러오는 중 오류가 발생했습니다.</p>
        <p>API 키를 확인해주세요.</p>
      </div>
    );
  }

  if (!isLoaded || loading) {
    return (
      <div className="map-loading">
        <p>지도를 불러오는 중...</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="map-error">
        <p>{error}</p>
        <button onClick={fetchLocationData} className="retry-button">
          다시 시도
        </button>
      </div>
    );
  }

  if (!location) {
    return (
      <div className="map-error">
        <p>위치 정보가 없습니다.</p>
      </div>
    );
  }

  return (
    <div className="map-page">
      <div className="map-wrap">
        <div className="map-title">{location.address}</div>
        <div className="map-coords">
          <span>
            Lat: <span>{location.lat.toFixed(6)}</span>
          </span>
          ,{' '}
          <span>
            Lng: <span>{location.lng.toFixed(6)}</span>
          </span>
        </div>
        <GoogleMap
          mapContainerClassName="map-container"
          center={mapCenter}
          zoom={mapOptions.zoom}
          options={mapOptions}
        >
          <Marker
            position={mapCenter}
            title={location.address}
            onClick={handleMarkerClick}
          />
          {infoWindowOpen && (
            <InfoWindow
              position={mapCenter}
              onCloseClick={() => setInfoWindowOpen(false)}
            >
              <div className="info-window-content">
                <b>{location.address}</b>
                <br />
                ({location.lat.toFixed(6)}, {location.lng.toFixed(6)})
              </div>
            </InfoWindow>
          )}
        </GoogleMap>
      </div>
    </div>
  );
};

export default MapPage;

