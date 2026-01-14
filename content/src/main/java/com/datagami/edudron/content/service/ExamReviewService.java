package com.datagami.edudron.content.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.content.domain.Assessment;
import com.datagami.edudron.content.domain.QuizOption;
import com.datagami.edudron.content.domain.QuizQuestion;
import com.datagami.edudron.content.repo.AssessmentRepository;
import com.datagami.edudron.content.repo.QuizQuestionRepository;
import com.datagami.edudron.student.domain.AssessmentSubmission;
import com.datagami.edudron.student.repo.AssessmentSubmissionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
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
    private AssessmentSubmissionRepository submissionRepository;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    /**
     * Review submission with AI - grades both objective and subjective questions
     */
    public AssessmentSubmission reviewSubmissionWithAI(String submissionId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        AssessmentSubmission submission = submissionRepository.findById(submissionId)
            .orElseThrow(() -> new IllegalArgumentException("Submission not found: " + submissionId));
        
        if (!submission.getClientId().equals(clientId)) {
            throw new IllegalArgumentException("Submission not found: " + submissionId);
        }
        
        Assessment exam = assessmentRepository.findByIdAndClientId(submission.getAssessmentId(), clientId)
            .orElseThrow(() -> new IllegalArgumentException("Assessment not found: " + submission.getAssessmentId()));
        
        if (exam.getAssessmentType() != Assessment.AssessmentType.EXAM) {
            throw new IllegalArgumentException("Assessment is not an exam");
        }
        
        // Get all questions for this exam
        List<QuizQuestion> questions = quizQuestionRepository.findByAssessmentIdAndClientIdOrderBySequenceAsc(
            exam.getId(), clientId);
        
        JsonNode answersJson = submission.getAnswersJson();
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
        
        // Update submission
        submission.setScore(BigDecimal.valueOf(totalScore).setScale(2, RoundingMode.HALF_UP));
        submission.setMaxScore(BigDecimal.valueOf(maxScore).setScale(2, RoundingMode.HALF_UP));
        
        if (maxScore > 0) {
            double percentage = (totalScore / maxScore) * 100.0;
            submission.setPercentage(BigDecimal.valueOf(percentage).setScale(2, RoundingMode.HALF_UP));
            submission.setIsPassed(percentage >= exam.getPassingScorePercentage());
        }
        
        submission.setGradedAt(OffsetDateTime.now());
        submission.setReviewStatus(AssessmentSubmission.ReviewStatus.AI_REVIEWED);
        
        // Store review feedback
        reviewFeedback.set("questionReviews", questionReviews);
        reviewFeedback.put("totalScore", totalScore);
        reviewFeedback.put("maxScore", maxScore);
        reviewFeedback.put("percentage", submission.getPercentage().doubleValue());
        submission.setAiReviewFeedback(reviewFeedback);
        
        AssessmentSubmission saved = submissionRepository.save(submission);
        logger.info("AI review completed for submission: {}, score: {}/{}", 
            submissionId, totalScore, maxScore);
        
        return saved;
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
    public AssessmentSubmission submitInstructorReview(String submissionId, BigDecimal score, 
                                                      BigDecimal maxScore, JsonNode feedback) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        AssessmentSubmission submission = submissionRepository.findById(submissionId)
            .orElseThrow(() -> new IllegalArgumentException("Submission not found: " + submissionId));
        
        if (!submission.getClientId().equals(clientId)) {
            throw new IllegalArgumentException("Submission not found: " + submissionId);
        }
        
        Assessment exam = assessmentRepository.findByIdAndClientId(submission.getAssessmentId(), clientId)
            .orElseThrow(() -> new IllegalArgumentException("Assessment not found"));
        
        submission.setScore(score);
        submission.setMaxScore(maxScore);
        
        if (maxScore != null && maxScore.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal percentage = score.divide(maxScore, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
            submission.setPercentage(percentage);
            submission.setIsPassed(percentage.compareTo(BigDecimal.valueOf(exam.getPassingScorePercentage())) >= 0);
        }
        
        submission.setGradedAt(OffsetDateTime.now());
        submission.setReviewStatus(AssessmentSubmission.ReviewStatus.INSTRUCTOR_REVIEWED);
        
        if (feedback != null) {
            submission.setAiReviewFeedback(feedback);
        }
        
        return submissionRepository.save(submission);
    }
    
    /**
     * Get review status
     */
    public AssessmentSubmission.ReviewStatus getReviewStatus(String submissionId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        AssessmentSubmission submission = submissionRepository.findById(submissionId)
            .orElseThrow(() -> new IllegalArgumentException("Submission not found: " + submissionId));
        
        if (!submission.getClientId().equals(clientId)) {
            throw new IllegalArgumentException("Submission not found: " + submissionId);
        }
        
        return submission.getReviewStatus() != null ? 
            submission.getReviewStatus() : AssessmentSubmission.ReviewStatus.PENDING;
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
