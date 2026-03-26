package com.datagami.edudron.student.repo;

import com.datagami.edudron.student.domain.ProjectEventSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectEventSubmissionRepository extends JpaRepository<ProjectEventSubmission, String> {
    List<ProjectEventSubmission> findByEventIdAndGroupIdAndClientIdOrderByVersionDesc(
            String eventId, String groupId, UUID clientId);
    Optional<ProjectEventSubmission> findFirstByEventIdAndGroupIdAndClientIdOrderByVersionDesc(
            String eventId, String groupId, UUID clientId);
    List<ProjectEventSubmission> findByEventIdAndClientId(String eventId, UUID clientId);
    List<ProjectEventSubmission> findByProjectIdAndClientId(String projectId, UUID clientId);
    Optional<ProjectEventSubmission> findByIdAndClientId(String id, UUID clientId);
}
