package com.datagami.edudron.content.service.psychometric;

import com.datagami.edudron.content.service.FoundryAIService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service for AI-powered question personalization
 * - Rephrases questions to be grade-appropriate
 * - Personalizes examples based on student interests
 * - Selects best next question based on current scores
 */
@Service
public class QuestionPersonalizationService {
    
    private static final Logger logger = LoggerFactory.getLogger(QuestionPersonalizationService.class);
    
    @Autowired
    private FoundryAIService foundryAIService;
    
    /**
     * Personalize a question based on student profile and detected interests
     * Makes questions more conversational, engaging, and personalized
     */
    public String personalizeQuestion(Question question, StudentProfile profile, SessionState state) {
        if (question == null) {
            return null;
        }
        
        String originalText = question.getText();
        
        // Build rich context for AI
        StringBuilder context = new StringBuilder();
        context.append("Original question: ").append(originalText).append("\n\n");
        
        if (profile != null) {
            context.append("Student Information:\n");
            context.append("- Grade: ").append(profile.getGrade()).append("\n");
            if (profile.getBoard() != null) {
                context.append("- Board: ").append(profile.getBoard()).append("\n");
            }
            if (profile.getName() != null) {
                context.append("- Name: ").append(profile.getName()).append("\n");
            }
        }
        
        // Add detected interests and patterns from answers so far
        if (state != null && !state.getCoreAnswers().isEmpty()) {
            context.append("\nWhat we know about the student so far:\n");
            String interests = detectInterestsFromAnswers(state);
            context.append("- Interests: ").append(interests).append("\n");
            
            // Add top RIASEC themes if available
            if (state.getRiasecScores() != null && !state.getRiasecScores().isEmpty()) {
                String topTheme = state.getRiasecScores().entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(e -> e.getKey() + " (score: " + e.getValue() + ")")
                    .orElse("");
                if (!topTheme.isEmpty()) {
                    context.append("- Strong traits: ").append(topTheme).append("\n");
                }
            }
            
            // Add stream preferences if available
            if (state.getStreamScores() != null && !state.getStreamScores().isEmpty()) {
                String topStream = state.getStreamScores().entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse("");
                if (!topStream.isEmpty()) {
                    context.append("- Leaning towards: ").append(topStream).append("\n");
                }
            }
        }
        
        // Special handling for forced-choice questions - need to keep options aligned
        boolean isForcedChoice = question.getType() == Question.QuestionType.FORCED_CHOICE;
        String optionsContext = "";
        if (isForcedChoice && question.getOptions() != null && !question.getOptions().isEmpty()) {
            optionsContext = "\n\nIMPORTANT: This is a forced-choice question with these exact options:\n";
            for (int i = 0; i < question.getOptions().size(); i++) {
                optionsContext += "- Option " + (i + 1) + ": \"" + question.getOptions().get(i) + "\"\n";
            }
            optionsContext += "\nYou MUST keep the question text aligned with these options. ";
            optionsContext += "The question should naturally lead to choosing between these two options. ";
            optionsContext += "Do NOT change the question to be about something completely different.\n";
        }
        
        String systemPrompt = """
            You are a cool, friendly mentor talking to a student about their future. Make this feel like a casual chat, NOT a research study or formal test.
            
            YOUR MISSION:
            Transform boring, research-y questions into fun, relatable questions that feel like you're just getting to know them.
            
            CRITICAL RULES:
            1. ZERO research words: Remove "research", "conduct experiments", "analyze", "statistical", "data analysis", "studies"
            2. Sound like a friend, not a scientist: Use "Do you like..." instead of "I am interested in..."
            3. Make it personal: If you know they like sports, say "Do you enjoy breaking down game strategies?" instead of generic questions
            4. Keep it real: Use words students actually use - "tricky" not "complex", "figure out" not "analyze", "curious" not "fascinated"
            5. One sentence max - keep it snappy and engaging
            6. Match their vibe: If they're into tech, use tech examples. Sports? Use sports. Music? Use music.
            7. Keep the MEANING the same (what we're measuring) but make it WAY more fun
            """ + optionsContext + """
            
            TRANSFORMATION EXAMPLES:
            
            ❌ OLD: "I am interested in conducting experiments and research"
            ✅ NEW: "Do you like trying new things to see what happens?"
            
            ❌ OLD: "I enjoy solving complex mathematical problems and equations"  
            ✅ NEW: "Do you get a kick out of solving tricky math problems?"
            
            ❌ OLD: "I am fascinated by how physical and chemical processes work"
            ✅ NEW: "Are you curious about why things work the way they do?"
            
            ❌ OLD: "I prefer organized and structured tasks"
            ✅ NEW: "Do you like having a plan before you dive into something?"
            
            FORCED-CHOICE QUESTION EXAMPLES (must match the options):
            - If options are "Build something" vs "Read about it", the question should be about hands-on vs learning
            - If options are "Natural world" vs "People and societies", the question should contrast these two
            - Personalize the examples but keep the core choice the same
            
            PERSONALIZATION EXAMPLES:
            - Sports fan: "Do you enjoy breaking down game plays and player stats?"
            - Music lover: "Do you wonder how your favorite artists create their songs?"
            - Tech geek: "Are you the type who wants to know how apps work behind the scenes?"
            - Creative type: "Do you love expressing yourself through art, music, or writing?"
            
            Return ONLY the question. No quotes, no explanations, just the question text.
            """;
        
        try {
            logger.info("🤖 Calling AI to personalize question: '{}'", originalText);
            logger.debug("Context sent to AI: {}", context.toString());
            
            String personalized = foundryAIService.callOpenAI(systemPrompt, context.toString());
            logger.info("🤖 AI response received, length: {}", personalized != null ? personalized.length() : 0);
            
            if (personalized != null && !personalized.trim().isEmpty()) {
                personalized = personalized.trim();
                // Remove quotes if AI added them
                if (personalized.startsWith("\"") && personalized.endsWith("\"")) {
                    personalized = personalized.substring(1, personalized.length() - 1).trim();
                }
                if (personalized.startsWith("'") && personalized.endsWith("'")) {
                    personalized = personalized.substring(1, personalized.length() - 1).trim();
                }
                // Remove any leading/trailing punctuation that might be from AI formatting
                personalized = personalized.replaceAll("^[\\\"'`]+|[\\\"'`]+$", "").trim();
                
                // Ensure it's actually personalized (not just the same)
                if (!personalized.equalsIgnoreCase(originalText) && personalized.length() > 10) {
                    logger.info("✅ AI successfully personalized: '{}' -> '{}'", originalText, personalized);
                    return personalized;
                } else {
                    logger.warn("⚠️ AI returned similar/empty text. Original: '{}', AI: '{}'. Using fallback.", originalText, personalized);
                }
            } else {
                logger.warn("⚠️ AI returned null or empty response");
            }
        } catch (Exception e) {
            logger.error("❌ AI personalization failed: {}", e.getMessage(), e);
        }
        
        // Fallback: at least remove research words and make conversational
        String fallback = removeResearchWords(originalText);
        logger.debug("Using fallback personalization: {} -> {}", originalText, fallback);
        return fallback;
    }
    
