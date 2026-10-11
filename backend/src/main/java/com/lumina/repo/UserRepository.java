package com.lumina.repo;

import com.lumina.domain.Enums.Role;
import com.lumina.domain.User;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String> {
    Optional<User> findByEmailIgnoreCase(String email);
    long countByRole(Role role);
}
