package com.datagami.edudron.student.repo;

import com.datagami.edudron.student.domain.InstructorAssignment;
import com.datagami.edudron.student.domain.InstructorAssignment.AssignmentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InstructorAssignmentRepository extends JpaRepository<InstructorAssignment, String> {
    
    // Find all assignments for an instructor
    List<InstructorAssignment> findByClientIdAndInstructorUserId(UUID clientId, String instructorUserId);
    
    // Find assignments by type
    List<InstructorAssignment> findByClientIdAndInstructorUserIdAndAssignmentType(
        UUID clientId, String instructorUserId, AssignmentType assignmentType);
    
    // Find all assignments for a class
    List<InstructorAssignment> findByClientIdAndClassId(UUID clientId, String classId);
    
    // Find all assignments for a section
    List<InstructorAssignment> findByClientIdAndSectionId(UUID clientId, String sectionId);
    
    // Find all assignments for a course
    List<InstructorAssignment> findByClientIdAndCourseId(UUID clientId, String courseId);
    
    // Find all assignments for a tenant
    List<InstructorAssignment> findByClientId(UUID clientId);
    
    // Find by id and client
    Optional<InstructorAssignment> findByIdAndClientId(String id, UUID clientId);
    
    // Check if an instructor has a specific class assignment
    boolean existsByClientIdAndInstructorUserIdAndClassId(UUID clientId, String instructorUserId, String classId);
    
    // Check if an instructor has a specific section assignment
    boolean existsByClientIdAndInstructorUserIdAndSectionId(UUID clientId, String instructorUserId, String sectionId);
    
    // Check if an instructor has a specific course assignment
    boolean existsByClientIdAndInstructorUserIdAndCourseId(UUID clientId, String instructorUserId, String courseId);
    
    // Delete specific assignments
    void deleteByClientIdAndInstructorUserIdAndClassId(UUID clientId, String instructorUserId, String classId);
    void deleteByClientIdAndInstructorUserIdAndSectionId(UUID clientId, String instructorUserId, String sectionId);
    void deleteByClientIdAndInstructorUserIdAndCourseId(UUID clientId, String instructorUserId, String courseId);
    
    // Delete all assignments for an instructor
    void deleteByClientIdAndInstructorUserId(UUID clientId, String instructorUserId);
    
    // Count assignments for an instructor
    long countByClientIdAndInstructorUserId(UUID clientId, String instructorUserId);
    
    // Find instructors assigned to a specific class (directly or via section)
    @Query("SELECT DISTINCT ia.instructorUserId FROM InstructorAssignment ia WHERE ia.clientId = :clientId AND " +
           "(ia.classId = :classId OR ia.sectionId IN (SELECT s.id FROM Section s WHERE s.clientId = :clientId AND s.classId = :classId))")
    List<String> findInstructorUserIdsByClassId(@Param("clientId") UUID clientId, @Param("classId") String classId);
    
    // Find instructors assigned to a specific section
    @Query("SELECT DISTINCT ia.instructorUserId FROM InstructorAssignment ia WHERE ia.clientId = :clientId AND " +
           "(ia.sectionId = :sectionId OR ia.classId = (SELECT s.classId FROM Section s WHERE s.id = :sectionId AND s.clientId = :clientId))")
    List<String> findInstructorUserIdsBySectionId(@Param("clientId") UUID clientId, @Param("sectionId") String sectionId);
}