    /**
     * Simple fallback to remove research-oriented words and make more conversational
     */
    public String removeResearchWords(String text) {
        if (text == null) return "";
        
        return text
            .replace("conduct experiments and research", "try new things and explore")
            .replace("conducting experiments", "trying things out")
            .replace("conduct experiments", "try things out")
            .replace("research", "explore")
            .replace("statistical analysis", "looking at patterns")
            .replace("analyzing texts", "diving into books")
            .replace("analyze data", "figure things out")
            .replace("I am interested in", "I like")
            .replace("I am fascinated by", "I'm curious about")
            .replace("I am more interested in", "I'm more curious about")
            .replace("complex mathematical problems", "tricky math problems")
            .replace("complex", "tricky")
            .replace("I prefer", "I like")
            .replace("I enjoy", "I like")
            .replace("organized and structured", "having a clear plan")
            .replace("structured tasks", "tasks with a plan")
            .replace("managing teams", "organizing people")
            .replace("customer relations", "talking to customers")
            .replace("social sciences", "learning about people")
            .replace("human behavior", "why people do what they do")
            .replace("abstract concepts", "big ideas")
            .replace("philosophy", "deep thinking")
            .replace("biological systems", "how living things work")
            .replace("physical and chemical processes", "how things work in nature");
    }
    
