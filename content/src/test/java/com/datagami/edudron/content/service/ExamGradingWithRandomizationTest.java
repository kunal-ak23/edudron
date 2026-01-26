package com.datagami.edudron.content.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.content.domain.Assessment;
import com.datagami.edudron.content.domain.QuizOption;
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

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test cases to verify that exam grading works correctly
 * even when questions and options have been randomized.
 * Grading should be based on IDs, not positions.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Exam Grading with Randomization Tests")
class ExamGradingWithRandomizationTest {

    @Mock
    private AssessmentRepository assessmentRepository;

    @Mock
    private QuizQuestionRepository quizQuestionRepository;

    @Mock
    private ExamReviewAIService examReviewAIService;

    @InjectMocks
    private ExamReviewService examReviewService;

    private ObjectMapper objectMapper;
    private UUID clientId;
    private String examId;
    private Assessment exam;
    private List<QuizQuestion> questions;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        clientId = UUID.randomUUID();
        examId = "exam123";

        // Set tenant context
        TenantContext.setClientId(clientId.toString());

        // Set objectMapper via reflection
        ReflectionTestUtils.setField(examReviewService, "objectMapper", objectMapper);

        // Setup mock exam
        exam = new Assessment();
        exam.setId(examId);
        exam.setClientId(clientId);
        exam.setAssessmentType(Assessment.AssessmentType.EXAM);
        exam.setReviewMethod(Assessment.ReviewMethod.INSTRUCTOR);

        // Setup mock questions with options
        questions = createMockQuestions();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Should grade correctly when question order is randomized")
    void testGradingWithRandomizedQuestionOrder() throws Exception {
        // Given: Student answered questions in randomized order
        ObjectNode studentAnswers = objectMapper.createObjectNode();
        
        // Student answers question 3, 1, 2 (randomized order, but all correct)
        studentAnswers.put("q3", "q3opt1"); // Correct answer for Q3
        studentAnswers.put("q1", "q1opt1"); // Correct answer for Q1
        studentAnswers.put("q2", "q2opt1"); // Correct answer for Q2

        // Get the gradeObjectiveQuestion method via reflection
        java.lang.reflect.Method method = ExamReviewService.class.getDeclaredMethod(
            "gradeObjectiveQuestion", QuizQuestion.class, JsonNode.class);
        method.setAccessible(true);

        // When: Grade each answer
        double scoreQ1 = (double) method.invoke(examReviewService, questions.get(0), studentAnswers.get("q1"));
        double scoreQ2 = (double) method.invoke(examReviewService, questions.get(1), studentAnswers.get("q2"));
        double scoreQ3 = (double) method.invoke(examReviewService, questions.get(2), studentAnswers.get("q3"));

        // Then: All should be correct regardless of order
        assertEquals(2.0, scoreQ1, "Q1 should be graded correctly");
        assertEquals(2.0, scoreQ2, "Q2 should be graded correctly");
        assertEquals(2.0, scoreQ3, "Q3 should be graded correctly");
    }

    @Test
    @DisplayName("Should grade correctly when MCQ options are randomized")
    void testGradingWithRandomizedMcqOptions() throws Exception {
        // Given: Options are displayed in different order, but student selects correct one
        ObjectNode studentAnswers = objectMapper.createObjectNode();
        
        // The correct answer ID remains the same, regardless of display position
        studentAnswers.put("q1", "q1opt1"); // Correct answer (might be displayed at position 3)
        studentAnswers.put("q2", "q2opt2"); // Wrong answer (might be displayed at position 1)

        // Get the gradeObjectiveQuestion method via reflection
        java.lang.reflect.Method method = ExamReviewService.class.getDeclaredMethod(
            "gradeObjectiveQuestion", QuizQuestion.class, JsonNode.class);
        method.setAccessible(true);

        // When
        double scoreQ1 = (double) method.invoke(examReviewService, questions.get(0), studentAnswers.get("q1"));
        double scoreQ2 = (double) method.invoke(examReviewService, questions.get(1), studentAnswers.get("q2"));

        // Then
        assertEquals(2.0, scoreQ1, "Correct answer should be graded correctly regardless of position");
        assertEquals(0.0, scoreQ2, "Wrong answer should be graded as incorrect regardless of position");
    }

    @Test
    @DisplayName("Should handle student selecting wrong option in randomized list")
    void testWrongAnswerWithRandomization() throws Exception {
        // Given
        ObjectNode studentAnswers = objectMapper.createObjectNode();
        studentAnswers.put("q1", "q1opt2"); // Wrong answer
        studentAnswers.put("q2", "q1opt3"); // Wrong answer
        studentAnswers.put("q3", "q3opt4"); // Wrong answer

        // Get the gradeObjectiveQuestion method via reflection
        java.lang.reflect.Method method = ExamReviewService.class.getDeclaredMethod(
            "gradeObjectiveQuestion", QuizQuestion.class, JsonNode.class);
        method.setAccessible(true);

        // When
        double scoreQ1 = (double) method.invoke(examReviewService, questions.get(0), studentAnswers.get("q1"));
        double scoreQ2 = (double) method.invoke(examReviewService, questions.get(1), studentAnswers.get("q2"));
        double scoreQ3 = (double) method.invoke(examReviewService, questions.get(2), studentAnswers.get("q3"));

        // Then
        assertEquals(0.0, scoreQ1, "Wrong answer should get 0 points");
        assertEquals(0.0, scoreQ2, "Wrong answer should get 0 points");
        assertEquals(0.0, scoreQ3, "Wrong answer should get 0 points");
    }

