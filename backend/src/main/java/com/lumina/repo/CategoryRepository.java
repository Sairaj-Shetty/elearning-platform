package com.lumina.repo;

import com.lumina.domain.Category;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends MongoRepository<Category, String> {
    Optional<Category> findBySlug(String slug);
    List<Category> findByActiveTrueOrderByOrderAscNameAsc();
    long countByParentId(String parentId);
}
