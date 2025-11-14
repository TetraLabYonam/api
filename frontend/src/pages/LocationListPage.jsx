import { useState, useEffect } from 'react';
import { getPlaces } from '../services/api';
import './LocationListPage.css';

const LocationListPage = () => {
    const [locations, setLocations] = useState([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(null);
    const [searchTerm, setSearchTerm] = useState('');

    // 실제 API 호출로 위치 정보를 가져오는 함수
    const fetchLocations = async () => {
        setLoading(true);
        setError(null);

        try {
            const data = await getPlaces();
            setLocations(data);
            setLoading(false);
        } catch (err) {
            console.error('위치 정보 조회 실패:', err);
            setError(`위치 정보를 불러오는 중 오류가 발생했습니다: ${err.message}`);
            setLoading(false);
        }
    };

    useEffect(() => {
        fetchLocations();
    }, []);

    // 검색 필터링
    const filteredLocations = locations.filter(location =>
        (location.business_unit || '').toLowerCase().includes(searchTerm.toLowerCase()) ||
        (location.address || '').toLowerCase().includes(searchTerm.toLowerCase())
    );

    const handleViewOnMap = (location) => {
        // Google Maps에서 위치 보기
        const url = `https://www.google.com/maps/search/?api=1&query=${location.lat},${location.lng}`;
        window.open(url, '_blank');
    };

    return (
        <div className="location-list-page">
            <div className="page-header">
                <h1>위치 정보 조회</h1>
                <p>등록된 사업장 위치 정보를 조회하고 지도에서 확인할 수 있습니다.</p>
            </div>

            <div className="search-section">
                <div className="search-box">
                    <input
                        type="text"
                        placeholder="사업장명 또는 주소로 검색..."
                        value={searchTerm}
                        onChange={(e) => setSearchTerm(e.target.value)}
                        className="search-input"
                    />
                    <button onClick={fetchLocations} className="refresh-btn" disabled={loading}>
                        {loading ? '로딩 중...' : '새로고침'}
                    </button>
                </div>
            </div>

            {error && <div className="error-message">{error}</div>}

            <div className="locations-section">
                <div className="section-header">
                    <h3>전체 위치 ({filteredLocations.length}개)</h3>
                </div>

                {loading ? (
                    <div className="loading-message">위치 정보를 불러오는 중...</div>
                ) : filteredLocations.length === 0 ? (
                    <div className="empty-message">
                        {searchTerm ? '검색 결과가 없습니다.' : '등록된 위치가 없습니다.'}
                    </div>
                ) : (
                    <div className="locations-grid">
                        {filteredLocations.map((location, index) => (
                            <div key={index} className="location-card">
                                <div className="location-header">
                                    <h4 className="location-title">
                                        {index + 1}. {location.business_unit || '이름 없음'}
                                    </h4>
                                </div>
                                <div className="location-content">
                                    <div className="location-address">
                                        📍 {location.address || '-'}
                                    </div>
                                    <div className="location-coords">
                                        🌐 위도: {location.lat?.toFixed(6) || '-'}, 경도: {location.lng?.toFixed(6) || '-'}
                                    </div>
                                    {location.phone_number && (
                                        <div className="location-phone">
                                            📞 {location.phone_number}
                                        </div>
                                    )}
                                    {location.description && (
                                        <div className="location-description">
                                            📝 {location.description}
                                        </div>
                                    )}
                                    <button
                                        className="map-btn"
                                        onClick={() => handleViewOnMap(location)}
                                    >
                                        🗺️ 지도에서 보기
                                    </button>
                                </div>
                            </div>
                        ))}
                    </div>
                )}
            </div>
        </div>
    );
};

export default LocationListPage;