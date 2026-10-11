package com.lumina.media;

import com.lumina.api.Views.LessonView;
import com.lumina.api.Views.TicketView;
import com.lumina.common.ApiException;
import com.lumina.common.Errors;
import com.lumina.common.MagicBytes;
import com.lumina.config.AppProperties;
import com.lumina.course.CourseCardFactory;
import com.lumina.course.CourseFinder;
import com.lumina.domain.Course;
import com.lumina.domain.Enums.CourseStatus;
import com.lumina.domain.Enums.LessonAccess;
import com.lumina.domain.Enums.LessonStatus;
import com.lumina.domain.Enums.Role;
import com.lumina.domain.Enums.SectionStatus;
import com.lumina.domain.Enums.VideoStatus;
import com.lumina.domain.User;
import com.lumina.repo.CourseRepository;
import com.lumina.repo.EnrollmentRepository;
import com.lumina.repo.UserRepository;
import com.lumina.security.AuthUser;
import com.lumina.security.JwtService;
import io.jsonwebtoken.Claims;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
public class MediaService {
    private final MediaStorage storage;
    private final CourseRepository courses;
    private final UserRepository users;
    private final EnrollmentRepository enrollments;
    private final CourseCardFactory cards;
    private final JwtService jwt;
    private final AppProperties props;

    public MediaService(MediaStorage storage, CourseRepository courses, UserRepository users,
                        EnrollmentRepository enrollments, CourseCardFactory cards, JwtService jwt, AppProperties props) {
        this.storage = storage;
        this.courses = courses;
        this.users = users;
        this.enrollments = enrollments;
        this.cards = cards;
        this.jwt = jwt;
        this.props = props;
    }

