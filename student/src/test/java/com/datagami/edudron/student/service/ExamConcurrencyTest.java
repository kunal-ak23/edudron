package com.datagami.edudron.student.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.student.domain.AssessmentSubmission;
import com.datagami.edudron.student.repo.AssessmentSubmissionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Exam Concurrency Tests")
class ExamConcurrencyTest {

    @Mock
    private AssessmentSubmissionRepository submissionRepository;

    @Mock
    private CommonEventService eventService;

    @Mock
    private AssessmentJourneyService assessmentJourneyService;

    @InjectMocks
    private ExamSubmissionService examSubmissionService;

    private ObjectMapper objectMapper;
    private UUID clientId;
    private String submissionId;
    private String studentId;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        clientId = UUID.randomUUID();
        submissionId = "submission123";
        studentId = "student123";

        // Set tenant context
        TenantContext.setClientId(clientId.toString());

        // Set objectMapper via reflection
        ReflectionTestUtils.setField(examSubmissionService, "objectMapper", objectMapper);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("saveProgress should retry on OptimisticLockingFailureException and succeed")
    void testSaveProgress_RetrySuccess() {
        // Given
        JsonNode answers = objectMapper.createObjectNode();
        AssessmentSubmission submission = new AssessmentSubmission();
        submission.setId(submissionId);
        submission.setClientId(clientId);
        submission.setStudentId(studentId);
        submission.setVersion(1L);

        // First call finds the submission
        // First call finds the submission
        when(submissionRepository.findById(submissionId)).thenAnswer(invocation -> {
            AssessmentSubmission s = new AssessmentSubmission();
            s.setId(submissionId);
            s.setClientId(clientId);
            s.setStudentId(studentId);
            s.setVersion(1L);
            return Optional.of(s);
        });

        // First save throws OptimisticLockingFailureException
        // Second save succeeds
        when(submissionRepository.save(any(AssessmentSubmission.class)))
                .thenThrow(new OptimisticLockingFailureException("Concurrent update"))
                .thenReturn(submission);

        // When
        AssessmentSubmission result = examSubmissionService.saveProgress(submissionId, answers, 100);

        // Then
        assertNotNull(result);
        // Should have called findById at least twice (initial + reload on retry)
        // Actually in my impl, I reload inside the loop, so:
        // Attempt 1: findById -> save (fail)
        // Attempt 2: findById -> save (success)
        verify(submissionRepository, times(2)).findById(submissionId);
        verify(submissionRepository, times(2)).save(any(AssessmentSubmission.class));
    }

    @Test
    @DisplayName("saveProgress should fail after max retries")
    void testSaveProgress_MaxRetriesExceeded() {
        // Given
        JsonNode answers = objectMapper.createObjectNode();
        AssessmentSubmission submission = new AssessmentSubmission();
        submission.setId(submissionId);
        submission.setClientId(clientId);
        submission.setStudentId(studentId);
        submission.setVersion(1L);

        when(submissionRepository.findById(submissionId)).thenAnswer(invocation -> {
            AssessmentSubmission s = new AssessmentSubmission();
            s.setId(submissionId);
            s.setClientId(clientId);
            s.setStudentId(studentId);
            s.setVersion(1L);
            return Optional.of(s);
        });
        when(submissionRepository.save(any(AssessmentSubmission.class)))
                .thenThrow(new OptimisticLockingFailureException("Concurrent update"));

        // When/Then
        assertThrows(OptimisticLockingFailureException.class, () -> {
            examSubmissionService.saveProgress(submissionId, answers, 100);
        });

        // Should have tried 3 times (maxRetries)
        verify(submissionRepository, times(3)).findById(submissionId);
        verify(submissionRepository, times(3)).save(any(AssessmentSubmission.class));
    }

    @Test
    @DisplayName("submitExam should retry on OptimisticLockingFailureException")
    void testSubmitExam_RetrySuccess() {
        // Given
        JsonNode answers = objectMapper.createObjectNode();
        AssessmentSubmission submission = new AssessmentSubmission();
        submission.setId(submissionId);
        submission.setClientId(clientId);
        submission.setStudentId(studentId);
        submission.setAssessmentId("exam1");
        submission.setCourseId("course1");
        submission.setEnrollmentId("enrollment1");
        submission.setVersion(1L);

        when(submissionRepository.findById(submissionId)).thenAnswer(invocation -> {
            AssessmentSubmission s = new AssessmentSubmission();
            s.setId(submissionId);
            s.setClientId(clientId);
            s.setStudentId(studentId);
            s.setAssessmentId("exam1");
            s.setCourseId("course1");
            s.setEnrollmentId("enrollment1");
            s.setVersion(1L);
            return Optional.of(s);
        });

        // First save fails, second succeeds
        when(submissionRepository.save(any(AssessmentSubmission.class)))
                .thenThrow(new OptimisticLockingFailureException("Concurrent update"))
                .thenReturn(submission);

        // When
        AssessmentSubmission result = examSubmissionService.submitExam(submissionId, answers);

        // Then
        assertNotNull(result);
        verify(submissionRepository, times(2)).findById(submissionId);
        verify(submissionRepository, times(2)).save(any(AssessmentSubmission.class));
        verify(eventService, times(1)).logAssessmentSubmission(any(), any(), any(), any(), any(), any(), any());
    }
}
