# Architecture

```text
Browser (React, Vite, Tailwind)
        |  REST /api/v1  +  refresh cookie
        v
Spring Boot 3.5  (JWT, RBAC, validation)
        |                         |
        | metadata                | video bytes
        v                         v
MongoDB 8 replica set         media volume
        |
        | optional text prompt, no passwords
        v
Gemini API
```

## Request path

1. The browser stores the access token in memory and the refresh token in an HttpOnly cookie.
2. Spring Security reads the bearer token and sets the role.
3. A controller calls a service. The service checks ownership and state before writing.
4. MongoDB stores the document. Video bytes go to `MEDIA_ROOT`.
5. Playback uses a short ticket or an enrollment check. A published free preview can play without enrollment.

## Course lifecycle

```text
DRAFT --> PENDING_REVIEW --> PUBLISHED --> ARCHIVED
  ^            |
  |            v
  +------- REJECTED
```

Students search only `PUBLISHED` courses that are not content-disabled. Archive and disable keep the enrollment and audit rows.

## AI boundary

```text
saved interests + enrollments --> rule score --> optional Gemini sentence
lesson description + objectives --> study reply or fallback text
published course ids only --> saved roadmap
objective quiz items --> deterministic score
short answer --> approximate score, or "not scored"
instructor suggestion --> shown, saved only after Apply
admin text review --> shown, never an automatic delete
```
