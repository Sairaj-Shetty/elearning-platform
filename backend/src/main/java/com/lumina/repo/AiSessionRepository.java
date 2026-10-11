package com.lumina.repo;

import com.lumina.domain.AiSession;
import com.lumina.domain.Enums.AiFeature;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface AiSessionRepository extends MongoRepository<AiSession, String> {
    Optional<AiSession> findByUserIdAndFeatureAndCourseIdAndLessonId(String userId, AiFeature feature, String courseId, String lessonId);
}
