package com.datagami.edudron.student.repo;

import com.datagami.edudron.student.domain.Section;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SectionRepository extends JpaRepository<Section, String> {
    
    List<Section> findByClientIdAndClassId(UUID clientId, String classId);
    
    List<Section> findByClientIdAndClassIdAndIsActive(UUID clientId, String classId, Boolean isActive);
    
    List<Section> findByClientId(UUID clientId);

    long countByClientId(UUID clientId);

    long countByClientIdAndIsActive(UUID clientId, Boolean isActive);
    
    Optional<Section> findByIdAndClientId(String id, UUID clientId);
    
    @Query("SELECT COUNT(DISTINCT e.studentId) FROM Enrollment e WHERE e.batchId = :sectionId AND e.clientId = :clientId")
    long countStudentsInSection(@Param("clientId") UUID clientId, @Param("sectionId") String sectionId);

    Optional<Section> findByNameAndClientId(String name, UUID clientId);

    Optional<Section> findByNameAndClientIdAndClassId(String name, UUID clientId, String classId);

    @Modifying
    @Query("UPDATE Section s SET s.isBacklog = :isBacklog, s.updatedAt = CURRENT_TIMESTAMP WHERE s.classId = :classId AND s.clientId = :clientId")
    int updateIsBacklogByClassId(@Param("classId") String classId, @Param("clientId") UUID clientId, @Param("isBacklog") boolean isBacklog);
}


