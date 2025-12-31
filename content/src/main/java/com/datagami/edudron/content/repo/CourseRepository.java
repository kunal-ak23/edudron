package com.datagami.edudron.content.repo;

import com.datagami.edudron.content.domain.Course;
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
public interface CourseRepository extends JpaRepository<Course, String> {
    
    // Find by tenant
    Page<Course> findByClientIdAndIsPublished(UUID clientId, Boolean isPublished, Pageable pageable);
    
    List<Course> findByClientId(UUID clientId);
    
    Page<Course> findByClientId(UUID clientId, Pageable pageable);
    
    Optional<Course> findByIdAndClientId(String id, UUID clientId);
    
    // Search and filter
    @Query("SELECT c FROM Course c WHERE c.clientId = :clientId " +
           "AND (:categoryId IS NULL OR c.categoryId = :categoryId) " +
           "AND (:difficultyLevel IS NULL OR c.difficultyLevel = :difficultyLevel) " +
           "AND (:language IS NULL OR c.language = :language) " +
           "AND (:isFree IS NULL OR c.isFree = :isFree) " +
           "AND (:isPublished IS NULL OR c.isPublished = :isPublished) " +
           "AND (:searchTerm IS NULL OR LOWER(c.title) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "OR LOWER(c.description) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    Page<Course> searchCourses(
        @Param("clientId") UUID clientId,
        @Param("categoryId") String categoryId,
        @Param("difficultyLevel") String difficultyLevel,
        @Param("language") String language,
        @Param("isFree") Boolean isFree,
        @Param("isPublished") Boolean isPublished,
        @Param("searchTerm") String searchTerm,
        Pageable pageable
    );
    
    // Find by tag - using native query for PostgreSQL array contains operator
    @Query(value = "SELECT * FROM content.courses c WHERE c.client_id = :clientId " +
           "AND :tag = ANY(c.tags)", nativeQuery = true)
    List<Course> findByClientIdAndTag(@Param("clientId") UUID clientId, @Param("tag") String tag);
    
    // Count by tenant
    long countByClientId(UUID clientId);
    
    // Count published by tenant
    long countByClientIdAndIsPublished(UUID clientId, Boolean isPublished);
}

