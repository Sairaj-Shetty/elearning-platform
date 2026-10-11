import { createContext, useCallback, useContext, useState, type ReactNode } from "react";
import { Link } from "react-router-dom";
import type { CourseCard } from "../lib/types";
import { difficultyLabel, formatDuration } from "../lib/format";

const ToastContext = createContext<(message: string, kind?: "ok" | "bad") => void>(() => undefined);

export function ToastProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<{ id: number; message: string; kind: "ok" | "bad" }[]>([]);
  const push = useCallback((message: string, kind: "ok" | "bad" = "ok") => {
    const id = Date.now() + Math.random();
    setItems((current) => [...current, { id, message, kind }]);
    setTimeout(() => setItems((current) => current.filter((item) => item.id !== id)), 4200);
  }, []);
  return (
    <ToastContext.Provider value={push}>
      {children}
      <div className="toast">
        {items.map((item) => (
          <div key={item.id} className="card" style={{ padding: 12, borderColor: item.kind === "bad" ? "var(--clay)" : "var(--line)" }}>
            {item.message}
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast() {
  return useContext(ToastContext);
}

export function CourseCardView({ course }: { course: CourseCard }) {
  return (
    <Link to={`/courses/${course.slug}`} className="card" style={{ overflow: "hidden", display: "grid" }}>
      <Thumb course={course} />
      <div style={{ padding: 14, display: "grid", gap: 8 }}>
        <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
          <span className="badge">{course.categoryName}</span>
          {course.seedData && <span className="badge">Sample</span>}
          {course.contentDisabled && <span className="badge">Unavailable</span>}
        </div>
        <strong>{course.title}</strong>
        <p className="muted" style={{ margin: 0, minHeight: 42 }}>{course.shortDescription}</p>
        <div className="muted" style={{ fontSize: 13 }}>
          {course.instructorName} · {difficultyLabel(course.difficulty)} · {formatDuration(course.estimatedDurationMinutes)}
        </div>
        <div className="muted" style={{ fontSize: 13 }}>
          {course.averageRating == null ? "No reviews yet" : `${course.averageRating} from ${course.reviewCount} reviews`}
          {" · "}{course.enrollmentCount} learners
        </div>
        {course.progressPercent != null && (
          <div>
            <div className="progress"><span style={{ width: `${course.progressPercent}%` }} /></div>
            <div className="muted" style={{ fontSize: 12, marginTop: 4 }}>{course.progressPercent}% of lessons saved as complete</div>
          </div>
        )}
      </div>
    </Link>
  );
}

export function Thumb({ course }: { course: Pick<CourseCard, "title" | "thumbnailUrl" | "domain"> }) {
  const hue = course.domain === "TECHNICAL" ? "160" : "18";
  return (
    <div className="thumb" style={{ background: `linear-gradient(145deg, hsl(${hue} 42% 22%), hsl(${hue} 55% 42%))` }}>
      {course.thumbnailUrl && <img src={course.thumbnailUrl} alt="" />}
      <div style={{ position: "absolute", left: 14, bottom: 12, color: "#f6f1e8", fontFamily: "Source Serif 4, serif", fontSize: 22 }}>
        {course.title.slice(0, 1)}
      </div>
    </div>
  );
}

export function Empty({ title, text }: { title: string; text: string }) {
  return (
    <div className="card" style={{ padding: 28 }}>
      <h2 style={{ marginTop: 0 }}>{title}</h2>
      <p className="muted">{text}</p>
    </div>
  );
}

export function Modal({ title, children, onClose }: { title: string; children: ReactNode; onClose: () => void }) {
  return (
    <div className="modal-back" onClick={onClose}>
      <div className="card" style={{ width: minWidth(), padding: 18 }} onClick={(event) => event.stopPropagation()}>
        <div style={{ display: "flex", justifyContent: "space-between", gap: 12 }}>
          <h2 style={{ margin: 0 }}>{title}</h2>
          <button className="btn btn-ghost" onClick={onClose}>Close</button>
        </div>
        <div style={{ marginTop: 14 }}>{children}</div>
      </div>
    </div>
  );
}

function minWidth() {
  return "min(560px, 100%)";
}
