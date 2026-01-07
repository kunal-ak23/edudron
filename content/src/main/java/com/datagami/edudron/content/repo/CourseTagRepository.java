package com.datagami.edudron.content.repo;

import com.datagami.edudron.content.domain.CourseTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CourseTagRepository extends JpaRepository<CourseTag, String> {
    
    Optional<CourseTag> findByClientIdAndName(UUID clientId, String name);
    
    @Query("SELECT t FROM CourseTag t WHERE t.clientId = :clientId " +
           "AND LOWER(t.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "ORDER BY t.usageCount DESC")
    List<CourseTag> searchByClientIdAndName(@Param("clientId") UUID clientId, @Param("searchTerm") String searchTerm);
    
    List<CourseTag> findByClientIdOrderByUsageCountDesc(UUID clientId);
}


