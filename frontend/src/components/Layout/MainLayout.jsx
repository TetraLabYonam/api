import { Outlet, Link, useLocation } from 'react-router-dom';
import './MainLayout.css';

const MainLayout = () => {
  const location = useLocation();

  const menuItems = [
    { path: '/map-excel', label: 'Excel 관리 (지도)', icon: '📊' },
    { path: '/member-excel', label: '회원 Excel 업로드', icon: '📁' },
    { path: '/member', label: '회원 관리', icon: '👥' },
    { path: '/schedule', label: '일정 관리', icon: '📅' },
    { path: '/rooms', label: '번호표 시스템', icon: '🎫' },
    { path: '/rooms/admin', label: '방 관리 (관리자)', icon: '🔐' },
    { path: '/hello', label: 'Hello', icon: '👋' },
  ];

  return (
    <div className="main-layout">
      {/* 헤더 */}
      <header className="header">
        <div className="header-left">
          <div className="logo">
            <span className="logo-icon">🎓</span>
            <span className="logo-text">연암공과대학교</span>
          </div>
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
                    <span className="menu-icon">{item.icon}</span>
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