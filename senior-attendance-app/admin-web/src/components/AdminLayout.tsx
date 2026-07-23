import type { ReactNode } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { useAuth } from '../features/auth/AuthContext';

const NAV_ITEMS = [
  { to: '/', label: '출석 현황' },
  { to: '/attend-management', label: '일정별 출석 관리' },
  { to: '/member-management', label: '회원 관리' },
  { to: '/place-management', label: '장소 관리' },
];

export function AdminLayout({ children }: { children: ReactNode }) {
  const { logout } = useAuth();
  const location = useLocation();

  return (
    <div className="admin-layout">
      <aside className="admin-sidebar">
        <div className="admin-sidebar-brand">시니어 출석 관리</div>
        <nav className="admin-sidebar-nav">
          {NAV_ITEMS.map((item) => (
            <Link
              key={item.to}
              to={item.to}
              className={`admin-sidebar-link${location.pathname === item.to ? ' active' : ''}`}
            >
              {item.label}
            </Link>
          ))}
        </nav>
      </aside>
      <div className="admin-main">
        <header className="admin-topbar">
          <button type="button" className="btn btn-secondary btn-sm" onClick={logout}>
            로그아웃
          </button>
        </header>
        <main className="admin-content">{children}</main>
      </div>
    </div>
  );
}
