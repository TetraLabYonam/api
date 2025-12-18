import { useEffect } from 'react';
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { useSocketStore } from './stores/socketStore';
import MainLayout from './components/Layout/MainLayout';
import HomePage from './pages/HomePage';
import RoomPage from './pages/RoomPage';
import AdminPage from './pages/AdminPage';
import MapPage from './pages/MapPage';
import ExcelMapPage from './pages/ExcelMapPage';
import MemberXlsPage from './pages/MemberXlsPage';
import MemberListPage from './pages/MemberListPage';
import LocationListPage from './pages/LocationListPage';
import './App.css';

function App() {
  const { connect, disconnect } = useSocketStore();

  useEffect(() => {
    // 앱 시작 시 WebSocket 연결
    connect();
    return () => {
      // 앱 종료 시 연결 해제
      disconnect();
    };
  }, [connect, disconnect]);

  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<MainLayout />}>
          <Route index element={<HomePage />} />
          <Route path="rooms" element={<RoomPage />} />
          <Route path="rooms/:roomUid" element={<RoomPage />} />
          <Route path="rooms/admin" element={<AdminPage />} />
          {/* 추가 페이지들은 나중에 구현 */}
          <Route path="mapV1" element={<MapPage />} />
          <Route path="map-excel" element={<ExcelMapPage />} />
          <Route path="member-excel" element={<MemberXlsPage />} />
          <Route path="member-list" element={<MemberListPage />} />
          <Route path="location-list" element={<LocationListPage />} />
          <Route path="member" element={<div style={{ padding: '20px' }}>회원 관리 (준비 중)</div>} />
          <Route path="schedule" element={<div style={{ padding: '20px' }}>일정 관리 (준비 중)</div>} />
          <Route path="hello" element={<div style={{ padding: '20px' }}>Hello 페이지 (준비 중)</div>} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}

export default App;
