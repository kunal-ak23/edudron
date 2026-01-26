package com.datagami.edudron.student.web;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.student.domain.AssessmentSubmission;
import com.datagami.edudron.student.domain.Enrollment;
import com.datagami.edudron.student.repo.AssessmentSubmissionRepository;
import com.datagami.edudron.student.repo.EnrollmentRepository;
import com.datagami.edudron.student.service.ExamSubmissionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
 * Test cases for applying randomization to exam responses.
 * Tests that students see consistent randomized order.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Exam Randomization Application Tests")
class ExamRandomizationApplicationTest {

    @Mock
    private AssessmentSubmissionRepository submissionRepository;

    @Mock
    private EnrollmentRepository enrollmentRepository;

    @Mock
    private ExamSubmissionService examSubmissionService;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private StudentExamController controller;

    private ObjectMapper objectMapper;
    private UUID clientId;
    private String studentId;
    private String examId;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        clientId = UUID.randomUUID();
        studentId = "student123";
        examId = "exam123";

        // Set tenant context
        TenantContext.setClientId(clientId.toString());

        // Set objectMapper via reflection
        ReflectionTestUtils.setField(controller, "objectMapper", objectMapper);
        ReflectionTestUtils.setField(controller, "gatewayUrl", "http://localhost:8080");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Should apply saved question order to exam response")
    void testApplyQuestionOrderRandomization() throws Exception {
        // Given
        ObjectNode examResponse = createMockExam(5);
        
        // Create submission with randomized question order
        AssessmentSubmission submission = new AssessmentSubmission();
        submission.setId("sub123");
        submission.setClientId(clientId);
        submission.setStudentId(studentId);
        submission.setAssessmentId(examId);
        
        ArrayNode questionOrder = objectMapper.createArrayNode();
        questionOrder.add("q3").add("q1").add("q5").add("q2").add("q4");
        submission.setQuestionOrder(questionOrder);

        when(submissionRepository.findFirstByClientIdAndStudentIdAndAssessmentIdOrderBySubmittedAtDesc(
            clientId, studentId, examId))
            .thenReturn(Optional.of(submission));

        // Get the applyRandomization method via reflection and call it
        java.lang.reflect.Method method = StudentExamController.class.getDeclaredMethod(
            "applyRandomization", JsonNode.class, String.class, String.class, UUID.class);
        method.setAccessible(true);
        
        // When
        JsonNode result = (JsonNode) method.invoke(controller, examResponse, examId, studentId, clientId);

        // Then
        assertNotNull(result);
        JsonNode questions = result.get("questions");
        assertNotNull(questions);
        assertTrue(questions.isArray());
        assertEquals(5, questions.size());

        // Verify questions are in the randomized order
        assertEquals("q3", questions.get(0).get("id").asText());
        assertEquals("q1", questions.get(1).get("id").asText());
        assertEquals("q5", questions.get(2).get("id").asText());
        assertEquals("q2", questions.get(3).get("id").asText());
        assertEquals("q4", questions.get(4).get("id").asText());
    }

