package com.datagami.edudron.content.service.psychometric;

import java.util.*;

/**
 * Core Question Bank - 18 fixed questions for Phase 1
 * These questions establish baseline interests, RIASEC profile, and skill comfort
 */
public class CoreQuestionBank {
    
    private static final List<Question> CORE_QUESTIONS = new ArrayList<>();
    
    static {
        initializeQuestions();
    }
    
    private static void initializeQuestions() {
        // Question 1: Math Confidence (Likert)
        Question q1 = new Question();
        q1.setId("CORE_001");
        q1.setText("I feel confident solving math problems"); // Base text - AI will personalize
        q1.setType(Question.QuestionType.LIKERT);
        q1.setOptions(Arrays.asList("Strongly Agree", "Agree", "Not Sure", "Disagree", "Strongly Disagree"));
        q1.setScoringTags(Arrays.asList(
            new ScoringTag("INDICATOR", "math_confidence", Map.of(
                "Strongly Agree", 2, "Agree", 1, "Not Sure", 0, "Disagree", -1, "Strongly Disagree", -2
            )),
            new ScoringTag("STREAM", "Science", Map.of(
                "Strongly Agree", 2, "Agree", 1, "Not Sure", 0, "Disagree", -1, "Strongly Disagree", -2
            )),
            new ScoringTag("RIASEC", "I", Map.of(
                "Strongly Agree", 1, "Agree", 1, "Not Sure", 0, "Disagree", 0, "Strongly Disagree", 0
            ))
        ));
        q1.setModule("CORE");
        q1.setOrder(1);
        CORE_QUESTIONS.add(q1);
        
        // Question 2: Language Confidence (Likert)
        Question q2 = new Question();
        q2.setId("CORE_002");
        q2.setText("I enjoy reading and writing");
        q2.setType(Question.QuestionType.LIKERT);
        q2.setOptions(Arrays.asList("Strongly Agree", "Agree", "Not Sure", "Disagree", "Strongly Disagree"));
        q2.setScoringTags(Arrays.asList(
            new ScoringTag("INDICATOR", "language_confidence", Map.of(
                "Strongly Agree", 2, "Agree", 1, "Not Sure", 0, "Disagree", -1, "Strongly Disagree", -2
            )),
            new ScoringTag("STREAM", "Arts", Map.of(
                "Strongly Agree", 1, "Agree", 1, "Not Sure", 0, "Disagree", -1, "Strongly Disagree", -1
            )),
            new ScoringTag("RIASEC", "A", Map.of(
                "Strongly Agree", 1, "Agree", 1, "Not Sure", 0, "Disagree", 0, "Strongly Disagree", 0
            ))
        ));
        q2.setModule("CORE");
        q2.setOrder(2);
        CORE_QUESTIONS.add(q2);
        
        // Question 3: Logic/Problem Solving (Likert)
        Question q3 = new Question();
        q3.setId("CORE_003");
        q3.setText("I enjoy solving puzzles and logical problems"); // Base text - AI will personalize
        q3.setType(Question.QuestionType.LIKERT);
        q3.setOptions(Arrays.asList("Strongly Agree", "Agree", "Not Sure", "Disagree", "Strongly Disagree"));
        q3.setScoringTags(Arrays.asList(
            new ScoringTag("INDICATOR", "logic_confidence", Map.of(
                "Strongly Agree", 2, "Agree", 1, "Not Sure", 0, "Disagree", -1, "Strongly Disagree", -2
            )),
            new ScoringTag("STREAM", "Science", Map.of(
                "Strongly Agree", 2, "Agree", 1, "Not Sure", 0, "Disagree", -1, "Strongly Disagree", -2
            )),
            new ScoringTag("RIASEC", "I", Map.of(
                "Strongly Agree", 2, "Agree", 1, "Not Sure", 0, "Disagree", 0, "Strongly Disagree", 0
            ))
        ));
        q3.setModule("CORE");
        q3.setOrder(3);
        CORE_QUESTIONS.add(q3);
        
        // Question 4: Teamwork (Likert)
        Question q4 = new Question();
        q4.setId("CORE_004");
        q4.setText("I prefer working in a team rather than alone");
        q4.setType(Question.QuestionType.LIKERT);
        q4.setOptions(Arrays.asList("Strongly Agree", "Agree", "Not Sure", "Disagree", "Strongly Disagree"));
        q4.setScoringTags(Arrays.asList(
            new ScoringTag("INDICATOR", "teamwork", Map.of(
                "Strongly Agree", 2, "Agree", 1, "Not Sure", 0, "Disagree", -1, "Strongly Disagree", -2
            )),
            new ScoringTag("RIASEC", "S", Map.of(
                "Strongly Agree", 2, "Agree", 1, "Not Sure", 0, "Disagree", -1, "Strongly Disagree", -2
            )),
            new ScoringTag("STREAM", "Commerce", Map.of(
                "Strongly Agree", 1, "Agree", 1, "Not Sure", 0, "Disagree", 0, "Strongly Disagree", 0
            ))
        ));
        q4.setModule("CORE");
        q4.setOrder(4);
        CORE_QUESTIONS.add(q4);
        
        // Question 5: Leadership (Likert)
        Question q5 = new Question();
        q5.setId("CORE_005");
        q5.setText("I like taking charge and leading others");
        q5.setType(Question.QuestionType.LIKERT);
        q5.setOptions(Arrays.asList("Strongly Agree", "Agree", "Not Sure", "Disagree", "Strongly Disagree"));
        q5.setScoringTags(Arrays.asList(
            new ScoringTag("INDICATOR", "leadership", Map.of(
                "Strongly Agree", 2, "Agree", 1, "Not Sure", 0, "Disagree", -1, "Strongly Disagree", -2
            )),
            new ScoringTag("RIASEC", "E", Map.of(
                "Strongly Agree", 2, "Agree", 1, "Not Sure", 0, "Disagree", -1, "Strongly Disagree", -2
            )),
            new ScoringTag("STREAM", "Commerce", Map.of(
                "Strongly Agree", 1, "Agree", 1, "Not Sure", 0, "Disagree", 0, "Strongly Disagree", 0
            ))
        ));
        q5.setModule("CORE");
        q5.setOrder(5);
        CORE_QUESTIONS.add(q5);
        
        // Question 6: Expression Confidence (Likert)
        Question q6 = new Question();
        q6.setId("CORE_006");
        q6.setText("I feel comfortable expressing my ideas and opinions");
        q6.setType(Question.QuestionType.LIKERT);
        q6.setOptions(Arrays.asList("Strongly Agree", "Agree", "Not Sure", "Disagree", "Strongly Disagree"));
        q6.setScoringTags(Arrays.asList(
            new ScoringTag("INDICATOR", "expression_confidence", Map.of(
                "Strongly Agree", 2, "Agree", 1, "Not Sure", 0, "Disagree", -1, "Strongly Disagree", -2
            )),
            new ScoringTag("RIASEC", "A", Map.of(
                "Strongly Agree", 1, "Agree", 1, "Not Sure", 0, "Disagree", 0, "Strongly Disagree", 0
            )),
            new ScoringTag("RIASEC", "S", Map.of(
                "Strongly Agree", 1, "Agree", 1, "Not Sure", 0, "Disagree", 0, "Strongly Disagree", 0
            )),
            new ScoringTag("STREAM", "Arts", Map.of(
                "Strongly Agree", 1, "Agree", 1, "Not Sure", 0, "Disagree", 0, "Strongly Disagree", 0
            ))
        ));
        q6.setModule("CORE");
        q6.setOrder(6);
        CORE_QUESTIONS.add(q6);
        
        // Question 7: Realistic vs Investigative - Hands-on vs Analytical (Forced Choice)
        Question q7 = new Question();
        q7.setId("CORE_007");
        q7.setText("Would you rather build something with your hands or read about how things work?"); // Base text - AI will personalize but keep options aligned
        q7.setType(Question.QuestionType.FORCED_CHOICE);
        q7.setOptions(Arrays.asList("Build something with my hands", "Read about how things work"));
        q7.setScoringTags(Arrays.asList(
            new ScoringTag("RIASEC", "R", Map.of("Build something with my hands", 2, "Read about how things work", 0)),
            new ScoringTag("RIASEC", "I", Map.of("Build something with my hands", 0, "Read about how things work", 2))
        ));
        q7.setModule("CORE");
        q7.setOrder(7);
        CORE_QUESTIONS.add(q7);
        
        // Question 8: Investigative - Research (Likert)
        Question q8 = new Question();
        q8.setId("CORE_008");
        q8.setText("I enjoy exploring new things and figuring out how they work"); // Base text - AI will personalize
        q8.setType(Question.QuestionType.LIKERT);
        q8.setOptions(Arrays.asList("Strongly Agree", "Agree", "Not Sure", "Disagree", "Strongly Disagree"));
        q8.setScoringTags(Arrays.asList(
            new ScoringTag("RIASEC", "I", Map.of(
                "Strongly Agree", 2, "Agree", 1, "Not Sure", 0, "Disagree", -1, "Strongly Disagree", -2
            )),
            new ScoringTag("STREAM", "Science", Map.of(
                "Strongly Agree", 2, "Agree", 1, "Not Sure", 0, "Disagree", -1, "Strongly Disagree", -2
            ))
        ));
        q8.setModule("CORE");
        q8.setOrder(8);
        CORE_QUESTIONS.add(q8);
        
        // Question 9: Artistic - Creativity (Likert)
        Question q9 = new Question();
        q9.setId("CORE_009");
        q9.setText("I enjoy creative activities like drawing, music, or writing");
        q9.setType(Question.QuestionType.LIKERT);
        q9.setOptions(Arrays.asList("Strongly Agree", "Agree", "Not Sure", "Disagree", "Strongly Disagree"));
        q9.setScoringTags(Arrays.asList(
            new ScoringTag("RIASEC", "A", Map.of(
                "Strongly Agree", 2, "Agree", 1, "Not Sure", 0, "Disagree", -1, "Strongly Disagree", -2
            )),
            new ScoringTag("STREAM", "Arts", Map.of(
                "Strongly Agree", 2, "Agree", 1, "Not Sure", 0, "Disagree", -1, "Strongly Disagree", -2
            ))
        ));
        q9.setModule("CORE");
        q9.setOrder(9);
        CORE_QUESTIONS.add(q9);
        
        // Question 10: Social - Helping (Likert)
        Question q10 = new Question();
        q10.setId("CORE_010");
        q10.setText("I like helping others and making a positive impact");
        q10.setType(Question.QuestionType.LIKERT);
        q10.setOptions(Arrays.asList("Strongly Agree", "Agree", "Not Sure", "Disagree", "Strongly Disagree"));
        q10.setScoringTags(Arrays.asList(
            new ScoringTag("RIASEC", "S", Map.of(
                "Strongly Agree", 2, "Agree", 1, "Not Sure", 0, "Disagree", -1, "Strongly Disagree", -2
            )),
            new ScoringTag("STREAM", "Arts", Map.of(
                "Strongly Agree", 1, "Agree", 1, "Not Sure", 0, "Disagree", 0, "Strongly Disagree", 0
            ))
        ));
        q10.setModule("CORE");
        q10.setOrder(10);
        CORE_QUESTIONS.add(q10);
        
        // Question 11: Enterprising - Business (Likert)
        Question q11 = new Question();
        q11.setId("CORE_011");
        q11.setText("I am interested in business and entrepreneurship"); // Base text - AI will personalize
        q11.setType(Question.QuestionType.LIKERT);
        q11.setOptions(Arrays.asList("Strongly Agree", "Agree", "Not Sure", "Disagree", "Strongly Disagree"));
        q11.setScoringTags(Arrays.asList(
            new ScoringTag("RIASEC", "E", Map.of(
                "Strongly Agree", 2, "Agree", 1, "Not Sure", 0, "Disagree", -1, "Strongly Disagree", -2
            )),
            new ScoringTag("STREAM", "Commerce", Map.of(
                "Strongly Agree", 2, "Agree", 1, "Not Sure", 0, "Disagree", -1, "Strongly Disagree", -2
            ))
        ));
        q11.setModule("CORE");
        q11.setOrder(11);
        CORE_QUESTIONS.add(q11);
        
        // Question 12: Conventional - Organization (Likert)
        Question q12 = new Question();
        q12.setId("CORE_012");
        q12.setText("I prefer organized and structured tasks"); // Base text - AI will personalize
        q12.setType(Question.QuestionType.LIKERT);
        q12.setOptions(Arrays.asList("Strongly Agree", "Agree", "Not Sure", "Disagree", "Strongly Disagree"));
        q12.setScoringTags(Arrays.asList(
            new ScoringTag("RIASEC", "C", Map.of(
                "Strongly Agree", 2, "Agree", 1, "Not Sure", 0, "Disagree", -1, "Strongly Disagree", -2
            )),
            new ScoringTag("STREAM", "Commerce", Map.of(
                "Strongly Agree", 1, "Agree", 1, "Not Sure", 0, "Disagree", 0, "Strongly Disagree", 0
            ))
        ));
        q12.setModule("CORE");
        q12.setOrder(12);
        CORE_QUESTIONS.add(q12);
        
        // Question 13: Science vs Arts Interest (Forced Choice)
        Question q13 = new Question();
        q13.setId("CORE_013");
        q13.setText("Are you more interested in how the natural world works or how people and societies work?"); // Base text - AI will personalize but keep options aligned
        q13.setType(Question.QuestionType.FORCED_CHOICE);
        q13.setOptions(Arrays.asList("How the natural world works", "How people and societies work"));
        q13.setScoringTags(Arrays.asList(
            new ScoringTag("STREAM", "Science", Map.of("How the natural world works", 2, "How people and societies work", 0)),
            new ScoringTag("STREAM", "Arts", Map.of("How the natural world works", 0, "How people and societies work", 2)),
            new ScoringTag("RIASEC", "I", Map.of("How the natural world works", 1, "How people and societies work", 0)),
            new ScoringTag("RIASEC", "S", Map.of("How the natural world works", 0, "How people and societies work", 1))
        ));
        q13.setModule("CORE");
        q13.setOrder(13);
        CORE_QUESTIONS.add(q13);
        
        // Question 14: Commerce Interest (Likert)
        Question q14 = new Question();
        q14.setId("CORE_014");
        q14.setText("I am interested in finance, economics, or management"); // Base text - AI will personalize
        q14.setType(Question.QuestionType.LIKERT);
        q14.setOptions(Arrays.asList("Strongly Agree", "Agree", "Not Sure", "Disagree", "Strongly Disagree"));
        q14.setScoringTags(Arrays.asList(
            new ScoringTag("STREAM", "Commerce", Map.of(
                "Strongly Agree", 2, "Agree", 1, "Not Sure", 0, "Disagree", -1, "Strongly Disagree", -2
            )),
            new ScoringTag("RIASEC", "E", Map.of(
                "Strongly Agree", 1, "Agree", 1, "Not Sure", 0, "Disagree", 0, "Strongly Disagree", 0
            )),
            new ScoringTag("RIASEC", "C", Map.of(
                "Strongly Agree", 1, "Agree", 1, "Not Sure", 0, "Disagree", 0, "Strongly Disagree", 0
            ))
        ));
        q14.setModule("CORE");
        q14.setOrder(14);
        CORE_QUESTIONS.add(q14);
        
        // Question 15: Arts Interest (Likert)
        Question q15 = new Question();
        q15.setId("CORE_015");
        q15.setText("I am interested in literature, history, or social sciences"); // Base text - AI will personalize
        q15.setType(Question.QuestionType.LIKERT);
        q15.setOptions(Arrays.asList("Strongly Agree", "Agree", "Not Sure", "Disagree", "Strongly Disagree"));
        q15.setScoringTags(Arrays.asList(
            new ScoringTag("STREAM", "Arts", Map.of(
                "Strongly Agree", 2, "Agree", 1, "Not Sure", 0, "Disagree", -1, "Strongly Disagree", -2
            )),
            new ScoringTag("RIASEC", "A", Map.of(
                "Strongly Agree", 1, "Agree", 1, "Not Sure", 0, "Disagree", 0, "Strongly Disagree", 0
            )),
            new ScoringTag("RIASEC", "S", Map.of(
                "Strongly Agree", 1, "Agree", 1, "Not Sure", 0, "Disagree", 0, "Strongly Disagree", 0
            ))
        ));
        q15.setModule("CORE");
        q15.setOrder(15);
        CORE_QUESTIONS.add(q15);
        
        // Question 16: Micro-subjective - Favorite Subject
        Question q16 = new Question();
        q16.setId("CORE_016");
        q16.setText("What is your favorite subject? (Type one word or short phrase)");
        q16.setType(Question.QuestionType.MICRO_SUBJECTIVE);
        q16.setOptions(null);
        q16.setScoringTags(Arrays.asList(
            // This will be analyzed by AI to map to streams/RIASEC
            new ScoringTag("INDICATOR", "favorite_subject", Map.of())
        ));
        q16.setModule("CORE");
        q16.setOrder(16);
        CORE_QUESTIONS.add(q16);
        
        // Question 17: Micro-subjective - Career Interest
        Question q17 = new Question();
        q17.setId("CORE_017");
        q17.setText("What career interests you most? (Type one word or short phrase)");
        q17.setType(Question.QuestionType.MICRO_SUBJECTIVE);
        q17.setOptions(null);
        q17.setScoringTags(Arrays.asList(
            // This will be analyzed by AI to map to streams/RIASEC
            new ScoringTag("INDICATOR", "career_interest", Map.of())
        ));
        q17.setModule("CORE");
        q17.setOrder(17);
        CORE_QUESTIONS.add(q17);
        
        // Question 18: Work Environment Preference - Structured vs Creative (Forced Choice)
        Question q18 = new Question();
        q18.setId("CORE_018");
        q18.setText("Would you prefer working in a lab or office with clear procedures, or in a creative space with flexibility?"); // Base text - AI will personalize but keep options aligned
        q18.setType(Question.QuestionType.FORCED_CHOICE);
        q18.setOptions(Arrays.asList("In a lab or office with clear procedures", "In a creative space with flexibility"));
        q18.setScoringTags(Arrays.asList(
            new ScoringTag("RIASEC", "C", Map.of("In a lab or office with clear procedures", 2, "In a creative space with flexibility", 0)),
            new ScoringTag("RIASEC", "A", Map.of("In a lab or office with clear procedures", 0, "In a creative space with flexibility", 2)),
            new ScoringTag("STREAM", "Science", Map.of("In a lab or office with clear procedures", 1, "In a creative space with flexibility", 0)),
            new ScoringTag("STREAM", "Arts", Map.of("In a lab or office with clear procedures", 0, "In a creative space with flexibility", 1))
        ));
        q18.setModule("CORE");
        q18.setOrder(18);
        CORE_QUESTIONS.add(q18);
    }
    
    public static List<Question> getCoreQuestions() {
        return new ArrayList<>(CORE_QUESTIONS);
    }
    
    public static Question getQuestionById(String questionId) {
        return CORE_QUESTIONS.stream()
            .filter(q -> q.getId().equals(questionId))
            .findFirst()
            .orElse(null);
    }
    
    public static Question getQuestionByOrder(int order) {
        if (order < 1 || order > CORE_QUESTIONS.size()) {
            return null;
        }
        return CORE_QUESTIONS.get(order - 1);
    }
}
