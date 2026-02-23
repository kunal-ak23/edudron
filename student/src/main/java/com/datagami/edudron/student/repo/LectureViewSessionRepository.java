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
                        @Param("lectureId") String lectureId);

        @Query("SELECT s FROM LectureViewSession s WHERE s.clientId = :clientId AND s.courseId = :courseId " +
                        "ORDER BY s.sessionStartedAt DESC")
        List<LectureViewSession> findByClientIdAndCourseId(
                        @Param("clientId") UUID clientId,
                        @Param("courseId") String courseId);

        @Query("SELECT s FROM LectureViewSession s WHERE s.clientId = :clientId AND s.lectureId = :lectureId " +
                        "ORDER BY s.sessionStartedAt DESC")
        List<LectureViewSession> findByClientIdAndLectureId(
                        @Param("clientId") UUID clientId,
                        @Param("lectureId") String lectureId);

        @Query("SELECT COUNT(s) FROM LectureViewSession s WHERE s.clientId = :clientId AND s.lectureId = :lectureId")
        long countSessionsByLectureId(
                        @Param("clientId") UUID clientId,
                        @Param("lectureId") String lectureId);

        @Query("SELECT s FROM LectureViewSession s WHERE s.clientId = :clientId AND s.lectureId = :lectureId " +
                        "AND s.durationSeconds < :thresholdSeconds AND s.sessionEndedAt IS NOT NULL " +
                        "ORDER BY s.sessionStartedAt DESC")
        List<LectureViewSession> findSessionsWithShortDuration(
                        @Param("clientId") UUID clientId,
                        @Param("lectureId") String lectureId,
                        @Param("thresholdSeconds") Integer thresholdSeconds);

        @Query("SELECT COUNT(DISTINCT s.studentId) FROM LectureViewSession s WHERE s.clientId = :clientId " +
                        "AND s.lectureId = :lectureId")
        long countUniqueViewersByLectureId(
                        @Param("clientId") UUID clientId,
                        @Param("lectureId") String lectureId);

        @Query("SELECT COUNT(DISTINCT s.studentId) FROM LectureViewSession s WHERE s.clientId = :clientId " +
                        "AND s.courseId = :courseId")
        long countUniqueViewersByCourseId(
                        @Param("clientId") UUID clientId,
                        @Param("courseId") String courseId);

        /**
         * Get aggregated engagement metrics for all lectures in a course.
         * This performs aggregations at the database level instead of loading all
         * sessions.
         */
        @Query("""
                        SELECT new com.datagami.edudron.student.dto.LectureEngagementAggregateDTO(
                            s.lectureId,
                            MAX(s.courseId),
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
                        @Param("thresholdSeconds") Integer thresholdSeconds);

        /**
         * Get aggregated engagement metrics for a specific lecture.
         */
        @Query("""
                        SELECT new com.datagami.edudron.student.dto.LectureEngagementAggregateDTO(
                            s.lectureId,
                            MAX(s.courseId),
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
                        @Param("thresholdSeconds") Integer thresholdSeconds);

        @Query("""
                        SELECT new com.datagami.edudron.student.dto.LectureEngagementAggregateDTO(
                            s.lectureId,
                            MAX(s.courseId),
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
                        INNER JOIN Enrollment e ON s.enrollmentId = e.id
                        WHERE s.clientId = :clientId AND s.lectureId = :lectureId AND e.batchId = :sectionId
                        GROUP BY s.lectureId
                        """)
        LectureEngagementAggregateDTO getLectureEngagementAggregateBySection(
                        @Param("clientId") UUID clientId,
                        @Param("lectureId") String lectureId,
                        @Param("thresholdSeconds") Integer thresholdSeconds,
                        @Param("sectionId") String sectionId);

        @Query("""
                        SELECT new com.datagami.edudron.student.dto.LectureEngagementAggregateDTO(
                            s.lectureId,
                            MAX(s.courseId),
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
                        INNER JOIN Enrollment e ON s.enrollmentId = e.id
                        WHERE s.clientId = :clientId AND s.lectureId = :lectureId AND e.classId = :classId
                        GROUP BY s.lectureId
                        """)
        LectureEngagementAggregateDTO getLectureEngagementAggregateByClass(
                        @Param("clientId") UUID clientId,
                        @Param("lectureId") String lectureId,
                        @Param("thresholdSeconds") Integer thresholdSeconds,
                        @Param("classId") String classId);

        /**
         * Get course-level aggregated metrics.
         * Using native SQL query to ensure correct schema resolution (same as
         * getActivityTimelineByCourse).
         * Returns List<Object[]> to handle cases where Spring Data JPA might return
         * null for single Object[].
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
                        @Param("courseId") String courseId);

        @Query(value = """
                        SELECT
                            COUNT(*)::bigint as totalSessions,
                            COALESCE(SUM(s.duration_seconds), 0)::bigint as totalDurationSeconds,
                            COALESCE(AVG(CASE WHEN s.duration_seconds IS NOT NULL AND s.session_ended_at IS NOT NULL
                                THEN s.duration_seconds ELSE NULL END), 0.0)::double precision as avgSessionDurationSeconds,
                            COALESCE(MAX(CASE WHEN s.is_completed_in_session IS TRUE THEN 1 ELSE 0 END), 0)::integer as hasCompleted
                        FROM student.lecture_view_sessions s
                        WHERE s.client_id = CAST(:clientId AS uuid) AND s.course_id = :courseId AND s.student_id = :studentId
                        """, nativeQuery = true)
        List<Object[]> getStudentCourseAggregatesList(
                        @Param("clientId") UUID clientId,
                        @Param("courseId") String courseId,
                        @Param("studentId") String studentId);

        /**
         * Wrapper method that returns Object[] for backward compatibility.
         * Extracts the first row from the list result.
         * 
         * This wrapper is necessary because Spring Data JPA can return null for single
         * Object[]
         * native queries, but List<Object[]> queries are more reliable.
         */
        default Object[] getCourseAggregates(UUID clientId, String courseId) {
                List<Object[]> results = getCourseAggregatesList(clientId, courseId);
                if (results != null && !results.isEmpty()) {
                        return results.get(0);
                }
                // Return array with zeros if no results (shouldn't happen for aggregate
                // queries, but handle gracefully)
                return new Object[] { 0L, 0L, 0.0, 0L };
        }

        default Object[] getStudentCourseAggregates(UUID clientId, String courseId, String studentId) {
                List<Object[]> results = getStudentCourseAggregatesList(clientId, courseId, studentId);
                if (results != null && !results.isEmpty()) {
                        return results.get(0);
                }
                return new Object[] { 0L, 0L, 0.0, 0 };
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
                        @Param("courseId") String courseId);

        /**
         * Get recent sessions with pagination (for lecture analytics).
         */
        @Query("SELECT s FROM LectureViewSession s WHERE s.clientId = :clientId AND s.lectureId = :lectureId " +
                        "ORDER BY s.sessionStartedAt DESC")
        Page<LectureViewSession> findRecentSessionsByLectureId(
                        @Param("clientId") UUID clientId,
                        @Param("lectureId") String lectureId,
                        Pageable pageable);

        @Query("SELECT s FROM LectureViewSession s INNER JOIN Enrollment e ON s.enrollmentId = e.id " +
                        "WHERE s.clientId = :clientId AND s.lectureId = :lectureId AND e.batchId = :sectionId " +
                        "ORDER BY s.sessionStartedAt DESC")
        Page<LectureViewSession> findRecentSessionsByLectureIdAndSectionId(
                        @Param("clientId") UUID clientId,
                        @Param("lectureId") String lectureId,
                        @Param("sectionId") String sectionId,
                        Pageable pageable);

        @Query("SELECT s FROM LectureViewSession s INNER JOIN Enrollment e ON s.enrollmentId = e.id " +
                        "WHERE s.clientId = :clientId AND s.lectureId = :lectureId AND e.classId = :classId " +
                        "ORDER BY s.sessionStartedAt DESC")
        Page<LectureViewSession> findRecentSessionsByLectureIdAndClassId(
                        @Param("clientId") UUID clientId,
                        @Param("lectureId") String lectureId,
                        @Param("classId") String classId,
                        Pageable pageable);

        /**
         * Find sessions by client and lecture ID with pagination (for compatibility).
         */
        default Page<LectureViewSession> findByClientIdAndLectureId(UUID clientId, String lectureId,
                        Pageable pageable) {
                return findRecentSessionsByLectureId(clientId, lectureId, pageable);
        }

        /**
         * Get first and last view timestamps for a lecture.
         * Uses native SQL so the driver returns java.sql.Timestamp, not OffsetDateTime.
         */
        @Query(value = """
                        SELECT
                            MIN(s.session_started_at) AS firstView,
                            MAX(s.session_started_at) AS lastView
                        FROM student.lecture_view_sessions s
                        WHERE s.client_id = CAST(:clientId AS uuid) AND s.lecture_id = :lectureId
                        """, nativeQuery = true)
        List<Object[]> getFirstAndLastViewList(
                        @Param("clientId") UUID clientId,
                        @Param("lectureId") String lectureId);

        @Query(value = """
                        SELECT
                            MIN(s.session_started_at) AS firstView,
                            MAX(s.session_started_at) AS lastView
                        FROM student.lecture_view_sessions s
                        INNER JOIN student.enrollments e ON s.enrollment_id = e.id
                        WHERE s.client_id = CAST(:clientId AS uuid) AND s.lecture_id = :lectureId AND e.batch_id = :sectionId
                        """, nativeQuery = true)
        List<Object[]> getFirstAndLastViewBySectionList(
                        @Param("clientId") UUID clientId,
                        @Param("lectureId") String lectureId,
                        @Param("sectionId") String sectionId);

        @Query(value = """
                        SELECT
                            MIN(s.session_started_at) AS firstView,
                            MAX(s.session_started_at) AS lastView
                        FROM student.lecture_view_sessions s
                        INNER JOIN student.enrollments e ON s.enrollment_id = e.id
                        WHERE s.client_id = CAST(:clientId AS uuid) AND s.lecture_id = :lectureId AND e.class_id = :classId
                        """, nativeQuery = true)
        List<Object[]> getFirstAndLastViewByClassList(
                        @Param("clientId") UUID clientId,
                        @Param("lectureId") String lectureId,
                        @Param("classId") String classId);

        default Object[] getFirstAndLastView(UUID clientId, String lectureId) {
                List<Object[]> results = getFirstAndLastViewList(clientId, lectureId);
                if (results != null && !results.isEmpty()) {
                        return results.get(0);
                }
                return null;
        }

        // ==================== SECTION ANALYTICS QUERIES ====================

        /**
         * Get aggregated engagement metrics for all lectures in a section (ACROSS ALL
         * COURSES).
         * Joins with Enrollment table to filter by section (batchId).
         * Note: No courseId filter - captures ALL courses assigned to the section.
         */
        @Query("""
                        SELECT new com.datagami.edudron.student.dto.LectureEngagementAggregateDTO(
                            s.lectureId,
                            MAX(s.courseId),
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
                        INNER JOIN Enrollment e ON s.enrollmentId = e.id
                        WHERE s.clientId = :clientId AND e.batchId = :sectionId
                        GROUP BY s.lectureId
                        ORDER BY COUNT(s) DESC
                        """)
        List<LectureEngagementAggregateDTO> getLectureEngagementAggregatesBySection(
                        @Param("clientId") UUID clientId,
                        @Param("sectionId") String sectionId,
                        @Param("thresholdSeconds") Integer thresholdSeconds);

        /**
         * Get section-level aggregated metrics (MULTI-COURSE AGGREGATION).
         * This query aggregates across ALL courses that students in the section are
         * enrolled in.
         */
        @Query(value = """
                        SELECT
                            COUNT(*)::bigint as totalSessions,
                            COUNT(DISTINCT s.student_id)::bigint as uniqueStudents,
                            COALESCE(AVG(CASE WHEN s.duration_seconds IS NOT NULL AND s.session_ended_at IS NOT NULL
                                THEN s.duration_seconds ELSE NULL END), 0.0)::double precision as avgDuration,
                            COALESCE(SUM(CASE WHEN s.is_completed_in_session IS TRUE THEN 1 ELSE 0 END), 0)::bigint as completedSessions,
                            COUNT(DISTINCT s.course_id)::bigint as totalCourses
                        FROM student.lecture_view_sessions s
                        INNER JOIN student.enrollments e ON s.enrollment_id = e.id
                        WHERE s.client_id = CAST(:clientId AS uuid) AND e.batch_id = :sectionId
                        """, nativeQuery = true)
        List<Object[]> getSectionAggregatesList(
                        @Param("clientId") UUID clientId,
                        @Param("sectionId") String sectionId);

        default Object[] getSectionAggregates(UUID clientId, String sectionId) {
                List<Object[]> results = getSectionAggregatesList(clientId, sectionId);
                if (results != null && !results.isEmpty()) {
                        return results.get(0);
                }
                // Return array with zeros if no results
                return new Object[] { 0L, 0L, 0.0, 0L, 0L };
        }

        /**
         * Get per-course breakdown for a section.
         * Shows individual course metrics within the section.
         */
        @Query(value = """
                        SELECT
                            s.course_id as courseId,
                            COUNT(*)::bigint as totalSessions,
                            COUNT(DISTINCT s.student_id)::bigint as uniqueStudents,
                            COALESCE(AVG(CASE WHEN s.is_completed_in_session IS TRUE THEN 100.0 ELSE 0.0 END), 0.0)::double precision as completionRate,
                            COALESCE(AVG(s.duration_seconds), 0)::integer as avgTimeSpentSeconds
                        FROM student.lecture_view_sessions s
                        INNER JOIN student.enrollments e ON s.enrollment_id = e.id
                        WHERE s.client_id = CAST(:clientId AS uuid) AND e.batch_id = :sectionId
                        GROUP BY s.course_id
                        ORDER BY totalSessions DESC
                        """, nativeQuery = true)
        List<Object[]> getCourseBreakdownBySection(
                        @Param("clientId") UUID clientId,
                        @Param("sectionId") String sectionId);

        /**
         * Get activity timeline for a section (ACROSS ALL COURSES).
         */
        @Query(value = """
                        SELECT
                            CAST(s.session_started_at AS DATE) as date,
                            COUNT(*)::bigint as sessionCount,
                            COUNT(DISTINCT s.student_id)::bigint as uniqueStudents
                        FROM student.lecture_view_sessions s
                        INNER JOIN student.enrollments e ON s.enrollment_id = e.id
                        WHERE s.client_id = CAST(:clientId AS uuid) AND e.batch_id = :sectionId
                        GROUP BY CAST(s.session_started_at AS DATE)
                        ORDER BY CAST(s.session_started_at AS DATE) ASC
                        """, nativeQuery = true)
        List<Object[]> getActivityTimelineBySection(
                        @Param("clientId") UUID clientId,
                        @Param("sectionId") String sectionId);

        // ==================== CLASS ANALYTICS QUERIES ====================

        /**
         * Get aggregated engagement metrics for all lectures in a class (ACROSS ALL
         * SECTIONS AND COURSES).
         * Joins with Enrollment table to filter by class (classId).
         */
        @Query("""
                        SELECT new com.datagami.edudron.student.dto.LectureEngagementAggregateDTO(
                            s.lectureId,
                            MAX(s.courseId),
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
                        INNER JOIN Enrollment e ON s.enrollmentId = e.id
                        WHERE s.clientId = :clientId AND e.classId = :classId
                        GROUP BY s.lectureId
                        ORDER BY COUNT(s) DESC
                        """)
        List<LectureEngagementAggregateDTO> getLectureEngagementAggregatesByClass(
                        @Param("clientId") UUID clientId,
                        @Param("classId") String classId,
                        @Param("thresholdSeconds") Integer thresholdSeconds);

        /**
         * Get class-level aggregated metrics (MULTI-COURSE, MULTI-SECTION AGGREGATION).
         * This query aggregates across ALL sections and ALL courses in the class.
         */
        @Query(value = """
                        SELECT
                            COUNT(*)::bigint as totalSessions,
                            COUNT(DISTINCT s.student_id)::bigint as uniqueStudents,
                            COALESCE(AVG(CASE WHEN s.duration_seconds IS NOT NULL AND s.session_ended_at IS NOT NULL
                                THEN s.duration_seconds ELSE NULL END), 0.0)::double precision as avgDuration,
                            COALESCE(SUM(CASE WHEN s.is_completed_in_session IS TRUE THEN 1 ELSE 0 END), 0)::bigint as completedSessions,
                            COUNT(DISTINCT s.course_id)::bigint as totalCourses,
                            COUNT(DISTINCT e.batch_id)::bigint as totalSections
                        FROM student.lecture_view_sessions s
                        INNER JOIN student.enrollments e ON s.enrollment_id = e.id
                        WHERE s.client_id = CAST(:clientId AS uuid) AND e.class_id = :classId
                        """, nativeQuery = true)
        List<Object[]> getClassAggregatesList(
                        @Param("clientId") UUID clientId,
                        @Param("classId") String classId);

        default Object[] getClassAggregates(UUID clientId, String classId) {
                List<Object[]> results = getClassAggregatesList(clientId, classId);
                if (results != null && !results.isEmpty()) {
                        return results.get(0);
                }
                // Return array with zeros if no results
                return new Object[] { 0L, 0L, 0.0, 0L, 0L, 0L };
        }

        /**
         * Get per-course breakdown for a class (ACROSS ALL SECTIONS).
         */
        @Query(value = """
                        SELECT
                            s.course_id as courseId,
                            COUNT(*)::bigint as totalSessions,
                            COUNT(DISTINCT s.student_id)::bigint as uniqueStudents,
                            COALESCE(AVG(CASE WHEN s.is_completed_in_session IS TRUE THEN 100.0 ELSE 0.0 END), 0.0)::double precision as completionRate,
                            COALESCE(AVG(s.duration_seconds), 0)::integer as avgTimeSpentSeconds
                        FROM student.lecture_view_sessions s
                        INNER JOIN student.enrollments e ON s.enrollment_id = e.id
                        WHERE s.client_id = CAST(:clientId AS uuid) AND e.class_id = :classId
                        GROUP BY s.course_id
                        ORDER BY totalSessions DESC
                        """, nativeQuery = true)
        List<Object[]> getCourseBreakdownByClass(
                        @Param("clientId") UUID clientId,
                        @Param("classId") String classId);

        /**
         * Get activity timeline for a class (ACROSS ALL SECTIONS AND COURSES).
         */
        @Query(value = """
                        SELECT
                            CAST(s.session_started_at AS DATE) as date,
                            COUNT(*)::bigint as sessionCount,
                            COUNT(DISTINCT s.student_id)::bigint as uniqueStudents
                        FROM student.lecture_view_sessions s
                        INNER JOIN student.enrollments e ON s.enrollment_id = e.id
                        WHERE s.client_id = CAST(:clientId AS uuid) AND e.class_id = :classId
                        GROUP BY CAST(s.session_started_at AS DATE)
                        ORDER BY CAST(s.session_started_at AS DATE) ASC
                        """, nativeQuery = true)
        List<Object[]> getActivityTimelineByClass(
                        @Param("clientId") UUID clientId,
                        @Param("classId") String classId);

        /**
         * Get section comparison within a class.
         * Shows aggregate metrics for each section (each section aggregated across its
         * courses).
         */
        @Query(value = """
                        SELECT
                            e.batch_id as sectionId,
                            COUNT(DISTINCT s.student_id)::bigint as totalStudents,
                            COUNT(DISTINCT CASE WHEN s.session_started_at >= NOW() - INTERVAL '30 days'
                                THEN s.student_id ELSE NULL END)::bigint as activeStudents,
                            COALESCE(AVG(CASE WHEN s.is_completed_in_session IS TRUE THEN 100.0 ELSE 0.0 END), 0.0)::double precision as avgCompletionRate,
                            COALESCE(AVG(s.duration_seconds), 0)::integer as avgTimeSpentSeconds
                        FROM student.lecture_view_sessions s
                        INNER JOIN student.enrollments e ON s.enrollment_id = e.id
                        WHERE s.client_id = CAST(:clientId AS uuid) AND e.class_id = :classId
                        GROUP BY e.batch_id
                        ORDER BY avgCompletionRate DESC
                        """, nativeQuery = true)
        List<Object[]> getSectionComparisonByClass(
                        @Param("clientId") UUID clientId,
                        @Param("classId") String classId);
}
