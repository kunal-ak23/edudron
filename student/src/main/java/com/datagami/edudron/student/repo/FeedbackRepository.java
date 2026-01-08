package com.datagami.edudron.student.repo;

import com.datagami.edudron.student.domain.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FeedbackRepository extends JpaRepository<Feedback, String> {
    List<Feedback> findByClientIdAndLectureId(UUID clientId, String lectureId);
    Optional<Feedback> findByClientIdAndStudentIdAndLectureId(UUID clientId, String studentId, String lectureId);
    List<Feedback> findByClientIdAndStudentIdAndCourseId(UUID clientId, String studentId, String courseId);
}

