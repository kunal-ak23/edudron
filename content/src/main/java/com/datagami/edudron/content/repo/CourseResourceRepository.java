package com.datagami.edudron.content.repo;

import com.datagami.edudron.content.domain.CourseResource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CourseResourceRepository extends JpaRepository<CourseResource, String> {
    
    List<CourseResource> findByCourseIdAndClientId(String courseId, UUID clientId);
    
    Optional<CourseResource> findByIdAndClientId(String id, UUID clientId);
    
    void deleteByCourseIdAndClientId(String courseId, UUID clientId);
}

