package com.datagami.edudron.student.repo;

import com.datagami.edudron.student.domain.ProjectSubmissionHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProjectSubmissionHistoryRepository extends JpaRepository<ProjectSubmissionHistory, String> {

    List<ProjectSubmissionHistory> findByGroupIdAndClientIdOrderBySubmittedAtDesc(String groupId, UUID clientId);
}
