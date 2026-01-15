package com.datagami.edudron.content.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.common.UlidGenerator;
import com.datagami.edudron.content.domain.Assessment;
import com.datagami.edudron.content.domain.Lecture;
import com.datagami.edudron.content.domain.LectureContent;
import com.datagami.edudron.content.domain.QuizOption;
import com.datagami.edudron.content.domain.QuizQuestion;
import com.datagami.edudron.content.domain.Section;
import com.datagami.edudron.content.repo.LectureContentRepository;
import com.datagami.edudron.content.repo.LectureRepository;
import com.datagami.edudron.content.repo.QuizQuestionRepository;
import com.datagami.edudron.content.repo.SectionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class ExamGenerationService {
    
    private static final Logger logger = LoggerFactory.getLogger(ExamGenerationService.class);
    
    @Autowired
    private FoundryAIService foundryAIService;
    
    @Autowired
    private SectionRepository sectionRepository;
    
    @Autowired
    private LectureRepository lectureRepository;
    
    @Autowired
    private LectureContentRepository lectureContentRepository;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    public void generateQuestionsFromModules(Assessment exam, List<String> moduleIds, 
                                            Integer numberOfQuestions, String difficulty) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        logger.info("Generating exam questions from {} modules for exam: {}", moduleIds.size(), exam.getId());
        
        // Extract content from selected modules
        String moduleContent = extractContentFromModules(moduleIds, clientId);
        
        if (moduleContent == null || moduleContent.trim().isEmpty()) {
            throw new IllegalStateException("No content found in selected modules");
        }
        
        // Generate questions using AI
        int questionsToGenerate = numberOfQuestions != null ? numberOfQuestions : 10;
        String difficultyLevel = difficulty != null ? difficulty : "INTERMEDIATE";
        
        logger.info("Calling AI to generate {} questions with difficulty: {}", questionsToGenerate, difficultyLevel);
        String aiResponse = foundryAIService.generateExamQuestions(moduleContent, questionsToGenerate, difficultyLevel);
        
        // Parse AI response and create questions
        List<QuestionData> questions = parseAIResponse(aiResponse);
        
        // Create QuizQuestion entities
        int sequence = 1;
        for (QuestionData questionData : questions) {
            QuizQuestion question = createQuizQuestion(exam, questionData, sequence, clientId);
            exam.getQuestions().add(question);
            sequence++;
        }
        
        logger.info("Successfully generated {} questions for exam: {}", questions.size(), exam.getId());
    }
    
    private String extractContentFromModules(List<String> moduleIds, UUID clientId) {
        StringBuilder content = new StringBuilder();
        
        for (String moduleId : moduleIds) {
            Section section = sectionRepository.findByIdAndClientId(moduleId, clientId)
                .orElse(null);
            
            if (section == null) {
                logger.warn("Section not found: {}", moduleId);
                continue;
            }
            
            content.append("## Module: ").append(section.getTitle()).append("\n");
            if (section.getDescription() != null) {
                content.append(section.getDescription()).append("\n\n");
            }
            
            // Get lectures in this section
            List<Lecture> lectures = lectureRepository.findBySectionIdAndClientIdOrderBySequenceAsc(moduleId, clientId);
            
            for (Lecture lecture : lectures) {
                content.append("### Lecture: ").append(lecture.getTitle()).append("\n");
                if (lecture.getDescription() != null) {
                    content.append(lecture.getDescription()).append("\n");
                }
                
                // Get lecture content
                List<LectureContent> contents = lectureContentRepository.findByLectureIdAndClientIdOrderBySequenceAsc(
                    lecture.getId(), clientId);
                
                for (LectureContent lc : contents) {
                    if (lc.getTextContent() != null && !lc.getTextContent().trim().isEmpty()) {
                        content.append(lc.getTextContent()).append("\n");
                    }
                    if (lc.getDescription() != null && !lc.getDescription().trim().isEmpty()) {
                        content.append(lc.getDescription()).append("\n");
                    }
                }
                content.append("\n");
            }
        }
        
        return content.toString();
    }
    
    private List<QuestionData> parseAIResponse(String aiResponse) {
        List<QuestionData> questions = new ArrayList<>();
        
        try {
            JsonNode jsonNode = objectMapper.readTree(aiResponse);
            
            if (jsonNode.isArray()) {
                for (JsonNode questionNode : jsonNode) {
                    QuestionData qd = new QuestionData();
                    qd.questionText = questionNode.get("question").asText();
                    qd.questionType = questionNode.get("type").asText();
                    qd.points = questionNode.has("points") ? questionNode.get("points").asInt() : 1;
                    
                    // For multiple choice questions
                    if ("MULTIPLE_CHOICE".equals(qd.questionType) && questionNode.has("options")) {
                        qd.options = new ArrayList<>();
                        for (JsonNode optionNode : questionNode.get("options")) {
                            QuestionOption opt = new QuestionOption();
                            opt.text = optionNode.get("text").asText();
                            opt.isCorrect = optionNode.has("isCorrect") && optionNode.get("isCorrect").asBoolean();
                            qd.options.add(opt);
                        }
                    }
                    
                    // For subjective questions, get tentative answer
                    if ("SHORT_ANSWER".equals(qd.questionType) || "ESSAY".equals(qd.questionType)) {
                        if (questionNode.has("tentativeAnswer")) {
                            qd.tentativeAnswer = questionNode.get("tentativeAnswer").asText();
                        }
                    }
                    
                    questions.add(qd);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to parse AI response for exam questions", e);
            throw new RuntimeException("Failed to parse AI-generated questions: " + e.getMessage(), e);
        }
        
        return questions;
    }
    
    private QuizQuestion createQuizQuestion(Assessment exam, QuestionData questionData, int sequence, UUID clientId) {
        QuizQuestion question = new QuizQuestion();
        question.setId(UlidGenerator.nextUlid());
        question.setClientId(clientId);
        question.setAssessmentId(exam.getId());
        question.setQuestionText(questionData.questionText);
        question.setPoints(questionData.points);
        question.setSequence(sequence);
        
        // Set question type
        try {
            question.setQuestionType(QuizQuestion.QuestionType.valueOf(questionData.questionType));
        } catch (IllegalArgumentException e) {
            logger.warn("Unknown question type: {}, defaulting to MULTIPLE_CHOICE", questionData.questionType);
            question.setQuestionType(QuizQuestion.QuestionType.MULTIPLE_CHOICE);
        }
        
        // Set tentative answer for subjective questions
        if (questionData.tentativeAnswer != null && !questionData.tentativeAnswer.trim().isEmpty()) {
            question.setTentativeAnswer(questionData.tentativeAnswer);
            question.setUseTentativeAnswerForGrading(true);
        }
        
        // Create options for multiple choice questions
        if (question.getQuestionType() == QuizQuestion.QuestionType.MULTIPLE_CHOICE && 
            questionData.options != null && !questionData.options.isEmpty()) {
            
            int optionSequence = 1;
            for (QuestionOption opt : questionData.options) {
                QuizOption option = new QuizOption();
                option.setId(UlidGenerator.nextUlid());
                option.setClientId(clientId);
                option.setQuestionId(question.getId());
                option.setOptionText(opt.text);
                option.setIsCorrect(opt.isCorrect);
                option.setSequence(optionSequence++);
                question.getOptions().add(option);
            }
        }
        
        return question;
    }
    
    // Inner classes for parsing
    private static class QuestionData {
        String questionText;
        String questionType;
        int points = 1;
        List<QuestionOption> options;
        String tentativeAnswer;
    }
    
    private static class QuestionOption {
        String text;
        boolean isCorrect;
    }
}
