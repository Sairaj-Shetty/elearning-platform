import { FormEvent, useEffect, useRef, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { api, apiErrorMessage, getAccessToken } from "../lib/api";
import { useAuth } from "../lib/auth";
import type { CourseCard, CourseDetail, Page, User } from "../lib/types";
import { CourseCardView, Empty, useToast } from "../components/ui";
import { formatWhen } from "../lib/format";

interface Enrollment {
  courseId: string;
  courseSlug: string;
  courseTitle: string;
  status: string;
  progressPercent: number;
  completedLessonCount: number;
  countableLessonCount: number;
  lastLessonId?: string | null;
  lastLessonTitle?: string | null;
  lastPositionSeconds?: number | null;
}

interface Dashboard {
  enrolledCount: number;
  completedCount: number;
  inProgressCount: number;
  averageProgress: number;
  continueLearning: Enrollment[];
  recentEvents: { id: string; eventType: string; courseTitle?: string; lessonTitle?: string; occurredAt: string }[];
}

interface Recommendation {
  course: CourseCard;
  score: number;
  reasons: string[];
  explanation?: string | null;
  source: string;
}

export function DashboardPage() {
  const [data, setData] = useState<Dashboard | null>(null);
  const [recommendations, setRecommendations] = useState<Recommendation[]>([]);
  const [certificates, setCertificates] = useState<{ id: string; studentName: string; courseTitle: string; completedAt: string }[]>([]);
  const [error, setError] = useState<string | null>(null);
  useEffect(() => {
    api<Dashboard>("/api/v1/me/dashboard").then(setData).catch((err) => setError(apiErrorMessage(err)));
    api<Recommendation[]>("/api/v1/ai/recommendations").then(setRecommendations).catch(() => setRecommendations([]));
    api<{ id: string; studentName: string; courseTitle: string; completedAt: string }[]>("/api/v1/me/certificates").then(setCertificates).catch(() => setCertificates([]));
  }, []);
  if (error) return <Empty title="Dashboard unavailable" text={error} />;
  if (!data) return <p>Loading saved learning…</p>;
  return (
    <div style={{ display: "grid", gap: 18 }}>
      <h1>Your learning</h1>
      <p className="muted">{data.enrolledCount} enrolled · {data.inProgressCount} in progress · {data.completedCount} completed · average {data.averageProgress}% of lessons. Percents come from saved completions, not from opening a lesson.</p>
      <section>
        <h2>Continue</h2>
        {data.continueLearning.length === 0 && <p className="muted">No enrollment yet. Browse the catalog and enroll.</p>}
        {data.continueLearning.map((item) => (
          <Link key={item.courseId} className="card" style={{ display: "block", padding: 14, marginBottom: 8 }} to={item.lastLessonId ? `/learn/${item.courseSlug}/lessons/${item.lastLessonId}` : `/courses/${item.courseSlug}`}>
            <strong>{item.lastLessonTitle || item.courseTitle}</strong>
            <div className="muted">{item.courseTitle} · {item.completedLessonCount} of {item.countableLessonCount} lessons · {item.progressPercent}%</div>
            <div className="progress" style={{ marginTop: 8 }}><span style={{ width: `${item.progressPercent}%` }} /></div>
          </Link>
        ))}
      </section>
      <section>
        <h2>Certificates</h2>
        {certificates.length === 0 && <p className="muted">A certificate is created when every countable lesson in a course is completed.</p>}
        {certificates.map((cert) => (
          <Link key={cert.id} className="card" style={{ display: "block", padding: 14, marginBottom: 8 }} to={`/certificates/${cert.id}`}>
            <strong>{cert.courseTitle}</strong>
            <div className="muted">{cert.studentName} · completed {new Date(cert.completedAt).toLocaleDateString()}</div>
          </Link>
        ))}
      </section>
      <section>
        <h2>Recent saved events</h2>
        {data.recentEvents.length === 0 && <p className="muted">Events appear after you enroll or record a lesson position.</p>}
        {data.recentEvents.map((item) => <p key={item.id}>{item.eventType} · {item.courseTitle} {item.lessonTitle} · {formatWhen(item.occurredAt)}</p>)}
      </section>
      <section>
        <h2>Suggested next</h2>
        {recommendations.length === 0 && <p className="muted">No suggestion yet. Add interests on your profile, or enroll in a course.</p>}
        <div className="grid-cards">
          {recommendations.map((item) => (
            <div key={item.course.id}>
              <CourseCardView course={item.course} />
              <p className="muted">{item.explanation || item.reasons.join(" ")} · {item.source.includes("GEMINI") ? "Gemini explanation of the rule score" : "Rule score only. Gemini was not used."}</p>
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}

export function HistoryPage() {
  const [items, setItems] = useState<{ id: string; eventType: string; courseTitle?: string; lessonTitle?: string; occurredAt: string }[]>([]);
  useEffect(() => {
    api<Page<typeof items[number]>>("/api/v1/me/history").then((page) => setItems(page.items)).catch(() => setItems([]));
  }, []);
  return (
    <div>
      <h1>Learning history</h1>
      <p className="muted">These rows are saved events. Opening a lesson records an open, not a completion.</p>
      <table className="table">
        <thead><tr><th>Event</th><th>Course</th><th>Lesson</th><th>Saved</th></tr></thead>
        <tbody>
          {items.map((item) => (
            <tr key={item.id}><td>{item.eventType}</td><td>{item.courseTitle || "—"}</td><td>{item.lessonTitle || "—"}</td><td>{formatWhen(item.occurredAt)}</td></tr>
          ))}
        </tbody>
      </table>
      {items.length === 0 && <p className="muted">History appears after an enrollment or a saved lesson position.</p>}
    </div>
  );
}

interface Roadmap {
  id: string;
  goal: string;
  source: string;
  stages: { index: number; title: string; description: string; concepts: string[]; courses: CourseCard[]; practice: string; milestone: string; completed: boolean }[];
}

export function RoadmapPage() {
  const toast = useToast();
  const [goal, setGoal] = useState("Prepare for a junior backend role");
  const [items, setItems] = useState<Roadmap[]>([]);
  useEffect(() => {
    api<Roadmap[]>("/api/v1/ai/roadmaps").then(setItems).catch(() => setItems([]));
  }, []);
  async function build(event: FormEvent) {
    event.preventDefault();
    try {
      const created = await api<Roadmap>("/api/v1/ai/roadmaps", { method: "POST", body: JSON.stringify({ goal }) });
      setItems((current) => [created, ...current]);
      toast("Roadmap saved to your account.");
    } catch (err) {
      toast(apiErrorMessage(err), "bad");
    }
  }
  async function complete(id: string, index: number) {
    try {
      const updated = await api<Roadmap>(`/api/v1/ai/roadmaps/${id}/stages/${index}/complete`, { method: "POST" });
      setItems((current) => current.map((item) => item.id === id ? updated : item));
    } catch (err) {
      toast(apiErrorMessage(err), "bad");
    }
  }
  return (
    <div style={{ display: "grid", gap: 12 }}>
      <h1>Roadmap</h1>
      <p className="muted">Stages link only to published courses. Creating a roadmap saves it. Gemini is optional; if it is down, the stages still come from the catalog.</p>
      <form onSubmit={build} style={{ display: "grid", gap: 8 }}>
        <label className="field"><span>Goal</span><input value={goal} onChange={(event) => setGoal(event.target.value)} /></label>
        <button className="btn btn-primary" type="submit">Build and save</button>
      </form>
      {items.map((roadmap) => (
        <div key={roadmap.id} className="card" style={{ padding: 16 }}>
          <h2>{roadmap.goal}</h2>
          <p className="muted">{roadmap.source === "GEMINI" ? "Gemini selected these published courses." : "Rule-based list of published courses. Gemini was not used."}</p>
          <ol>
            {roadmap.stages.map((stage) => (
              <li key={stage.index} style={{ marginBottom: 10 }}>
                <strong>{stage.title}</strong> {stage.completed ? "· marked done" : ""}
                <div>{stage.description}</div>
                <div className="muted">{stage.practice} Milestone: {stage.milestone}</div>
                {stage.courses.map((course) => <Link key={course.id} to={`/courses/${course.slug}`}>{course.title}. </Link>)}
                {!stage.completed && <button className="btn btn-ghost" onClick={() => complete(roadmap.id, stage.index)}>Mark stage done</button>}
              </li>
            ))}
          </ol>
        </div>
      ))}
    </div>
  );
}

export function AssistantPage() {
  return <StudyChat />;
}

function StudyChat({ courseId, lessonId }: { courseId?: string; lessonId?: string }) {
  const toast = useToast();
  const [message, setMessage] = useState("");
  const [reply, setReply] = useState<string | null>(null);
  const [available, setAvailable] = useState<boolean | null>(null);
  async function ask(event: FormEvent) {
    event.preventDefault();
    try {
      const data = await api<{ reply: string; aiAvailable: boolean; grounded: boolean }>("/api/v1/ai/assistant", {
        method: "POST",
        body: JSON.stringify({ courseId, lessonId, message })
      });
      setReply(data.reply);
      setAvailable(data.aiAvailable);
    } catch (err) {
      toast(apiErrorMessage(err), "bad");
    }
  }
  return (
    <form onSubmit={ask} style={{ display: "grid", gap: 8 }}>
      <h2>Study help</h2>
      <p className="muted">Answers use the lesson description and objectives stored with the course. They do not claim the assistant watched the video. Chats are kept for 30 days.</p>
      <label className="field"><span>Question</span><textarea value={message} onChange={(event) => setMessage(event.target.value)} required minLength={2} /></label>
      <button className="btn btn-primary" type="submit">Ask</button>
      {reply && <div className="card" style={{ padding: 14 }}><p>{reply}</p><p className="muted">{available ? "Gemini response, still limited to stored lesson text." : "Gemini was unavailable. This is a reminder from the stored lesson text."}</p></div>}
    </form>
  );
}

export function LearnPlayer() {
  const { slug, lessonId } = useParams();
  const videoRef = useRef<HTMLVideoElement>(null);
  const toast = useToast();
  const [course, setCourse] = useState<CourseDetail | null>(null);
  const [src, setSrc] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [note, setNote] = useState("");
  const [notes, setNotes] = useState<{ id: string; body: string; positionSeconds?: number | null }[]>([]);
  const [completedLessons, setCompletedLessons] = useState<Set<string>>(new Set());
  const [tab, setTab] = useState<"notes" | "help" | "quiz">("notes");
  const [quiz, setQuiz] = useState<{ id: string; title: string; questions: { questionId: string; prompt: string; type: string; options: string[] }[] } | null>(null);
  const [score, setScore] = useState<string | null>(null);

  useEffect(() => {
    if (!slug) return;
    api<CourseDetail>(`/api/v1/courses/${slug}`).then(setCourse).catch((err) => setError(apiErrorMessage(err)));
  }, [slug]);

  const lesson = course?.sections.flatMap((section) => section.lessons).find((item) => item.lessonId === lessonId);

  useEffect(() => {
    if (!course || !lesson) return;
    const direct = `/api/v1/media/videos/${course.id}/${lesson.lessonId}`;
    if (lesson.access === "FREE_PREVIEW" && !course.viewer.enrolled) {
      setSrc(direct);
      return;
    }
    api<{ url: string }>("/api/v1/media/tickets", { method: "POST", body: JSON.stringify({ courseId: course.id, lessonId: lesson.lessonId }) })
      .then((ticket) => setSrc(ticket.url))
      .catch((err) => setError(apiErrorMessage(err)));
    api<{ id: string; lessonId: string; body: string; positionSeconds?: number | null }[]>(`/api/v1/me/notes?courseId=${course.id}`)
      .then((rows) => setNotes(rows.filter((row) => row.lessonId === lesson.lessonId)))
      .catch(() => setNotes([]));
    if (course.viewer.enrolled) {
      api<{ lessonId: string; status: string }[]>(`/api/v1/me/progress?courseId=${course.id}`)
        .then((rows) => setCompletedLessons(new Set(rows.filter((row) => row.status === "COMPLETED").map((row) => row.lessonId))))
        .catch(() => setCompletedLessons(new Set()));
    }
  }, [course, lesson]);

  useEffect(() => {
    const node = videoRef.current;
    if (!node || !course || !lesson || !course.viewer.enrolled) return;
    const timer = window.setInterval(() => {
      if (!node.duration || document.hidden) return;
      api("/api/v1/me/progress", {
        method: "PUT",
        body: JSON.stringify({
          courseId: course.id,
          lessonId: lesson.lessonId,
          positionSeconds: node.currentTime,
          durationSeconds: node.duration
        })
      }).catch(() => undefined);
    }, 10000);
    return () => window.clearInterval(timer);
  }, [course, lesson]);

  async function complete() {
    if (!course || !lesson) return;
    try {
      await api("/api/v1/me/progress/complete", { method: "POST", body: JSON.stringify({ courseId: course.id, lessonId: lesson.lessonId }) });
      setCompletedLessons((current) => new Set(current).add(lesson.lessonId));
      toast("Lesson marked complete from watched time.");
    } catch (err) {
      toast(apiErrorMessage(err), "bad");
    }
  }
  async function saveNote(event: FormEvent) {
    event.preventDefault();
    if (!course || !lesson) return;
    try {
      await api("/api/v1/me/notes", { method: "POST", body: JSON.stringify({ courseId: course.id, lessonId: lesson.lessonId, type: "NOTE", body: note, positionSeconds: videoRef.current?.currentTime || 0 }) });
      setNote("");
      setNotes((await api<{ id: string; lessonId: string; body: string; positionSeconds?: number | null }[]>(`/api/v1/me/notes?courseId=${course.id}`)).filter((row) => row.lessonId === lesson.lessonId));
    } catch (err) {
      toast(apiErrorMessage(err), "bad");
    }
  }
  async function makeQuiz() {
    if (!course || !lesson) return;
    try {
      setQuiz(await api("/api/v1/ai/quizzes/generate", { method: "POST", body: JSON.stringify({ courseId: course.id, lessonId: lesson.lessonId, count: 4 }) }));
      setScore(null);
    } catch (err) {
      toast(apiErrorMessage(err), "bad");
    }
  }
  async function grade(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!quiz) return;
    const data = new FormData(event.currentTarget);
    const answers = quiz.questions.map((question, index) => {
      const raw = String(data.get(question.questionId) || "");
      if (question.type === "SHORT") return { questionId: question.questionId, textAnswer: raw };
      const selected = question.options.indexOf(raw);
      return { questionId: question.questionId, selectedIndex: selected < 0 ? index : selected };
    });
    try {
      const result = await api<{ totalScore: number; maxScore: number; percent?: number | null; includesApproximateScore: boolean; answers: { feedback?: string }[] }>(`/api/v1/quizzes/${quiz.id}/attempts`, { method: "POST", body: JSON.stringify({ answers }) });
      setScore(`${result.totalScore} of ${result.maxScore} points${result.percent == null ? "" : ` (${result.percent}%)`}. ${result.includesApproximateScore ? "Short answers are approximate. " : ""}${result.answers.map((item) => item.feedback || "").join(" ")}`);
    } catch (err) {
      toast(apiErrorMessage(err), "bad");
    }
  }

  if (error) return <Empty title="Lesson unavailable" text={error} />;
  if (!course || !lesson) return <p>Loading lesson…</p>;
  return (
    <div className="player">
      <aside className="card" style={{ padding: 12, alignSelf: "start" }}>
        <strong>{course.title}</strong>
        {course.sections.map((section) => (
          <div key={section.sectionId} style={{ marginTop: 10 }}>
            <div className="muted">{section.title}</div>
            {section.lessons.map((item) => (
              <Link key={item.lessonId} to={`/learn/${course.slug}/lessons/${item.lessonId}`} style={{ display: "block", padding: "6px 0", fontWeight: item.lessonId === lesson.lessonId ? 700 : 500 }}>
                {completedLessons.has(item.lessonId) ? "✓ " : ""}{item.title}
              </Link>
            ))}
          </div>
        ))}
      </aside>
      <div style={{ display: "grid", gap: 12 }}>
        <h1 style={{ margin: 0 }}>{lesson.title}</h1>
        {lesson.videoStatus !== "READY" || !src ? (
          <div className="notice">This lesson has no playable video yet. A placeholder is not shown as if it could play. {lesson.access === "ENROLLED_ONLY" && !course.viewer.enrolled ? "Enroll to request access." : ""}</div>
        ) : (
          <video ref={videoRef} key={src} controls preload="metadata" style={{ width: "100%", background: "#111", borderRadius: 16 }} src={withAuth(src)} />
        )}
        <p>{lesson.description}</p>
        {course.viewer.enrolled && <button className="btn btn-ghost" onClick={complete}>Mark complete if I watched enough</button>}
        <div style={{ display: "flex", gap: 8 }}>
          <button className="btn btn-ghost" onClick={() => setTab("notes")}>Notes</button>
          <button className="btn btn-ghost" onClick={() => setTab("help")}>Study help</button>
          <button className="btn btn-ghost" onClick={() => setTab("quiz")}>Quiz</button>
        </div>
        {tab === "notes" && (
          <div>
            <form onSubmit={saveNote} style={{ display: "grid", gap: 8 }}>
              <textarea className="field" value={note} onChange={(event) => setNote(event.target.value)} placeholder="Note at the current time" />
              <button className="btn btn-primary" type="submit">Save note</button>
            </form>
            {notes.map((item) => <p key={item.id}>{item.body} <span className="muted">{item.positionSeconds ?? 0}s</span></p>)}
          </div>
        )}
        {tab === "help" && <StudyChat courseId={course.id} lessonId={lesson.lessonId} />}
        {tab === "quiz" && (
          <div>
            <button className="btn btn-primary" onClick={makeQuiz}>Generate a quiz from this lesson</button>
            {quiz && (
              <form onSubmit={grade} style={{ display: "grid", gap: 10, marginTop: 12 }}>
                {quiz.questions.map((question) => (
                  <label key={question.questionId} className="field"><span>{question.prompt}</span>
                    {question.type === "SHORT" ? <input name={question.questionId} /> : (
                      <select name={question.questionId}>{question.options.map((option) => <option key={option}>{option}</option>)}</select>
                    )}
                  </label>
                ))}
                <button className="btn btn-ghost" type="submit">Score</button>
                {score && <p>{score}</p>}
              </form>
            )}
          </div>
        )}
      </div>
    </div>
  );
}

function withAuth(url: string) {
  const token = getAccessToken();
  if (!token || url.includes("ticket=")) return url;
  return url;
}

export function ProfilePage() {
  const { user, refreshUser } = useAuth();
  const toast = useToast();
  const [form, setForm] = useState<Partial<User>>({});
  const [certificates, setCertificates] = useState<{ id: string; courseTitle: string; completedAt: string }[]>([]);
  useEffect(() => { if (user) setForm(user); }, [user]);
  useEffect(() => { api<{ id: string; courseTitle: string; completedAt: string }[]>("/api/v1/me/certificates").then(setCertificates).catch(() => setCertificates([])); }, []);
  async function save(event: FormEvent) {
    event.preventDefault();
    try {
      await api("/api/v1/me", { method: "PUT", body: JSON.stringify({
        fullName: form.fullName, headline: form.headline, bio: form.bio,
        interests: split(form.interests), skills: split(form.skills), learningGoals: split(form.learningGoals)
      }) });
      await refreshUser();
      toast("Profile saved.");
    } catch (err) {
      toast(apiErrorMessage(err), "bad");
    }
  }
  return (
    <div>
    <form onSubmit={save} className="card" style={{ padding: 18, display: "grid", gap: 10 }}>
      <h1>Profile</h1>
      <label className="field"><span>Name</span><input value={form.fullName || ""} onChange={(event) => setForm({ ...form, fullName: event.target.value })} /></label>
      <label className="field"><span>Headline</span><input value={form.headline || ""} onChange={(event) => setForm({ ...form, headline: event.target.value })} /></label>
      <label className="field"><span>Bio</span><textarea value={form.bio || ""} onChange={(event) => setForm({ ...form, bio: event.target.value })} /></label>
      <label className="field"><span>Interests, comma separated</span><input value={(form.interests || []).join(", ")} onChange={(event) => setForm({ ...form, interests: event.target.value.split(",").map((item) => item.trim()).filter(Boolean) })} /></label>
      <label className="field"><span>Skills</span><input value={(form.skills || []).join(", ")} onChange={(event) => setForm({ ...form, skills: event.target.value.split(",").map((item) => item.trim()).filter(Boolean) })} /></label>
      <label className="field"><span>Goals</span><input value={(form.learningGoals || []).join(", ")} onChange={(event) => setForm({ ...form, learningGoals: event.target.value.split(",").map((item) => item.trim()).filter(Boolean) })} /></label>
      <button className="btn btn-primary" type="submit">Save</button>
    </form>
    <section style={{ marginTop: 16 }}>
      <h2>Certificates</h2>
      {certificates.length === 0 && <p className="muted">No certificates yet.</p>}
      {certificates.map((cert) => <p key={cert.id}><Link to={`/certificates/${cert.id}`}>{cert.courseTitle}</Link> · {new Date(cert.completedAt).toLocaleDateString()}</p>)}
    </section>
  </div>
  );
}

function split(value?: string[]) {
  return value || [];
}

export function NotificationsPage() {
  const [items, setItems] = useState<{ id: string; title: string; body: string; read: boolean; createdAt: string }[]>([]);
  async function load() {
    setItems(await api("/api/v1/me/notifications"));
  }
  useEffect(() => { load().catch(() => setItems([])); }, []);
  return (
    <div>
      <h1>Notifications</h1>
      {items.map((item) => (
        <button key={item.id} className="card" style={{ display: "block", width: "100%", textAlign: "left", padding: 12, marginBottom: 8 }} onClick={() => api(`/api/v1/me/notifications/${item.id}/read`, { method: "POST" }).then(load)}>
          <strong>{item.title}</strong>
          <div>{item.body}</div>
          <span className="muted">{item.read ? "Read" : "Unread"} · {formatWhen(item.createdAt)}</span>
        </button>
      ))}
    </div>
  );
}

export function CertificatePage() {
  const { id } = useParams();
  const [cert, setCert] = useState<{ id: string; studentName: string; courseTitle: string; completedAt: string; issuedAt: string } | null>(null);
  const [error, setError] = useState<string | null>(null);
  useEffect(() => {
    if (!id) return;
    api<typeof cert>(`/api/v1/me/certificates/${id}`).then(setCert).catch((err) => setError(apiErrorMessage(err)));
  }, [id]);
  if (error) return <Empty title="Certificate unavailable" text={error} />;
  if (!cert) return <p>Loading certificate…</p>;
  return (
    <div className="card" style={{ padding: 32, maxWidth: 720, margin: "0 auto" }}>
      <h1>Certificate of completion</h1>
      <p className="muted">Stored record {cert.id}</p>
      <p><strong>{cert.studentName}</strong> completed <strong>{cert.courseTitle}</strong>.</p>
      <p>Completion date: {new Date(cert.completedAt).toLocaleDateString()}</p>
      <p>Issued: {new Date(cert.issuedAt).toLocaleDateString()}</p>
      <button className="btn btn-ghost" onClick={() => window.print()}>Print / download</button>
    </div>
  );
}
