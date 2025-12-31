package com.datagami.edudron.content.repo;

import com.datagami.edudron.content.domain.Section;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SectionRepository extends JpaRepository<Section, String> {
    
    List<Section> findByCourseIdAndClientIdOrderBySequenceAsc(String courseId, UUID clientId);
    
    Optional<Section> findByIdAndClientId(String id, UUID clientId);
    
    @Query("SELECT MAX(s.sequence) FROM Section s WHERE s.courseId = :courseId AND s.clientId = :clientId")
    Integer findMaxSequenceByCourseIdAndClientId(@Param("courseId") String courseId, @Param("clientId") UUID clientId);
    
    void deleteByCourseIdAndClientId(String courseId, UUID clientId);
}

