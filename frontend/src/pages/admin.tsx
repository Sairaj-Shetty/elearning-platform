import { FormEvent, useEffect, useState } from "react";
import { api, apiErrorMessage } from "../lib/api";
import type { Category, CourseCard, Page, User } from "../lib/types";
import { Empty, useToast } from "../components/ui";
import { formatWhen } from "../lib/format";

export function AdminPage({ view }: { view: "overview" | "users" | "courses" | "categories" | "reports" | "audit" }) {
  if (view === "users") return <Users />;
  if (view === "courses") return <Courses />;
  if (view === "categories") return <Categories />;
  if (view === "reports") return <Reports />;
  if (view === "audit") return <Audit />;
  return <Overview />;
}

function Overview() {
  const [data, setData] = useState<{
    students: number; instructors: number; admins: number;
    coursesByStatus: Record<string, number>; totalLessons: number; readyVideos: number;
    enrollments: number; openReports: number;
  } | null>(null);
  const [error, setError] = useState<string | null>(null);
  useEffect(() => {
    api<typeof data>("/api/v1/admin/dashboard").then(setData).catch((err) => setError(apiErrorMessage(err)));
  }, []);
  if (error) return <Empty title="Admin overview unavailable" text={error} />;
  if (!data) return <p>Loading records…</p>;
  return (
    <div>
      <h1>Moderation overview</h1>
      <p>{data.students} students · {data.instructors} instructors · {data.admins} admins</p>
      <p>{data.enrollments} enrollments · {data.readyVideos} ready videos of {data.totalLessons} lessons · {data.openReports} open reports</p>
      <p className="muted">Courses by status: {Object.entries(data.coursesByStatus).map(([key, value]) => `${key} ${value}`).join(" · ")}</p>
    </div>
  );
}

