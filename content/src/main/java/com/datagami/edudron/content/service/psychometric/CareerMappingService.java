package com.datagami.edudron.content.service.psychometric;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Mapping Service for Career Fields and Course Suggestions
 * Maps RIASEC themes and streams to career fields and courses
 */
@Service
public class CareerMappingService {
    
    private static final Logger logger = LoggerFactory.getLogger(CareerMappingService.class);
    
    // RIASEC to Career Fields mapping
    private static final Map<String, List<String>> RIASEC_TO_CAREERS = Map.of(
        "R", Arrays.asList(
            "Mechanical Engineering", "Civil Engineering", "Architecture", 
            "Automotive Technology", "Construction Management", "Industrial Design"
        ),
        "I", Arrays.asList(
            "Data Science", "Research Scientist", "Software Engineering",
            "Biomedical Research", "Physics Research", "Chemistry Research"
        ),
        "A", Arrays.asList(
            "Graphic Design", "Creative Writing", "Music Production",
            "Film Making", "Fine Arts", "Advertising"
        ),
        "S", Arrays.asList(
            "Teaching", "Counseling", "Social Work",
            "Healthcare", "Human Resources", "Public Relations"
        ),
        "E", Arrays.asList(
            "Business Management", "Entrepreneurship", "Sales",
            "Marketing", "Investment Banking", "Consulting"
        ),
        "C", Arrays.asList(
            "Accounting", "Financial Analysis", "Data Entry",
            "Administration", "Banking", "Auditing"
        )
    );
    
    // Stream to Career Fields mapping
    private static final Map<String, List<String>> STREAM_TO_CAREERS = Map.of(
        "Science", Arrays.asList(
            "Engineering", "Medicine", "Research",
            "Data Science", "Biotechnology", "Pharmacy"
        ),
        "Commerce", Arrays.asList(
            "Business Management", "Finance", "Accounting",
            "Economics", "Marketing", "Entrepreneurship"
        ),
        "Arts", Arrays.asList(
            "Journalism", "Law", "Psychology",
            "Social Work", "Teaching", "Creative Arts"
        )
    );
    
    // Indicator to Course Bias mapping (for weak indicators)
    private static final Map<String, List<String>> INDICATOR_TO_COURSE_BIAS = Map.of(
        "math_confidence", Arrays.asList("Math Foundation", "Basic Mathematics", "Math Booster"),
        "language_confidence", Arrays.asList("English Foundation", "Communication Skills", "Language Booster"),
        "logic_confidence", Arrays.asList("Logical Reasoning", "Problem Solving", "Critical Thinking"),
        "teamwork", Arrays.asList("Team Management", "Collaboration Skills"),
        "leadership", Arrays.asList("Leadership Development", "Management Skills"),
        "expression_confidence", Arrays.asList("Public Speaking", "Communication Skills", "Presentation Skills")
    );
    
    // Stream + RIASEC to Course Tags (example - should be configurable)
    private static final Map<String, List<String>> STREAM_RIASEC_TO_COURSE_TAGS = Map.of(
        "Science_I", Arrays.asList("science", "research", "analytical", "mathematics"),
        "Science_R", Arrays.asList("science", "engineering", "practical", "hands-on"),
        "Commerce_E", Arrays.asList("commerce", "business", "management", "entrepreneurship"),
        "Commerce_C", Arrays.asList("commerce", "accounting", "finance", "organization"),
        "Arts_A", Arrays.asList("arts", "creative", "expression", "literature"),
        "Arts_S", Arrays.asList("arts", "social", "communication", "helping")
    );
    
    /**
     * Get suggested career fields based on RIASEC themes and streams
     */
    public List<String> getSuggestedCareerFields(List<RiasecTheme> topRiasecThemes, 
                                                  String primaryStream, 
                                                  String secondaryStream) {
        Set<String> careers = new LinkedHashSet<>();
        
        // Add careers from top RIASEC themes
        for (RiasecTheme theme : topRiasecThemes) {
            List<String> riasecCareers = RIASEC_TO_CAREERS.get(theme.getCode());
            if (riasecCareers != null) {
                careers.addAll(riasecCareers.subList(0, Math.min(2, riasecCareers.size())));
            }
        }
        
        // Add careers from primary stream
        List<String> streamCareers = STREAM_TO_CAREERS.get(primaryStream);
        if (streamCareers != null) {
            careers.addAll(streamCareers.subList(0, Math.min(2, streamCareers.size())));
        }
        
        // Add careers from secondary stream if present
        if (secondaryStream != null) {
            List<String> secondaryCareers = STREAM_TO_CAREERS.get(secondaryStream);
            if (secondaryCareers != null) {
                careers.add(secondaryCareers.get(0)); // Add one from secondary
            }
        }
        
        // Limit to 3 careers
        List<String> result = new ArrayList<>(careers);
        return result.subList(0, Math.min(3, result.size()));
    }
    
    /**
     * Get suggested course IDs based on stream, RIASEC, and indicators
     * This should query actual course database - for now returns example tags
     */
    public List<String> getSuggestedCourseIds(String primaryStream,
                                              List<RiasecTheme> topRiasecThemes,
                                              Map<String, Double> indicatorScores) {
        // TODO: This should query actual course database with tags
        // For now, return example course tags that would be used to query
        
        List<String> courseTags = new ArrayList<>();
        
        // Add stream tag
        courseTags.add(primaryStream.toLowerCase());
        
        // Add top RIASEC tag
        if (!topRiasecThemes.isEmpty()) {
            String riasecCode = topRiasecThemes.get(0).getCode();
            String streamRiasecKey = primaryStream + "_" + riasecCode;
            List<String> tags = STREAM_RIASEC_TO_COURSE_TAGS.get(streamRiasecKey);
            if (tags != null) {
                courseTags.addAll(tags);
            }
        }
        
        // Add course bias for weak indicators
        for (Map.Entry<String, Double> indicator : indicatorScores.entrySet()) {
            if (indicator.getValue() < -2.0) { // Weak indicator
                List<String> biasCourses = INDICATOR_TO_COURSE_BIAS.get(indicator.getKey());
                if (biasCourses != null) {
                    courseTags.addAll(biasCourses);
                }
            }
        }
        
        // TODO: Query course database with these tags and return actual course IDs
        // For now, return placeholder
        logger.info("Suggested course tags: {}", courseTags);
        return Arrays.asList("COURSE_001", "COURSE_002", "COURSE_003"); // Placeholder
    }
    
    /**
     * Get course tags for a given stream and RIASEC combination
     */
    public List<String> getCourseTagsForStreamRiasec(String stream, String riasecCode) {
        String key = stream + "_" + riasecCode;
        return STREAM_RIASEC_TO_COURSE_TAGS.getOrDefault(key, new ArrayList<>());
    }
}