    /**
     * Select the best next question from available questions based on current scores
     * This helps reduce uncertainty and improve confidence
     */
    public Question selectBestNextQuestion(List<Question> availableQuestions, SessionState state) {
        if (availableQuestions == null || availableQuestions.isEmpty()) {
            return null;
        }
        
        if (availableQuestions.size() == 1) {
            return availableQuestions.get(0);
        }
        
        // Build context about current scores and what we need to clarify
        StringBuilder context = new StringBuilder();
        context.append("Current RIASEC scores: ").append(state.getRiasecScores()).append("\n");
        context.append("Current stream scores: ").append(state.getStreamScores()).append("\n");
        context.append("Current confidence: ").append(state.getConfidenceScore()).append("\n");
        context.append("Available questions:\n");
        
        for (int i = 0; i < availableQuestions.size(); i++) {
            Question q = availableQuestions.get(i);
            context.append(i + 1).append(". ").append(q.getText()).append(" (measures: ");
            if (q.getScoringTags() != null) {
                q.getScoringTags().forEach(tag -> {
                    context.append(tag.getCategory()).append(":").append(tag.getName()).append(" ");
                });
            }
            context.append(")\n");
        }
        
        String systemPrompt = """
            You are a psychometric test assistant selecting the next best question.
            Your goal is to select the question that will:
            1. Reduce uncertainty in areas where scores are close
            2. Strengthen confidence in areas where we have strong signals
            3. Cover gaps in our understanding of the student
            
            Analyze the current scores and available questions.
            Return ONLY the question number (1, 2, 3, etc.) that should be asked next.
            If multiple questions are equally good, choose the first one.
            """;
        
        try {
            String response = foundryAIService.callOpenAI(systemPrompt, context.toString());
            // Extract number from response
            int questionIndex = extractQuestionNumber(response, availableQuestions.size());
            logger.info("AI selected question index: {} from {} available", questionIndex, availableQuestions.size());
            return availableQuestions.get(questionIndex);
        } catch (Exception e) {
            logger.error("Failed to select question via AI, using first available", e);
            return availableQuestions.get(0);
        }
    }
    
    /**
     * Detect interests from answers - analyzes micro-subjective answers and patterns
     */
    private String detectInterestsFromAnswers(SessionState state) {
        List<String> detectedInterests = new ArrayList<>();
        
        // Analyze micro-subjective answers (favorite subject, career interest)
        if (state.getCoreAnswers() != null) {
            for (Answer answer : state.getCoreAnswers()) {
                String value = answer.getValue().toLowerCase();
                
                // Check for favorite subject mentions
                if (value.contains("math") || value.contains("science") || value.contains("physics") || 
                    value.contains("chemistry") || value.contains("biology")) {
                    detectedInterests.add("science and math");
                }
                if (value.contains("english") || value.contains("literature") || value.contains("writing") ||
                    value.contains("poetry") || value.contains("reading")) {
                    detectedInterests.add("reading and writing");
                }
                if (value.contains("history") || value.contains("social") || value.contains("geography")) {
                    detectedInterests.add("history and social studies");
                }
                if (value.contains("commerce") || value.contains("business") || value.contains("economics") ||
                    value.contains("accounting") || value.contains("finance")) {
                    detectedInterests.add("business and finance");
                }
                
                // Check for hobby/interest mentions
                if (value.contains("sport") || value.contains("cricket") || value.contains("football") ||
                    value.contains("basketball") || value.contains("game")) {
                    detectedInterests.add("sports and games");
                }
                if (value.contains("music") || value.contains("sing") || value.contains("instrument")) {
                    detectedInterests.add("music");
                }
                if (value.contains("art") || value.contains("draw") || value.contains("paint") ||
                    value.contains("design") || value.contains("creative")) {
                    detectedInterests.add("art and creativity");
                }
                if (value.contains("tech") || value.contains("computer") || value.contains("coding") ||
                    value.contains("programming") || value.contains("app") || value.contains("game development")) {
                    detectedInterests.add("technology and coding");
                }
            }
        }
        
        // Also check RIASEC scores for patterns
        if (state.getRiasecScores() != null && !state.getRiasecScores().isEmpty()) {
            String topTheme = state.getRiasecScores().entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("");
            
            Map<String, String> riasecToInterests = Map.of(
                "R", "building things, hands-on projects",
                "I", "figuring things out, problem-solving",
                "A", "creative projects, music, art",
                "S", "helping people, teaching, social activities",
                "E", "business ideas, leading projects",
                "C", "organizing things, planning"
            );
            
            String riasecInterest = riasecToInterests.get(topTheme);
            if (riasecInterest != null && !detectedInterests.contains(riasecInterest)) {
                detectedInterests.add(riasecInterest);
            }
        }
        
        if (detectedInterests.isEmpty()) {
            return "general interests";
        }
        
        return String.join(", ", detectedInterests);
    }
    
    /**
     * Extract question number from AI response
     */
    private int extractQuestionNumber(String response, int maxQuestions) {
        if (response == null) {
            return 0;
        }
        
        // Try to find a number in the response
        String trimmed = response.trim();
        for (int i = 1; i <= maxQuestions; i++) {
            if (trimmed.contains(String.valueOf(i)) && 
                (trimmed.startsWith(String.valueOf(i)) || 
                 trimmed.contains(" " + i + " ") ||
                 trimmed.contains(" " + i + ".") ||
                 trimmed.contains("question " + i))) {
                return i - 1; // Convert to 0-based index
            }
        }
        
        // Default to first question
        return 0;
    }
}

