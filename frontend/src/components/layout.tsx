import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { useEffect, useState } from "react";
import { useAuth } from "../lib/auth";
import { api } from "../lib/api";
import type { PublicConfig } from "../lib/types";

export function PublicLayout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [config, setConfig] = useState<PublicConfig | null>(null);
  useEffect(() => {
    api<PublicConfig>("/api/v1/config/public").then(setConfig).catch(() => setConfig(null));
  }, []);
  return (
    <>
      <header className="nav">
        <div className="shell" style={{ display: "flex", alignItems: "center", gap: 16, minHeight: 68 }}>
          <NavLink to="/" className="brand"><span className="mark">L</span> Lumina</NavLink>
          <NavLink to="/courses">Catalog</NavLink>
          <div style={{ marginLeft: "auto", display: "flex", gap: 8, alignItems: "center" }}>
            <ThemeButton />
            {user ? (
              <>
                <NavLink to={homeFor(user.role)} className="btn btn-ghost">Workspace</NavLink>
                <button className="btn btn-ghost" onClick={() => logout().then(() => navigate("/"))}>Sign out</button>
              </>
            ) : (
              <>
                <NavLink to="/login" className="btn btn-ghost">Sign in</NavLink>
                <NavLink to="/register" className="btn btn-primary">Create account</NavLink>
              </>
            )}
          </div>
        </div>
      </header>
      {config?.seedEnabled && (
        <div className="shell" style={{ paddingTop: 12 }}>
          <div className="notice">Sample catalog is loaded. Cards marked Sample are seeded teaching data, not your personal history.</div>
        </div>
      )}
      <main className="shell" style={{ padding: "28px 0 64px" }}><Outlet /></main>
    </>
  );
}

export function AppLayout() {
  const { user, logout, ready } = useAuth();
  const navigate = useNavigate();
  const [unread, setUnread] = useState(0);
  useEffect(() => {
    if (!user) return;
    api<number>("/api/v1/me/notifications/unread-count").then(setUnread).catch(() => setUnread(0));
  }, [user]);
  if (!ready) return <p className="shell">Checking your session…</p>;
  if (!user) return <PublicLayout />;
  const links = linksFor(user.role, unread);
  return (
    <div style={{ minHeight: "100vh" }}>
      <header className="nav" style={{ position: "sticky", top: 0, zIndex: 10 }}>
        <div className="shell" style={{ display: "flex", alignItems: "center", gap: 12, minHeight: 64, flexWrap: "wrap" }}>
          <NavLink to="/" className="brand"><span className="mark">L</span> Lumina</NavLink>
          <nav style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
            {links.map((link) => (
              <NavLink key={link.to} to={link.to} style={({ isActive }) => ({ fontWeight: isActive ? 700 : 500 })}>{link.label}</NavLink>
            ))}
          </nav>
          <div style={{ marginLeft: "auto", display: "flex", gap: 8, alignItems: "center" }}>
            <ThemeButton />
            <NavLink to={homeFor(user.role)} className="btn btn-ghost">
              {user.avatarUrl ? <img src={user.avatarUrl} alt="" style={{ width: 28, height: 28, borderRadius: "50%", objectFit: "cover", verticalAlign: "middle" }} /> : null}
              {" "}Dashboard
            </NavLink>
            <NavLink to="/profile" className="btn btn-ghost">{user.fullName}</NavLink>
            <button className="btn btn-ghost" onClick={() => logout().then(() => navigate("/"))}>Sign out</button>
          </div>
        </div>
      </header>
      <main className="shell" style={{ padding: "22px 0 64px" }}><Outlet /></main>
    </div>
  );
}

function ThemeButton() {
  const [dark, setDark] = useState(() => document.documentElement.classList.contains("dark"));
  return (
    <button className="btn btn-ghost" onClick={() => {
      const next = !dark;
      document.documentElement.classList.toggle("dark", next);
      localStorage.setItem("lumina-theme", next ? "dark" : "light");
      setDark(next);
    }}>{dark ? "Light" : "Dark"}</button>
  );
}

function homeFor(role: string) {
  if (role === "ADMIN") return "/admin";
  if (role === "INSTRUCTOR") return "/teach";
  return "/learn";
}

function linksFor(role: string, unread = 0) {
  const common = [{ to: "/courses", label: "Catalog" }, { to: "/profile", label: "Profile" }, { to: "/notifications", label: unread ? `Notifications (${unread})` : "Notifications" }];
  if (role === "ADMIN") return [{ to: "/admin", label: "Overview" }, { to: "/admin/users", label: "Users" }, { to: "/admin/courses", label: "Moderation" }, { to: "/admin/categories", label: "Categories" }, { to: "/admin/reports", label: "Reports" }, { to: "/admin/audit", label: "Audit" }, ...common];
  if (role === "INSTRUCTOR") return [{ to: "/teach", label: "My courses" }, { to: "/teach/new", label: "New course" }, { to: "/teach/insights", label: "Insights" }, ...common];
  return [{ to: "/learn", label: "Dashboard" }, { to: "/learn/history", label: "History" }, { to: "/learn/roadmap", label: "Roadmap" }, { to: "/learn/assistant", label: "Study help" }, ...common];
}