    @Test
    @DisplayName("Should apply saved MCQ option order to exam response")
    void testApplyMcqOptionOrderRandomization() throws Exception {
        // Given
        ObjectNode examResponse = createMockExam(2);
        
        AssessmentSubmission submission = new AssessmentSubmission();
        submission.setId("sub123");
        submission.setClientId(clientId);
        submission.setStudentId(studentId);
        submission.setAssessmentId(examId);
        
        // Create randomized option orders for each question
        ObjectNode mcqOptionOrders = objectMapper.createObjectNode();
        
        ArrayNode q1Options = objectMapper.createArrayNode();
        q1Options.add("q1opt3").add("q1opt1").add("q1opt4").add("q1opt2");
        mcqOptionOrders.set("q1", q1Options);
        
        ArrayNode q2Options = objectMapper.createArrayNode();
        q2Options.add("q2opt2").add("q2opt4").add("q2opt1").add("q2opt3");
        mcqOptionOrders.set("q2", q2Options);
        
        submission.setMcqOptionOrders(mcqOptionOrders);

        when(submissionRepository.findFirstByClientIdAndStudentIdAndAssessmentIdOrderBySubmittedAtDesc(
            clientId, studentId, examId))
            .thenReturn(Optional.of(submission));

        // Get the applyRandomization method via reflection
        java.lang.reflect.Method method = StudentExamController.class.getDeclaredMethod(
            "applyRandomization", JsonNode.class, String.class, String.class, UUID.class);
        method.setAccessible(true);
        
        // When
        JsonNode result = (JsonNode) method.invoke(controller, examResponse, examId, studentId, clientId);

        // Then
        assertNotNull(result);
        JsonNode questions = result.get("questions");
        
        // Check Q1 options are reordered
        JsonNode q1 = questions.get(0);
        JsonNode q1OptionsResult = q1.get("options");
        assertEquals("q1opt3", q1OptionsResult.get(0).get("id").asText());
        assertEquals("q1opt1", q1OptionsResult.get(1).get("id").asText());
        assertEquals("q1opt4", q1OptionsResult.get(2).get("id").asText());
        assertEquals("q1opt2", q1OptionsResult.get(3).get("id").asText());
        
        // Check Q2 options are reordered
        JsonNode q2 = questions.get(1);
        JsonNode q2OptionsResult = q2.get("options");
        assertEquals("q2opt2", q2OptionsResult.get(0).get("id").asText());
        assertEquals("q2opt4", q2OptionsResult.get(1).get("id").asText());
        assertEquals("q2opt1", q2OptionsResult.get(2).get("id").asText());
        assertEquals("q2opt3", q2OptionsResult.get(3).get("id").asText());
    }

    @Test
    @DisplayName("Should return original exam when no submission exists")
    void testNoSubmission_ReturnsOriginal() throws Exception {
        // Given
        ObjectNode examResponse = createMockExam(3);

        when(submissionRepository.findFirstByClientIdAndStudentIdAndAssessmentIdOrderBySubmittedAtDesc(
            clientId, studentId, examId))
            .thenReturn(Optional.empty());

        // Get the applyRandomization method via reflection
        java.lang.reflect.Method method = StudentExamController.class.getDeclaredMethod(
            "applyRandomization", JsonNode.class, String.class, String.class, UUID.class);
        method.setAccessible(true);
        
        // When
        JsonNode result = (JsonNode) method.invoke(controller, examResponse, examId, studentId, clientId);

        // Then
        assertNotNull(result);
        JsonNode questions = result.get("questions");
        
        // Verify original order is maintained
        assertEquals("q1", questions.get(0).get("id").asText());
        assertEquals("q2", questions.get(1).get("id").asText());
        assertEquals("q3", questions.get(2).get("id").asText());
    }

    @Test
    @DisplayName("Should return original exam when submission has no randomization")
    void testSubmissionWithoutRandomization_ReturnsOriginal() throws Exception {
        // Given
        ObjectNode examResponse = createMockExam(3);
        
        AssessmentSubmission submission = new AssessmentSubmission();
        submission.setId("sub123");
        submission.setClientId(clientId);
        submission.setStudentId(studentId);
        submission.setAssessmentId(examId);
        // No questionOrder or mcqOptionOrders set

        when(submissionRepository.findFirstByClientIdAndStudentIdAndAssessmentIdOrderBySubmittedAtDesc(
            clientId, studentId, examId))
            .thenReturn(Optional.of(submission));

        // Get the applyRandomization method via reflection
        java.lang.reflect.Method method = StudentExamController.class.getDeclaredMethod(
            "applyRandomization", JsonNode.class, String.class, String.class, UUID.class);
        method.setAccessible(true);
        
        // When
        JsonNode result = (JsonNode) method.invoke(controller, examResponse, examId, studentId, clientId);

        // Then
        assertNotNull(result);
        JsonNode questions = result.get("questions");
        
        // Verify original order is maintained
        assertEquals("q1", questions.get(0).get("id").asText());
        assertEquals("q2", questions.get(1).get("id").asText());
        assertEquals("q3", questions.get(2).get("id").asText());
    }

