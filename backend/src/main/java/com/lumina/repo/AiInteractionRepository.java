package com.lumina.repo;

import com.lumina.domain.AiInteraction;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AiInteractionRepository extends MongoRepository<AiInteraction, String> {
}
