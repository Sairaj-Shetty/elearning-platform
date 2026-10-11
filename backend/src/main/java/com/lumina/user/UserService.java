package com.lumina.user;

import com.lumina.api.Requests.ProfileRequest;
import com.lumina.api.Views.CourseCard;
import com.lumina.api.Views.InstructorPublic;
import com.lumina.api.Views.LinkView;
import com.lumina.api.Views.UserView;
import com.lumina.common.Errors;
import com.lumina.domain.Course;
import com.lumina.domain.Enums.AccountStatus;
import com.lumina.domain.Enums.CourseStatus;
import com.lumina.domain.Enums.Role;
import com.lumina.domain.User;
import com.lumina.repo.CourseRepository;
import com.lumina.repo.UserRepository;
import com.lumina.security.AuthUser;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class UserService {
    private final UserRepository users;
    private final CourseRepository courses;
    private final CourseCardFactory cards;

    public UserService(UserRepository users, CourseRepository courses, CourseCardFactory cards) {
        this.users = users;
        this.courses = courses;
        this.cards = cards;
    }

    public User require(String id) {
        return users.findById(id).orElseThrow(() -> Errors.notFound("User not found."));
    }

    public UserView me(AuthUser actor) {
        return UserMapper.toView(require(actor.id()));
    }

    public UserView update(AuthUser actor, ProfileRequest request) {
        User user = require(actor.id());
        user.setFullName(request.fullName().trim());
        user.setHeadline(blankToNull(request.headline()));
        user.setBio(blankToNull(request.bio()));
        user.setExperience(blankToNull(request.experience()));
        user.setInterests(clean(request.interests()));
        user.setSkills(clean(request.skills()));
        user.setLearningGoals(clean(request.learningGoals()));
        user.setPreferredCategoryIds(clean(request.preferredCategoryIds()));
        user.setQualifications(clean(request.qualifications()));
        user.setPortfolioLinks(request.portfolioLinks() == null ? new ArrayList<>() : request.portfolioLinks().stream().map(link -> {
            User.PortfolioLink saved = new User.PortfolioLink();
            saved.setLabel(link.label().trim());
            saved.setUrl(link.url().trim());
            return saved;
        }).toList());
        user.setUpdatedAt(Instant.now());
        user.setRecommendationCache(null);
        users.save(user);
        if (user.getRole() == Role.INSTRUCTOR) {
            courses.findByInstructorId(user.getId()).forEach(course -> {
                course.setInstructorName(user.getFullName());
                courses.save(course);
            });
        }
        return UserMapper.toView(user);
    }

    public InstructorPublic publicInstructor(String id) {
        User user = require(id);
        if (user.getRole() != Role.INSTRUCTOR || user.getStatus() != AccountStatus.ACTIVE) {
            throw Errors.notFound("Instructor not found.");
        }
        List<CourseCard> published = courses.findByInstructorId(id).stream()
                .filter(course -> course.getStatus() == CourseStatus.PUBLISHED && !course.isContentDisabled())
                .map(course -> cards.card(course, null))
                .toList();
        return new InstructorPublic(
                user.getId(),
                user.getFullName(),
                user.getHeadline(),
                user.getBio(),
                UserMapper.avatarUrl(user.getAvatarKey()),
                user.getSkills(),
                user.getQualifications(),
                user.getExperience(),
                user.getPortfolioLinks() == null ? List.of() : user.getPortfolioLinks().stream()
                        .map(link -> new LinkView(link.getLabel(), link.getUrl())).toList(),
                published
        );
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static List<String> clean(List<String> values) {
        if (values == null) return new ArrayList<>();
        return values.stream().filter(value -> value != null && !value.isBlank()).map(String::trim).distinct().limit(20).toList();
    }
}
