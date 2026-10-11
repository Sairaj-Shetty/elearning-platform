package com.lumina.repo;

import com.lumina.domain.Note;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface NoteRepository extends MongoRepository<Note, String> {
    List<Note> findByStudentIdAndCourseIdOrderByCreatedAtDesc(String studentId, String courseId);
}
