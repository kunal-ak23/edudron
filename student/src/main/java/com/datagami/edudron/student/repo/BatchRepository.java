package com.datagami.edudron.student.repo;

import com.datagami.edudron.student.domain.Batch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BatchRepository extends JpaRepository<Batch, String> {
    
    List<Batch> findByClientIdAndCourseId(UUID clientId, String courseId);
    
    List<Batch> findByClientIdAndCourseIdAndIsActive(UUID clientId, String courseId, Boolean isActive);
    
    List<Batch> findByClientId(UUID clientId);
    
    Optional<Batch> findByIdAndClientId(String id, UUID clientId);
    
    @Query("SELECT COUNT(e) FROM Enrollment e WHERE e.batchId = :batchId AND e.clientId = :clientId")
    long countStudentsInBatch(@Param("clientId") UUID clientId, @Param("batchId") String batchId);
}

