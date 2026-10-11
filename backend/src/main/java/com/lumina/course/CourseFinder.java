package com.lumina.course;

import com.lumina.common.Errors;
import com.lumina.domain.Course;

public final class CourseFinder {
    private CourseFinder() {}

    public record Located(Course.Section section, Course.Lesson lesson) {}

    public static Located requireLesson(Course course, String lessonId) {
        for (Course.Section section : course.getSections()) {
            for (Course.Lesson lesson : section.getLessons()) {
                if (lesson.getLessonId().equals(lessonId)) {
                    return new Located(section, lesson);
                }
            }
        }
        throw Errors.notFound("Lesson not found.");
    }

    public static Course.Section requireSection(Course course, String sectionId) {
        return course.getSections().stream()
                .filter(section -> section.getSectionId().equals(sectionId))
                .findFirst()
                .orElseThrow(() -> Errors.notFound("Section not found."));
    }

    public static String lessonTitle(Course course, String lessonId) {
        if (course == null || lessonId == null) return "Lesson";
        for (Course.Section section : course.getSections()) {
            for (Course.Lesson lesson : section.getLessons()) {
                if (lesson.getLessonId().equals(lessonId)) return lesson.getTitle();
            }
        }
        return "Removed lesson";
    }
}
