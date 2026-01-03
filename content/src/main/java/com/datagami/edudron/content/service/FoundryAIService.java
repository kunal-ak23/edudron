package com.datagami.edudron.content.service;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.ai.openai.models.ChatCompletions;
import com.azure.ai.openai.models.ChatCompletionsOptions;
import com.azure.ai.openai.models.ChatRequestMessage;
import com.azure.ai.openai.models.ChatRequestSystemMessage;
import com.azure.ai.openai.models.ChatRequestUserMessage;
import com.azure.core.credential.AzureKeyCredential;
import com.datagami.edudron.content.dto.CourseRequirements;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class FoundryAIService {
    
    private static final Logger logger = LoggerFactory.getLogger(FoundryAIService.class);
    
    private final OpenAIClient client;
    private final String deploymentName;
    private final ObjectMapper objectMapper;
    
    public FoundryAIService(
            @Value("${azure.openai.endpoint:}") String endpoint,
            @Value("${azure.openai.api-key:}") String apiKey,
            @Value("${azure.openai.deployment-name:gpt-4}") String deploymentName,
            ObjectMapper objectMapper) {
        this.deploymentName = deploymentName;
        this.objectMapper = objectMapper;
        
        if (endpoint == null || endpoint.isEmpty() || apiKey == null || apiKey.isEmpty()) {
            logger.warn("Azure OpenAI endpoint or API key not configured. AI course generation will be disabled.");
            this.client = null;
        } else {
            // Normalize endpoint - ensure it doesn't end with / and is properly formatted
            String normalizedEndpoint = endpoint.trim();
            if (normalizedEndpoint.endsWith("/")) {
                normalizedEndpoint = normalizedEndpoint.substring(0, normalizedEndpoint.length() - 1);
            }
            
            logger.info("Initializing Azure OpenAI client:");
            logger.info("  - Endpoint: {}", normalizedEndpoint);
            logger.info("  - Deployment Name: {}", deploymentName);
            logger.info("  - API Key: {} (length: {})", apiKey.isEmpty() ? "EMPTY" : "***" + apiKey.substring(Math.max(0, apiKey.length() - 4)), apiKey.length());
            
            this.client = new OpenAIClientBuilder()
                    .endpoint(normalizedEndpoint)
                    .credential(new AzureKeyCredential(apiKey))
                    .buildClient();
            logger.info("Azure OpenAI client initialized successfully with deployment: {}", deploymentName);
        }
    }
    
    public CourseRequirements parseCourseRequirements(String prompt) {
        if (client == null) {
            logger.error("Azure OpenAI client is null - cannot parse course requirements");
            throw new IllegalStateException("Azure OpenAI is not configured");
        }
        
        logger.info("Parsing course requirements from prompt (length: {} chars)", prompt != null ? prompt.length() : 0);
        logger.debug("Prompt content: {}", prompt);
        
        String systemPrompt = """
            You are an expert course designer. Extract course requirements from the user's prompt and return a JSON object with the following structure:
            {
                "title": "Course title",
                "description": "Detailed course description",
                "numberOfModules": 5,
                "lecturesPerModule": 4,
                "difficultyLevel": "BEGINNER|INTERMEDIATE|ADVANCED",
                "language": "en",
                "tags": ["tag1", "tag2"],
                "estimatedDurationMinutes": 120,
                "certificateEligible": true,
                "maxCompletionDays": 30
            }
            
            Extract reasonable defaults if not specified. Return ONLY valid JSON, no additional text.
            """;
        
        try {
            logger.info("Calling OpenAI API with deployment: {}", deploymentName);
            String response = callOpenAI(systemPrompt, prompt);
            logger.info("Received response from OpenAI (length: {} chars)", response != null ? response.length() : 0);
            // Clean response - remove markdown code blocks if present
            response = response.trim();
            if (response.startsWith("```json")) {
                response = response.substring(7);
            }
            if (response.startsWith("```")) {
                response = response.substring(3);
            }
            if (response.endsWith("```")) {
                response = response.substring(0, response.length() - 3);
            }
            response = response.trim();
            
            logger.debug("Parsing JSON response: {}", response);
            JsonNode jsonNode = objectMapper.readTree(response);
            CourseRequirements requirements = objectMapper.treeToValue(jsonNode, CourseRequirements.class);
            logger.info("Successfully parsed course requirements: title={}, modules={}, lecturesPerModule={}", 
                requirements.getTitle(), requirements.getNumberOfModules(), requirements.getLecturesPerModule());
            
            // Set defaults if not provided
            if (requirements.getLanguage() == null || requirements.getLanguage().isEmpty()) {
                requirements.setLanguage("en");
            }
            if (requirements.getNumberOfModules() == null || requirements.getNumberOfModules() < 1) {
                requirements.setNumberOfModules(5);
            }
            if (requirements.getLecturesPerModule() == null || requirements.getLecturesPerModule() < 1) {
                requirements.setLecturesPerModule(4);
            }
            if (requirements.getEstimatedDurationMinutes() == null) {
                requirements.setEstimatedDurationMinutes(requirements.getNumberOfModules() * requirements.getLecturesPerModule() * 10);
            }
            
            return requirements;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            logger.error("Failed to parse JSON response from OpenAI. Response may be malformed.", e);
            logger.error("Raw response that failed to parse: {}", e.getMessage());
            throw new RuntimeException("Failed to parse course requirements: Invalid JSON response from OpenAI. " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Failed to parse course requirements", e);
            throw new RuntimeException("Failed to parse course requirements: " + e.getMessage(), e);
        }
    }
    
    public List<SectionInfo> generateCourseStructure(CourseRequirements requirements) {
        if (client == null) {
            throw new IllegalStateException("Azure OpenAI is not configured");
        }
        
        String systemPrompt = """
            You are an expert course designer. Generate a course structure with sections (modules) and lectures.
            Return a JSON array where each section has:
            {
                "title": "Section title",
                "description": "Section description",
                "lectures": [
                    {"title": "Lecture title", "description": "Brief lecture description"}
                ]
            }
            
            Generate %d sections with approximately %d lectures each.
            Return ONLY valid JSON array, no additional text.
            """.formatted(requirements.getNumberOfModules(), requirements.getLecturesPerModule());
        
        String userPrompt = "Course: " + requirements.getTitle() + "\nDescription: " + requirements.getDescription();
        
        try {
            String response = callOpenAI(systemPrompt, userPrompt);
            // Clean response
            response = response.trim();
            if (response.startsWith("```json")) {
                response = response.substring(7);
            }
            if (response.startsWith("```")) {
                response = response.substring(3);
            }
            if (response.endsWith("```")) {
                response = response.substring(0, response.length() - 3);
            }
            response = response.trim();
            
            JsonNode jsonArray = objectMapper.readTree(response);
            List<SectionInfo> sections = new ArrayList<>();
            
            for (JsonNode sectionNode : jsonArray) {
                SectionInfo section = new SectionInfo();
                section.setTitle(sectionNode.get("title").asText());
                section.setDescription(sectionNode.has("description") ? sectionNode.get("description").asText() : "");
                
                List<LectureInfo> lectures = new ArrayList<>();
                if (sectionNode.has("lectures")) {
                    for (JsonNode lectureNode : sectionNode.get("lectures")) {
                        LectureInfo lecture = new LectureInfo();
                        lecture.setTitle(lectureNode.get("title").asText());
                        lecture.setDescription(lectureNode.has("description") ? lectureNode.get("description").asText() : "");
                        lectures.add(lecture);
                    }
                }
                section.setLectures(lectures);
                sections.add(section);
            }
            
            return sections;
        } catch (Exception e) {
            logger.error("Error generating course structure", e);
            throw new RuntimeException("Failed to generate course structure: " + e.getMessage(), e);
        }
    }
    
    public String generateSectionContent(String sectionTitle, String courseContext) {
        if (client == null) {
            throw new IllegalStateException("Azure OpenAI is not configured");
        }
        
        String systemPrompt = """
            You are an expert course designer. Generate a detailed description for a course section.
            Make it engaging and informative, explaining what students will learn in this section.
            """;
        
        String userPrompt = "Course Context: " + courseContext + "\n\nSection: " + sectionTitle + "\n\nGenerate a detailed section description.";
        
        try {
            return callOpenAI(systemPrompt, userPrompt);
        } catch (Exception e) {
            logger.error("Error generating section content", e);
            throw new RuntimeException("Failed to generate section content: " + e.getMessage(), e);
        }
    }
    
    public String generateLectureContent(String lectureTitle, String sectionContext, String courseContext, 
                                        String writingFormat, String referenceContent) {
        if (client == null) {
            throw new IllegalStateException("Azure OpenAI is not configured");
        }
        
        StringBuilder systemPromptBuilder = new StringBuilder();
        systemPromptBuilder.append("""
            You are an expert course instructor. Generate comprehensive, detailed lecture content in markdown format.
            Include:
            - Introduction
            - Main concepts with explanations
            - Examples and practical applications
            - Key takeaways
            - Summary
            
            Make it educational, clear, and well-structured. Use markdown formatting for headings, lists, code blocks, etc.
            """);
        
        // Add writing format guidance if provided
        if (writingFormat != null && !writingFormat.trim().isEmpty()) {
            systemPromptBuilder.append("\n\nIMPORTANT: Follow this writing style and format:\n");
            systemPromptBuilder.append(writingFormat);
            systemPromptBuilder.append("\n\nEnsure all generated content matches this writing style.");
        }
        
        String systemPrompt = systemPromptBuilder.toString();
        
        StringBuilder userPromptBuilder = new StringBuilder();
        userPromptBuilder.append(String.format(
            "Course: %s\n\nSection: %s\n\nLecture: %s\n\n",
            courseContext, sectionContext, lectureTitle
        ));
        
        // Add reference content if provided
        if (referenceContent != null && !referenceContent.trim().isEmpty()) {
            userPromptBuilder.append("Use the following reference content as context:\n");
            userPromptBuilder.append(referenceContent);
            userPromptBuilder.append("\n\n");
        }
        
        userPromptBuilder.append("Generate comprehensive lecture content in markdown format.");
        
        String userPrompt = userPromptBuilder.toString();
        
        try {
            return callOpenAI(systemPrompt, userPrompt);
        } catch (Exception e) {
            logger.error("Error generating lecture content", e);
            throw new RuntimeException("Failed to generate lecture content: " + e.getMessage(), e);
        }
    }
    
    public List<String> generateLearningObjectives(String courseTitle, String courseDescription, List<String> sectionTitles) {
        if (client == null) {
            throw new IllegalStateException("Azure OpenAI is not configured");
        }
        
        String systemPrompt = """
            You are an expert course designer. Generate 3-5 learning objectives for a course.
            Return a JSON array of strings, each being a learning objective.
            Return ONLY valid JSON array, no additional text.
            """;
        
        String sectionsText = String.join(", ", sectionTitles);
        String userPrompt = String.format(
            "Course: %s\nDescription: %s\nSections: %s\n\nGenerate learning objectives.",
            courseTitle, courseDescription, sectionsText
        );
        
        try {
            String response = callOpenAI(systemPrompt, userPrompt);
            // Clean response
            response = response.trim();
            if (response.startsWith("```json")) {
                response = response.substring(7);
            }
            if (response.startsWith("```")) {
                response = response.substring(3);
            }
            if (response.endsWith("```")) {
                response = response.substring(0, response.length() - 3);
            }
            response = response.trim();
            
            JsonNode jsonArray = objectMapper.readTree(response);
            List<String> objectives = new ArrayList<>();
            for (JsonNode node : jsonArray) {
                objectives.add(node.asText());
            }
            return objectives;
        } catch (Exception e) {
            logger.error("Error generating learning objectives", e);
            throw new RuntimeException("Failed to generate learning objectives: " + e.getMessage(), e);
        }
    }
    
    private String callOpenAI(String systemPrompt, String userPrompt) {
        if (client == null) {
            throw new IllegalStateException("Azure OpenAI is not configured");
        }
        
        List<ChatRequestMessage> messages = new ArrayList<>();
        messages.add(new ChatRequestSystemMessage(systemPrompt));
        messages.add(new ChatRequestUserMessage(userPrompt));
        
        ChatCompletionsOptions options = new ChatCompletionsOptions(messages);
        options.setTemperature(0.7d);
        options.setMaxTokens(4000);
        
        logger.debug("Preparing OpenAI API call:");
        logger.debug("  - Deployment: {}", deploymentName);
        logger.debug("  - Messages count: {}", messages.size());
        logger.debug("  - Temperature: {}", options.getTemperature());
        logger.debug("  - Max tokens: {}", options.getMaxTokens());
        
        int maxRetries = 3;
        int retryCount = 0;
        
        while (retryCount < maxRetries) {
            try {
                logger.info("Calling OpenAI API (attempt {}/{}) with deployment: {}", retryCount + 1, maxRetries, deploymentName);
                ChatCompletions completions = client.getChatCompletions(deploymentName, options);
                logger.info("OpenAI API call successful");
                
                if (completions.getChoices() != null && !completions.getChoices().isEmpty()) {
                    String content = completions.getChoices().get(0).getMessage().getContent();
                    if (content != null && !content.isEmpty()) {
                        logger.info("Received content from OpenAI (length: {} chars)", content.length());
                        return content;
                    } else {
                        logger.warn("OpenAI returned empty content in response");
                    }
                } else {
                    logger.warn("OpenAI returned no choices in response");
                }
                
                // If we get here, content was empty - retry
                retryCount++;
                if (retryCount < maxRetries) {
                    long waitSeconds = (long) Math.pow(2, retryCount);
                    logger.info("Retrying in {} seconds (attempt {}/{})", waitSeconds, retryCount + 1, maxRetries);
                    try {
                        TimeUnit.SECONDS.sleep(waitSeconds);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted while waiting for retry", e);
                    }
                }
            } catch (com.azure.core.exception.ResourceNotFoundException e) {
                // Handle deployment not found error
                String errorMessage = String.format(
                    "Azure OpenAI deployment '%s' not found. Please verify the deployment name in your Azure OpenAI resource. " +
                    "Configure it using: azure.openai.deployment-name=<your-deployment-name>",
                    deploymentName
                );
                logger.error("ResourceNotFoundException: {}", errorMessage);
                logger.error("Deployment name used: {}", deploymentName);
                logger.error("Full error details: ", e);
                throw new IllegalStateException(errorMessage, e);
            } catch (com.azure.core.exception.HttpResponseException e) {
                // Handle HTTP errors with detailed logging
                int statusCode = e.getResponse() != null ? e.getResponse().getStatusCode() : 0;
                String responseBody = e.getValue() != null ? e.getValue().toString() : "No response body";
                
                logger.error("HttpResponseException calling OpenAI API:");
                logger.error("  - Status Code: {}", statusCode);
                logger.error("  - Deployment: {}", deploymentName);
                logger.error("  - Error Message: {}", e.getMessage());
                logger.error("  - Response Body: {}", responseBody);
                logger.error("  - Full exception: ", e);
                
                // Handle rate limiting
                if (statusCode == 429) {
                    logger.warn("Rate limited (429). Retrying...");
                    retryCount++;
                    if (retryCount < maxRetries) {
                        long waitSeconds = (long) Math.pow(2, retryCount);
                        logger.info("Retrying in {} seconds (attempt {}/{})", waitSeconds, retryCount + 1, maxRetries);
                        try {
                            TimeUnit.SECONDS.sleep(waitSeconds);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException("Interrupted while waiting for retry", ie);
                        }
                        continue;
                    }
                }
                
                // For 404 errors, provide more context
                if (statusCode == 404) {
                    String detailedError = String.format(
                        "OpenAI API returned 404 Not Found. This usually means:\n" +
                        "  1. The deployment name '%s' doesn't exist in your Azure OpenAI resource\n" +
                        "  2. The endpoint URL might be incorrect\n" +
                        "  3. The API path is wrong\n\n" +
                        "Please verify:\n" +
                        "  - Deployment name in Azure AI Studio\n" +
                        "  - Endpoint URL format (should be: https://<resource>.openai.azure.com/)\n" +
                        "  - Response body: %s",
                        deploymentName, responseBody
                    );
                    logger.error(detailedError);
                    throw new RuntimeException("Failed to call OpenAI API: " + detailedError, e);
                }
                
                throw new RuntimeException("Failed to call OpenAI API: Status " + statusCode + " - " + e.getMessage() + ". Response: " + responseBody, e);
            } catch (Exception e) {
                logger.error("Unexpected error calling OpenAI API:");
                logger.error("  - Deployment: {}", deploymentName);
                logger.error("  - Error Type: {}", e.getClass().getName());
                logger.error("  - Error Message: {}", e.getMessage());
                logger.error("  - Full exception: ", e);
                throw new RuntimeException("Failed to call OpenAI API: " + e.getMessage(), e);
            }
        }
        
        throw new RuntimeException("Failed to get response from OpenAI after " + maxRetries + " retries");
    }
    
    // Inner classes for structure generation
    public static class SectionInfo {
        private String title;
        private String description;
        private List<LectureInfo> lectures;
        
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public List<LectureInfo> getLectures() { return lectures; }
        public void setLectures(List<LectureInfo> lectures) { this.lectures = lectures; }
    }
    
    public static class LectureInfo {
        private String title;
        private String description;
        
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }
}

