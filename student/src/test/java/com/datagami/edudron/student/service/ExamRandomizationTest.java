package com.datagami.edudron.student.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.student.domain.AssessmentSubmission;
import com.datagami.edudron.student.domain.Enrollment;
import com.datagami.edudron.student.repo.AssessmentSubmissionRepository;
import com.datagami.edudron.student.repo.EnrollmentRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test cases for Exam Randomization functionality.
 * Tests randomization generation, persistence, and application.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Exam Randomization Tests")
class ExamRandomizationTest {

    @Mock
    private AssessmentSubmissionRepository submissionRepository;

    @Mock
    private EnrollmentRepository enrollmentRepository;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private CommonEventService eventService;

    @InjectMocks
    private ExamSubmissionService examSubmissionService;

    private ObjectMapper objectMapper;
    private UUID clientId;
    private String studentId;
    private String courseId;
    private String examId;
    private Enrollment enrollment;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        clientId = UUID.randomUUID();
        studentId = "student123";
        courseId = "course123";
        examId = "exam123";

        // Set tenant context
        TenantContext.setClientId(clientId.toString());

        // Set objectMapper via reflection
        ReflectionTestUtils.setField(examSubmissionService, "objectMapper", objectMapper);
        ReflectionTestUtils.setField(examSubmissionService, "gatewayUrl", "http://localhost:8080");

        // Setup mock enrollment
        enrollment = new Enrollment();
        enrollment.setId("enrollment123");
        enrollment.setClientId(clientId);
        enrollment.setStudentId(studentId);
        enrollment.setCourseId(courseId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Should generate randomized question order when randomizeQuestions is enabled")
    void testRandomizeQuestions_Success() throws Exception {
        // Given
        JsonNode examResponse = createMockExamResponse(true, false, 5);
        when(enrollmentRepository.findByClientIdAndStudentIdAndCourseId(clientId, studentId, courseId))
            .thenReturn(List.of(enrollment));
        when(submissionRepository.findByClientIdAndStudentIdAndAssessmentId(any(), any(), any()))
            .thenReturn(List.of());
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(JsonNode.class)))
            .thenReturn(new ResponseEntity<>(examResponse, HttpStatus.OK));

        ArgumentCaptor<AssessmentSubmission> submissionCaptor = ArgumentCaptor.forClass(AssessmentSubmission.class);
        when(submissionRepository.save(submissionCaptor.capture()))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        AssessmentSubmission submission = examSubmissionService.startExam(studentId, courseId, examId, 3600, 1);

        // Then
        assertNotNull(submission);
        JsonNode questionOrder = submissionCaptor.getValue().getQuestionOrder();
        assertNotNull(questionOrder, "Question order should be set");
        assertTrue(questionOrder.isArray(), "Question order should be an array");
        assertEquals(5, questionOrder.size(), "Should have 5 questions in randomized order");

