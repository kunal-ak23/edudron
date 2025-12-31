package com.datagami.edudron.content.repo;

import com.datagami.edudron.content.domain.CourseInstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CourseInstructorRepository extends JpaRepository<CourseInstructor, String> {
    
    List<CourseInstructor> findByCourseIdAndClientId(String courseId, UUID clientId);
    
    Optional<CourseInstructor> findByCourseIdAndInstructorUserIdAndClientId(
        String courseId, String instructorUserId, UUID clientId
    );
    
    List<CourseInstructor> findByInstructorUserIdAndClientId(String instructorUserId, UUID clientId);
    
    void deleteByCourseIdAndClientId(String courseId, UUID clientId);
}

