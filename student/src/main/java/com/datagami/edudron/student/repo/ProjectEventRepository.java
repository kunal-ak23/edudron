package com.datagami.edudron.student.repo;

import com.datagami.edudron.student.domain.ProjectEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectEventRepository extends JpaRepository<ProjectEvent, String> {

    List<ProjectEvent> findByProjectIdAndClientIdOrderBySequenceAsc(String projectId, UUID clientId);

    Optional<ProjectEvent> findByIdAndClientId(String id, UUID clientId);

    List<ProjectEvent> findByProjectIdAndSectionIdAndClientIdOrderBySequenceAsc(
        String projectId, String sectionId, UUID clientId);

    List<ProjectEvent> findByProjectIdAndSectionIdIsNullAndClientIdOrderBySequenceAsc(
        String projectId, UUID clientId);
}
