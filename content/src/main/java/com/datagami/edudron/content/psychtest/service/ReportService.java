package com.datagami.edudron.content.psychtest.service;

import com.datagami.edudron.content.psychtest.service.RecommendationService.RecommendedCourse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ReportService {
    public static final String DISCLAIMER = "This guidance is for educational and career planning purposes only. It is guidance, not diagnosis.";

    private final ObjectMapper objectMapper;

    public ReportService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Deterministic fallback report (AI-backed report will be added in ai-client todo).
     */
    public String buildFallbackReportJson(
        List<String> topDomains,
        String overallConfidence,
        String streamSuggestion,
        List<String> careerFields,
        List<RecommendedCourse> courses
    ) {
        try {
            Map<String, Object> report = new HashMap<>();
            report.put("disclaimer", DISCLAIMER);
            report.put("top_domains", topDomains);
            report.put("overall_confidence", overallConfidence);
            report.put("stream_suggestion", streamSuggestion);
            report.put("career_fields", careerFields);
            report.put("strengths", List.of(
                "Shows interest patterns aligned with your top RIASEC domains.",
                "Has consistent responses in key areas (based on answered questions)."
            ));
            report.put("growth_areas", List.of(
                "Try short projects to validate interests in real life.",
                "Strengthen fundamentals through small weekly practice goals."
            ));
            report.put("next_steps", List.of(
                "Explore a small project related to your top domain this week.",
                "Talk to a teacher/mentor about subjects you enjoy.",
                "Try one recommended course and track what you liked."
            ));
            report.put("recommended_courses", courses);
            return objectMapper.writeValueAsString(report);
        } catch (Exception e) {
            return "{\"disclaimer\":\"" + DISCLAIMER.replace("\"", "") + "\"}";
        }
    }
}

