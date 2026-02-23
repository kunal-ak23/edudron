package com.datagami.edudron.content.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.content.domain.Assessment;
import com.datagami.edudron.content.domain.QuizQuestion;
import com.datagami.edudron.content.repo.AssessmentRepository;
import com.datagami.edudron.content.repo.QuizQuestionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestTemplate;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Exam Review Blank Answer Tests")
class ExamReviewServiceBlankAnswerTest {

    @Mock
    private AssessmentRepository assessmentRepository;

    @Mock
    private QuizQuestionRepository quizQuestionRepository;

    @Mock
    private ExamReviewAIService examReviewAIService;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private ExamReviewService examReviewService;

    private ObjectMapper objectMapper;
    private UUID clientId;
    private String examId;
    private String submissionId;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        clientId = UUID.randomUUID();
        examId = "exam123";
        submissionId = "sub123";

        TenantContext.setClientId(clientId.toString());
        ReflectionTestUtils.setField(examReviewService, "objectMapper", objectMapper);
        ReflectionTestUtils.setField(examReviewService, "gatewayUrl", "http://localhost:8080");
        ReflectionTestUtils.setField(examReviewService, "restTemplate", restTemplate);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Should mark blank answers with 0 points for MC and TF questions")
    void testBlankAnswersGrading() {
        // Setup mock exam
        Assessment exam = new Assessment();
        exam.setId(examId);
        exam.setClientId(clientId);
        exam.setAssessmentType(Assessment.AssessmentType.EXAM);
        exam.setReviewMethod(Assessment.ReviewMethod.AI);
        exam.setPassingScorePercentage(70);
        when(assessmentRepository.findByIdAndClientId(examId, clientId)).thenReturn(Optional.of(exam));

        // Setup mock questions
        List<QuizQuestion> questions = new ArrayList<>();

        // Q1: Multiple Choice
        QuizQuestion q1 = new QuizQuestion();
        q1.setId("q1");
        q1.setQuestionType(QuizQuestion.QuestionType.MULTIPLE_CHOICE);
        q1.setPoints(5);
        questions.add(q1);

        // Q2: True/False (where correct answer is false)
        QuizQuestion q2 = new QuizQuestion();
        q2.setId("q2");
        q2.setQuestionType(QuizQuestion.QuestionType.TRUE_FALSE);
        q2.setPoints(5);
        questions.add(q2);

        // Q3: Subjective
        QuizQuestion q3 = new QuizQuestion();
        q3.setId("q3");
        q3.setQuestionType(QuizQuestion.QuestionType.SHORT_ANSWER);
        q3.setPoints(10);
        questions.add(q3);

        when(quizQuestionRepository.findByAssessmentIdAndClientIdWithOptions(examId, clientId)).thenReturn(questions);

        // Setup submission mock
        ObjectNode submissionNode = objectMapper.createObjectNode();
        submissionNode.put("assessmentId", examId);
        ObjectNode answersJson = objectMapper.createObjectNode();
        answersJson.putNull("q1"); // Explicit null for MC
        answersJson.put("q2", ""); // Empty string for TF
        // q3 is missing entirely
        submissionNode.set("answersJson", answersJson);

        ResponseEntity<JsonNode> submissionResponse = new ResponseEntity<>(submissionNode, HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(org.springframework.http.HttpMethod.GET), any(), eq(JsonNode.class)))
                .thenReturn(submissionResponse);

        // Mock the update call
        when(restTemplate.exchange(anyString(), eq(org.springframework.http.HttpMethod.POST), any(),
                eq(JsonNode.class)))
                .thenReturn(new ResponseEntity<>(objectMapper.createObjectNode(), HttpStatus.OK));

        // When
        JsonNode result = examReviewService.reviewSubmissionWithAI(submissionId);

        // Then
        assertEquals(0.0, result.get("totalScore").asDouble(), "Total score should be 0");
        JsonNode qReviews = result.get("questionReviews");
        assertEquals(3, qReviews.size());

        for (JsonNode review : qReviews) {
            assertEquals(0.0, review.get("pointsEarned").asDouble(),
                    "Points earned should be 0 for question " + review.get("questionId").asText());
            assertEquals("No answer provided", review.get("feedback").asText());
            assertFalse(review.get("isCorrect").asBoolean());
        }

        // Verify AI service was never called for blank answers
        verify(examReviewAIService, never()).compareAnswersSemantically(anyString(), anyString());
    }
}
