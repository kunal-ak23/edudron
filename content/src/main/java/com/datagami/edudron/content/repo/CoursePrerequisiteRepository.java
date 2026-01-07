package com.datagami.edudron.content.repo;

import com.datagami.edudron.content.domain.CoursePrerequisite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CoursePrerequisiteRepository extends JpaRepository<CoursePrerequisite, String> {
    
    List<CoursePrerequisite> findByCourseIdAndClientId(String courseId, UUID clientId);
    
    void deleteByCourseIdAndClientId(String courseId, UUID clientId);
}


