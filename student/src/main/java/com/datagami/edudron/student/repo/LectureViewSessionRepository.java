package com.datagami.edudron.student.repo;

import com.datagami.edudron.student.domain.LectureViewSession;
import com.datagami.edudron.student.dto.LectureEngagementAggregateDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    /**
     * Get aggregated engagement metrics for all lectures in a course.
     * This performs aggregations at the database level instead of loading all sessions.
     */
    @Query("""
        SELECT new com.datagami.edudron.student.dto.LectureEngagementAggregateDTO(
            s.lectureId,
            COUNT(s),
            COUNT(DISTINCT s.studentId),
            AVG(CASE WHEN s.durationSeconds IS NOT NULL AND s.sessionEndedAt IS NOT NULL 
                THEN CAST(s.durationSeconds AS double) ELSE NULL END),
            SUM(CASE WHEN s.isCompletedInSession = true THEN 1L ELSE 0L END),
            COUNT(s),
            SUM(CASE WHEN s.durationSeconds IS NOT NULL AND s.sessionEndedAt IS NOT NULL 
                AND s.durationSeconds < :thresholdSeconds THEN 1L ELSE 0L END)
        )
        FROM LectureViewSession s
        WHERE s.clientId = :clientId AND s.courseId = :courseId
        GROUP BY s.lectureId
        ORDER BY COUNT(s) DESC
        """)
    List<LectureEngagementAggregateDTO> getLectureEngagementAggregatesByCourse(
        @Param("clientId") UUID clientId,
        @Param("courseId") String courseId,
        @Param("thresholdSeconds") Integer thresholdSeconds
    );

    /**
     * Get aggregated engagement metrics for a specific lecture.
     */
    @Query("""
        SELECT new com.datagami.edudron.student.dto.LectureEngagementAggregateDTO(
            s.lectureId,
            COUNT(s),
            COUNT(DISTINCT s.studentId),
            AVG(CASE WHEN s.durationSeconds IS NOT NULL AND s.sessionEndedAt IS NOT NULL 
                THEN CAST(s.durationSeconds AS double) ELSE NULL END),
            SUM(CASE WHEN s.isCompletedInSession = true THEN 1L ELSE 0L END),
            COUNT(s),
            SUM(CASE WHEN s.durationSeconds IS NOT NULL AND s.sessionEndedAt IS NOT NULL 
                AND s.durationSeconds < :thresholdSeconds THEN 1L ELSE 0L END)
        )
        FROM LectureViewSession s
        WHERE s.clientId = :clientId AND s.lectureId = :lectureId
        GROUP BY s.lectureId
        """)
    LectureEngagementAggregateDTO getLectureEngagementAggregate(
        @Param("clientId") UUID clientId,
        @Param("lectureId") String lectureId,
        @Param("thresholdSeconds") Integer thresholdSeconds
    );

    /**
     * Get course-level aggregated metrics.
     * Using native SQL query to ensure correct schema resolution (same as getActivityTimelineByCourse).
     * Returns List<Object[]> to handle cases where Spring Data JPA might return null for single Object[].
     * We'll take the first element if available.
     */
    @Query(value = """
        SELECT 
            COUNT(*)::bigint as totalSessions,
            COUNT(DISTINCT s.student_id)::bigint as uniqueStudents,
            COALESCE(AVG(CASE WHEN s.duration_seconds IS NOT NULL AND s.session_ended_at IS NOT NULL 
                THEN s.duration_seconds ELSE NULL END), 0.0)::double precision as avgDuration,
            COALESCE(SUM(CASE WHEN s.is_completed_in_session IS TRUE THEN 1 ELSE 0 END), 0)::bigint as completedSessions
        FROM student.lecture_view_sessions s
        WHERE s.client_id = CAST(:clientId AS uuid) AND s.course_id = :courseId
        """, nativeQuery = true)
    List<Object[]> getCourseAggregatesList(
        @Param("clientId") UUID clientId,
        @Param("courseId") String courseId
    );
    
    /**
     * Wrapper method that returns Object[] for backward compatibility.
     * Extracts the first row from the list result.
     * 
     * This wrapper is necessary because Spring Data JPA can return null for single Object[] 
     * native queries, but List<Object[]> queries are more reliable.
     */
    default Object[] getCourseAggregates(UUID clientId, String courseId) {
        List<Object[]> results = getCourseAggregatesList(clientId, courseId);
        if (results != null && !results.isEmpty()) {
            return results.get(0);
        }
        // Return array with zeros if no results (shouldn't happen for aggregate queries, but handle gracefully)
        return new Object[]{0L, 0L, 0.0, 0L};
    }

    /**
     * Get activity timeline data aggregated by day.
     * Using CAST to DATE for PostgreSQL compatibility.
     */
    @Query(value = """
        SELECT 
            CAST(s.session_started_at AS DATE) as date,
            COUNT(*) as sessionCount,
            COUNT(DISTINCT s.student_id) as uniqueStudents
        FROM student.lecture_view_sessions s
        WHERE s.client_id = CAST(:clientId AS uuid) AND s.course_id = :courseId
        GROUP BY CAST(s.session_started_at AS DATE)
        ORDER BY CAST(s.session_started_at AS DATE) ASC
        """, nativeQuery = true)
    List<Object[]> getActivityTimelineByCourse(
        @Param("clientId") UUID clientId,
        @Param("courseId") String courseId
    );

    /**
     * Get recent sessions with pagination (for lecture analytics).
     */
    @Query("SELECT s FROM LectureViewSession s WHERE s.clientId = :clientId AND s.lectureId = :lectureId " +
           "ORDER BY s.sessionStartedAt DESC")
    Page<LectureViewSession> findRecentSessionsByLectureId(
        @Param("clientId") UUID clientId,
        @Param("lectureId") String lectureId,
        Pageable pageable
    );
    
    /**
     * Find sessions by client and lecture ID with pagination (for compatibility).
     */
    default Page<LectureViewSession> findByClientIdAndLectureId(UUID clientId, String lectureId, Pageable pageable) {
        return findRecentSessionsByLectureId(clientId, lectureId, pageable);
    }

    /**
     * Get first and last view timestamps for a lecture.
     */
    @Query("""
        SELECT 
            MIN(s.sessionStartedAt) as firstView,
            MAX(s.sessionStartedAt) as lastView
        FROM LectureViewSession s
        WHERE s.clientId = :clientId AND s.lectureId = :lectureId
        """)
    Object[] getFirstAndLastView(
        @Param("clientId") UUID clientId,
        @Param("lectureId") String lectureId
    );
}
