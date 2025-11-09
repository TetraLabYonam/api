import { Link } from 'react-router-dom';
import './HomePage.css';

const HomePage = () => {
  return (
    <div className="home-page">
      <h1>메인 페이지</h1>
      <div className="menu">
        <Link to="/map-excel" className="menu-item">
          Excel 관리 (지도)
        </Link>
        <Link to="/member-excel" className="menu-item">
          회원 Excel 업로드
        </Link>
        <Link to="/member" className="menu-item">
          회원 관리
        </Link>
        <Link to="/schedule" className="menu-item">
          일정 관리
        </Link>
        <Link to="/rooms" className="menu-item">
          번호표 시스템 (방 목록)
        </Link>
        <Link to="/rooms/admin" className="menu-item admin-link">
          🔐 방 관리 (관리자)
        </Link>
        <Link to="/hello" className="menu-item">
          Hello
        </Link>
      </div>
    </div>
  );
};

export default HomePage;