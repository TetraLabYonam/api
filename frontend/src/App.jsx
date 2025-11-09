import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { SocketProvider } from './contexts/SocketContext';
import MainLayout from './components/Layout/MainLayout';
import HomePage from './pages/HomePage';
import RoomPage from './pages/RoomPage';
import AdminPage from './pages/AdminPage';
import MapPage from './pages/MapPage';
import ExcelMapPage from './pages/ExcelMapPage';
import MemberXlsPage from './pages/MemberXlsPage';
import './App.css';

function App() {
  return (
    <BrowserRouter>
      <SocketProvider>
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
            <Route path="member" element={<div style={{ padding: '20px' }}>회원 관리 (준비 중)</div>} />
            <Route path="schedule" element={<div style={{ padding: '20px' }}>일정 관리 (준비 중)</div>} />
            <Route path="hello" element={<div style={{ padding: '20px' }}>Hello 페이지 (준비 중)</div>} />
          </Route>
        </Routes>
      </SocketProvider>
    </BrowserRouter>
  );
}

export default App;
