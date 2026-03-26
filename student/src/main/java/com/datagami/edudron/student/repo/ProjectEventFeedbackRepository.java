package com.datagami.edudron.student.repo;

import com.datagami.edudron.student.domain.ProjectEventFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface ProjectEventFeedbackRepository extends JpaRepository<ProjectEventFeedback, String> {
    List<ProjectEventFeedback> findBySubmissionIdAndClientId(String submissionId, UUID clientId);
    List<ProjectEventFeedback> findByEventIdAndGroupIdAndClientIdOrderByFeedbackAtDesc(
            String eventId, String groupId, UUID clientId);
}
