import { createContext, useContext, useEffect, useState, type ReactNode } from "react";
import { api, setAccessToken, type ApiError } from "./api";
import type { User } from "./types";

interface AuthState {
  user: User | null;
  ready: boolean;
  login: (email: string, password: string) => Promise<User>;
  register: (body: Record<string, unknown>) => Promise<User>;
  logout: () => Promise<void>;
  refreshUser: () => Promise<void>;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [ready, setReady] = useState(false);

  async function applySession(path: string, body?: Record<string, unknown>) {
    const data = await api<{ accessToken: string; user: User }>(path, {
      method: "POST",
      body: body ? JSON.stringify(body) : undefined
    });
    setAccessToken(data.accessToken);
    setUser(data.user);
    return data.user;
  }

  useEffect(() => {
    api<{ accessToken: string; user: User }>("/api/v1/auth/refresh", { method: "POST" }, false)
      .then((data) => {
        setAccessToken(data.accessToken);
        setUser(data.user);
      })
      .catch(() => setAccessToken(null))
      .finally(() => setReady(true));
  }, []);

  const value: AuthState = {
    user,
    ready,
    login: (email, password) => applySession("/api/v1/auth/login", { email, password }),
    register: (body) => applySession("/api/v1/auth/register", body),
    logout: async () => {
      try {
        await api("/api/v1/auth/logout", { method: "POST" });
      } catch {
        /* local session still ends */
      }
      setAccessToken(null);
      setUser(null);
    },
    refreshUser: async () => {
      const me = await api<User>("/api/v1/auth/me");
      setUser(me);
    }
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const value = useContext(AuthContext);
  if (!value) throw new Error("AuthProvider is missing");
  return value;
}

export function isApiError(error: unknown): error is ApiError {
  return error instanceof Error && "code" in error;
}
