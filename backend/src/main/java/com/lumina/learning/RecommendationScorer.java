package com.lumina.learning;

import com.lumina.domain.Course;
import com.lumina.domain.Enums.Difficulty;
import com.lumina.domain.User;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class RecommendationScorer {
    public record Scored(Course course, int score, List<String> reasons) {}

    public List<Scored> score(User student, List<Course> published, Set<String> enrolledIds, boolean hasBeginnerProgress) {
        List<String> interests = lower(student.getInterests());
        List<String> skills = lower(student.getSkills());
        List<String> goals = lower(student.getLearningGoals());
        Set<String> preferred = student.getPreferredCategoryIds() == null
                ? Set.of()
                : Set.copyOf(student.getPreferredCategoryIds());

        List<Scored> scored = new ArrayList<>();
        for (Course course : published) {
            if (enrolledIds.contains(course.getId())) continue;
            int score = 0;
            List<String> reasons = new ArrayList<>();
            if (preferred.contains(course.getCategoryId()) || preferred.contains(course.getSubcategoryId())) {
                score += 30;
                reasons.add("It matches a category you selected.");
            }
            String overlap = overlap(course, interests, skills);
            if (overlap != null) {
                score += 20;
                reasons.add("It overlaps with " + overlap + ", which is in your interests or skills.");
            }
            String goalHit = goalMatch(course, goals);
            if (goalHit != null) {
                score += 25;
                reasons.add("It relates to your goal: " + goalHit + ".");
            }
            if (course.getDifficulty() == Difficulty.BEGINNER && enrolledIds.isEmpty()) {
                score += 10;
                reasons.add("It is a beginner course, and you have not enrolled yet.");
            } else if (course.getDifficulty() == Difficulty.INTERMEDIATE && hasBeginnerProgress) {
                score += 12;
                reasons.add("You already started beginner work, and this is an intermediate next step.");
            }
            int popularity = Math.min(course.getStats() == null ? 0 : course.getStats().getEnrollmentCount(), 20);
            score += popularity;
            if (popularity > 0) {
                reasons.add("Other learners on Lumina have enrolled in it.");
            }
            if (reasons.isEmpty()) {
                reasons.add("It is a published course you have not started.");
            }
            scored.add(new Scored(course, score, reasons));
        }
        scored.sort((a, b) -> {
            int byScore = Integer.compare(b.score(), a.score());
            if (byScore != 0) return byScore;
            int ae = a.course().getStats() == null ? 0 : a.course().getStats().getEnrollmentCount();
            int be = b.course().getStats() == null ? 0 : b.course().getStats().getEnrollmentCount();
            return Integer.compare(be, ae);
        });
        return scored.size() > 6 ? scored.subList(0, 6) : scored;
    }

    private static String overlap(Course course, List<String> interests, List<String> skills) {
        List<String> haystack = new ArrayList<>();
        if (course.getTags() != null) haystack.addAll(lower(course.getTags()));
        if (course.getSkills() != null) haystack.addAll(lower(course.getSkills()));
        haystack.add(course.getTitle().toLowerCase(Locale.ROOT));
        for (String needle : interests) {
            if (contains(haystack, needle)) return needle;
        }
        for (String needle : skills) {
            if (contains(haystack, needle)) return needle;
        }
        return null;
    }

    private static String goalMatch(Course course, List<String> goals) {
        String title = course.getTitle().toLowerCase(Locale.ROOT);
        for (String goal : goals) {
            if (goal.length() < 3) continue;
            if (title.contains(goal) || goal.contains(title.toLowerCase(Locale.ROOT))) return goal;
            if (course.getTags() != null) {
                for (String tag : course.getTags()) {
                    if (goal.contains(tag.toLowerCase(Locale.ROOT))) return goal;
                }
            }
            for (String word : goal.split("[^a-z0-9]+")) {
                if (word.length() >= 4 && title.contains(word)) return goal;
            }
        }
        return null;
    }

    private static boolean contains(List<String> haystack, String needle) {
        if (needle.length() < 3) return false;
        for (String item : haystack) {
            if (item.contains(needle) || needle.contains(item)) return true;
        }
        return false;
    }

    private static List<String> lower(List<String> values) {
        if (values == null) return List.of();
        return values.stream().filter(v -> v != null && !v.isBlank()).map(v -> v.toLowerCase(Locale.ROOT)).toList();
    }
}
