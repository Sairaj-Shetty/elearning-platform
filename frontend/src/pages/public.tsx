import { FormEvent, useEffect, useState } from "react";
import { Link, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { api, apiErrorMessage } from "../lib/api";
import { useAuth } from "../lib/auth";
import type { Category, CourseCard, CourseDetail, Page } from "../lib/types";
import { CourseCardView, Empty, useToast } from "../components/ui";
import { difficultyLabel, formatDuration } from "../lib/format";

export function HomePage() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [q, setQ] = useState("");
  const [categoryId, setCategoryId] = useState("");
  const [difficulty, setDifficulty] = useState("");
  const [domain, setDomain] = useState("");
  const [categories, setCategories] = useState<Category[]>([]);
  const [newest, setNewest] = useState<CourseCard[]>([]);
  const [trending, setTrending] = useState<CourseCard[]>([]);
  const [bestFit, setBestFit] = useState<{ course: CourseCard; score: number; explanation?: string | null; source: string }[]>([]);
  const [index, setIndex] = useState(0);
  const [paused, setPaused] = useState(false);

  useEffect(() => {
    api<Category[]>("/api/v1/categories").then(setCategories).catch(() => setCategories([]));
    api<Page<CourseCard>>("/api/v1/courses?sort=newest&size=8").then((page) => setNewest(page.items)).catch(() => setNewest([]));
    api<Page<CourseCard>>("/api/v1/courses?sort=popular&size=6").then((page) => setTrending(page.items)).catch(() => setTrending([]));
  }, []);

  useEffect(() => {
    if (!user) return;
    api<{ course: CourseCard; score: number; reasons: string[]; explanation?: string | null; source: string }[]>("/api/v1/ai/recommendations")
      .then((items) => setBestFit(items.map((item) => ({ course: item.course, score: item.score, explanation: item.explanation, source: item.source }))))
      .catch(() => setBestFit([]));
  }, [user]);

  useEffect(() => {
    if (paused || newest.length <= 1) return;
    const timer = window.setInterval(() => setIndex((current) => (current + 1) % newest.length), 3000);
    return () => window.clearInterval(timer);
  }, [paused, newest.length]);

  function search(event: FormEvent) {
    event.preventDefault();
    const params = new URLSearchParams();
    if (q) params.set("q", q);
    if (categoryId) params.set("categoryId", categoryId);
    if (difficulty) params.set("difficulty", difficulty);
    if (domain) params.set("domain", domain);
    navigate(`/courses?${params.toString()}`);
  }

  const current = newest[index];

  return (
    <div style={{ display: "grid", gap: 28 }}>
      <section className="card" style={{ padding: 32, display: "grid", gap: 12 }}>
        <p className="badge" style={{ width: "fit-content" }}>Academic project · real progress, real roles</p>
        <h1 style={{ fontSize: 56, margin: 0, maxWidth: 720 }}>A quieter place to learn, teach, and keep a record of the work.</h1>
        <p className="muted" style={{ maxWidth: 640, fontSize: 18 }}>
          Lumina keeps courses, video access, and lesson progress in MongoDB. Recommendations explain themselves from your saved interests and enrollments.
        </p>
        <form onSubmit={search} className="filters">
          <label className="field"><span>Search</span><input value={q} onChange={(event) => setQ(event.target.value)} placeholder="Title, skill, or topic" /></label>
          <label className="field"><span>Category</span>
            <select value={categoryId} onChange={(event) => setCategoryId(event.target.value)}>
              <option value="">All</option>
              {categories.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}
            </select>
          </label>
          <label className="field"><span>Level</span>
            <select value={difficulty} onChange={(event) => setDifficulty(event.target.value)}>
              <option value="">Any</option>
              <option value="BEGINNER">Beginner</option>
              <option value="INTERMEDIATE">Intermediate</option>
              <option value="ADVANCED">Advanced</option>
            </select>
          </label>
          <label className="field"><span>Field</span>
            <select value={domain} onChange={(event) => setDomain(event.target.value)}>
              <option value="">All</option>
              <option value="TECHNICAL">Technical</option>
              <option value="NON_TECHNICAL">Non-technical</option>
            </select>
          </label>
          <button className="btn btn-primary" type="submit">Search published courses</button>
        </form>
      </section>

      <section>
        <h2>New courses</h2>
        {newest.length === 0 ? <Empty title="No published courses yet" text="An instructor can draft a course, and an admin has to approve it before it appears here." /> : (
          <div className="card" style={{ padding: 24, display: "grid", gap: 12 }}>
            {current && <CourseCardView course={current} />}
            <div style={{ display: "flex", gap: 8, alignItems: "center", justifyContent: "center" }}>
              <button className="btn btn-ghost" onClick={() => setIndex((current) => (current - 1 + newest.length) % newest.length)}>Previous</button>
              {newest.map((_, dot) => (
                <button key={dot} className="btn btn-ghost" style={{ padding: "4px 8px", fontWeight: dot === index ? 700 : 400 }} onClick={() => setIndex(dot)}>{dot + 1}</button>
              ))}
              <button className="btn btn-ghost" onClick={() => setIndex((current) => (current + 1) % newest.length)}>Next</button>
              <button className="btn btn-ghost" onClick={() => setPaused((value) => !value)}>{paused ? "Resume" : "Pause"}</button>
            </div>
          </div>
        )}
      </section>

      <section>
        <h2>Trending</h2>
        {trending.length === 0 || trending.every((course) => course.enrollmentCount === 0) ? (
          <p className="muted">No course has enrollments yet, so there is no trending ranking.</p>
        ) : (
          <div className="grid-cards">{trending.map((course) => <CourseCardView key={course.id} course={course} />)}</div>
        )}
      </section>

      <section>
        <h2>Best fit for you</h2>
        {!user && <p className="muted">Sign in to see recommendations from your saved interests and enrollments.</p>}
        {user && bestFit.length === 0 && <p className="muted">No suggestion yet. Add interests on your profile, or enroll in a course.</p>}
        <div className="grid-cards">
          {bestFit.map((item) => (
            <div key={item.course.id}>
              <CourseCardView course={item.course} />
              <p className="muted">{item.explanation || "Rule score only"} · {item.source.includes("GEMINI") ? "Gemini explanation of the rule score" : "Rule score only. Gemini was not used."}</p>
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}

export function CatalogPage() {
  const [params, setParams] = useSearchParams();
  const [page, setPage] = useState<Page<CourseCard> | null>(null);
  const [categories, setCategories] = useState<Category[]>([]);
  const [error, setError] = useState<string | null>(null);
  const q = params.get("q") || "";
  const categoryId = params.get("categoryId") || "";
  const difficulty = params.get("difficulty") || "";
  const pageNo = Number(params.get("page") || 0);
  useEffect(() => {
    api<Category[]>("/api/v1/categories").then(setCategories).catch(() => undefined);
  }, []);
  useEffect(() => {
    const query = new URLSearchParams(params);
    if (!query.has("size")) query.set("size", "12");
    api<Page<CourseCard>>(`/api/v1/courses?${query.toString()}`)
      .then((data) => { setPage(data); setError(null); })
      .catch((err) => setError(apiErrorMessage(err)));
  }, [params]);
  function update(key: string, value: string) {
    const next = new URLSearchParams(params);
    if (value) next.set(key, value); else next.delete(key);
    if (key !== "page") next.delete("page");
    setParams(next);
  }
  return (
    <div style={{ display: "grid", gap: 16 }}>
      <h1 style={{ marginBottom: 0 }}>Catalog</h1>
      <p className="muted">Only published courses appear here. Drafts and courses waiting for review stay private.</p>
      <div className="filters">
        <label className="field"><span>Search</span><input defaultValue={q} onBlur={(event) => update("q", event.target.value)} placeholder="Title, skill, or topic" /></label>
        <label className="field"><span>Category</span>
          <select value={categoryId} onChange={(event) => update("categoryId", event.target.value)}>
            <option value="">All</option>
            {categories.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}
          </select>
        </label>
        <label className="field"><span>Level</span>
          <select value={difficulty} onChange={(event) => update("difficulty", event.target.value)}>
            <option value="">Any</option>
            <option value="BEGINNER">Beginner</option>
            <option value="INTERMEDIATE">Intermediate</option>
            <option value="ADVANCED">Advanced</option>
          </select>
        </label>
      </div>
      {error && <p className="error">{error}</p>}
      {page && page.items.length === 0 && <Empty title="Nothing matched" text="Try another word, or clear the filters." />}
      <div className="grid-cards">{page?.items.map((course) => <CourseCardView key={course.id} course={course} />)}</div>
      {page && page.totalPages > 1 && (
        <div style={{ display: "flex", gap: 8 }}>
          <button className="btn btn-ghost" disabled={pageNo <= 0} onClick={() => update("page", String(pageNo - 1))}>Previous</button>
          <span className="muted">Page {page.page + 1} of {page.totalPages}</span>
          <button className="btn btn-ghost" disabled={pageNo + 1 >= page.totalPages} onClick={() => update("page", String(pageNo + 1))}>Next</button>
        </div>
      )}
    </div>
  );
}

export function CoursePage() {
  const { slug } = useParams();
  const { user } = useAuth();
  const navigate = useNavigate();
  const toast = useToast();
  const [course, setCourse] = useState<CourseDetail | null>(null);
  const [reviews, setReviews] = useState<{ id: string; studentName: string; rating: number; comment?: string; seedData: boolean }[]>([]);
  const [completedLessons, setCompletedLessons] = useState<Set<string>>(new Set());
  const [error, setError] = useState<string | null>(null);
  useEffect(() => {
    if (!slug) return;
    api<CourseDetail>(`/api/v1/courses/${slug}`)
      .then(async (data) => {
        setCourse(data);
        setReviews(await api(`/api/v1/courses/${data.id}/reviews`));
        if (data.viewer?.enrolled) {
          api<{ lessonId: string; status: string }[]>(`/api/v1/me/progress?courseId=${data.id}`)
            .then((rows) => setCompletedLessons(new Set(rows.filter((row) => row.status === "COMPLETED").map((row) => row.lessonId))))
            .catch(() => setCompletedLessons(new Set()));
        }
      })
      .catch((err) => setError(apiErrorMessage(err)));
  }, [slug]);
  async function enroll() {
    if (!course) return;
    if (!user) return navigate("/login");
    try {
      await api("/api/v1/enrollments", { method: "POST", body: JSON.stringify({ courseId: course.id }) });
      toast("Enrollment saved.");
      const fresh = await api<CourseDetail>(`/api/v1/courses/${slug}`);
      setCourse(fresh);
    } catch (err) {
      toast(apiErrorMessage(err), "bad");
    }
  }
  if (error) return <Empty title="Course unavailable" text={error} />;
  if (!course) return <p>Loading course…</p>;
  const first = course.sections.flatMap((section) => section.lessons)[0];
  return (
    <div style={{ display: "grid", gap: 18 }}>
      <div className="card" style={{ padding: 22, display: "grid", gap: 10 }}>
        <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
          <span className="badge">{course.categoryName}</span>
          <span className="badge">{difficultyLabel(course.difficulty)}</span>
          {course.seedData && <span className="badge">Sample course</span>}
        </div>
        <h1 style={{ margin: 0 }}>{course.title}</h1>
        <p>{course.shortDescription}</p>
        <p className="muted">{course.instructorName} · {formatDuration(course.estimatedDurationMinutes)} · {course.lessonCount} lessons</p>
        {course.contentDisabled && <div className="notice">This course is temporarily unavailable. {course.disabledReason || "An admin disabled new access."}</div>}
        <div style={{ display: "flex", gap: 8 }}>
          {course.viewer.enrolled ? (
            <Link className="btn btn-primary" to={`/learn/${course.slug}/lessons/${course.viewer.lastLessonId || first?.lessonId || ""}`}>Continue</Link>
          ) : (
            <button className="btn btn-primary" disabled={course.contentDisabled || user?.role !== "STUDENT" && !!user} onClick={enroll}>
              {user && user.role !== "STUDENT" ? "Students enroll" : "Enroll"}
            </button>
          )}
          <Link className="btn btn-ghost" to={`/instructors/${course.instructor.id}`}>Instructor</Link>
        </div>
        {course.viewer.enrolled && (
          <p className="muted">{course.viewer.completedLessonCount} of {course.viewer.countableLessonCount} lessons completed · {course.viewer.progressPercent ?? 0}%</p>
        )}
      </div>
      <div className="card" style={{ padding: 22 }}>
        <h2>About</h2>
        <p style={{ whiteSpace: "pre-wrap" }}>{course.description}</p>
        <h3>You will practice</h3>
        <ul>{course.learningObjectives.map((item) => <li key={item}>{item}</li>)}</ul>
      </div>
      <div className="card" style={{ padding: 22 }}>
        <h2>Curriculum</h2>
        {course.sections.map((section) => (
          <div key={section.sectionId} style={{ marginBottom: 16 }}>
            <strong>{section.title}</strong>
            {section.lessons.map((lesson) => (
              <div key={lesson.lessonId} style={{ display: "flex", justifyContent: "space-between", gap: 12, padding: "8px 0", borderBottom: "1px solid var(--line)" }}>
                <span>{completedLessons.has(lesson.lessonId) ? "✓ " : ""}{lesson.title}</span>
                <span className="muted">{lesson.access === "FREE_PREVIEW" ? "Preview" : "Enrolled"} · {lesson.videoStatus === "READY" ? "Video ready" : "No playable video"}</span>
              </div>
            ))}
          </div>
        ))}
      </div>
      <div className="card" style={{ padding: 22 }}>
        <h2>Reviews</h2>
        {reviews.length === 0 && <p className="muted">No reviews yet.</p>}
        {reviews.map((review) => (
          <p key={review.id}><strong>{review.studentName}</strong> · {review.rating}/5 {review.seedData ? "· sample review" : ""}<br />{review.comment}</p>
        ))}
        {course.viewer.enrolled && <ReviewForm courseId={course.id} onSaved={async () => setReviews(await api(`/api/v1/courses/${course.id}/reviews`))} />}
      </div>
    </div>
  );
}

export function InstructorPage() {
  const { id } = useParams();
  const [profile, setProfile] = useState<{ fullName: string; headline?: string; bio?: string; qualifications: string[]; courses: CourseCard[] } | null>(null);
  useEffect(() => {
    if (id) api<typeof profile>(`/api/v1/instructors/${id}`).then(setProfile).catch(() => setProfile(null));
  }, [id]);
  if (!profile) return <Empty title="Instructor not found" text="This public profile is only available for instructor accounts." />;
  return (
    <div style={{ display: "grid", gap: 16 }}>
      <div className="card" style={{ padding: 22 }}>
        <h1>{profile.fullName}</h1>
        <p>{profile.headline}</p>
        <p className="muted">{profile.bio}</p>
        <ul>{profile.qualifications.map((item) => <li key={item}>{item}</li>)}</ul>
      </div>
      <div className="grid-cards">{profile.courses.map((course) => <CourseCardView key={course.id} course={course} />)}</div>
    </div>
  );
}

export function LoginPage() {
  const { login, user } = useAuth();
  const navigate = useNavigate();
  const toast = useToast();
  const [demos, setDemos] = useState<{ role: string; email: string; password: string; name: string; note: string }[]>([]);
  useEffect(() => {
    api<typeof demos>("/api/v1/auth/demo-accounts").then(setDemos).catch(() => setDemos([]));
  }, []);
  if (user) return <Link to={user.role === "ADMIN" ? "/admin" : user.role === "INSTRUCTOR" ? "/teach" : "/learn"}>Continue to your workspace</Link>;
  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    try {
      const signedIn = await login(String(data.get("email")), String(data.get("password")));
      navigate(signedIn.role === "ADMIN" ? "/admin" : signedIn.role === "INSTRUCTOR" ? "/teach" : "/learn");
    } catch (err) {
      toast(apiErrorMessage(err), "bad");
    }
  }
  return (
    <form className="card" style={{ padding: 22, display: "grid", gap: 12, maxWidth: 460 }} onSubmit={onSubmit}>
      <h1>Sign in</h1>
      <label className="field"><span>Email</span><input name="email" type="email" required /></label>
      <label className="field"><span>Password</span><input name="password" type="password" required minLength={8} /></label>
      <button className="btn btn-primary" type="submit">Sign in</button>
      {demos.length > 0 && (
        <div className="notice">
          <strong>Sample accounts</strong>
          <p>{demos[0]?.note} Shared sample password: {demos[0]?.password}. These are not real user secrets.</p>
          {demos.map((demo) => <div key={demo.email}>{demo.role}: {demo.name} · {demo.email}</div>)}
        </div>
      )}
    </form>
  );
}

function ReviewForm({ courseId, onSaved }: { courseId: string; onSaved: () => Promise<void> }) {
  const toast = useToast();
  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    try {
      await api(`/api/v1/courses/${courseId}/reviews`, {
        method: "POST",
        body: JSON.stringify({ rating: Number(data.get("rating")), comment: data.get("comment") })
      });
      toast("Review saved.");
      await onSaved();
    } catch (err) {
      toast(apiErrorMessage(err), "bad");
    }
  }
  return (
    <form onSubmit={onSubmit} style={{ display: "grid", gap: 8, marginTop: 12 }}>
      <label className="field"><span>Your rating</span>
        <select name="rating" defaultValue="5"><option>5</option><option>4</option><option>3</option><option>2</option><option>1</option></select>
      </label>
      <label className="field"><span>Comment</span><textarea name="comment" /></label>
      <button className="btn btn-ghost" type="submit">Save review</button>
    </form>
  );
}

export function RegisterPage() {
  const { register } = useAuth();
  const navigate = useNavigate();
  const toast = useToast();
  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    try {
      const user = await register({
        fullName: data.get("fullName"),
        email: data.get("email"),
        password: data.get("password"),
        role: data.get("role")
      });
      navigate(user.role === "INSTRUCTOR" ? "/teach" : "/learn");
    } catch (err) {
      toast(apiErrorMessage(err), "bad");
    }
  }
  return (
    <form className="card" style={{ padding: 22, display: "grid", gap: 12, maxWidth: 520 }} onSubmit={onSubmit}>
      <h1>Create an account</h1>
      <p className="muted">Admin accounts are not created here. A seeded or existing admin manages roles.</p>
      <label className="field"><span>Name</span><input name="fullName" required minLength={2} /></label>
      <label className="field"><span>Email</span><input name="email" type="email" required /></label>
      <label className="field"><span>Password</span><input name="password" type="password" required minLength={8} /></label>
      <label className="field"><span>I am joining as</span>
        <select name="role" defaultValue="STUDENT">
          <option value="STUDENT">Student</option>
          <option value="INSTRUCTOR">Instructor</option>
        </select>
      </label>
      <button className="btn btn-primary" type="submit">Create account</button>
    </form>
  );
}
