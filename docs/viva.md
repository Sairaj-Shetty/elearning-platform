# Viva mapping

| Question | Where to show it |
| --- | --- |
| Why MongoDB instead of a relational database? | Course sections and lessons are nested. Enrollments and progress are separate collections. See `docs/architecture.md` and the schema section of the README. |
| How is role security enforced? | `SecurityConfig` matchers and service checks. A student calling `/api/v1/instructor/courses` gets 403. The integration test covers this. |
| Why is a draft hidden? | Catalog queries require `PUBLISHED`. The course page for a draft slug does not return the draft to a student. |
| How does video stay out of MongoDB? | `MediaService` writes the file, then saves `storageKey`, size, and `READY`. A failed save does not report success. |
| Why does seeking to the end not complete a lesson? | `WatchProgressPolicy` counts elapsed watch time, not the playhead alone. The unit test covers a single jump. |
| What survives a restart? | Mongo volume `mongo_data` and media volume `media_data`. Progress is a document, not browser memory. |
| What if Gemini is down? | `GeminiClient` returns a code such as `NOT_CONFIGURED`. Screens keep the rule-based or stored-text fallback. |
| What is not a machine-learning model? | Recommendations are scored in `RecommendationScorer`. Gemini only writes an explanation. |
| Can AI overwrite a course? | No. The instructor screen shows a suggestion and saves it only after Apply. |
| Can AI delete a course? | No. Admin text review returns a label. Approve, reject, archive, and disable are separate human actions. |
| Where are sample passwords? | README and the seed-only `/api/v1/auth/demo-accounts` response. They are not created when seeding is off. |
| What is the green video? | A W3C web-platform-tests playback fixture, not lesson content. |
