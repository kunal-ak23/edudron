package com.datagami.edudron.content.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.common.TenantContextRestTemplateInterceptor;
import com.datagami.edudron.content.domain.Assessment;
import com.datagami.edudron.content.domain.QuizOption;
import com.datagami.edudron.content.domain.QuizQuestion;
import com.datagami.edudron.content.repo.AssessmentRepository;
import com.datagami.edudron.content.repo.QuizQuestionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ExamReviewService {
    
    private static final Logger logger = LoggerFactory.getLogger(ExamReviewService.class);
    
    @Autowired
    private ExamReviewAIService examReviewAIService;
    
    @Autowired
    private AssessmentRepository assessmentRepository;
    
    @Autowired
    private QuizQuestionRepository quizQuestionRepository;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Value("${GATEWAY_URL:http://localhost:8080}")
    private String gatewayUrl;
    
    private RestTemplate restTemplate;
    
    private RestTemplate getRestTemplate() {
        if (restTemplate == null) {
            restTemplate = new RestTemplate();
            List<ClientHttpRequestInterceptor> interceptors = new ArrayList<>();
            interceptors.add(new TenantContextRestTemplateInterceptor());
            interceptors.add((request, body, execution) -> {
                ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                if (attributes != null) {
                    HttpServletRequest currentRequest = attributes.getRequest();
                    String authHeader = currentRequest.getHeader("Authorization");
                    if (authHeader != null && !authHeader.isBlank()) {
                        if (!request.getHeaders().containsKey("Authorization")) {
                            request.getHeaders().add("Authorization", authHeader);
                        }
                    }
                }
                return execution.execute(request, body);
            });
            restTemplate.setInterceptors(interceptors);
        }
        return restTemplate;
    }
    
    /**
     * Review submission with AI - grades both objective and subjective questions
     */
    public JsonNode reviewSubmissionWithAI(String submissionId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        // Fetch submission from student service
        String submissionUrl = gatewayUrl + "/api/student/exams/submissions/" + submissionId;
        ResponseEntity<JsonNode> submissionResponse = getRestTemplate().exchange(
            submissionUrl,
            HttpMethod.GET,
            new HttpEntity<>(new HttpHeaders()),
            JsonNode.class
        );
        
        if (!submissionResponse.getStatusCode().is2xxSuccessful() || submissionResponse.getBody() == null) {
            throw new IllegalArgumentException("Submission not found: " + submissionId);
        }
        
        JsonNode submission = submissionResponse.getBody();
        String assessmentId = submission.get("assessmentId").asText();
        
        Assessment exam = assessmentRepository.findByIdAndClientId(assessmentId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Assessment not found: " + assessmentId));
        
        if (exam.getAssessmentType() != Assessment.AssessmentType.EXAM) {
            throw new IllegalArgumentException("Assessment is not an exam");
        }
        
        // Get all questions for this exam
        List<QuizQuestion> questions = quizQuestionRepository.findByAssessmentIdAndClientIdOrderBySequenceAsc(
            exam.getId(), clientId);
        
        JsonNode answersJson = submission.get("answersJson");
        if (answersJson == null) {
            throw new IllegalStateException("No answers found in submission");
        }
        
        double totalScore = 0.0;
        double maxScore = 0.0;
        ObjectNode reviewFeedback = objectMapper.createObjectNode();
        ArrayNode questionReviews = objectMapper.createArrayNode();
        
        for (QuizQuestion question : questions) {
            maxScore += question.getPoints();
            
            JsonNode answerNode = answersJson.get(question.getId());
            if (answerNode == null) {
                // No answer provided
                questionReviews.add(createQuestionReview(question.getId(), 0.0, question.getPoints(), 
                    "No answer provided", false));
                continue;
            }
            
            double pointsEarned = 0.0;
            String feedback = "";
            boolean isCorrect = false;
            
            if (question.getQuestionType() == QuizQuestion.QuestionType.MULTIPLE_CHOICE ||
                question.getQuestionType() == QuizQuestion.QuestionType.TRUE_FALSE) {
                // Objective question - exact match
                pointsEarned = gradeObjectiveQuestion(question, answerNode);
                isCorrect = pointsEarned == question.getPoints();
                feedback = isCorrect ? "Correct" : "Incorrect";
            } else {
                // Subjective question - semantic similarity
                String studentAnswer = answerNode.asText();
                String tentativeAnswer = question.getEditedTentativeAnswer() != null ? 
                    question.getEditedTentativeAnswer() : question.getTentativeAnswer();
                
                if (tentativeAnswer != null && question.getUseTentativeAnswerForGrading()) {
                    double similarityScore = examReviewAIService.compareAnswersSemantically(
                        studentAnswer, tentativeAnswer);
                    pointsEarned = examReviewAIService.calculateGradeFromSimilarity(
                        similarityScore, question.getPoints());
                    feedback = String.format("Similarity: %.1f%%", similarityScore);
                    isCorrect = similarityScore >= 70.0;
                } else {
                    // No tentative answer available - cannot auto-grade
                    feedback = "Requires manual review";
                    pointsEarned = 0.0;
                }
            }
            
            totalScore += pointsEarned;
            questionReviews.add(createQuestionReview(question.getId(), pointsEarned, question.getPoints(), 
                feedback, isCorrect));
        }
        
        // Calculate percentage
        double percentage = 0.0;
        if (maxScore > 0) {
            percentage = (totalScore / maxScore) * 100.0;
        }
        
        // Store review feedback
        reviewFeedback.set("questionReviews", questionReviews);
        reviewFeedback.put("totalScore", totalScore);
        reviewFeedback.put("maxScore", maxScore);
        reviewFeedback.put("percentage", percentage);
        reviewFeedback.put("isPassed", percentage >= exam.getPassingScorePercentage());
        
        // Update submission in student service
        ObjectNode updateRequest = objectMapper.createObjectNode();
        updateRequest.put("score", totalScore);
        updateRequest.put("maxScore", maxScore);
        updateRequest.put("percentage", percentage);
        updateRequest.put("isPassed", percentage >= exam.getPassingScorePercentage());
        updateRequest.set("aiReviewFeedback", reviewFeedback);
        updateRequest.put("reviewStatus", "AI_REVIEWED");
        
        String updateUrl = gatewayUrl + "/api/assessments/submissions/" + submissionId + "/grade";
        getRestTemplate().exchange(
            updateUrl,
            HttpMethod.POST,
            new HttpEntity<>(updateRequest, new HttpHeaders()),
            JsonNode.class
        );
        
        logger.info("AI review completed for submission: {}, score: {}/{}", 
            submissionId, totalScore, maxScore);
        
        return reviewFeedback;
    }
    
    private double gradeObjectiveQuestion(QuizQuestion question, JsonNode answerNode) {
        if (question.getQuestionType() == QuizQuestion.QuestionType.MULTIPLE_CHOICE) {
            String selectedOptionId = answerNode.asText();
            
            // Find the correct option
            for (QuizOption option : question.getOptions()) {
                if (option.getId().equals(selectedOptionId) && option.getIsCorrect()) {
                    return question.getPoints();
                }
            }
            return 0.0;
        } else if (question.getQuestionType() == QuizQuestion.QuestionType.TRUE_FALSE) {
            boolean studentAnswer = answerNode.asBoolean();
            // For TRUE_FALSE, we need to check against the correct answer
            // Assuming the first option with isCorrect=true is the correct answer
            boolean correctAnswer = false;
            for (QuizOption option : question.getOptions()) {
                if (option.getIsCorrect()) {
                    correctAnswer = Boolean.parseBoolean(option.getOptionText());
                    break;
                }
            }
            return (studentAnswer == correctAnswer) ? question.getPoints() : 0.0;
        }
        return 0.0;
    }
    
    private ObjectNode createQuestionReview(String questionId, double pointsEarned, double maxPoints, 
                                           String feedback, boolean isCorrect) {
        ObjectNode review = objectMapper.createObjectNode();
        review.put("questionId", questionId);
        review.put("pointsEarned", pointsEarned);
        review.put("maxPoints", maxPoints);
        review.put("feedback", feedback);
        review.put("isCorrect", isCorrect);
        return review;
    }
    
    /**
     * Submit instructor review (manual override)
     */
    public JsonNode submitInstructorReview(String submissionId, BigDecimal score, 
                                           BigDecimal maxScore, JsonNode feedback) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        // Fetch submission to get assessment ID
        String submissionUrl = gatewayUrl + "/api/student/exams/submissions/" + submissionId;
        ResponseEntity<JsonNode> submissionResponse = getRestTemplate().exchange(
            submissionUrl,
            HttpMethod.GET,
            new HttpEntity<>(new HttpHeaders()),
            JsonNode.class
        );
        
        if (!submissionResponse.getStatusCode().is2xxSuccessful() || submissionResponse.getBody() == null) {
            throw new IllegalArgumentException("Submission not found: " + submissionId);
        }
        
        JsonNode submission = submissionResponse.getBody();
        String assessmentId = submission.get("assessmentId").asText();
        
        Assessment exam = assessmentRepository.findByIdAndClientId(assessmentId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Assessment not found"));
        
        BigDecimal percentage = BigDecimal.ZERO;
        boolean isPassed = false;
        if (maxScore != null && maxScore.compareTo(BigDecimal.ZERO) > 0) {
            percentage = score.divide(maxScore, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
            isPassed = percentage.compareTo(BigDecimal.valueOf(exam.getPassingScorePercentage())) >= 0;
        }
        
        // Update submission in student service
        ObjectNode updateRequest = objectMapper.createObjectNode();
        updateRequest.put("score", score.doubleValue());
        updateRequest.put("maxScore", maxScore.doubleValue());
        updateRequest.put("percentage", percentage.doubleValue());
        updateRequest.put("isPassed", isPassed);
        if (feedback != null) {
            updateRequest.set("aiReviewFeedback", feedback);
        }
        updateRequest.put("reviewStatus", "INSTRUCTOR_REVIEWED");
        
        String updateUrl = gatewayUrl + "/api/assessments/submissions/" + submissionId + "/grade";
        ResponseEntity<JsonNode> updateResponse = getRestTemplate().exchange(
            updateUrl,
            HttpMethod.POST,
            new HttpEntity<>(updateRequest, new HttpHeaders()),
            JsonNode.class
        );
        
        return updateResponse.getBody();
    }
    
    /**
     * Get review status
     */
    public String getReviewStatus(String submissionId) {
        try {
            String submissionUrl = gatewayUrl + "/api/student/exams/submissions/" + submissionId;
            ResponseEntity<JsonNode> submissionResponse = getRestTemplate().exchange(
                submissionUrl,
                HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()),
                JsonNode.class
            );
            
            if (submissionResponse.getStatusCode().is2xxSuccessful() && submissionResponse.getBody() != null) {
                JsonNode submission = submissionResponse.getBody();
                return submission.has("reviewStatus") ? 
                    submission.get("reviewStatus").asText() : "PENDING";
            }
        } catch (Exception e) {
            logger.error("Failed to get review status", e);
        }
        return "PENDING";
    }
    
    /**
     * Update tentative answer for a question
     */
    public QuizQuestion updateTentativeAnswer(String questionId, String editedTentativeAnswer, 
                                             Boolean useForGrading) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        QuizQuestion question = quizQuestionRepository.findByIdAndClientId(questionId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Question not found: " + questionId));
        
        if (editedTentativeAnswer != null) {
            question.setEditedTentativeAnswer(editedTentativeAnswer);
        }
        
        if (useForGrading != null) {
            question.setUseTentativeAnswerForGrading(useForGrading);
        }
        
        return quizQuestionRepository.save(question);
    }
}
