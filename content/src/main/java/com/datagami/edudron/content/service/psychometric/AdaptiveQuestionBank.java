package com.datagami.edudron.content.service.psychometric;

import java.util.*;

/**
 * Adaptive Question Bank - Questions for deep-dive modules
 * Each module has 6-10 questions that can be selected dynamically
 */
public class AdaptiveQuestionBank {
    
    private static final Map<String, List<Question>> MODULE_QUESTIONS = new HashMap<>();
    
    static {
        initializeScienceModule();
        initializeCommerceModule();
        initializeArtsModule();
    }
    
    private static void initializeScienceModule() {
        List<Question> questions = new ArrayList<>();
        
        // Science Q1: Advanced Math Interest
        Question q1 = new Question();
        q1.setId("SCIENCE_001");
        q1.setText("I enjoy solving challenging math problems"); // Base text - AI will personalize
        q1.setType(Question.QuestionType.LIKERT);
        q1.setOptions(Arrays.asList("Strongly Agree", "Agree", "Not Sure", "Disagree", "Strongly Disagree"));
        q1.setScoringTags(Arrays.asList(
            new ScoringTag("STREAM", "Science", Map.of(
                "Strongly Agree", 2, "Agree", 1, "Not Sure", 0, "Disagree", -1, "Strongly Disagree", -2
            )),
            new ScoringTag("INDICATOR", "math_confidence", Map.of(
                "Strongly Agree", 2, "Agree", 1, "Not Sure", 0, "Disagree", -1, "Strongly Disagree", -2
            )),
            new ScoringTag("RIASEC", "I", Map.of(
                "Strongly Agree", 1, "Agree", 1, "Not Sure", 0, "Disagree", 0, "Strongly Disagree", 0
            ))
        ));
        q1.setModule("SCIENCE");
        q1.setOrder(1);
        questions.add(q1);
        
        // Science Q2: Physics/Chemistry Interest
        Question q2 = new Question();
        q2.setId("SCIENCE_002");
        q2.setText("I'm curious about how things work in the natural world"); // Base text - AI will personalize
        q2.setType(Question.QuestionType.LIKERT);
        q2.setOptions(Arrays.asList("Strongly Agree", "Agree", "Not Sure", "Disagree", "Strongly Disagree"));
        q2.setScoringTags(Arrays.asList(
            new ScoringTag("STREAM", "Science", Map.of(
                "Strongly Agree", 2, "Agree", 1, "Not Sure", 0, "Disagree", -1, "Strongly Disagree", -2
            )),
            new ScoringTag("RIASEC", "I", Map.of(
                "Strongly Agree", 2, "Agree", 1, "Not Sure", 0, "Disagree", -1, "Strongly Disagree", -2
            ))
        ));
        q2.setModule("SCIENCE");
        q2.setOrder(2);
        questions.add(q2);
        
        // Science Q3: Biology Interest
        Question q3 = new Question();
        q3.setId("SCIENCE_003");
        q3.setText("I enjoy learning about living things and how they work"); // Base text - AI will personalize
        q3.setType(Question.QuestionType.LIKERT);
        q3.setOptions(Arrays.asList("Strongly Agree", "Agree", "Not Sure", "Disagree", "Strongly Disagree"));
        q3.setScoringTags(Arrays.asList(
            new ScoringTag("STREAM", "Science", Map.of(
                "Strongly Agree", 2, "Agree", 1, "Not Sure", 0, "Disagree", -1, "Strongly Disagree", -2
            )),
            new ScoringTag("RIASEC", "I", Map.of(
                "Strongly Agree", 1, "Agree", 1, "Not Sure", 0, "Disagree", 0, "Strongly Disagree", 0
            )),
            new ScoringTag("RIASEC", "R", Map.of(
                "Strongly Agree", 1, "Agree", 1, "Not Sure", 0, "Disagree", 0, "Strongly Disagree", 0
            ))
        ));
        q3.setModule("SCIENCE");
        q3.setOrder(3);
        questions.add(q3);
        
        // Science Q4: Problem-solving approach - Logic vs Creativity (Forced Choice)
        Question q4 = new Question();
        q4.setId("SCIENCE_004");
        q4.setText("When facing a problem, would you rather analyze it step by step using logic or try different creative approaches?"); // Base text - AI will personalize but keep options aligned
        q4.setType(Question.QuestionType.FORCED_CHOICE);
        q4.setOptions(Arrays.asList("Analyze it step by step using logic", "Try different creative approaches"));
        q4.setScoringTags(Arrays.asList(
            new ScoringTag("STREAM", "Science", Map.of("Analyze it step by step using logic", 2, "Try different creative approaches", 0)),
            new ScoringTag("INDICATOR", "logic_confidence", Map.of("Analyze it step by step using logic", 2, "Try different creative approaches", 0)),
            new ScoringTag("RIASEC", "I", Map.of("Analyze it step by step using logic", 1, "Try different creative approaches", 0))
        ));
        q4.setModule("SCIENCE");
        q4.setOrder(4);
        questions.add(q4);
        
        // Science Q5: Data and Analysis
        Question q5 = new Question();
        q5.setId("SCIENCE_005");
        q5.setText("I enjoy looking at charts and graphs to spot patterns"); // Base text - AI will personalize
        q5.setType(Question.QuestionType.LIKERT);
        q5.setOptions(Arrays.asList("Strongly Agree", "Agree", "Not Sure", "Disagree", "Strongly Disagree"));
        q5.setScoringTags(Arrays.asList(
            new ScoringTag("STREAM", "Science", Map.of(
                "Strongly Agree", 2, "Agree", 1, "Not Sure", 0, "Disagree", -1, "Strongly Disagree", -2
            )),
            new ScoringTag("RIASEC", "I", Map.of(
                "Strongly Agree", 1, "Agree", 1, "Not Sure", 0, "Disagree", 0, "Strongly Disagree", 0
            )),
            new ScoringTag("RIASEC", "C", Map.of(
                "Strongly Agree", 1, "Agree", 1, "Not Sure", 0, "Disagree", 0, "Strongly Disagree", 0
            ))
        ));
        q5.setModule("SCIENCE");
        q5.setOrder(5);
        questions.add(q5);
        
        // Science Q6: Technology Interest
        Question q6 = new Question();
        q6.setId("SCIENCE_006");
        q6.setText("I'm interested in how technology and programming work"); // Base text - AI will personalize
        q6.setType(Question.QuestionType.LIKERT);
        q6.setOptions(Arrays.asList("Strongly Agree", "Agree", "Not Sure", "Disagree", "Strongly Disagree"));
        q6.setScoringTags(Arrays.asList(
            new ScoringTag("STREAM", "Science", Map.of(
                "Strongly Agree", 2, "Agree", 1, "Not Sure", 0, "Disagree", -1, "Strongly Disagree", -2
            )),
            new ScoringTag("RIASEC", "I", Map.of(
                "Strongly Agree", 1, "Agree", 1, "Not Sure", 0, "Disagree", 0, "Strongly Disagree", 0
            )),
            new ScoringTag("RIASEC", "R", Map.of(
                "Strongly Agree", 1, "Agree", 1, "Not Sure", 0, "Disagree", 0, "Strongly Disagree", 0
            ))
        ));
        q6.setModule("SCIENCE");
        q6.setOrder(6);
        questions.add(q6);
        
        MODULE_QUESTIONS.put("SCIENCE", questions);
    }
    
