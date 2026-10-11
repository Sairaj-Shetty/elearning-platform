package com.lumina.catalog;

import com.lumina.api.Requests.CategoryRequest;
import com.lumina.api.Views.CategoryView;
import com.lumina.api.Views.CourseCard;
import com.lumina.api.Views.CourseDetail;
import com.lumina.common.Errors;
import com.lumina.common.Pages;
import com.lumina.common.Pages.PageData;
import com.lumina.common.Slugger;
import com.lumina.course.CourseCardFactory;
import com.lumina.domain.Category;
import com.lumina.domain.Course;
import com.lumina.domain.Enrollment;
import com.lumina.domain.Enums.CourseStatus;
import com.lumina.domain.Enums.Difficulty;
import com.lumina.domain.Enums.Domain;
import com.lumina.domain.Enums.Role;
import com.lumina.domain.LearningEvent;
import com.lumina.domain.Enums.EventType;
import com.lumina.domain.User;
import com.lumina.repo.CategoryRepository;
import com.lumina.repo.CourseRepository;
import com.lumina.repo.EnrollmentRepository;
import com.lumina.repo.LearningEventRepository;
import com.lumina.repo.UserRepository;
import com.lumina.security.AuthUser;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class CatalogService {
    private final CategoryRepository categories;
    private final CourseRepository courses;
    private final EnrollmentRepository enrollments;
    private final UserRepository users;
    private final LearningEventRepository events;
    private final CourseCardFactory cards;
    private final MongoTemplate mongo;

    public CatalogService(CategoryRepository categories, CourseRepository courses, EnrollmentRepository enrollments,
                          UserRepository users, LearningEventRepository events, CourseCardFactory cards, MongoTemplate mongo) {
        this.categories = categories;
        this.courses = courses;
        this.enrollments = enrollments;
        this.users = users;
        this.events = events;
        this.cards = cards;
        this.mongo = mongo;
    }

    public List<CategoryView> publicCategories() {
        return categories.findByActiveTrueOrderByOrderAscNameAsc().stream().map(this::toView).toList();
    }

    public List<CategoryView> adminCategories() {
        return categories.findAll().stream()
                .sorted(Comparator.comparing(Category::getOrder).thenComparing(Category::getName))
                .map(this::toView)
                .toList();
    }

    public CategoryView createCategory(CategoryRequest request) {
        Category category = new Category();
        applyCategory(category, request, null);
        categories.save(category);
        return toView(category);
    }

    public CategoryView updateCategory(String id, CategoryRequest request) {
        Category category = categories.findById(id).orElseThrow(() -> Errors.notFound("Category not found."));
        applyCategory(category, request, category.getId());
        categories.save(category);
        return toView(category);
    }

    public CategoryView deactivate(String id) {
        Category category = categories.findById(id).orElseThrow(() -> Errors.notFound("Category not found."));
        category.setActive(false);
        categories.save(category);
        return toView(category);
    }

    public PageData<CourseCard> search(String q, String domain, String categoryId, String difficulty, String language,
                                       String sort, Integer page, Integer size, AuthUser actor) {
        int pageNumber = Pages.page(page);
        int pageSize = Pages.size(size, 12);
        List<Criteria> parts = new ArrayList<>();
        parts.add(Criteria.where("status").is(CourseStatus.PUBLISHED));
        parts.add(Criteria.where("contentDisabled").is(false));
        if (domain != null && !domain.isBlank()) parts.add(Criteria.where("domain").is(Domain.valueOf(domain)));
        if (difficulty != null && !difficulty.isBlank()) parts.add(Criteria.where("difficulty").is(Difficulty.valueOf(difficulty)));
        if (language != null && !language.isBlank()) parts.add(Criteria.where("language").regex("^" + Pattern.quote(language.trim()) + "$", "i"));
        if (categoryId != null && !categoryId.isBlank()) {
            parts.add(new Criteria().orOperator(
                    Criteria.where("categoryId").is(categoryId),
                    Criteria.where("subcategoryId").is(categoryId)
            ));
        }
        if (q != null && !q.isBlank()) {
            String safe = Pattern.quote(q.trim());
            parts.add(new Criteria().orOperator(
                    Criteria.where("title").regex(safe, "i"),
                    Criteria.where("shortDescription").regex(safe, "i"),
                    Criteria.where("tags").regex(safe, "i"),
                    Criteria.where("skills").regex(safe, "i"),
                    Criteria.where("instructorName").regex(safe, "i")
            ));
            if (actor != null && actor.role() == Role.STUDENT) {
                LearningEvent event = new LearningEvent();
                event.setStudentId(actor.id());
                event.setEventType(EventType.SEARCHED);
                event.setOccurredAt(Instant.now());
                event.getPayload().put("query", q.trim());
                events.save(event);
            }
        }
        Query query = new Query(new Criteria().andOperator(parts));
        long total = mongo.count(query, Course.class);
        query.with(sort(sort)).skip((long) pageNumber * pageSize).limit(pageSize);
        List<CourseCard> items = mongo.find(query, Course.class).stream()
                .map(course -> cards.card(course, enrollment(actor, course.getId())))
                .toList();
        return PageData.of(items, pageNumber, pageSize, total);
    }

    public CourseDetail bySlug(String slug, AuthUser actor) {
        Course course = courses.findBySlug(slug).orElseThrow(() -> Errors.notFound("Course not found."));
        boolean owner = actor != null && course.getInstructorId().equals(actor.id());
        boolean admin = actor != null && actor.role() == Role.ADMIN;
        if (!owner && !admin) {
            if (course.getStatus() != CourseStatus.PUBLISHED || course.isContentDisabled()) {
                Enrollment existing = actor == null ? null : enrollments.findByStudentIdAndCourseId(actor.id(), course.getId()).orElse(null);
                if (existing == null || course.getStatus() == CourseStatus.DRAFT || course.getStatus() == CourseStatus.PENDING_REVIEW || course.getStatus() == CourseStatus.REJECTED) {
                    throw Errors.notFound("Course not found.");
                }
            }
        }
        User instructor = users.findById(course.getInstructorId()).orElse(null);
        Enrollment enrollment = enrollment(actor, course.getId());
        return cards.detail(course, instructor, enrollment, owner || admin);
    }

    public Course requirePublishedOrEnrolled(String courseId, String studentId) {
        Course course = courses.findById(courseId).orElseThrow(() -> Errors.notFound("Course not found."));
        if (course.getStatus() == CourseStatus.PUBLISHED && !course.isContentDisabled()) return course;
        if (course.getStatus() == CourseStatus.ARCHIVED && enrollments.findByStudentIdAndCourseId(studentId, courseId).isPresent()) {
            return course;
        }
        throw Errors.notFound("Course not found.");
    }

    private Enrollment enrollment(AuthUser actor, String courseId) {
        if (actor == null || actor.role() != Role.STUDENT) return null;
        return enrollments.findByStudentIdAndCourseId(actor.id(), courseId).orElse(null);
    }

    private CategoryView toView(Category category) {
        long count = courses.countByCategoryIdOrSubcategoryId(category.getId(), category.getId());
        return new CategoryView(category.getId(), category.getName(), category.getSlug(), category.getDomain().name(),
                category.getParentId(), category.getDescription(), category.isActive(), category.getOrder(), count);
    }

    private void applyCategory(Category category, CategoryRequest request, String currentId) {
        Domain domain = request.domain();
        if (request.parentId() != null && !request.parentId().isBlank()) {
            Category parent = categories.findById(request.parentId()).orElseThrow(() -> Errors.bad("BAD_CATEGORY", "Parent category not found."));
            if (parent.getParentId() != null) throw Errors.bad("BAD_CATEGORY", "Subcategories cannot contain more categories.");
            category.setParentId(parent.getId());
            domain = parent.getDomain();
        } else {
            category.setParentId(null);
            if (domain == null) throw Errors.bad("VALIDATION_ERROR", "A top-level category needs a technical or non-technical classification.");
        }
        category.setName(request.name().trim());
        category.setDescription(request.description());
        category.setDomain(domain);
        category.setSlug(uniqueSlug(Slugger.slug(request.name()), currentId));
        if (category.getId() == null) {
            category.setActive(true);
            category.setOrder((int) categories.count() + 1);
        }
    }

    private String uniqueSlug(String base, String currentId) {
        String candidate = base;
        int suffix = 2;
        while (true) {
            Category existing = categories.findBySlug(candidate).orElse(null);
            if (existing == null || existing.getId().equals(currentId)) return candidate;
            candidate = base + "-" + suffix++;
        }
    }

    private static Sort sort(String sort) {
        return switch (sort == null ? "newest" : sort) {
            case "title" -> Sort.by(Sort.Direction.ASC, "title");
            case "popular" -> Sort.by(Sort.Direction.DESC, "stats.enrollmentCount");
            case "duration" -> Sort.by(Sort.Direction.ASC, "estimatedDurationMinutes");
            default -> Sort.by(Sort.Direction.DESC, "publishedAt");
        };
    }
}
