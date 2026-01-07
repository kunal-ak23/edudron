package com.datagami.edudron.content.repo;

import com.datagami.edudron.content.domain.Assessment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AssessmentRepository extends JpaRepository<Assessment, String> {
    
    List<Assessment> findByCourseIdAndClientIdOrderBySequenceAsc(String courseId, UUID clientId);
    
    List<Assessment> findByLectureIdAndClientIdOrderBySequenceAsc(String lectureId, UUID clientId);
    
    Optional<Assessment> findByIdAndClientId(String id, UUID clientId);
    
    @Query("SELECT MAX(a.sequence) FROM Assessment a WHERE a.courseId = :courseId AND a.clientId = :clientId")
    Integer findMaxSequenceByCourseIdAndClientId(@Param("courseId") String courseId, @Param("clientId") UUID clientId);
    
    void deleteByCourseIdAndClientId(String courseId, UUID clientId);
    
    void deleteByLectureIdAndClientId(String lectureId, UUID clientId);
}


