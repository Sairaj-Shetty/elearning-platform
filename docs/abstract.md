# Abstract

Lumina is a role-based e-learning platform for an academic database project. Students, instructors, and administrators share one MongoDB database. Access is checked in the Spring Boot API, not only in the React screens.

A course moves from draft to review to published, rejected, or archived. Sections and lessons live inside the course document. Video files live on a volume, and the document stores only metadata and a storage key. Progress is a separate collection. A lesson is complete only after enough watched time is saved, so opening a page cannot inflate a dashboard.

Personalization is rule-based. Gemini, when configured, explains a recommendation, drafts text, or scores a short answer. Those calls are optional. The platform still registers users, publishes courses, plays authorized video, and stores progress when the model is unavailable.

The design choice is a document database because a course outline is a nested document, while enrollments, progress, and audit rows are separate collections with their own indexes.
