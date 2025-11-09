import { Outlet } from 'react-router-dom';
import './MainLayout.css';

const MainLayout = () => {
  return (
    <div className="main-layout">
      <div className="content-wrapper">
        <Outlet />
      </div>
    </div>
  );
};

export default MainLayout;