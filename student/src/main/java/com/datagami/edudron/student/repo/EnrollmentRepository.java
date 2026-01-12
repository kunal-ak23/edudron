package com.datagami.edudron.student.repo;

import com.datagami.edudron.student.domain.Enrollment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EnrollmentRepository extends JpaRepository<Enrollment, String> {
    
    // Use custom query to handle potential duplicates
    @Query("SELECT e FROM Enrollment e WHERE e.clientId = :clientId AND e.studentId = :studentId " +
           "AND e.courseId = :courseId ORDER BY e.enrolledAt DESC")
    List<Enrollment> findByClientIdAndStudentIdAndCourseId(
        @Param("clientId") UUID clientId,
        @Param("studentId") String studentId,
        @Param("courseId") String courseId
    );
    
    List<Enrollment> findByClientIdAndStudentId(UUID clientId, String studentId);
    
    Page<Enrollment> findByClientIdAndStudentId(UUID clientId, String studentId, Pageable pageable);
    
    List<Enrollment> findByClientIdAndCourseId(UUID clientId, String courseId);
    
    Page<Enrollment> findByClientIdAndCourseId(UUID clientId, String courseId, Pageable pageable);
    
    long countByClientIdAndCourseId(UUID clientId, String courseId);
    
    @Query("SELECT COUNT(e) > 0 FROM Enrollment e WHERE e.clientId = :clientId AND e.studentId = :studentId " +
           "AND e.courseId = :courseId")
    boolean existsByClientIdAndStudentIdAndCourseId(
        @Param("clientId") UUID clientId,
        @Param("studentId") String studentId,
        @Param("courseId") String courseId
    );
    
    List<Enrollment> findByClientIdAndBatchId(UUID clientId, String batchId);
    
    long countByClientIdAndBatchId(UUID clientId, String batchId);
    
    List<Enrollment> findByClientIdAndClassId(UUID clientId, String classId);
}