    public LessonView uploadVideo(AuthUser actor, String courseId, String lessonId, MultipartFile file) {
        Course course = owned(actor, courseId);
        Course.Lesson lesson = CourseFinder.requireLesson(course, lessonId).lesson();
        if (lesson.getStatus() != LessonStatus.ACTIVE) {
            throw Errors.bad("INVALID_STATE", "This lesson has been removed.");
        }
        if (file == null || file.isEmpty()) {
            throw Errors.bad("BAD_UPLOAD", "Choose a video file.");
        }
        if (file.getSize() > props.media().maxVideoBytes()) {
            throw Errors.bad("BAD_UPLOAD", "Video files must be 500 MB or smaller.");
        }
        String extension = videoExtension(file.getOriginalFilename(), file.getContentType());
        Path temp = null;
        boolean markedUploading = false;
        String previousKey = lesson.getVideo() == null ? null : lesson.getVideo().getStorageKey();
        try {
            temp = Files.createTempFile("lumina-video-", extension);
            file.transferTo(temp);
            byte[] header = header(temp);
            boolean mp4 = MagicBytes.isMp4(header);
            boolean webm = MagicBytes.isWebm(header);
            if (!mp4 && !webm) {
                throw Errors.bad("BAD_UPLOAD", "Upload an MP4 or WebM video. The file content did not match.");
            }
            String key = "videos/" + courseId + "/" + lessonId + "/" + UUID.randomUUID() + (mp4 ? ".mp4" : ".webm");
            lesson.setVideo(lesson.getVideo() == null ? new Course.Video() : lesson.getVideo());
            lesson.getVideo().setStatus(VideoStatus.UPLOADING);
            courses.save(course);
            markedUploading = true;
            storage.store(key, temp);
            temp = null;
            lesson.getVideo().setStatus(VideoStatus.READY);
            lesson.getVideo().setStorageKey(key);
            lesson.getVideo().setOriginalFilename(safeName(file.getOriginalFilename()));
            lesson.getVideo().setMimeType(mp4 ? "video/mp4" : "video/webm");
            lesson.getVideo().setSizeBytes(file.getSize());
            lesson.getVideo().setUploadedAt(Instant.now());
            lesson.getVideo().setFailureReason(null);
            course.setUpdatedAt(Instant.now());
            courses.save(course);
            if (previousKey != null && !previousKey.equals(key)) {
                storage.delete(previousKey);
            }
            return cards.lesson(lesson);
        } catch (ApiException ex) {
            if (markedUploading) markFailed(course, lesson, ex.getMessage());
            throw ex;
        } catch (IOException ex) {
            if (markedUploading) markFailed(course, lesson, "The upload could not be stored.");
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_FAILED", "The upload could not be stored.");
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                    // temporary file is best-effort
                }
            }
        }
    }

    public LessonView deleteVideo(AuthUser actor, String courseId, String lessonId) {
        Course course = owned(actor, courseId);
        Course.Lesson lesson = CourseFinder.requireLesson(course, lessonId).lesson();
        if (lesson.getVideo() != null) {
            storage.delete(lesson.getVideo().getStorageKey());
            lesson.setVideo(new Course.Video());
            lesson.getVideo().setStatus(VideoStatus.REMOVED);
        }
        course.setUpdatedAt(Instant.now());
        courses.save(course);
        return cards.lesson(lesson);
    }

    public String uploadThumbnail(AuthUser actor, String courseId, MultipartFile file) {
        Course course = owned(actor, courseId);
        String key = storeImage(file, "images/thumbnails/" + courseId + "/" + UUID.randomUUID());
        String previous = course.getThumbnailKey();
        course.setThumbnailKey(key);
        course.setUpdatedAt(Instant.now());
        courses.save(course);
        if (previous != null) storage.delete(previous);
        return CourseCardFactory.thumbnail(key);
    }

    public String uploadAvatar(AuthUser actor, MultipartFile file) {
        User user = users.findById(actor.id()).orElseThrow(() -> Errors.notFound("User not found."));
        String key = storeImage(file, "images/avatars/" + user.getId() + "/" + UUID.randomUUID());
        String previous = user.getAvatarKey();
        user.setAvatarKey(key);
        user.setUpdatedAt(Instant.now());
        users.save(user);
        if (previous != null) storage.delete(previous);
        return "/api/v1/media/files/" + key;
    }

    public TicketView ticket(AuthUser actor, String courseId, String lessonId) {
        Course course = courses.findById(courseId).orElseThrow(() -> Errors.notFound("Course not found."));
        Course.Lesson lesson = CourseFinder.requireLesson(course, lessonId).lesson();
        assertCanWatch(course, lesson, actor, null);
        if (lesson.getVideo() == null || lesson.getVideo().getStatus() != VideoStatus.READY) {
            throw new ApiException(HttpStatus.NOT_FOUND, "VIDEO_NOT_READY", "This lesson does not have a playable video yet.");
        }
        String token = jwt.mediaTicket(actor.id(), courseId, lessonId);
        return new TicketView("/api/v1/media/videos/" + courseId + "/" + lessonId + "?ticket=" + token, 7200);
    }

    public Resource stream(String courseId, String lessonId, String ticket, AuthUser actor) {
        Course course = courses.findById(courseId).orElseThrow(() -> Errors.notFound("Course not found."));
        Course.Lesson lesson = CourseFinder.requireLesson(course, lessonId).lesson();
        assertCanWatch(course, lesson, actor, ticket);
        if (lesson.getVideo() == null || lesson.getVideo().getStatus() != VideoStatus.READY || lesson.getVideo().getStorageKey() == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "VIDEO_NOT_READY", "This lesson does not have a playable video yet.");
        }
        return storage.load(lesson.getVideo().getStorageKey());
    }

    public String contentType(String courseId, String lessonId) {
        Course course = courses.findById(courseId).orElseThrow(() -> Errors.notFound("Course not found."));
        Course.Lesson lesson = CourseFinder.requireLesson(course, lessonId).lesson();
        return lesson.getVideo() == null || lesson.getVideo().getMimeType() == null ? "video/mp4" : lesson.getVideo().getMimeType();
    }

    public Resource publicImage(String key) {
        if (key == null || !key.startsWith("images/") || key.contains("..")) {
            throw Errors.notFound("File not found.");
        }
        return storage.load(key);
    }

    private void assertCanWatch(Course course, Course.Lesson lesson, AuthUser actor, String ticket) {
        if (ticket != null && !ticket.isBlank()) {
            try {
                Claims claims = jwt.parse(ticket);
                if ("media".equals(claims.get("purpose", String.class))
                        && course.getId().equals(claims.get("cid", String.class))
                        && lesson.getLessonId().equals(claims.get("lid", String.class))) {
                    return;
                }
            } catch (Exception ignored) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "BAD_TICKET", "This video link expired. Reload the lesson.");
            }
        }
        boolean preview = lesson.getAccess() == LessonAccess.FREE_PREVIEW
                && lesson.getStatus() == LessonStatus.ACTIVE
                && course.getStatus() == CourseStatus.PUBLISHED
                && !course.isContentDisabled();
        if (preview && ticket == null) {
            return;
        }
        if (actor == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Log in to watch this lesson.");
        }
        if (actor.role() == Role.ADMIN || course.getInstructorId().equals(actor.id())) {
            return;
        }
        if (course.getStatus() != CourseStatus.PUBLISHED && course.getStatus() != CourseStatus.ARCHIVED) {
            throw Errors.forbidden("This course is not available.");
        }
        if (course.isContentDisabled()) {
            throw Errors.forbidden("This course is temporarily unavailable while it is reviewed.");
        }
        if (lesson.getStatus() != LessonStatus.ACTIVE) {
            throw Errors.notFound("Lesson not found.");
        }
        boolean enrolled = enrollments.findByStudentIdAndCourseId(actor.id(), course.getId()).isPresent();
        if (!enrolled) {
            throw Errors.forbidden("Enroll in this course to watch the lesson.");
        }
    }

    private Course owned(AuthUser actor, String courseId) {
        Course course = courses.findById(courseId).orElseThrow(() -> Errors.notFound("Course not found."));
        if (!course.getInstructorId().equals(actor.id())) {
            throw Errors.forbidden("You can edit only your own course.");
        }
        return course;
    }

    private String storeImage(MultipartFile file, String keyPrefix) {
        if (file == null || file.isEmpty()) throw Errors.bad("BAD_UPLOAD", "Choose an image.");
        if (file.getSize() > props.media().maxImageBytes()) throw Errors.bad("BAD_UPLOAD", "Images must be 5 MB or smaller.");
        Path temp = null;
        try {
            temp = Files.createTempFile("lumina-image-", ".bin");
            file.transferTo(temp);
            byte[] header = header(temp);
            String extension;
            if (MagicBytes.isJpeg(header)) extension = ".jpg";
            else if (MagicBytes.isPng(header)) extension = ".png";
            else if (MagicBytes.isWebp(header)) extension = ".webp";
            else throw Errors.bad("BAD_UPLOAD", "Upload a JPEG, PNG, or WebP image.");
            String key = keyPrefix + extension;
            storage.store(key, temp);
            temp = null;
            return key;
        } catch (ApiException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_FAILED", "The image could not be saved.");
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                    // ignore
                }
            }
        }
    }

    private void markFailed(Course course, Course.Lesson lesson, String reason) {
        try {
            if (lesson.getVideo() == null) lesson.setVideo(new Course.Video());
            lesson.getVideo().setStatus(VideoStatus.FAILED);
            lesson.getVideo().setFailureReason(reason);
            courses.save(course);
        } catch (Exception ignored) {
            // the original error is more useful
        }
    }

    private static byte[] header(Path path) throws IOException {
        byte[] header = new byte[32];
        try (InputStream in = Files.newInputStream(path)) {
            int read = in.read(header);
            if (read < header.length) {
                byte[] exact = new byte[Math.max(read, 0)];
                System.arraycopy(header, 0, exact, 0, exact.length);
                return exact;
            }
        }
        return header;
    }

    private static String videoExtension(String filename, String contentType) {
        String lower = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".webm") || (contentType != null && contentType.contains("webm"))) return ".webm";
        return ".mp4";
    }

    private static String safeName(String filename) {
        if (filename == null) return "video";
        return filename.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