    @Test
    @DisplayName("Should grade based on option ID, not option position")
    void testGradingByIdNotPosition() throws Exception {
        // Given: Create a question where the correct answer is not the first option
        QuizQuestion question = new QuizQuestion();
        question.setId("q999");
        question.setQuestionType(QuizQuestion.QuestionType.MULTIPLE_CHOICE);
        question.setPoints(3);

        List<QuizOption> options = new ArrayList<>();
        
        // Option at position 1 (sequence 1) is WRONG
        QuizOption opt1 = new QuizOption();
        opt1.setId("opt1");
        opt1.setSequence(1);
        opt1.setIsCorrect(false);
        opt1.setQuestion(question);
        options.add(opt1);

        // Option at position 2 (sequence 2) is WRONG
        QuizOption opt2 = new QuizOption();
        opt2.setId("opt2");
        opt2.setSequence(2);
        opt2.setIsCorrect(false);
        opt2.setQuestion(question);
        options.add(opt2);

        // Option at position 3 (sequence 3) is CORRECT
        QuizOption opt3 = new QuizOption();
        opt3.setId("opt3");
        opt3.setSequence(3);
        opt3.setIsCorrect(true);
        opt3.setQuestion(question);
        options.add(opt3);

        // Option at position 4 (sequence 4) is WRONG
        QuizOption opt4 = new QuizOption();
        opt4.setId("opt4");
        opt4.setSequence(4);
        opt4.setIsCorrect(false);
        opt4.setQuestion(question);
        options.add(opt4);

        question.setOptions(options);

        // Student selects option by ID (opt3), which is correct
        JsonNode studentAnswer = objectMapper.valueToTree("opt3");

        // Get the gradeObjectiveQuestion method via reflection
        java.lang.reflect.Method method = ExamReviewService.class.getDeclaredMethod(
            "gradeObjectiveQuestion", QuizQuestion.class, JsonNode.class);
        method.setAccessible(true);

        // When
        double score = (double) method.invoke(examReviewService, question, studentAnswer);

        // Then
        assertEquals(3.0, score, "Should grade by ID, not position - opt3 is correct regardless of being at position 3");
    }

    @Test
    @DisplayName("Should give partial credit correctly with randomized questions")
    void testPartialCreditWithRandomization() throws Exception {
        // Given: Mixed correct and wrong answers
        ObjectNode studentAnswers = objectMapper.createObjectNode();
        studentAnswers.put("q1", "q1opt1"); // Correct (2 points)
        studentAnswers.put("q2", "q2opt2"); // Wrong (0 points)
        studentAnswers.put("q3", "q3opt1"); // Correct (2 points)

        // Get the gradeObjectiveQuestion method via reflection
        java.lang.reflect.Method method = ExamReviewService.class.getDeclaredMethod(
            "gradeObjectiveQuestion", QuizQuestion.class, JsonNode.class);
        method.setAccessible(true);

        // When
        double totalScore = 0;
        totalScore += (double) method.invoke(examReviewService, questions.get(0), studentAnswers.get("q1"));
        totalScore += (double) method.invoke(examReviewService, questions.get(1), studentAnswers.get("q2"));
        totalScore += (double) method.invoke(examReviewService, questions.get(2), studentAnswers.get("q3"));

        // Then
        assertEquals(4.0, totalScore, "Should calculate partial credit correctly: 2 + 0 + 2 = 4");
    }

    // Helper method to create mock questions
    private List<QuizQuestion> createMockQuestions() {
        List<QuizQuestion> questionList = new ArrayList<>();

        for (int i = 1; i <= 3; i++) {
            QuizQuestion question = new QuizQuestion();
            question.setId("q" + i);
            question.setAssessmentId(examId);
            question.setClientId(clientId);
            question.setQuestionType(QuizQuestion.QuestionType.MULTIPLE_CHOICE);
            question.setQuestionText("Question " + i);
            question.setPoints(2);
            question.setSequence(i);

            List<QuizOption> options = new ArrayList<>();
            for (int j = 1; j <= 4; j++) {
                QuizOption option = new QuizOption();
                option.setId("q" + i + "opt" + j);
                option.setOptionText("Option " + j);
                option.setIsCorrect(j == 1); // First option is always correct
                option.setSequence(j);
                option.setQuestion(question);
                options.add(option);
            }
            question.setOptions(options);
            questionList.add(question);
        }

        return questionList;
    }
}
