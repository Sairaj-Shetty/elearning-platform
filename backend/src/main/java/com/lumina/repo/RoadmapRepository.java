package com.lumina.repo;

import com.lumina.domain.Roadmap;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface RoadmapRepository extends MongoRepository<Roadmap, String> {
    List<Roadmap> findByStudentIdOrderByCreatedAtDesc(String studentId);
}
