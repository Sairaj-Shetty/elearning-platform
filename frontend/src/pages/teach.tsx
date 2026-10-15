import { FormEvent, useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { api, apiErrorMessage } from "../lib/api";
import type { Category, CourseCard, CourseDetail } from "../lib/types";
import { Empty, useToast } from "../components/ui";

interface Dash {
  totalCourses: number;
  published: number;
  draft: number;
  pending: number;
  rejected: number;
  archived: number;
  totalEnrollments: number;
  completedEnrollments: number;
  averageProgress: number;
  lessonsWithVideo: number;
  totalLessons: number;
  recentCourses: CourseCard[];
  recentEnrollments: { studentName: string; courseTitle: string; courseId: string; progressPercent: number; enrolledAt: string }[];
}

const emptyCourse = {
  title: "",
  shortDescription: "",
  description: "",
  categoryId: "",
  domain: "TECHNICAL",
  difficulty: "BEGINNER",
  language: "English",
  learningObjectives: "Explain one idea from the lesson",
  prerequisites: "",
  targetAudience: "Beginners",
  tags: "",
  skills: ""
};

export function TeachPage() {
  const { courseId } = useParams();
  const toast = useToast();
  const [dash, setDash] = useState<Dash | null>(null);
  const [courses, setCourses] = useState<CourseCard[]>([]);
  const [course, setCourse] = useState<CourseDetail | null>(null);
  const [roster, setRoster] = useState<any[]>([]);
  const [error, setError] = useState<string | null>(null);

  async function loadList() {
    const [dashboard, mine] = await Promise.all([
      api<Dash>("/api/v1/instructor/dashboard"),
      api<CourseCard[]>("/api/v1/instructor/courses")
    ]);
    setDash(dashboard);
    setCourses(mine);
  }

  useEffect(() => {
    loadList().catch((err) => setError(apiErrorMessage(err)));
  }, []);

  useEffect(() => {
    if (!courseId) {
      setCourse(null);
      return;
    }
    api<CourseDetail>(`/api/v1/instructor/courses/${courseId}`).then(setCourse).catch((err) => setError(apiErrorMessage(err)));
    if (courseId) api<any[]>(`/api/v1/instructor/courses/${courseId}/learners`).then(setRoster).catch(() => setRoster([]));
  }, [courseId]);

  async function act(path: string, method = "POST") {
    try {
      const updated = await api<CourseDetail>(path, { method });
      setCourse(updated);
      await loadList();
      toast("Saved.");
    } catch (err) {
      toast(apiErrorMessage(err), "bad");
    }
  }

  if (error) return <Empty title="Instructor area unavailable" text={error} />;

  return (
    <div style={{ display: "grid", gap: 16 }}>
      <h1>Teaching</h1>
      {dash && (
        <div className="card" style={{ padding: 14 }}>
          <strong>{dash.totalCourses} courses</strong>
          <p className="muted">{dash.published} published · {dash.draft} draft · {dash.pending} waiting · {dash.rejected} rejected · {dash.archived} archived</p>
          <p className="muted">{dash.totalEnrollments} enrollments · {dash.completedEnrollments} completed · average progress {dash.averageProgress}% · videos ready {dash.lessonsWithVideo}/{dash.totalLessons}</p>
        </div>
      )}
      {!course && (
        <div className="card" style={{ padding: 8 }}>
          <table className="table">
            <thead><tr><th>Course</th><th>Status</th><th>Lessons</th><th>Learners</th></tr></thead>
            <tbody>
              {courses.map((item) => (
                <tr key={item.id}>
                  <td><Link to={`/teach/${item.id}`}>{item.title}</Link>{item.seedData ? " · sample" : ""}</td>
                  <td>{item.status}</td>
                  <td>{item.lessonCount}</td>
                  <td>{item.enrollmentCount}</td>
                </tr>
              ))}
            </tbody>
          </table>
          {courses.length === 0 && <p className="muted">No courses yet. Create one, then submit it for review.</p>}
        </div>
      )}
      {course && <Editor course={course} onChange={setCourse} onAct={act} onReload={loadList} />}
      {course && (
        <div className="card" style={{ padding: 12 }}>
          <h3>Roster</h3>
          <table className="table">
            <thead><tr><th>Name</th><th>Email</th><th>Progress</th><th>Completed</th><th>Last lesson</th></tr></thead>
            <tbody>
              {roster.map((row: any, index: number) => (
                <tr key={index}>
                  <td>{row.studentName || row.name}</td>
                  <td>{row.studentEmail || row.email}</td>
                  <td>{row.progressPercent ?? row.progress}%</td>
                  <td>{row.completedLessonCount ?? row.completedLessons} of {row.countableLessonCount}</td>
                  <td>{row.lastLessonTitle || row.lastLesson || row.lastLessonId || "—"}</td>
                </tr>
              ))}
            </tbody>
          </table>
          {roster.length === 0 && <p className="muted">No enrolled students yet.</p>}
        </div>
      )}
    </div>
  );
}

function Editor({ course, onChange, onAct, onReload }: {
  course: CourseDetail;
  onChange: (course: CourseDetail) => void;
  onAct: (path: string, method?: string) => Promise<void>;
  onReload: () => Promise<void>;
}) {
  const toast = useToast();
  const [sectionTitle, setSectionTitle] = useState("");
  const [lessonTitle, setLessonTitle] = useState("");
  const [sectionId, setSectionId] = useState(course.sections[0]?.sectionId || "");
  const [suggestion, setSuggestion] = useState<{ description?: string; objectives?: string[]; sectionTitles?: string[]; message?: string; aiAvailable?: boolean } | null>(null);
  const editable = course.status === "DRAFT" || course.status === "REJECTED";

  async function addSection(event: FormEvent) {
    event.preventDefault();
    try {
      onChange(await api(`/api/v1/instructor/courses/${course.id}/sections`, { method: "POST", body: JSON.stringify({ title: sectionTitle, description: "" }) }));
      setSectionTitle("");
    } catch (err) {
      toast(apiErrorMessage(err), "bad");
    }
  }
  async function addLesson(event: FormEvent) {
    event.preventDefault();
    try {
      onChange(await api(`/api/v1/instructor/courses/${course.id}/sections/${sectionId}/lessons`, {
        method: "POST",
        body: JSON.stringify({ title: lessonTitle, description: "Describe what the learner will do.", objectives: ["Follow the saved lesson text"], estimatedDurationMinutes: 8, access: "ENROLLED_ONLY", resources: [] })
      }));
      setLessonTitle("");
    } catch (err) {
      toast(apiErrorMessage(err), "bad");
    }
  }
  async function upload(lessonId: string, file: File) {
    const body = new FormData();
    body.append("file", file);
    try {
      await api(`/api/v1/instructor/courses/${course.id}/lessons/${lessonId}/video`, { method: "POST", body });
      onChange(await api(`/api/v1/instructor/courses/${course.id}`));
      toast("Video stored. Playback is available only after this save succeeds.");
    } catch (err) {
      toast(apiErrorMessage(err), "bad");
    }
  }
  async function assist() {
    try {
      const data = await api<typeof suggestion>("/api/v1/instructor/ai/assist", {
        method: "POST",
        body: JSON.stringify({ task: "Improve the description and suggest section titles. Do not publish.", title: course.title, description: course.description, domain: course.domain, difficulty: course.difficulty, categoryName: course.categoryName })
      });
      setSuggestion(data);
    } catch (err) {
      toast(apiErrorMessage(err), "bad");
    }
  }
  async function applySuggestion() {
    if (!suggestion?.description) return;
    try {
      onChange(await api(`/api/v1/instructor/courses/${course.id}`, {
        method: "PUT",
        body: JSON.stringify(writeBody(course, suggestion.description, suggestion.objectives))
      }));
      toast("You approved the suggestion. It is now saved.");
      setSuggestion(null);
      await onReload();
    } catch (err) {
      toast(apiErrorMessage(err), "bad");
    }
  }

  return (
    <div style={{ display: "grid", gap: 12 }}>
      <p><Link to="/teach">All courses</Link></p>
      <h2>{course.title}</h2>
      <p className="muted">Status {course.status}. {course.rejectionReason ? `Rejection: ${course.rejectionReason}` : "Students cannot see this until an admin publishes it."}</p>
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
        {editable && <button className="btn btn-primary" onClick={() => onAct(`/api/v1/instructor/courses/${course.id}/submit`)}>Submit for review</button>}
        {course.status === "PENDING_REVIEW" && <button className="btn btn-ghost" onClick={() => onAct(`/api/v1/instructor/courses/${course.id}/withdraw`)}>Withdraw</button>}
        {editable && <button className="btn btn-danger" onClick={() => onAct(`/api/v1/instructor/courses/${course.id}`, "DELETE").then(() => onReload())}>Delete draft</button>}
        <button className="btn btn-ghost" onClick={assist}>Ask for a draft suggestion</button>
      </div>
      {suggestion && (
        <div className="notice">
          <strong>Suggestion, not applied</strong>
          <p>{suggestion.message}</p>
          <p>{suggestion.description}</p>
          <p className="muted">{suggestion.aiAvailable ? "Gemini wrote this. It will not replace your course until you apply it." : "Gemini was unavailable. This is a local outline, still not applied."}</p>
          {suggestion.sectionTitles && <p>Possible sections: {suggestion.sectionTitles.join(", ")}</p>}
          <button className="btn btn-primary" onClick={applySuggestion}>Apply this description</button>
        </div>
      )}
      <form onSubmit={addSection} style={{ display: "flex", gap: 8 }}>
        <input value={sectionTitle} onChange={(event) => setSectionTitle(event.target.value)} placeholder="New section title" required />
        <button className="btn btn-ghost" type="submit">Add section</button>
      </form>
      {course.sections.map((section) => (
        <div key={section.sectionId} className="card" style={{ padding: 12 }}>
          <strong>{section.title}</strong> {section.status !== "ACTIVE" && <span className="muted">removed</span>}
          {section.lessons.map((lesson) => (
            <div key={lesson.lessonId} style={{ display: "grid", gap: 6, padding: "8px 0", borderTop: "1px solid var(--line)" }}>
              <span>{lesson.title} · {lesson.videoStatus} · {lesson.access}</span>
              <input type="file" accept="video/mp4,video/webm" onChange={(event) => {
                const file = event.target.files?.[0];
                if (file) void upload(lesson.lessonId, file);
              }} />
            </div>
          ))}
        </div>
      ))}
      {course.sections.length > 0 && (
        <form onSubmit={addLesson} style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
          <select value={sectionId} onChange={(event) => setSectionId(event.target.value)}>
            {course.sections.map((section) => <option key={section.sectionId} value={section.sectionId}>{section.title}</option>)}
          </select>
          <input value={lessonTitle} onChange={(event) => setLessonTitle(event.target.value)} placeholder="New lesson title" required />
          <button className="btn btn-ghost" type="submit">Add lesson</button>
        </form>
      )}
    </div>
  );
}

function writeBody(course: CourseDetail, description: string, objectives?: string[]) {
  return {
    title: course.title,
    shortDescription: course.shortDescription,
    description,
    categoryId: course.categoryId,
    domain: course.domain,
    difficulty: course.difficulty,
    language: course.language,
    learningObjectives: objectives && objectives.length ? objectives : course.learningObjectives,
    prerequisites: course.prerequisites,
    targetAudience: course.targetAudience,
    tags: course.tags,
    skills: course.skills
  };
}

export function NewCoursePage() {
  const navigate = useNavigate();
  const toast = useToast();
  const [categories, setCategories] = useState<Category[]>([]);
  const [form, setForm] = useState(emptyCourse);
  useEffect(() => {
    api<Category[]>("/api/v1/categories").then((items) => {
      setCategories(items);
      if (items[0]) setForm((current) => ({ ...current, categoryId: items[0].id, domain: items[0].domain }));
    }).catch((err) => toast(apiErrorMessage(err), "bad"));
  }, [toast]);
  function set(key: keyof typeof form, value: string) {
    setForm((current) => ({ ...current, [key]: value }));
  }
  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    try {
      const created = await api<CourseDetail>("/api/v1/instructor/courses", {
        method: "POST",
        body: JSON.stringify({
          ...form,
          learningObjectives: form.learningObjectives.split("\n").map((item) => item.trim()).filter(Boolean),
          prerequisites: split(form.prerequisites),
          targetAudience: split(form.targetAudience),
          tags: split(form.tags),
          skills: split(form.skills)
        })
      });
      toast("Draft saved. It is not public.");
      navigate(`/teach/${created.id}`);
    } catch (err) {
      toast(apiErrorMessage(err), "bad");
    }
  }
  return (
    <form onSubmit={onSubmit} className="card" style={{ padding: 16, display: "grid", gap: 10 }}>
      <h1>New course</h1>
      <p className="muted">This stays a draft until you submit it and an admin approves it.</p>
      <label className="field"><span>Title</span><input value={form.title} onChange={(event) => set("title", event.target.value)} required /></label>
      <label className="field"><span>Short description</span><input value={form.shortDescription} onChange={(event) => set("shortDescription", event.target.value)} required /></label>
      <label className="field"><span>Full description</span><textarea value={form.description} onChange={(event) => set("description", event.target.value)} required /></label>
      <label className="field"><span>Category</span>
        <select value={form.categoryId} onChange={(event) => set("categoryId", event.target.value)} required>
          {categories.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}
        </select>
      </label>
      <label className="field"><span>Domain</span>
        <select value={form.domain} onChange={(event) => set("domain", event.target.value)}>
          <option value="TECHNICAL">Technical</option>
          <option value="NON_TECHNICAL">Non-technical</option>
        </select>
      </label>
      <label className="field"><span>Level</span>
        <select value={form.difficulty} onChange={(event) => set("difficulty", event.target.value)}>
          <option value="BEGINNER">Beginner</option>
          <option value="INTERMEDIATE">Intermediate</option>
          <option value="ADVANCED">Advanced</option>
        </select>
      </label>
      <label className="field"><span>Language</span><input value={form.language} onChange={(event) => set("language", event.target.value)} required /></label>
      <label className="field"><span>Objectives, one per line</span><textarea value={form.learningObjectives} onChange={(event) => set("learningObjectives", event.target.value)} required /></label>
      <label className="field"><span>Audience, comma separated</span><input value={form.targetAudience} onChange={(event) => set("targetAudience", event.target.value)} /></label>
      <label className="field"><span>Tags</span><input value={form.tags} onChange={(event) => set("tags", event.target.value)} /></label>
      <label className="field"><span>Skills</span><input value={form.skills} onChange={(event) => set("skills", event.target.value)} /></label>
      <button className="btn btn-primary" type="submit">Save draft</button>
    </form>
  );
}

function split(value: string) {
  return value.split(",").map((item) => item.trim()).filter(Boolean);
}

export function InsightsPage() {
  const [dash, setDash] = useState<Dash | null>(null);
  const [narrative, setNarrative] = useState<string | null>(null);
  useEffect(() => {
    api<Dash>("/api/v1/instructor/dashboard").then(setDash).catch(() => setDash(null));
    api<{ narrative: string; aiAvailable: boolean }>("/api/v1/ai/insights").then((data) => setNarrative(data.narrative)).catch(() => setNarrative(null));
  }, []);
  return (
    <div style={{ display: "grid", gap: 12 }}>
      <h1>Insights</h1>
      <p className="muted">Counts below come from saved courses and enrollments. They are not estimated.</p>
      {dash && <div className="card" style={{ padding: 14 }}>{dash.totalEnrollments} enrollments, {dash.completedEnrollments} completed, average {dash.averageProgress}% of lessons.</div>}
      {narrative && <div className="notice">{narrative}</div>}
    </div>
  );
}