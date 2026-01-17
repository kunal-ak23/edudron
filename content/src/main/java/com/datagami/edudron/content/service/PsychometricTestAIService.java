package com.datagami.edudron.content.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PsychometricTestAIService {
    
    private static final Logger logger = LoggerFactory.getLogger(PsychometricTestAIService.class);
    
    private final FoundryAIService foundryAIService;
    private final ObjectMapper objectMapper;
    
    @Autowired
    public PsychometricTestAIService(FoundryAIService foundryAIService, ObjectMapper objectMapper) {
        this.foundryAIService = foundryAIService;
        this.objectMapper = objectMapper;
    }
    
    /**
     * Generate the initial welcome message and first question for the psychometric test.
     */
    public String generateInitialQuestion() {
        String systemPrompt = """
            You are a friendly and engaging psychometric test assistant. Your goal is to help users discover their career 
            and field inclinations through a conversational test. Start with a warm welcome message and ask the first 
            open-ended, subjective question to understand the user's interests, values, work preferences, and personality.
            
            Keep the question conversational, engaging, and easy to answer. Focus on discovering their natural inclinations 
            toward different fields (e.g., Engineering, Medicine, Arts, Business, Science, etc.).
            
            Return ONLY the question text. Do not include any explanatory text, greetings, or conversational language 
            before or after the question. Just the question itself.
            """;
        
        String userPrompt = "Generate the first question for a psychometric test.";
        
        try {
            String response = foundryAIService.callOpenAI(systemPrompt, userPrompt);
            logger.info("Generated initial question (length: {} chars)", response != null ? response.length() : 0);
            return response != null ? response.trim() : "What are your main interests and hobbies?";
        } catch (Exception e) {
            logger.error("Failed to generate initial question", e);
            return "What are your main interests and hobbies?";
        }
    }
    
    /**
     * Generate the next question based on conversation history.
     */
    public String generateNextQuestion(String conversationHistoryJson, String currentPhase) {
        String systemPrompt;
        String userPrompt;
        
        if ("INITIAL_EXPLORATION".equals(currentPhase)) {
            systemPrompt = """
                You are a psychometric test assistant in the initial exploration phase. Based on the conversation history, 
                ask another open-ended, subjective question to understand the user's interests, values, work preferences, 
                and personality. Each question should build on previous answers to discover their natural inclinations 
                toward different fields.
                
                Keep questions conversational, engaging, and easy to answer. Vary the topics (interests, values, work style, 
                problem-solving approach, etc.) to get a comprehensive understanding.
                
                Return ONLY the question text. Do not include any explanatory text or conversational language.
                """;
            
            userPrompt = "Conversation history:\n" + formatConversationHistory(conversationHistoryJson) + 
                        "\n\nGenerate the next exploration question.";
        } else {
            // FIELD_DEEP_DIVE phase
            systemPrompt = """
                You are a psychometric test assistant in the deep dive phase. The user has shown a strong inclination 
                toward a specific field. Based on the conversation history, generate a question that explores this field 
                in more depth. Alternate between subjective questions (personality fit, values, preferences) and objective 
                questions (knowledge, skills, problem-solving in this field).
                
                Make questions engaging and relevant to the identified field. Subjective questions explore fit and alignment. 
                Objective questions test understanding and capability.
                
                Return ONLY the question text. Do not include any explanatory text or conversational language.
                """;
            
            userPrompt = "Conversation history:\n" + formatConversationHistory(conversationHistoryJson) + 
                        "\n\nGenerate the next deep dive question.";
        }
        
        try {
            String response = foundryAIService.callOpenAI(systemPrompt, userPrompt);
            logger.info("Generated next question for phase {} (length: {} chars)", currentPhase, response != null ? response.length() : 0);
            return response != null ? response.trim() : "Can you tell me more about that?";
        } catch (Exception e) {
            logger.error("Failed to generate next question", e);
            return "Can you tell me more about that?";
        }
    }
    
    /**
     * Analyze conversation history to identify field inclinations.
     * Returns a map of field names to confidence scores (0-1).
     */
    public Map<String, Double> analyzeFieldInclination(String conversationHistoryJson) {
        String systemPrompt = """
            You are an expert career counselor analyzing a psychometric test conversation. Analyze the conversation history 
            and identify potential field inclinations based on the user's responses.
            
            Return a JSON object with field names as keys and confidence scores (0-1) as values. Fields should be specific 
            (e.g., "Software Engineering", "Clinical Medicine", "Graphic Design", "Financial Analysis", "Data Science", etc.).
            
            Include only fields with confidence scores above 0.3. Return at least 3-5 fields if possible.
            
            CRITICAL: Return ONLY valid JSON object. Do NOT include any explanatory text, greetings, or conversational language 
            before or after the JSON. Start your response directly with '{' and end with '}'.
            
            Example format:
            {
              "Software Engineering": 0.85,
              "Data Science": 0.70,
              "Product Management": 0.45
            }
            """;
        
        String userPrompt = "Conversation history:\n" + formatConversationHistory(conversationHistoryJson) + 
                           "\n\nAnalyze and return field inclinations as JSON.";
        
        try {
            String response = foundryAIService.callOpenAI(systemPrompt, userPrompt);
            logger.info("Received field analysis response (length: {} chars)", response != null ? response.length() : 0);
            
            // Extract JSON from response
            String jsonResponse = extractJsonFromResponse(response);
            JsonNode jsonNode = objectMapper.readTree(jsonResponse);
            
            Map<String, Double> fieldScores = new HashMap<>();
            jsonNode.fields().forEachRemaining(entry -> {
                try {
                    double score = entry.getValue().asDouble();
                    if (score > 0.3) { // Only include fields with meaningful scores
                        fieldScores.put(entry.getKey(), score);
                    }
                } catch (Exception e) {
                    logger.warn("Failed to parse score for field: {}", entry.getKey(), e);
                }
            });
            
            logger.info("Identified {} fields with scores", fieldScores.size());
            return fieldScores;
        } catch (Exception e) {
            logger.error("Failed to analyze field inclination", e);
            return new HashMap<>();
        }
    }
    
    /**
     * Generate comprehensive test results based on conversation history and field scores.
     */
    public Map<String, Object> generateTestResults(String conversationHistoryJson, Map<String, Double> fieldScores) {
        String systemPrompt = """
            You are an expert career counselor generating comprehensive psychometric test results. Based on the conversation 
            history and field scores, generate detailed results.
            
            Return a JSON object with the following structure:
            {
              "primaryField": "Field name with highest score",
              "secondaryFields": ["Field 2", "Field 3"],
              "recommendations": [
                {
                  "field": "Field name",
                  "reason": "Why this field is a good fit",
                  "nextSteps": "Recommended actions"
                }
              ],
              "testSummary": "Comprehensive summary of the test results and insights"
            }
            
            CRITICAL: Return ONLY valid JSON object. Do NOT include any explanatory text before or after the JSON.
            """;
        
        String userPrompt = "Conversation history:\n" + formatConversationHistory(conversationHistoryJson) + 
                           "\n\nField scores:\n" + formatFieldScores(fieldScores) + 
                           "\n\nGenerate comprehensive test results as JSON.";
        
        try {
            String response = foundryAIService.callOpenAI(systemPrompt, userPrompt);
            logger.info("Received test results response (length: {} chars)", response != null ? response.length() : 0);
            
            // Extract JSON from response
            String jsonResponse = extractJsonFromResponse(response);
            JsonNode jsonNode = objectMapper.readTree(jsonResponse);
            
            Map<String, Object> results = new HashMap<>();
            results.put("primaryField", jsonNode.has("primaryField") ? jsonNode.get("primaryField").asText() : null);
            results.put("secondaryFields", jsonNode.has("secondaryFields") ? jsonNode.get("secondaryFields") : objectMapper.createArrayNode());
            results.put("recommendations", jsonNode.has("recommendations") ? jsonNode.get("recommendations") : objectMapper.createArrayNode());
            results.put("testSummary", jsonNode.has("testSummary") ? jsonNode.get("testSummary").asText() : "");
            
            logger.info("Generated test results with primary field: {}", results.get("primaryField"));
            return results;
        } catch (Exception e) {
            logger.error("Failed to generate test results", e);
            // Return default structure
            Map<String, Object> defaultResults = new HashMap<>();
            defaultResults.put("primaryField", fieldScores.isEmpty() ? null : fieldScores.entrySet().iterator().next().getKey());
            defaultResults.put("secondaryFields", new ArrayList<>());
            defaultResults.put("recommendations", new ArrayList<>());
            defaultResults.put("testSummary", "Test completed. Please review your field scores.");
            return defaultResults;
        }
    }
    
    /**
     * Format conversation history JSON into a readable string for AI prompts.
     */
    private String formatConversationHistory(String conversationHistoryJson) {
        try {
            JsonNode history = objectMapper.readTree(conversationHistoryJson);
            if (!history.isArray()) {
                return "No conversation history available.";
            }
            
            StringBuilder sb = new StringBuilder();
            for (JsonNode message : history) {
                String role = message.has("role") ? message.get("role").asText() : "unknown";
                String content = message.has("content") ? message.get("content").asText() : "";
                sb.append(role.toUpperCase()).append(": ").append(content).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            logger.warn("Failed to format conversation history", e);
            return "Conversation history unavailable.";
        }
    }
    
    /**
     * Format field scores map into a readable string for AI prompts.
     */
    private String formatFieldScores(Map<String, Double> fieldScores) {
        StringBuilder sb = new StringBuilder();
        fieldScores.entrySet().stream()
            .sorted((a, b) -> Double.compare(b.getValue(), a.getValue())) // Sort by score descending
            .forEach(entry -> sb.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n"));
        return sb.toString();
    }
    
    /**
     * Generate reasoning for test results
     */
    public String generateReasoning(String prompt) {
        String systemPrompt = """
            You are a career guidance assistant generating explanations for psychometric test results.
            Generate brief, student-friendly explanations (2-3 sentences) that:
            - Use phrases like "Based on your responses", "May suit you", "Suggested"
            - Do NOT use deterministic language like "You should" or "You must"
            - Are age-appropriate and encouraging
            - Explain why suggestions were made based on the test results
            
            Return ONLY the explanation text. Do not include any additional commentary.
            """;
        
        try {
            String response = foundryAIService.callOpenAI(systemPrompt, prompt);
            logger.info("Generated reasoning (length: {} chars)", response != null ? response.length() : 0);
            return response != null ? response.trim() : "Based on your responses, we have generated personalized suggestions for you.";
        } catch (Exception e) {
            logger.error("Failed to generate reasoning", e);
            return "Based on your responses, we have generated personalized suggestions for you.";
        }
    }
    
    /**
     * Extract JSON from AI response, handling cases where response includes explanatory text.
     */
    private String extractJsonFromResponse(String response) {
        if (response == null || response.trim().isEmpty()) {
            return "{}";
        }
        
        String trimmed = response.trim();
        
        // Find first { and last }
        int firstBrace = trimmed.indexOf('{');
        int lastBrace = trimmed.lastIndexOf('}');
        
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return trimmed.substring(firstBrace, lastBrace + 1);
        }
        
        return trimmed;
    }
}