    private static void initializeCommerceModule() {
        List<Question> questions = new ArrayList<>();
        
        // Commerce Q1: Business Interest
        Question q1 = new Question();
        q1.setId("COMMERCE_001");
        q1.setText("I'm curious about how businesses operate and make money"); // Base text - AI will personalize
        q1.setType(Question.QuestionType.LIKERT);
        q1.setOptions(Arrays.asList("Strongly Agree", "Agree", "Not Sure", "Disagree", "Strongly Disagree"));
        q1.setScoringTags(Arrays.asList(
            new ScoringTag("STREAM", "Commerce", Map.of(
                "Strongly Agree", 2, "Agree", 1, "Not Sure", 0, "Disagree", -1, "Strongly Disagree", -2
            )),
            new ScoringTag("RIASEC", "E", Map.of(
                "Strongly Agree", 2, "Agree", 1, "Not Sure", 0, "Disagree", -1, "Strongly Disagree", -2
            ))
        ));
        q1.setModule("COMMERCE");
        q1.setOrder(1);
        questions.add(q1);
        
        // Commerce Q2: Finance Interest
        Question q2 = new Question();
        q2.setId("COMMERCE_002");
        q2.setText("I enjoy learning about money, finance, and economics"); // Base text - AI will personalize
        q2.setType(Question.QuestionType.LIKERT);
        q2.setOptions(Arrays.asList("Strongly Agree", "Agree", "Not Sure", "Disagree", "Strongly Disagree"));
        q2.setScoringTags(Arrays.asList(
            new ScoringTag("STREAM", "Commerce", Map.of(
                "Strongly Agree", 2, "Agree", 1, "Not Sure", 0, "Disagree", -1, "Strongly Disagree", -2
            )),
            new ScoringTag("RIASEC", "C", Map.of(
                "Strongly Agree", 1, "Agree", 1, "Not Sure", 0, "Disagree", 0, "Strongly Disagree", 0
            )),
            new ScoringTag("RIASEC", "E", Map.of(
                "Strongly Agree", 1, "Agree", 1, "Not Sure", 0, "Disagree", 0, "Strongly Disagree", 0
            ))
        ));
        q2.setModule("COMMERCE");
        q2.setOrder(2);
        questions.add(q2);
        
        // Commerce Q3: Decision Making
        Question q3 = new Question();
        q3.setId("COMMERCE_003");
        q3.setText("I enjoy making decisions and taking calculated risks"); // Base text - AI will personalize
        q3.setType(Question.QuestionType.LIKERT);
        q3.setOptions(Arrays.asList("Strongly Agree", "Agree", "Not Sure", "Disagree", "Strongly Disagree"));
        q3.setScoringTags(Arrays.asList(
            new ScoringTag("STREAM", "Commerce", Map.of(
                "Strongly Agree", 1, "Agree", 1, "Not Sure", 0, "Disagree", -1, "Strongly Disagree", -1
            )),
            new ScoringTag("RIASEC", "E", Map.of(
                "Strongly Agree", 2, "Agree", 1, "Not Sure", 0, "Disagree", -1, "Strongly Disagree", -2
            )),
            new ScoringTag("INDICATOR", "leadership", Map.of(
                "Strongly Agree", 1, "Agree", 1, "Not Sure", 0, "Disagree", 0, "Strongly Disagree", 0
            ))
        ));
        q3.setModule("COMMERCE");
        q3.setOrder(3);
        questions.add(q3);
        
        // Commerce Q4: Organization Preference
        Question q4 = new Question();
        q4.setId("COMMERCE_004");
        q4.setText("I prefer tasks with clear guidelines and deadlines"); // Base text - AI will personalize
        q4.setType(Question.QuestionType.LIKERT);
        q4.setOptions(Arrays.asList("Strongly Agree", "Agree", "Not Sure", "Disagree", "Strongly Disagree"));
        q4.setScoringTags(Arrays.asList(
            new ScoringTag("STREAM", "Commerce", Map.of(
                "Strongly Agree", 1, "Agree", 1, "Not Sure", 0, "Disagree", -1, "Strongly Disagree", -1
            )),
            new ScoringTag("RIASEC", "C", Map.of(
                "Strongly Agree", 2, "Agree", 1, "Not Sure", 0, "Disagree", -1, "Strongly Disagree", -2
            ))
        ));
        q4.setModule("COMMERCE");
        q4.setOrder(4);
        questions.add(q4);
        
        // Commerce Q5: Sales/Marketing Interest
        Question q5 = new Question();
        q5.setId("COMMERCE_005");
        q5.setText("I enjoy sales, marketing, and working with customers"); // Base text - AI will personalize
        q5.setType(Question.QuestionType.LIKERT);
        q5.setOptions(Arrays.asList("Strongly Agree", "Agree", "Not Sure", "Disagree", "Strongly Disagree"));
        q5.setScoringTags(Arrays.asList(
            new ScoringTag("STREAM", "Commerce", Map.of(
                "Strongly Agree", 2, "Agree", 1, "Not Sure", 0, "Disagree", -1, "Strongly Disagree", -2
            )),
            new ScoringTag("RIASEC", "E", Map.of(
                "Strongly Agree", 2, "Agree", 1, "Not Sure", 0, "Disagree", -1, "Strongly Disagree", -2
            )),
            new ScoringTag("RIASEC", "S", Map.of(
                "Strongly Agree", 1, "Agree", 1, "Not Sure", 0, "Disagree", 0, "Strongly Disagree", 0
            ))
        ));
        q5.setModule("COMMERCE");
        q5.setOrder(5);
        questions.add(q5);
        
        // Commerce Q6: Management Interest
        Question q6 = new Question();
        q6.setId("COMMERCE_006");
        q6.setText("I'm interested in managing teams and projects"); // Base text - AI will personalize
        q6.setType(Question.QuestionType.LIKERT);
        q6.setOptions(Arrays.asList("Strongly Agree", "Agree", "Not Sure", "Disagree", "Strongly Disagree"));
        q6.setScoringTags(Arrays.asList(
            new ScoringTag("STREAM", "Commerce", Map.of(
                "Strongly Agree", 1, "Agree", 1, "Not Sure", 0, "Disagree", -1, "Strongly Disagree", -1
            )),
            new ScoringTag("RIASEC", "E", Map.of(
                "Strongly Agree", 2, "Agree", 1, "Not Sure", 0, "Disagree", -1, "Strongly Disagree", -2
            )),
            new ScoringTag("INDICATOR", "leadership", Map.of(
                "Strongly Agree", 2, "Agree", 1, "Not Sure", 0, "Disagree", 0, "Strongly Disagree", 0
            ))
        ));
        q6.setModule("COMMERCE");
        q6.setOrder(6);
        questions.add(q6);
        
        MODULE_QUESTIONS.put("COMMERCE", questions);
    }
    
