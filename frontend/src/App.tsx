import { Navigate, Route, Routes } from "react-router-dom";
import type { ReactNode } from "react";
import { useAuth } from "./lib/auth";
import type { Role } from "./lib/types";
import { AppLayout, PublicLayout } from "./components/layout";
import { CatalogPage, CoursePage, HomePage, InstructorPage, LoginPage, RegisterPage } from "./pages/public";
import { AssistantPage, DashboardPage, HistoryPage, LearnPlayer, NotificationsPage, ProfilePage, RoadmapPage } from "./pages/learn";
import { InsightsPage, NewCoursePage, TeachPage } from "./pages/teach";
import { AdminPage } from "./pages/admin";

export function App() {
  return (
    <Routes>
      <Route element={<PublicLayout />}>
        <Route path="/" element={<HomePage />} />
        <Route path="/courses" element={<CatalogPage />} />
        <Route path="/courses/:slug" element={<CoursePage />} />
        <Route path="/instructors/:id" element={<InstructorPage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
      </Route>
      <Route element={<RequireAuth />}>
        <Route path="/learn" element={<DashboardPage />} />
        <Route path="/learn/history" element={<HistoryPage />} />
        <Route path="/learn/roadmap" element={<RoadmapPage />} />
        <Route path="/learn/assistant" element={<AssistantPage />} />
        <Route path="/learn/:slug/lessons/:lessonId" element={<LearnPlayer />} />
        <Route path="/profile" element={<ProfilePage />} />
        <Route path="/notifications" element={<NotificationsPage />} />
        <Route path="/teach" element={<RoleGate role="INSTRUCTOR"><TeachPage /></RoleGate>} />
        <Route path="/teach/new" element={<RoleGate role="INSTRUCTOR"><NewCoursePage /></RoleGate>} />
        <Route path="/teach/insights" element={<RoleGate role="INSTRUCTOR"><InsightsPage /></RoleGate>} />
        <Route path="/teach/:courseId" element={<RoleGate role="INSTRUCTOR"><TeachPage /></RoleGate>} />
        <Route path="/admin" element={<RoleGate role="ADMIN"><AdminPage view="overview" /></RoleGate>} />
        <Route path="/admin/users" element={<RoleGate role="ADMIN"><AdminPage view="users" /></RoleGate>} />
        <Route path="/admin/courses" element={<RoleGate role="ADMIN"><AdminPage view="courses" /></RoleGate>} />
        <Route path="/admin/categories" element={<RoleGate role="ADMIN"><AdminPage view="categories" /></RoleGate>} />
        <Route path="/admin/reports" element={<RoleGate role="ADMIN"><AdminPage view="reports" /></RoleGate>} />
        <Route path="/admin/audit" element={<RoleGate role="ADMIN"><AdminPage view="audit" /></RoleGate>} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}

function RoleGate({ role, children }: { role: Role; children: ReactNode }) {
  const { user } = useAuth();
  if (!user || user.role !== role) {
    return <p>This page is limited to the {role.toLowerCase()} role. The server checks the same rule.</p>;
  }
  return children;
}

function RequireAuth() {
  const { user, ready } = useAuth();
  if (!ready) return <p className="shell">Checking your session…</p>;
  if (!user) return <Navigate to="/login" replace />;
  return <AppLayout />;
}
