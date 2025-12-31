package com.datagami.edudron.student.repo;

import com.datagami.edudron.student.domain.Enrollment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EnrollmentRepository extends JpaRepository<Enrollment, String> {
    
    Optional<Enrollment> findByClientIdAndStudentIdAndCourseId(UUID clientId, String studentId, String courseId);
    
    List<Enrollment> findByClientIdAndStudentId(UUID clientId, String studentId);
    
    Page<Enrollment> findByClientIdAndStudentId(UUID clientId, String studentId, Pageable pageable);
    
    List<Enrollment> findByClientIdAndCourseId(UUID clientId, String courseId);
    
    Page<Enrollment> findByClientIdAndCourseId(UUID clientId, String courseId, Pageable pageable);
    
    long countByClientIdAndCourseId(UUID clientId, String courseId);
    
    boolean existsByClientIdAndStudentIdAndCourseId(UUID clientId, String studentId, String courseId);
    
    List<Enrollment> findByClientIdAndBatchId(UUID clientId, String batchId);
    
    long countByClientIdAndBatchId(UUID clientId, String batchId);
}