function Users() {
  const toast = useToast();
  const [page, setPage] = useState<Page<User> | null>(null);
  async function load() {
    setPage(await api("/api/v1/admin/users?size=20"));
  }
  useEffect(() => { load().catch((err) => toast(apiErrorMessage(err), "bad")); }, [toast]);
  async function patch(id: string, path: string, body: object) {
    try {
      await api(`/api/v1/admin/users/${id}/${path}`, { method: "PATCH", body: JSON.stringify(body) });
      await load();
    } catch (err) {
      toast(apiErrorMessage(err), "bad");
    }
  }
  return (
    <div>
      <h1>Users</h1>
      <p className="muted">Passwords are never shown. Suspending an account blocks the next login.</p>
      <table className="table">
        <thead><tr><th>Person</th><th>Role</th><th>Status</th><th></th></tr></thead>
        <tbody>
          {page?.items.map((user) => (
            <tr key={user.id}>
              <td>{user.fullName}<br /><span className="muted">{user.email}{user.seedData ? " · sample" : ""}</span></td>
              <td>{user.role}</td>
              <td>{user.status}</td>
              <td>
                <button className="btn btn-ghost" onClick={() => patch(user.id, "status", { status: user.status === "ACTIVE" ? "SUSPENDED" : "ACTIVE" })}>
                  {user.status === "ACTIVE" ? "Suspend" : "Activate"}
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function Courses() {
  const toast = useToast();
  const [status, setStatus] = useState("PENDING_REVIEW");
  const [page, setPage] = useState<Page<CourseCard> | null>(null);
  const [reason, setReason] = useState("Please add a clearer description and a ready lesson video.");
  const [review, setReview] = useState<string | null>(null);
  async function load(next = status) {
    setPage(await api(`/api/v1/admin/courses?status=${next}&size=20`));
  }
  useEffect(() => { load().catch((err) => toast(apiErrorMessage(err), "bad")); }, [toast]);
  async function act(id: string, path: string, body?: object) {
    try {
      await api(`/api/v1/admin/courses/${id}/${path}`, { method: "POST", body: body ? JSON.stringify(body) : undefined });
      toast("Decision saved.");
      await load();
    } catch (err) {
      toast(apiErrorMessage(err), "bad");
    }
  }
  async function assist(course: CourseCard) {
    try {
      const result = await api<{ explanation: string; severity: string; riskCategory: string; aiAvailable: boolean }>("/api/v1/admin/ai/review-text", {
        method: "POST",
        body: JSON.stringify({ title: course.title, description: course.shortDescription })
      });
      setReview(`${result.riskCategory} / ${result.severity}. ${result.explanation} ${result.aiAvailable ? "" : "Gemini was unavailable, so this is not an automated judgment."} Nothing was deleted.`);
    } catch (err) {
      toast(apiErrorMessage(err), "bad");
    }
  }
  return (
    <div style={{ display: "grid", gap: 10 }}>
      <h1>Course moderation</h1>
      <label className="field"><span>Status</span>
        <select value={status} onChange={(event) => { setStatus(event.target.value); void load(event.target.value); }}>
          {["PENDING_REVIEW", "PUBLISHED", "DRAFT", "REJECTED", "ARCHIVED"].map((item) => <option key={item}>{item}</option>)}
        </select>
      </label>
      <label className="field"><span>Rejection reason</span><textarea value={reason} onChange={(event) => setReason(event.target.value)} /></label>
      {review && <div className="notice">{review}</div>}
      {page?.items.map((course) => (
        <div key={course.id} className="card" style={{ padding: 12 }}>
          <strong>{course.title}</strong> · {course.status} · {course.instructorName}
          <p className="muted">{course.shortDescription}</p>
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
            <button className="btn btn-primary" onClick={() => act(course.id, "approve")}>Approve</button>
            <button className="btn btn-danger" onClick={() => act(course.id, "reject", { reason })}>Reject</button>
            <button className="btn btn-ghost" onClick={() => act(course.id, "archive")}>Archive</button>
            <button className="btn btn-ghost" onClick={() => act(course.id, "disable", { disabled: !course.contentDisabled, reason: "Temporarily unavailable" })}>
              {course.contentDisabled ? "Enable content" : "Disable content"}
            </button>
            <button className="btn btn-ghost" onClick={() => assist(course)}>Review text only</button>
          </div>
        </div>
      ))}
      {page && page.items.length === 0 && <p className="muted">No courses in this status.</p>}
    </div>
  );
}

function Categories() {
  const toast = useToast();
  const [items, setItems] = useState<Category[]>([]);
  const [name, setName] = useState("");
  async function load() {
    setItems(await api("/api/v1/admin/categories"));
  }
  useEffect(() => { load().catch((err) => toast(apiErrorMessage(err), "bad")); }, [toast]);
  async function create(event: FormEvent) {
    event.preventDefault();
    try {
      await api("/api/v1/admin/categories", { method: "POST", body: JSON.stringify({ name, description: name, domain: "TECHNICAL" }) });
      setName("");
      await load();
    } catch (err) {
      toast(apiErrorMessage(err), "bad");
    }
  }
  return (
    <div>
      <h1>Categories</h1>
      <form onSubmit={create} style={{ display: "flex", gap: 8 }}>
        <input value={name} onChange={(event) => setName(event.target.value)} placeholder="Category name" required />
        <button className="btn btn-primary" type="submit">Add</button>
      </form>
      <ul>{items.map((item) => <li key={item.id}>{item.name} · {item.domain} · {item.active ? "active" : "inactive"} · {item.courseCount}</li>)}</ul>
    </div>
  );
}

function Reports() {
  const toast = useToast();
  const [page, setPage] = useState<Page<{ id: string; reporterName: string; reason: string; description?: string; status: string; seedData: boolean; createdAt: string }> | null>(null);
  async function load() {
    setPage(await api("/api/v1/admin/reports"));
  }
  useEffect(() => { load().catch((err) => toast(apiErrorMessage(err), "bad")); }, [toast]);
  async function resolve(id: string, status: string) {
    try {
      await api(`/api/v1/admin/reports/${id}/resolve`, { method: "POST", body: JSON.stringify({ status, resolution: "Reviewed by an admin." }) });
      await load();
    } catch (err) {
      toast(apiErrorMessage(err), "bad");
    }
  }
  return (
    <div>
      <h1>Reports</h1>
      {page?.items.map((item) => (
        <div key={item.id} className="card" style={{ padding: 12, marginBottom: 8 }}>
          <strong>{item.reason}</strong> · {item.status} {item.seedData ? "· sample" : ""}
          <p>{item.description}</p>
          <p className="muted">{item.reporterName} · {formatWhen(item.createdAt)}</p>
          <button className="btn btn-ghost" onClick={() => resolve(item.id, "RESOLVED")}>Resolve</button>
          <button className="btn btn-ghost" onClick={() => resolve(item.id, "DISMISSED")}>Dismiss</button>
        </div>
      ))}
    </div>
  );
}

function Audit() {
  const [page, setPage] = useState<Page<{ id: string; actorRole: string; action: string; details?: string; createdAt: string; seedData: boolean }> | null>(null);
  useEffect(() => {
    api<typeof page>("/api/v1/admin/audit").then(setPage).catch(() => setPage(null));
  }, []);
  return (
    <div>
      <h1>Audit</h1>
      <table className="table">
        <thead><tr><th>When</th><th>Action</th><th>Role</th><th>Details</th></tr></thead>
        <tbody>
          {page?.items.map((item) => (
            <tr key={item.id}><td>{formatWhen(item.createdAt)}</td><td>{item.action}{item.seedData ? " · sample" : ""}</td><td>{item.actorRole}</td><td>{item.details}</td></tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