    @Test
    @DisplayName("Should handle missing question IDs gracefully")
    void testMissingQuestionIds_HandlesGracefully() throws Exception {
        // Given
        ObjectNode examResponse = createMockExam(3);
        
        AssessmentSubmission submission = new AssessmentSubmission();
        submission.setId("sub123");
        submission.setClientId(clientId);
        submission.setStudentId(studentId);
        submission.setAssessmentId(examId);
        
        // Question order includes non-existent question
        ArrayNode questionOrder = objectMapper.createArrayNode();
        questionOrder.add("q1").add("q999").add("q2"); // q999 doesn't exist
        submission.setQuestionOrder(questionOrder);

        when(submissionRepository.findFirstByClientIdAndStudentIdAndAssessmentIdOrderBySubmittedAtDesc(
            clientId, studentId, examId))
            .thenReturn(Optional.of(submission));

        // Get the applyRandomization method via reflection
        java.lang.reflect.Method method = StudentExamController.class.getDeclaredMethod(
            "applyRandomization", JsonNode.class, String.class, String.class, UUID.class);
        method.setAccessible(true);
        
        // When
        JsonNode result = (JsonNode) method.invoke(controller, examResponse, examId, studentId, clientId);

        // Then
        assertNotNull(result);
        JsonNode questions = result.get("questions");
        
        // Should only include valid questions
        assertEquals(2, questions.size());
        assertEquals("q1", questions.get(0).get("id").asText());
        assertEquals("q2", questions.get(1).get("id").asText());
    }

    @Test
    @DisplayName("Should maintain correct answers after randomization")
    void testCorrectAnswersMaintained_AfterRandomization() throws Exception {
        // Given
        ObjectNode examResponse = createMockExam(2);
        
        AssessmentSubmission submission = new AssessmentSubmission();
        submission.setId("sub123");
        submission.setClientId(clientId);
        submission.setStudentId(studentId);
        submission.setAssessmentId(examId);
        
        // Randomize options for q1
        ObjectNode mcqOptionOrders = objectMapper.createObjectNode();
        ArrayNode q1Options = objectMapper.createArrayNode();
        q1Options.add("q1opt4").add("q1opt1").add("q1opt2").add("q1opt3"); // Correct answer (opt1) is now at position 2
        mcqOptionOrders.set("q1", q1Options);
        submission.setMcqOptionOrders(mcqOptionOrders);

        when(submissionRepository.findFirstByClientIdAndStudentIdAndAssessmentIdOrderBySubmittedAtDesc(
            clientId, studentId, examId))
            .thenReturn(Optional.of(submission));

        // Get the applyRandomization method via reflection
        java.lang.reflect.Method method = StudentExamController.class.getDeclaredMethod(
            "applyRandomization", JsonNode.class, String.class, String.class, UUID.class);
        method.setAccessible(true);
        
        // When
        JsonNode result = (JsonNode) method.invoke(controller, examResponse, examId, studentId, clientId);

        // Then
        assertNotNull(result);
        JsonNode questions = result.get("questions");
        JsonNode q1 = questions.get(0);
        JsonNode options = q1.get("options");
        
        // Verify the correct answer flag follows the option
        assertFalse(options.get(0).get("isCorrect").asBoolean(), "q1opt4 should not be correct");
        assertTrue(options.get(1).get("isCorrect").asBoolean(), "q1opt1 should still be marked as correct");
        assertFalse(options.get(2).get("isCorrect").asBoolean(), "q1opt2 should not be correct");
        assertFalse(options.get(3).get("isCorrect").asBoolean(), "q1opt3 should not be correct");
    }

    // Helper method to create mock exam response
    private ObjectNode createMockExam(int numQuestions) {
        ObjectNode exam = objectMapper.createObjectNode();
        exam.put("id", examId);
        exam.put("title", "Test Exam");

        ArrayNode questions = objectMapper.createArrayNode();
        for (int i = 1; i <= numQuestions; i++) {
            ObjectNode question = objectMapper.createObjectNode();
            question.put("id", "q" + i);
            question.put("questionType", "MULTIPLE_CHOICE");
            question.put("questionText", "Question " + i);
            question.put("points", 2);

            ArrayNode options = objectMapper.createArrayNode();
            for (int j = 1; j <= 4; j++) {
                ObjectNode option = objectMapper.createObjectNode();
                option.put("id", "q" + i + "opt" + j);
                option.put("optionText", "Option " + j);
                option.put("isCorrect", j == 1); // First option is correct
                option.put("sequence", j);
                options.add(option);
            }
            question.set("options", options);
            questions.add(question);
        }
        exam.set("questions", questions);

        return exam;
    }
}
