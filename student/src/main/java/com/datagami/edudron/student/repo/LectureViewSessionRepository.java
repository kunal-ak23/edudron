package com.datagami.edudron.student.repo;

import com.datagami.edudron.student.domain.LectureViewSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LectureViewSessionRepository extends JpaRepository<LectureViewSession, String> {
    
    @Query("SELECT s FROM LectureViewSession s WHERE s.clientId = :clientId AND s.studentId = :studentId " +
           "AND s.lectureId = :lectureId ORDER BY s.sessionStartedAt DESC")
    List<LectureViewSession> findByClientIdAndStudentIdAndLectureId(
        @Param("clientId") UUID clientId,
        @Param("studentId") String studentId,
        @Param("lectureId") String lectureId
    );
    
    @Query("SELECT s FROM LectureViewSession s WHERE s.clientId = :clientId AND s.courseId = :courseId " +
           "ORDER BY s.sessionStartedAt DESC")
    List<LectureViewSession> findByClientIdAndCourseId(
        @Param("clientId") UUID clientId,
        @Param("courseId") String courseId
    );
    
    @Query("SELECT s FROM LectureViewSession s WHERE s.clientId = :clientId AND s.lectureId = :lectureId " +
           "ORDER BY s.sessionStartedAt DESC")
    List<LectureViewSession> findByClientIdAndLectureId(
        @Param("clientId") UUID clientId,
        @Param("lectureId") String lectureId
    );
    
    @Query("SELECT COUNT(s) FROM LectureViewSession s WHERE s.clientId = :clientId AND s.lectureId = :lectureId")
    long countSessionsByLectureId(
        @Param("clientId") UUID clientId,
        @Param("lectureId") String lectureId
    );
    
    @Query("SELECT s FROM LectureViewSession s WHERE s.clientId = :clientId AND s.lectureId = :lectureId " +
           "AND s.durationSeconds < :thresholdSeconds AND s.sessionEndedAt IS NOT NULL " +
           "ORDER BY s.sessionStartedAt DESC")
    List<LectureViewSession> findSessionsWithShortDuration(
        @Param("clientId") UUID clientId,
        @Param("lectureId") String lectureId,
        @Param("thresholdSeconds") Integer thresholdSeconds
    );
    
    @Query("SELECT COUNT(DISTINCT s.studentId) FROM LectureViewSession s WHERE s.clientId = :clientId " +
           "AND s.lectureId = :lectureId")
    long countUniqueViewersByLectureId(
        @Param("clientId") UUID clientId,
        @Param("lectureId") String lectureId
    );
    
    @Query("SELECT COUNT(DISTINCT s.studentId) FROM LectureViewSession s WHERE s.clientId = :clientId " +
           "AND s.courseId = :courseId")
    long countUniqueViewersByCourseId(
        @Param("clientId") UUID clientId,
        @Param("courseId") String courseId
    );
}
