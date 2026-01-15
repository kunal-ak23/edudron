package com.datagami.edudron.content.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.common.UlidGenerator;
import com.datagami.edudron.content.domain.QuizOption;
import com.datagami.edudron.content.domain.QuizQuestion;
import com.datagami.edudron.content.repo.AssessmentRepository;
import com.datagami.edudron.content.repo.QuizOptionRepository;
import com.datagami.edudron.content.repo.QuizQuestionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class QuestionService {
    
    private static final Logger logger = LoggerFactory.getLogger(QuestionService.class);
    
    @Autowired
    private QuizQuestionRepository questionRepository;
    
    @Autowired
    private QuizOptionRepository optionRepository;
    
    @Autowired
    private AssessmentRepository assessmentRepository;
    
    public QuizQuestion createQuestion(String examId, QuizQuestion.QuestionType questionType, 
                                      String questionText, Integer points, 
                                      List<OptionData> options, String tentativeAnswer) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        // Verify exam exists
        assessmentRepository.findByIdAndClientId(examId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Exam not found: " + examId));
        
        // Get next sequence
        Integer maxSequence = questionRepository.findByAssessmentIdAndClientIdOrderBySequenceAsc(examId, clientId)
            .stream()
            .mapToInt(QuizQuestion::getSequence)
            .max()
            .orElse(0);
        int nextSequence = maxSequence + 1;
        
        // Validate question type requirements
        if (questionType == QuizQuestion.QuestionType.MULTIPLE_CHOICE) {
            if (options == null || options.size() < 2) {
                throw new IllegalArgumentException("Multiple choice questions require at least 2 options");
            }
            long correctCount = options.stream().filter(OptionData::isCorrect).count();
            if (correctCount == 0) {
                throw new IllegalArgumentException("Multiple choice questions require at least one correct answer");
            }
        } else if (questionType == QuizQuestion.QuestionType.TRUE_FALSE) {
            // For TRUE_FALSE questions, create True/False options automatically if not provided
            if (options == null || options.isEmpty()) {
                // Determine correct answer from tentativeAnswer
                boolean correctAnswer = tentativeAnswer != null && 
                    (tentativeAnswer.equalsIgnoreCase("true") || tentativeAnswer.equals("1"));
                options = new ArrayList<>();
                options.add(new OptionData("True", correctAnswer));
                options.add(new OptionData("False", !correctAnswer));
            }
        }
        
        QuizQuestion question = new QuizQuestion();
        question.setId(UlidGenerator.nextUlid());
        question.setClientId(clientId);
        question.setAssessmentId(examId);
        question.setQuestionType(questionType);
        question.setQuestionText(questionText);
        question.setPoints(points != null ? points : 1);
        question.setSequence(nextSequence);
        
        if (tentativeAnswer != null && !tentativeAnswer.isBlank()) {
            question.setTentativeAnswer(tentativeAnswer);
        }
        
        QuizQuestion saved = questionRepository.save(question);
        
        // Create options for MULTIPLE_CHOICE and TRUE_FALSE questions
        if ((questionType == QuizQuestion.QuestionType.MULTIPLE_CHOICE || 
             questionType == QuizQuestion.QuestionType.TRUE_FALSE) && options != null) {
            List<QuizOption> questionOptions = new ArrayList<>();
            for (int i = 0; i < options.size(); i++) {
                OptionData optData = options.get(i);
                QuizOption option = new QuizOption();
                option.setId(UlidGenerator.nextUlid());
                option.setClientId(clientId);
                option.setQuestionId(saved.getId());
                option.setOptionText(optData.getText());
                option.setIsCorrect(optData.isCorrect());
                option.setSequence(i + 1);
                questionOptions.add(option);
            }
            optionRepository.saveAll(questionOptions);
        }
        
        // Reload question with options
        QuizQuestion reloaded = questionRepository.findByIdAndClientId(saved.getId(), clientId)
            .orElse(saved);
        if (reloaded.getOptions() != null) {
            reloaded.getOptions().size(); // Force initialization
        }
        
        logger.info("Created question {} for exam {}", reloaded.getId(), examId);
        return reloaded;
    }
    
    public QuizQuestion updateQuestion(String questionId, String questionText, Integer points,
                                      List<OptionData> options, String tentativeAnswer) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        QuizQuestion question = questionRepository.findByIdAndClientId(questionId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Question not found: " + questionId));
        
        if (questionText != null) {
            question.setQuestionText(questionText);
        }
        if (points != null) {
            question.setPoints(points);
        }
        if (tentativeAnswer != null) {
            question.setTentativeAnswer(tentativeAnswer);
        }
        
        // Update options for MULTIPLE_CHOICE and TRUE_FALSE questions
        if ((question.getQuestionType() == QuizQuestion.QuestionType.MULTIPLE_CHOICE || 
             question.getQuestionType() == QuizQuestion.QuestionType.TRUE_FALSE) && options != null) {
            // Delete existing options
            optionRepository.deleteByQuestionIdAndClientId(questionId, clientId);
            
            // Create new options
            List<QuizOption> questionOptions = new ArrayList<>();
            for (int i = 0; i < options.size(); i++) {
                OptionData optData = options.get(i);
                QuizOption option = new QuizOption();
                option.setId(UlidGenerator.nextUlid());
                option.setClientId(clientId);
                option.setQuestionId(questionId);
                option.setOptionText(optData.getText());
                option.setIsCorrect(optData.isCorrect());
                option.setSequence(i + 1);
                questionOptions.add(option);
            }
            optionRepository.saveAll(questionOptions);
            question.setOptions(questionOptions);
        }
        
        QuizQuestion updated = questionRepository.save(question);
        
        // Reload question with options
        QuizQuestion reloaded = questionRepository.findByIdAndClientId(questionId, clientId)
            .orElse(updated);
        if (reloaded.getOptions() != null) {
            reloaded.getOptions().size(); // Force initialization
        }
        
        logger.info("Updated question {}", questionId);
        return reloaded;
    }
    
    public void deleteQuestion(String questionId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        QuizQuestion question = questionRepository.findByIdAndClientId(questionId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Question not found: " + questionId));
        
        // Delete options first (cascade should handle this, but being explicit)
        optionRepository.deleteByQuestionIdAndClientId(questionId, clientId);
        
        // Delete question
        questionRepository.delete(question);
        logger.info("Deleted question {}", questionId);
    }
    
    public void reorderQuestions(String examId, List<String> questionIds) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        // Verify exam exists
        assessmentRepository.findByIdAndClientId(examId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Exam not found: " + examId));
        
        // Update sequence for each question
        for (int i = 0; i < questionIds.size(); i++) {
            String questionId = questionIds.get(i);
            QuizQuestion question = questionRepository.findByIdAndClientId(questionId, clientId)
                .orElseThrow(() -> new IllegalArgumentException("Question not found: " + questionId));
            
            if (!question.getAssessmentId().equals(examId)) {
                throw new IllegalArgumentException("Question does not belong to exam: " + examId);
            }
            
            question.setSequence(i + 1);
            questionRepository.save(question);
        }
        
        logger.info("Reordered {} questions for exam {}", questionIds.size(), examId);
    }
    
    // Helper class for option data
    public static class OptionData {
        private String text;
        private boolean correct;
        
        public OptionData() {}
        
        public OptionData(String text, boolean correct) {
            this.text = text;
            this.correct = correct;
        }
        
        public String getText() {
            return text;
        }
        
        public void setText(String text) {
            this.text = text;
        }
        
        public boolean isCorrect() {
            return correct;
        }
        
        public void setCorrect(boolean correct) {
            this.correct = correct;
        }
    }
}
