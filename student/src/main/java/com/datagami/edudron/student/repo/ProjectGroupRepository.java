package com.datagami.edudron.student.repo;

import com.datagami.edudron.student.domain.ProjectGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectGroupRepository extends JpaRepository<ProjectGroup, String> {

    List<ProjectGroup> findByProjectIdAndClientId(String projectId, UUID clientId);

    Optional<ProjectGroup> findByIdAndClientId(String id, UUID clientId);
}
