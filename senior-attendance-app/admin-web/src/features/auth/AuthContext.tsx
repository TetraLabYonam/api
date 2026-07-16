import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import { login as loginRequest, refreshAccessToken, setAccessToken } from '../../api/client';

interface AuthContextValue {
  isLoggedIn: boolean;
  isLoading: boolean;
  login: (username: string, password: string) => Promise<boolean>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [isLoggedIn, setIsLoggedIn] = useState(false);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    refreshAccessToken().then((ok) => {
      setIsLoggedIn(ok);
      setIsLoading(false);
    });
  }, []);

  async function login(username: string, password: string): Promise<boolean> {
    const ok = await loginRequest(username, password);
    setIsLoggedIn(ok);
    return ok;
  }

  function logout(): void {
    setAccessToken(null);
    setIsLoggedIn(false);
  }

  return (
    <AuthContext.Provider value={{ isLoggedIn, isLoading, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return ctx;
}
