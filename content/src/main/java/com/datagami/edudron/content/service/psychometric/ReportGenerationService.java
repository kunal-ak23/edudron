package com.datagami.edudron.content.service.psychometric;

import com.datagami.edudron.content.domain.PsychometricTestResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Service for generating downloadable reports (PDF or web view)
 */
@Service
public class ReportGenerationService {
    
    private static final Logger logger = LoggerFactory.getLogger(ReportGenerationService.class);
    
    /**
     * Generate report content as HTML (can be converted to PDF)
     */
    public String generateReportHtml(PsychometricTestResult result, TestResult testResult, String studentName) {
        StringBuilder html = new StringBuilder();
        
        html.append("<!DOCTYPE html>\n");
        html.append("<html>\n<head>\n");
        html.append("<meta charset='UTF-8'>\n");
        html.append("<title>Career Guidance Assessment Report</title>\n");
        html.append("<style>\n");
        html.append("body { font-family: Arial, sans-serif; margin: 40px; line-height: 1.6; }\n");
        html.append("h1 { color: #1f2937; border-bottom: 3px solid #3b82f6; padding-bottom: 10px; }\n");
        html.append("h2 { color: #374151; margin-top: 30px; margin-bottom: 15px; }\n");
        html.append(".disclaimer { background-color: #fef3c7; border-left: 4px solid #f59e0b; padding: 15px; margin: 20px 0; }\n");
        html.append(".section { margin: 20px 0; padding: 15px; background-color: #f9fafb; border-radius: 5px; }\n");
        html.append(".primary-stream { background-color: #dbeafe; padding: 15px; border-radius: 5px; margin: 10px 0; }\n");
        html.append(".riasec-theme { margin: 10px 0; padding: 10px; background-color: white; border-left: 3px solid #3b82f6; }\n");
        html.append(".career-field { display: inline-block; margin: 5px; padding: 8px 15px; background-color: #e5e7eb; border-radius: 20px; }\n");
        html.append("</style>\n");
        html.append("</head>\n<body>\n");
        
        // Header
        html.append("<h1>Career Guidance Assessment Report</h1>\n");
        html.append("<p><strong>Student:</strong> ").append(escapeHtml(studentName)).append("</p>\n");
        html.append("<p><strong>Test Date:</strong> ").append(
            result.getCreatedAt().format(DateTimeFormatter.ofPattern("MMMM dd, yyyy"))
        ).append("</p>\n");
        
        // Disclaimer
        html.append("<div class='disclaimer'>\n");
        html.append("<h3>Important Disclaimer</h3>\n");
        html.append("<p>").append(escapeHtml(getDisclaimerText())).append("</p>\n");
        html.append("</div>\n");
        
        // What the test measures
        html.append("<h2>What This Test Measures</h2>\n");
        html.append("<div class='section'>\n");
        html.append("<p>This assessment evaluates your interests, skills, and personality traits using:</p>\n");
        html.append("<ul>\n");
        html.append("<li><strong>RIASEC Model:</strong> Realistic, Investigative, Artistic, Social, Enterprising, and Conventional personality types</li>\n");
        html.append("<li><strong>Skill Indicators:</strong> Math confidence, language confidence, logic, teamwork, leadership, and expression</li>\n");
        html.append("<li><strong>Stream Preferences:</strong> Science, Commerce, and Arts</li>\n");
        html.append("</ul>\n");
        html.append("</div>\n");
        
        // Primary Stream
        if (testResult.getPrimaryStream() != null) {
            html.append("<h2>Suggested Primary Stream</h2>\n");
            html.append("<div class='primary-stream'>\n");
            html.append("<h3>").append(escapeHtml(testResult.getPrimaryStream())).append("</h3>\n");
            if (testResult.getSecondaryStream() != null) {
                html.append("<p><strong>Secondary Stream:</strong> ").append(escapeHtml(testResult.getSecondaryStream())).append("</p>\n");
            }
            html.append("</div>\n");
        }
        
        // RIASEC Results
        if (testResult.getTopRiasecThemes() != null && !testResult.getTopRiasecThemes().isEmpty()) {
            html.append("<h2>Your RIASEC Profile</h2>\n");
            html.append("<div class='section'>\n");
            for (RiasecTheme theme : testResult.getTopRiasecThemes()) {
                html.append("<div class='riasec-theme'>\n");
                html.append("<h4>").append(escapeHtml(theme.getName())).append(" (").append(theme.getCode()).append(")</h4>\n");
                html.append("<p>").append(escapeHtml(theme.getExplanation())).append("</p>\n");
                html.append("<p><strong>Score:</strong> ").append(String.format("%.2f", theme.getScore())).append("</p>\n");
                html.append("</div>\n");
            }
            html.append("</div>\n");
        }
        
        // Suggested Career Fields
        if (testResult.getSuggestedCareerFields() != null && !testResult.getSuggestedCareerFields().isEmpty()) {
            html.append("<h2>Suggested Career Fields</h2>\n");
            html.append("<div class='section'>\n");
            for (String career : testResult.getSuggestedCareerFields()) {
                html.append("<span class='career-field'>").append(escapeHtml(career)).append("</span>\n");
            }
            html.append("</div>\n");
        }
        
        // Suggested Courses
        if (testResult.getSuggestedCourseIds() != null && !testResult.getSuggestedCourseIds().isEmpty()) {
            html.append("<h2>Suggested LMS Courses</h2>\n");
            html.append("<div class='section'>\n");
            html.append("<ul>\n");
            for (String courseId : testResult.getSuggestedCourseIds()) {
                html.append("<li>").append(escapeHtml(courseId)).append("</li>\n");
            }
            html.append("</ul>\n");
            html.append("</div>\n");
        }
        
        // Reasoning
        if (testResult.getReasoning() != null && !testResult.getReasoning().isEmpty()) {
            html.append("<h2>Why These Suggestions?</h2>\n");
            html.append("<div class='section'>\n");
            html.append("<p>").append(escapeHtml(testResult.getReasoning())).append("</p>\n");
            html.append("</div>\n");
        }
        
        // Final Disclaimer
        html.append("<div class='disclaimer' style='margin-top: 40px;'>\n");
        html.append("<p><strong>Remember:</strong> ").append(escapeHtml(getDisclaimerText())).append("</p>\n");
        html.append("</div>\n");
        
        html.append("</body>\n</html>");
        
        return html.toString();
    }
    
    /**
     * Get disclaimer text
     */
    private String getDisclaimerText() {
        return "This assessment is generated by an AI system. No human counselor has reviewed individual results. " +
               "Results are suggestions only and non-binding. Students should discuss decisions with parents/guardians/counselors.";
    }
    
    /**
     * Escape HTML special characters
     */
    private String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    }
}
