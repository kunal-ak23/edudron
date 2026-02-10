package com.datagami.edudron.student.repo;

import com.datagami.edudron.student.domain.AssessmentJourneyEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AssessmentJourneyEventRepository extends JpaRepository<AssessmentJourneyEvent, String> {

    List<AssessmentJourneyEvent> findByClientIdAndSubmissionIdOrderByCreatedAtAsc(UUID clientId, String submissionId);

    List<AssessmentJourneyEvent> findByClientIdAndAssessmentIdAndStudentIdOrderByCreatedAtAsc(
        UUID clientId, String assessmentId, String studentId);
}
