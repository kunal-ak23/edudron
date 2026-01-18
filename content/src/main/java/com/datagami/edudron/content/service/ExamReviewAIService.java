package com.datagami.edudron.content.service;

import com.datagami.edudron.content.domain.QuizQuestion;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;


@Service
public class ExamReviewAIService {
    
    private static final Logger logger = LoggerFactory.getLogger(ExamReviewAIService.class);
    
    private final FoundryAIService foundryAIService;
    private final ObjectMapper objectMapper;
    
    @Autowired
    public ExamReviewAIService(FoundryAIService foundryAIService, ObjectMapper objectMapper) {
        this.foundryAIService = foundryAIService;
        this.objectMapper = objectMapper;
    }
    
    /**
     * Compare student answer with tentative answer using semantic similarity
     * Returns a similarity score from 0-100
     */
    public double compareAnswersSemantically(String studentAnswer, String tentativeAnswer) {
        if (studentAnswer == null || studentAnswer.trim().isEmpty()) {
            return 0.0;
        }
        
        if (tentativeAnswer == null || tentativeAnswer.trim().isEmpty()) {
            logger.warn("Tentative answer is empty, cannot compare");
            return 0.0;
        }
        
        try {
            String systemPrompt = """
                You are an expert grader. Compare a student's answer with the expected answer and determine the similarity score.
                
                Return a JSON object with:
                {
                    "similarityScore": <number between 0 and 100>,
                    "explanation": "Brief explanation of the comparison"
                }
                
                Scoring guidelines:
                - 90-100: Excellent match, covers all key points
                - 70-89: Good match, covers most key points with minor gaps
                - 50-69: Partial match, covers some key points but missing important details
                - 30-49: Weak match, minimal overlap with expected answer
                - 0-29: Poor match, very little or no overlap
                
                Consider:
                - Key concepts and ideas
                - Accuracy of information
                - Completeness of answer
                - Use of appropriate terminology
                
                CRITICAL: Return ONLY valid JSON. Start with '{' and end with '}'.
                """;
            
            String userPrompt = String.format(
                "Expected Answer:\n%s\n\nStudent Answer:\n%s\n\nCompare these answers and provide a similarity score.",
                tentativeAnswer, studentAnswer
            );
            
            String response = foundryAIService.callOpenAI(systemPrompt, userPrompt);
            
            // Extract JSON from response
            String jsonResponse = extractJsonFromResponse(response);
            JsonNode jsonNode = objectMapper.readTree(jsonResponse);
            
            double similarityScore = jsonNode.get("similarityScore").asDouble();
            
            return Math.max(0.0, Math.min(100.0, similarityScore)); // Clamp between 0 and 100
            
        } catch (Exception e) {
            logger.error("Failed to compare answers semantically", e);
            // Fallback: simple text comparison
            return calculateSimpleSimilarity(studentAnswer, tentativeAnswer);
        }
    }
    
    /**
     * Simple fallback similarity calculation using word overlap
     */
    private double calculateSimpleSimilarity(String answer1, String answer2) {
        if (answer1 == null || answer2 == null) {
            return 0.0;
        }
        
        String[] words1 = answer1.toLowerCase().split("\\s+");
        String[] words2 = answer2.toLowerCase().split("\\s+");
        
        int commonWords = 0;
        for (String word : words1) {
            for (String word2 : words2) {
                if (word.equals(word2)) {
                    commonWords++;
                    break;
                }
            }
        }
        
        int totalWords = Math.max(words1.length, words2.length);
        if (totalWords == 0) {
            return 0.0;
        }
        
        return (double) commonWords / totalWords * 100.0;
    }
    
    /**
     * Extract JSON from AI response (handles conversational text)
     */
    private String extractJsonFromResponse(String response) {
        if (response == null || response.trim().isEmpty()) {
            throw new IllegalArgumentException("Empty response from AI");
        }
        
        // Try to find JSON object
        int startIdx = response.indexOf('{');
        int endIdx = response.lastIndexOf('}');
        
        if (startIdx == -1 || endIdx == -1 || endIdx <= startIdx) {
            throw new IllegalArgumentException("No JSON object found in response: " + response);
        }
        
        return response.substring(startIdx, endIdx + 1);
    }
    
    /**
     * Calculate grade based on similarity score
     * Returns points earned (0 to maxPoints)
     */
    public double calculateGradeFromSimilarity(double similarityScore, double maxPoints) {
        if (similarityScore >= 70.0) {
            // Full credit for 70%+ similarity
            return maxPoints;
        } else if (similarityScore >= 50.0) {
            // Partial credit: proportional between 50-70%
            double proportion = (similarityScore - 50.0) / 20.0; // 0 to 1
            return maxPoints * (0.5 + 0.5 * proportion); // 50% to 100% of points
        } else if (similarityScore >= 30.0) {
            // Minimal credit: 10-50% of points
            double proportion = (similarityScore - 30.0) / 20.0; // 0 to 1
            return maxPoints * (0.1 + 0.4 * proportion); // 10% to 50% of points
        } else {
            // No credit for <30% similarity
            return 0.0;
        }
    }
}
