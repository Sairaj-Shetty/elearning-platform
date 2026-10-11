package com.lumina.repo;

import com.lumina.domain.LearningEvent;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface LearningEventRepository extends MongoRepository<LearningEvent, String> {
}