        // Verify all question IDs are present
        Set<String> questionIds = new HashSet<>();
        for (JsonNode idNode : questionOrder) {
            questionIds.add(idNode.asText());
        }
        assertEquals(5, questionIds.size(), "All question IDs should be unique");
        assertTrue(questionIds.contains("q1"));
        assertTrue(questionIds.contains("q2"));
        assertTrue(questionIds.contains("q3"));
        assertTrue(questionIds.contains("q4"));
        assertTrue(questionIds.contains("q5"));
    }

    @Test
    @DisplayName("Should generate randomized MCQ option orders when randomizeMcqOptions is enabled")
    void testRandomizeMcqOptions_Success() throws Exception {
        // Given
        JsonNode examResponse = createMockExamResponse(false, true, 3);
        when(enrollmentRepository.findByClientIdAndStudentIdAndCourseId(clientId, studentId, courseId))
            .thenReturn(List.of(enrollment));
        when(submissionRepository.findByClientIdAndStudentIdAndAssessmentId(any(), any(), any()))
            .thenReturn(List.of());
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(JsonNode.class)))
            .thenReturn(new ResponseEntity<>(examResponse, HttpStatus.OK));

        ArgumentCaptor<AssessmentSubmission> submissionCaptor = ArgumentCaptor.forClass(AssessmentSubmission.class);
        when(submissionRepository.save(submissionCaptor.capture()))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        AssessmentSubmission submission = examSubmissionService.startExam(studentId, courseId, examId, 3600, 1);

        // Then
        assertNotNull(submission);
        JsonNode mcqOptionOrders = submissionCaptor.getValue().getMcqOptionOrders();
        assertNotNull(mcqOptionOrders, "MCQ option orders should be set");
        assertTrue(mcqOptionOrders.isObject(), "MCQ option orders should be an object");
        assertEquals(3, mcqOptionOrders.size(), "Should have option orders for 3 questions");

        // Verify each question has randomized options
        for (int i = 1; i <= 3; i++) {
            String questionId = "q" + i;
            assertTrue(mcqOptionOrders.has(questionId), "Should have option order for " + questionId);
            JsonNode optionOrder = mcqOptionOrders.get(questionId);
            assertTrue(optionOrder.isArray(), "Option order should be an array");
            assertEquals(4, optionOrder.size(), "Should have 4 options");

            // Verify all option IDs are present
            Set<String> optionIds = new HashSet<>();
            for (JsonNode idNode : optionOrder) {
                optionIds.add(idNode.asText());
            }
            assertEquals(4, optionIds.size(), "All option IDs should be unique");
        }
    }

    @Test
    @DisplayName("Should generate both question and option randomization when both are enabled")
    void testBothRandomizations_Success() throws Exception {
        // Given
        JsonNode examResponse = createMockExamResponse(true, true, 4);
        when(enrollmentRepository.findByClientIdAndStudentIdAndCourseId(clientId, studentId, courseId))
            .thenReturn(List.of(enrollment));
        when(submissionRepository.findByClientIdAndStudentIdAndAssessmentId(any(), any(), any()))
            .thenReturn(List.of());
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(JsonNode.class)))
            .thenReturn(new ResponseEntity<>(examResponse, HttpStatus.OK));

        ArgumentCaptor<AssessmentSubmission> submissionCaptor = ArgumentCaptor.forClass(AssessmentSubmission.class);
        when(submissionRepository.save(submissionCaptor.capture()))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        AssessmentSubmission submission = examSubmissionService.startExam(studentId, courseId, examId, 3600, 1);

        // Then
        assertNotNull(submission);
        
        JsonNode questionOrder = submissionCaptor.getValue().getQuestionOrder();
        assertNotNull(questionOrder, "Question order should be set");
        assertEquals(4, questionOrder.size());

        JsonNode mcqOptionOrders = submissionCaptor.getValue().getMcqOptionOrders();
        assertNotNull(mcqOptionOrders, "MCQ option orders should be set");
        assertEquals(4, mcqOptionOrders.size());
    }

    @Test
    @DisplayName("Should not generate randomization when both settings are disabled")
    void testNoRandomization_Success() throws Exception {
        // Given
        JsonNode examResponse = createMockExamResponse(false, false, 3);
        when(enrollmentRepository.findByClientIdAndStudentIdAndCourseId(clientId, studentId, courseId))
            .thenReturn(List.of(enrollment));
        when(submissionRepository.findByClientIdAndStudentIdAndAssessmentId(any(), any(), any()))
            .thenReturn(List.of());
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(JsonNode.class)))
            .thenReturn(new ResponseEntity<>(examResponse, HttpStatus.OK));

        ArgumentCaptor<AssessmentSubmission> submissionCaptor = ArgumentCaptor.forClass(AssessmentSubmission.class);
        when(submissionRepository.save(submissionCaptor.capture()))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        AssessmentSubmission submission = examSubmissionService.startExam(studentId, courseId, examId, 3600, 1);

        // Then
        assertNotNull(submission);
        assertNull(submissionCaptor.getValue().getQuestionOrder(), "Question order should not be set");
        assertNull(submissionCaptor.getValue().getMcqOptionOrders(), "MCQ option orders should not be set");
    }

    @Test
    @DisplayName("Should resume existing submission without generating new randomization")
    void testResumeExistingSubmission_NoNewRandomization() throws Exception {
        // Given
        AssessmentSubmission existingSubmission = new AssessmentSubmission();
        existingSubmission.setId("submission123");
        existingSubmission.setClientId(clientId);
        existingSubmission.setStudentId(studentId);
        existingSubmission.setAssessmentId(examId);
        existingSubmission.setCompletedAt(null); // In progress
        
        // Set existing randomization
        ArrayNode existingOrder = objectMapper.createArrayNode();
        existingOrder.add("q3").add("q1").add("q2");
        existingSubmission.setQuestionOrder(existingOrder);

        when(enrollmentRepository.findByClientIdAndStudentIdAndCourseId(clientId, studentId, courseId))
            .thenReturn(List.of(enrollment));
        when(submissionRepository.findByClientIdAndStudentIdAndAssessmentId(clientId, studentId, examId))
            .thenReturn(List.of(existingSubmission));

        // When
        AssessmentSubmission submission = examSubmissionService.startExam(studentId, courseId, examId, 3600, 1);

        // Then
        assertNotNull(submission);
        assertEquals("submission123", submission.getId());
        assertEquals(existingOrder, submission.getQuestionOrder());
        verify(submissionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should handle exam API failure gracefully without throwing exception")
    void testExamApiFails_ContinuesWithoutRandomization() throws Exception {
        // Given
        when(enrollmentRepository.findByClientIdAndStudentIdAndCourseId(clientId, studentId, courseId))
            .thenReturn(List.of(enrollment));
        when(submissionRepository.findByClientIdAndStudentIdAndAssessmentId(any(), any(), any()))
            .thenReturn(List.of());
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(JsonNode.class)))
            .thenThrow(new RuntimeException("API Error"));

        ArgumentCaptor<AssessmentSubmission> submissionCaptor = ArgumentCaptor.forClass(AssessmentSubmission.class);
        when(submissionRepository.save(submissionCaptor.capture()))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        AssessmentSubmission submission = examSubmissionService.startExam(studentId, courseId, examId, 3600, 1);

        // Then
        assertNotNull(submission, "Submission should still be created");
        assertNull(submissionCaptor.getValue().getQuestionOrder(), "Question order should be null on API failure");
        assertNull(submissionCaptor.getValue().getMcqOptionOrders(), "MCQ option orders should be null on API failure");
    }

    @Test
    @DisplayName("Should only randomize MULTIPLE_CHOICE questions, not SHORT_ANSWER or ESSAY")
    void testOnlyRandomizesMcqQuestions() throws Exception {
        // Given
        ObjectNode examResponse = objectMapper.createObjectNode();
        examResponse.put("id", examId);
        examResponse.put("randomizeQuestions", false);
        examResponse.put("randomizeMcqOptions", true);

        ArrayNode questions = objectMapper.createArrayNode();
        
        // Add MCQ question
        ObjectNode mcqQuestion = objectMapper.createObjectNode();
        mcqQuestion.put("id", "q1");
        mcqQuestion.put("questionType", "MULTIPLE_CHOICE");
        ArrayNode mcqOptions = objectMapper.createArrayNode();
        mcqOptions.add(createOption("opt1", "A", true));
        mcqOptions.add(createOption("opt2", "B", false));
        mcqQuestion.set("options", mcqOptions);
        questions.add(mcqQuestion);

        // Add SHORT_ANSWER question (should not get randomization)
        ObjectNode shortQuestion = objectMapper.createObjectNode();
        shortQuestion.put("id", "q2");
        shortQuestion.put("questionType", "SHORT_ANSWER");
        questions.add(shortQuestion);

        // Add ESSAY question (should not get randomization)
        ObjectNode essayQuestion = objectMapper.createObjectNode();
        essayQuestion.put("id", "q3");
        essayQuestion.put("questionType", "ESSAY");
        questions.add(essayQuestion);

        examResponse.set("questions", questions);

        when(enrollmentRepository.findByClientIdAndStudentIdAndCourseId(clientId, studentId, courseId))
            .thenReturn(List.of(enrollment));
        when(submissionRepository.findByClientIdAndStudentIdAndAssessmentId(any(), any(), any()))
            .thenReturn(List.of());
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(JsonNode.class)))
            .thenReturn(new ResponseEntity<>(examResponse, HttpStatus.OK));

        ArgumentCaptor<AssessmentSubmission> submissionCaptor = ArgumentCaptor.forClass(AssessmentSubmission.class);
        when(submissionRepository.save(submissionCaptor.capture()))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        AssessmentSubmission submission = examSubmissionService.startExam(studentId, courseId, examId, 3600, 1);

        // Then
        assertNotNull(submission);
        JsonNode mcqOptionOrders = submissionCaptor.getValue().getMcqOptionOrders();
        assertNotNull(mcqOptionOrders);
        assertEquals(1, mcqOptionOrders.size(), "Should only have option order for MCQ question");
        assertTrue(mcqOptionOrders.has("q1"));
        assertFalse(mcqOptionOrders.has("q2"), "SHORT_ANSWER should not be randomized");
        assertFalse(mcqOptionOrders.has("q3"), "ESSAY should not be randomized");
    }

    // Helper method to create mock exam response
    private JsonNode createMockExamResponse(boolean randomizeQuestions, boolean randomizeMcqOptions, int numQuestions) {
        ObjectNode examResponse = objectMapper.createObjectNode();
        examResponse.put("id", examId);
        examResponse.put("randomizeQuestions", randomizeQuestions);
        examResponse.put("randomizeMcqOptions", randomizeMcqOptions);

        ArrayNode questions = objectMapper.createArrayNode();
        for (int i = 1; i <= numQuestions; i++) {
            ObjectNode question = objectMapper.createObjectNode();
            question.put("id", "q" + i);
            question.put("questionType", "MULTIPLE_CHOICE");
            question.put("questionText", "Question " + i);

            ArrayNode options = objectMapper.createArrayNode();
            for (int j = 1; j <= 4; j++) {
                options.add(createOption("q" + i + "opt" + j, "Option " + j, j == 1));
            }
            question.set("options", options);
            questions.add(question);
        }
        examResponse.set("questions", questions);

        return examResponse;
    }

    private ObjectNode createOption(String id, String text, boolean isCorrect) {
        ObjectNode option = objectMapper.createObjectNode();
        option.put("id", id);
        option.put("optionText", text);
        option.put("isCorrect", isCorrect);
        return option;
    }
}