    private static void initializeArtsModule() {
        List<Question> questions = new ArrayList<>();
        
        // Arts Q1: Literature Interest
        Question q1 = new Question();
        q1.setId("ARTS_001");
        q1.setText("I enjoy reading literature, poetry, and analyzing texts"); // Base text - AI will personalize
        q1.setType(Question.QuestionType.LIKERT);
        q1.setOptions(Arrays.asList("Strongly Agree", "Agree", "Not Sure", "Disagree", "Strongly Disagree"));
        q1.setScoringTags(Arrays.asList(
            new ScoringTag("STREAM", "Arts", Map.of(
                "Strongly Agree", 2, "Agree", 1, "Not Sure", 0, "Disagree", -1, "Strongly Disagree", -2
            )),
            new ScoringTag("RIASEC", "A", Map.of(
                "Strongly Agree", 2, "Agree", 1, "Not Sure", 0, "Disagree", -1, "Strongly Disagree", -2
            )),
            new ScoringTag("INDICATOR", "language_confidence", Map.of(
                "Strongly Agree", 1, "Agree", 1, "Not Sure", 0, "Disagree", 0, "Strongly Disagree", 0
            ))
        ));
        q1.setModule("ARTS");
        q1.setOrder(1);
        questions.add(q1);
        
        // Arts Q2: History Interest
        Question q2 = new Question();
        q2.setId("ARTS_002");
        q2.setText("I'm interested in history, culture, and social studies"); // Base text - AI will personalize
        q2.setType(Question.QuestionType.LIKERT);
        q2.setOptions(Arrays.asList("Strongly Agree", "Agree", "Not Sure", "Disagree", "Strongly Disagree"));
        q2.setScoringTags(Arrays.asList(
            new ScoringTag("STREAM", "Arts", Map.of(
                "Strongly Agree", 2, "Agree", 1, "Not Sure", 0, "Disagree", -1, "Strongly Disagree", -2
            )),
            new ScoringTag("RIASEC", "S", Map.of(
                "Strongly Agree", 1, "Agree", 1, "Not Sure", 0, "Disagree", 0, "Strongly Disagree", 0
            )),
            new ScoringTag("RIASEC", "A", Map.of(
                "Strongly Agree", 1, "Agree", 1, "Not Sure", 0, "Disagree", 0, "Strongly Disagree", 0
            ))
        ));
        q2.setModule("ARTS");
        q2.setOrder(2);
        questions.add(q2);
        
        // Arts Q3: Creative Expression
        Question q3 = new Question();
        q3.setId("ARTS_003");
        q3.setText("I enjoy expressing myself through creative writing, art, or performance"); // Base text - AI will personalize
        q3.setType(Question.QuestionType.LIKERT);
        q3.setOptions(Arrays.asList("Strongly Agree", "Agree", "Not Sure", "Disagree", "Strongly Disagree"));
        q3.setScoringTags(Arrays.asList(
            new ScoringTag("STREAM", "Arts", Map.of(
                "Strongly Agree", 2, "Agree", 1, "Not Sure", 0, "Disagree", -1, "Strongly Disagree", -2
            )),
            new ScoringTag("RIASEC", "A", Map.of(
                "Strongly Agree", 2, "Agree", 1, "Not Sure", 0, "Disagree", -1, "Strongly Disagree", -2
            )),
            new ScoringTag("INDICATOR", "expression_confidence", Map.of(
                "Strongly Agree", 2, "Agree", 1, "Not Sure", 0, "Disagree", 0, "Strongly Disagree", 0
            ))
        ));
        q3.setModule("ARTS");
        q3.setOrder(3);
        questions.add(q3);
        
        // Arts Q4: Social Issues Interest
        Question q4 = new Question();
        q4.setId("ARTS_004");
        q4.setText("I'm interested in social issues, psychology, and human behavior"); // Base text - AI will personalize
        q4.setType(Question.QuestionType.LIKERT);
        q4.setOptions(Arrays.asList("Strongly Agree", "Agree", "Not Sure", "Disagree", "Strongly Disagree"));
        q4.setScoringTags(Arrays.asList(
            new ScoringTag("STREAM", "Arts", Map.of(
                "Strongly Agree", 2, "Agree", 1, "Not Sure", 0, "Disagree", -1, "Strongly Disagree", -2
            )),
            new ScoringTag("RIASEC", "S", Map.of(
                "Strongly Agree", 2, "Agree", 1, "Not Sure", 0, "Disagree", -1, "Strongly Disagree", -2
            )),
            new ScoringTag("RIASEC", "I", Map.of(
                "Strongly Agree", 1, "Agree", 1, "Not Sure", 0, "Disagree", 0, "Strongly Disagree", 0
            ))
        ));
        q4.setModule("ARTS");
        q4.setOrder(4);
        questions.add(q4);
        
        // Arts Q5: Communication Preference
        Question q5 = new Question();
        q5.setId("ARTS_005");
        q5.setText("I prefer activities that involve communication and interaction"); // Base text - AI will personalize
        q5.setType(Question.QuestionType.LIKERT);
        q5.setOptions(Arrays.asList("Strongly Agree", "Agree", "Not Sure", "Disagree", "Strongly Disagree"));
        q5.setScoringTags(Arrays.asList(
            new ScoringTag("STREAM", "Arts", Map.of(
                "Strongly Agree", 1, "Agree", 1, "Not Sure", 0, "Disagree", -1, "Strongly Disagree", -1
            )),
            new ScoringTag("RIASEC", "S", Map.of(
                "Strongly Agree", 2, "Agree", 1, "Not Sure", 0, "Disagree", -1, "Strongly Disagree", -2
            )),
            new ScoringTag("INDICATOR", "expression_confidence", Map.of(
                "Strongly Agree", 1, "Agree", 1, "Not Sure", 0, "Disagree", 0, "Strongly Disagree", 0
            ))
        ));
        q5.setModule("ARTS");
        q5.setOrder(5);
        questions.add(q5);
        
        // Arts Q6: Philosophy/Abstract Thinking
        Question q6 = new Question();
        q6.setId("ARTS_006");
        q6.setText("I enjoy thinking about abstract concepts, philosophy, and ethics"); // Base text - AI will personalize
        q6.setType(Question.QuestionType.LIKERT);
        q6.setOptions(Arrays.asList("Strongly Agree", "Agree", "Not Sure", "Disagree", "Strongly Disagree"));
        q6.setScoringTags(Arrays.asList(
            new ScoringTag("STREAM", "Arts", Map.of(
                "Strongly Agree", 2, "Agree", 1, "Not Sure", 0, "Disagree", -1, "Strongly Disagree", -2
            )),
            new ScoringTag("RIASEC", "A", Map.of(
                "Strongly Agree", 1, "Agree", 1, "Not Sure", 0, "Disagree", 0, "Strongly Disagree", 0
            )),
            new ScoringTag("RIASEC", "I", Map.of(
                "Strongly Agree", 1, "Agree", 1, "Not Sure", 0, "Disagree", 0, "Strongly Disagree", 0
            ))
        ));
        q6.setModule("ARTS");
        q6.setOrder(6);
        questions.add(q6);
        
        MODULE_QUESTIONS.put("ARTS", questions);
    }
    
