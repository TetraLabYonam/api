import { Outlet, Link, useLocation } from 'react-router-dom';
import './MainLayout.css';

const MainLayout = () => {
  const location = useLocation();

  const menuItems = [
    { path: '/map-excel', label: 'Excel 관리 (지도)' },
    { path: '/location-list', label: '위치 정보 조회' },
    { path: '/member-excel', label: '회원 Excel 업로드' },
    { path: '/member-list', label: '회원 정보 조회' },
    { path: '/member', label: '회원 관리' },
    { path: '/schedule', label: '일정 관리' },
    { path: '/rooms', label: '번호표 시스템' },
    { path: '/rooms/admin', label: '방 관리 (관리자)' },
    { path: '/hello', label: 'Hello' },
  ];

  return (
    <div className="main-layout">
      {/* 헤더 */}
      <header className="header">
        <div className="header-left">
          <Link to="/" className="logo">
            <span className="logo-icon">🎓</span>
            <span className="logo-text">S:Link</span>
          </Link>
        </div>
        <div className="header-right">
          <span className="user-info">관리자</span>
        </div>
      </header>

      <div className="layout-body">
        {/* 사이드바 */}
        <aside className="sidebar">
          <div className="sidebar-header">
            <h3>My Page</h3>
          </div>
          <nav className="sidebar-nav">
            <ul className="menu-list">
              {menuItems.map((item) => (
                <li key={item.path} className="menu-item">
                  <Link
                    to={item.path}
                    className={`menu-link ${location.pathname === item.path ? 'active' : ''}`}
                  >
                    <span className="menu-label">{item.label}</span>
                  </Link>
                </li>
              ))}
            </ul>
          </nav>
        </aside>

        {/* 메인 콘텐츠 */}
        <main className="main-content">
          <div className="content-header">
            <h2>Dashboard</h2>
          </div>
          <div className="content-body">
            <Outlet />
          </div>
        </main>
      </div>
    </div>
  );
};

export default MainLayout;