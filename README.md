# Lumina

Lumina is an academic e-learning platform for three roles: student, instructor, and admin. The React app talks only to a Spring Boot API. MongoDB is the only application database. Video files stay on a volume, not inside documents.

This is an original project. It does not use Udemy, Coursera, or Coloso names, logos, or layouts.

## What it does

- Register and log in with a role. The API rejects actions the role is not allowed to take. Hiding a button is not the security check.
- Instructors draft a course, add sections and lessons, upload an MP4 or WebM, and submit it. Students do not see it until an admin publishes it.
- Admins approve or reject with a reason, archive a course, or disable content without deleting enrollment history.
- Students enroll only in published courses, play a video they are allowed to watch, and keep progress in MongoDB. Opening a lesson does not mark it complete. Completion uses watched time.
- Recommendations start from saved interests and enrollments. Gemini can explain those reasons. If the key is missing or the call fails, the rule-based result still works.
- Study help uses the stored lesson text. It does not claim to have watched the video.
- Roadmaps are saved and only link to published courses.
- Quizzes score multiple choice on the server. Short answers are labeled approximate, or left unscored if Gemini is down.
- An instructor must apply an AI suggestion before it is saved. Admin text review never deletes a course and does not analyze video.

## Versions

These are pinned so a later upgrade does not silently change behavior.

| Piece | Version | Why |
| --- | --- | --- |
| Java | 21 | Current Spring Boot baseline |
| Spring Boot | 3.5.16 | Servlet API, Security, and MongoDB starter in one release train |
| springdoc | 2.8.17 | OpenAPI UI that matches Boot 3.5, not the Boot 4 line |
| jjwt | 0.12.6 | Access tokens and short-lived video tickets |
| MongoDB | 8.0 | Replica set, so approvals can use a transaction |
| Testcontainers | 1.21.4 | Integration test against a real MongoDB |
| Node | 22 | Frontend build in CI and Docker |
| React | 19 | UI |
| Vite | 6 | Dev server and production build |
| Tailwind | 4 | Styling |

Gemini is called as `gemini-3.5-flash-lite` through the backend only. Change `GEMINI_MODEL` if Google retires that name. A missing key does not stop the rest of the app.

## Run with Docker Compose

```bash
cp .env.example .env
docker compose up --build
```

Open http://localhost:5173. The API is at http://localhost:8080. Swagger UI is at http://localhost:8080/swagger-ui.html.

Compose starts MongoDB as a one-node replica set named `rs0` and stores data in the `mongo_data` volume. Video files go to the `media_data` volume. `docker compose restart` keeps both. `docker compose down -v` deletes them on purpose.

Seeding is on by default in Compose and off if you run the jar without `SEED_ENABLED=true`. The seed is idempotent: it will not reset a password that already exists. Do not enable seeding in production. The `prod` profile refuses to start when seeding is on or when `JWT_SECRET` is still the development default.

### Sample accounts

These exist only when seeding is on. The shared password is `SEED_PASSWORD` (default `LuminaDev#2026`).

| Role | Email |
| --- | --- |
| Admin | admin@lumina.local |
| Instructor | meera@lumina.local, arjun@lumina.local, elena@lumina.local, kabir@lumina.local |
| Student | arya@lumina.local, rohan@lumina.local, nina@lumina.local, dev@lumina.local, sam@lumina.local, leela@lumina.local |

The sign-in page lists a shorter set when seeding is on. Sample courses are labeled Sample. Most seeded lessons have no video. Two courses, Java and Spring Boot and Python Programming, attach a tiny playback fixture so a student can try the player. That file is `backend/src/main/resources/sample/sample-lesson.mp4`, taken from the W3C web-platform-tests file `media/2x2-green.mp4`. It is a playback fixture, not course content.

## Local frontend against a local API

```bash
cd frontend
npm ci
npm run dev
```

Vite proxies `/api` to http://localhost:8080. The backend still needs Java 21, Maven, and MongoDB.

## Configuration

Secrets come from environment variables. See `.env.example`. Do not commit `.env`, a Gemini key, or a real JWT secret. The browser never receives the Gemini key.

`COOKIE_SECURE=false` is for local HTTP. Set it to `true` behind HTTPS so the refresh cookie is not sent on plain HTTP.

## Data model

MongoDB collections:

- `users` — role, profile, password hash. The hash is never returned.
- `categories` — technical and non-technical catalog tree.
- `courses` — status is `DRAFT`, `PENDING_REVIEW`, `PUBLISHED`, `REJECTED`, or `ARCHIVED`. Sections and lessons are embedded. Video metadata points at a storage key. The binary is not in the document.
- `enrollments` — one row per student and course, with completed-lesson counts.
- `lessonProgress` — position and accumulated watch time. Completion is not the same as opening the lesson.
- `learningEvents` — history that survives refresh.
- `notes`, `reviews`, `reports`, `notifications`, `auditLogs`
- `quizzes`, `quizAttempts`
- `roadmaps`
- `aiSessions`, `aiInteractions` — AI chats expire after 30 days. They do not store passwords or tokens.
- `refreshTokens` — hashed refresh tokens with a TTL.

Course status changes that must stay consistent run in a MongoDB transaction when the replica set is available.

## Tests

```bash
cd backend && mvn -B test
cd frontend && npm ci && npm test && npm run build
```

GitHub Actions runs those commands. The Java integration test uses Testcontainers and covers role denial, catalog hiding, duplicate enrollment, a video upload, a seek that does not complete the lesson, and the assistant fallback when Gemini is not configured. Unit tests cover the watch-time rule and the sample MP4 header.

## Limitations

- Payments are not included. Enrollment still controls access, and a lesson can be marked as a free preview.
- Gemini is a language model, not a trained recommender. Recommendations are scored with rules.
- Short-answer quiz scores are approximate and are skipped when Gemini is down.
- The admin AI check reads text only. It does not watch a video and it does not delete anything.
- The sample MP4 is a few seconds of green video. It is only there so the player and progress rule can be demonstrated.
- A production host still needs HTTPS, a private `JWT_SECRET`, `SEED_ENABLED=false`, and the `prod` profile.