    /**
     * Get all questions for a module
     */
    public static List<Question> getModuleQuestions(String module) {
        return new ArrayList<>(MODULE_QUESTIONS.getOrDefault(module.toUpperCase(), new ArrayList<>()));
    }
    
    /**
     * Get a specific question by ID
     */
    public static Question getQuestionById(String questionId) {
        for (List<Question> questions : MODULE_QUESTIONS.values()) {
            for (Question q : questions) {
                if (q.getId().equals(questionId)) {
                    return q;
                }
            }
        }
        return null;
    }
    
    /**
     * Select next adaptive question based on current state
     * Returns list of available questions for AI selection
     */
    public static List<Question> getAvailableQuestions(String module, List<Answer> answeredQuestions) {
        List<Question> moduleQuestions = getModuleQuestions(module);
        
        // Filter out already answered questions
        Set<String> answeredIds = new HashSet<>();
        for (Answer answer : answeredQuestions) {
            answeredIds.add(answer.getQuestionId());
        }
        
        List<Question> available = new ArrayList<>();
        for (Question question : moduleQuestions) {
            if (!answeredIds.contains(question.getId())) {
                available.add(question);
            }
        }
        
        return available;
    }
    
    /**
     * Select next adaptive question based on current state
     * This is a simple implementation - can be enhanced with AI-based selection
     */
    public static Question selectNextAdaptiveQuestion(String module, List<Answer> answeredQuestions, SessionState state) {
        List<Question> available = getAvailableQuestions(module, answeredQuestions);
        
        if (available.isEmpty()) {
            return null; // All questions answered
        }
        
        // Return first available (AI selection will be done in service layer)
        return available.get(0);
    }
}
