package com.datagami.edudron.student.repo;

import com.datagami.edudron.student.domain.ProjectTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectTemplateRepository extends JpaRepository<ProjectTemplate, String> {
    List<ProjectTemplate> findByClientIdOrderByCreatedAtDesc(UUID clientId);
    Optional<ProjectTemplate> findByIdAndClientId(String id, UUID clientId);
}
