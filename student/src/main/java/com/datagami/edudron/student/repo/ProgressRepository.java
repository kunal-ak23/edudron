package com.datagami.edudron.student.repo;

import com.datagami.edudron.student.domain.Progress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProgressRepository extends JpaRepository<Progress, String> {
    
    Optional<Progress> findByClientIdAndStudentIdAndLectureId(UUID clientId, String studentId, String lectureId);
    
    List<Progress> findByClientIdAndStudentIdAndCourseId(UUID clientId, String studentId, String courseId);
    
    List<Progress> findByClientIdAndEnrollmentId(UUID clientId, String enrollmentId);
    
    @Query("SELECT p FROM Progress p WHERE p.clientId = :clientId AND p.studentId = :studentId " +
           "AND p.courseId = :courseId AND p.lectureId IS NOT NULL")
    List<Progress> findLectureProgressByClientIdAndStudentIdAndCourseId(
        @Param("clientId") UUID clientId,
        @Param("studentId") String studentId,
        @Param("courseId") String courseId
    );
    
    @Query("SELECT p FROM Progress p WHERE p.clientId = :clientId AND p.studentId = :studentId " +
           "AND p.courseId = :courseId AND p.sectionId IS NOT NULL AND p.lectureId IS NULL")
    List<Progress> findSectionProgressByClientIdAndStudentIdAndCourseId(
        @Param("clientId") UUID clientId,
        @Param("studentId") String studentId,
        @Param("courseId") String courseId
    );
    
    @Query("SELECT COUNT(p) FROM Progress p WHERE p.clientId = :clientId AND p.studentId = :studentId " +
           "AND p.courseId = :courseId AND p.lectureId IS NOT NULL AND p.isCompleted = true")
    long countCompletedLectures(@Param("clientId") UUID clientId, 
                                @Param("studentId") String studentId, 
                                @Param("courseId") String courseId);
}


