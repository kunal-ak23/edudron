package com.datagami.edudron.content.repo;

import com.datagami.edudron.content.domain.CourseCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CourseCategoryRepository extends JpaRepository<CourseCategory, String> {
    
    List<CourseCategory> findByClientIdAndIsActiveOrderBySequenceAsc(UUID clientId, Boolean isActive);
    
    List<CourseCategory> findByClientIdAndParentCategoryIdIsNullAndIsActiveOrderBySequenceAsc(UUID clientId, Boolean isActive);
    
    List<CourseCategory> findByParentCategoryIdAndClientIdOrderBySequenceAsc(String parentCategoryId, UUID clientId);
    
    Optional<CourseCategory> findByIdAndClientId(String id, UUID clientId);
}


