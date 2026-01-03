package com.datagami.edudron.content.repo;

import com.datagami.edudron.content.domain.CourseGenerationIndex;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CourseGenerationIndexRepository extends JpaRepository<CourseGenerationIndex, String> {
    
    List<CourseGenerationIndex> findByClientIdAndIsActiveTrue(UUID clientId);
    
    List<CourseGenerationIndex> findByClientIdAndIndexTypeAndIsActiveTrue(
        UUID clientId, 
        CourseGenerationIndex.IndexType indexType
    );
    
    Optional<CourseGenerationIndex> findByIdAndClientId(String id, UUID clientId);
    
    void deleteByIdAndClientId(String id, UUID clientId);
}

