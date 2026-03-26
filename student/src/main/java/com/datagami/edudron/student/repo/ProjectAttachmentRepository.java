package com.datagami.edudron.student.repo;

import com.datagami.edudron.student.domain.ProjectAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectAttachmentRepository extends JpaRepository<ProjectAttachment, String> {

    List<ProjectAttachment> findByProjectIdAndClientIdAndContext(
            String projectId, UUID clientId, ProjectAttachment.AttachmentContext context);

    List<ProjectAttachment> findByGroupIdAndClientIdAndContext(
            String groupId, UUID clientId, ProjectAttachment.AttachmentContext context);

    List<ProjectAttachment> findByProjectIdAndClientId(String projectId, UUID clientId);

    Optional<ProjectAttachment> findByIdAndClientId(String id, UUID clientId);

    void deleteByIdAndClientId(String id, UUID clientId);
}
