package com.datagami.edudron.student.repo;

import com.datagami.edudron.student.domain.Note;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NoteRepository extends JpaRepository<Note, String> {
    List<Note> findByClientIdAndStudentIdAndLectureId(UUID clientId, String studentId, String lectureId);
    List<Note> findByClientIdAndStudentIdAndCourseId(UUID clientId, String studentId, String courseId);
}

