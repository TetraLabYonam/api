import { type ReactNode } from 'react';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider, useAuth } from './features/auth/AuthContext';
import { LoginPage } from './features/auth/LoginPage';
import { LobbyPage } from './features/lobby/LobbyPage';
import { AttendManagementPage } from './features/attend-management/AttendManagementPage';

function RequireAuth({ children }: { children: ReactNode }) {
  const { isLoggedIn, isLoading } = useAuth();
  if (isLoading) {
    return <p>로딩 중...</p>;
  }
  if (!isLoggedIn) {
    return <Navigate to="/login" replace />;
  }
  return <>{children}</>;
}

export function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route
            path="/"
            element={
              <RequireAuth>
                <LobbyPage />
              </RequireAuth>
            }
          />
          <Route
            path="/attend-management"
            element={
              <RequireAuth>
                <AttendManagementPage />
              </RequireAuth>
            }
          />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
}
