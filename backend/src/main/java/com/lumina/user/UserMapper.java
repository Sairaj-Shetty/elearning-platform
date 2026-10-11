package com.lumina.user;

import com.lumina.api.Views.LinkView;
import com.lumina.api.Views.UserView;
import com.lumina.domain.User;

import java.util.List;

public final class UserMapper {
    private UserMapper() {}

    public static UserView toView(User user) {
        List<LinkView> links = user.getPortfolioLinks() == null ? List.of() : user.getPortfolioLinks().stream()
                .map(link -> new LinkView(link.getLabel(), link.getUrl()))
                .toList();
        return new UserView(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole().name(),
                user.getStatus().name(),
                avatarUrl(user.getAvatarKey()),
                user.getBio(),
                user.getHeadline(),
                user.getExperience(),
                safe(user.getInterests()),
                safe(user.getSkills()),
                safe(user.getLearningGoals()),
                safe(user.getPreferredCategoryIds()),
                safe(user.getQualifications()),
                links,
                user.getCreatedAt(),
                user.getLastLoginAt(),
                user.isSeedData()
        );
    }

    public static String avatarUrl(String key) {
        return key == null ? null : "/api/v1/media/files/" + key;
    }

    private static List<String> safe(List<String> values) {
        return values == null ? List.of() : values;
    }
}
