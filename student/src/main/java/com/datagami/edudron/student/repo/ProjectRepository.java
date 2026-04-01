package com.datagami.edudron.student.repo;

import com.datagami.edudron.student.domain.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectRepository extends JpaRepository<Project, String> {

    Optional<Project> findByIdAndClientId(String id, UUID clientId);

    List<Project> findByClientIdAndSectionId(UUID clientId, String sectionId);

    List<Project> findByClientIdAndStatus(UUID clientId, Project.ProjectStatus status);

    List<Project> findByClientIdOrderByCreatedAtDesc(UUID clientId);

    List<Project> findByClientIdAndCourseId(UUID clientId, String courseId);

    List<Project> findByClientIdAndCourseIdAndSectionId(UUID clientId, String courseId, String sectionId);
}
