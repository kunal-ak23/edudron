package com.datagami.edudron.student.web;

import com.datagami.edudron.student.dto.ClassAnalyticsDTO;
import com.datagami.edudron.student.dto.CourseAnalyticsDTO;
import com.datagami.edudron.student.dto.LectureAnalyticsDTO;
import com.datagami.edudron.student.dto.SectionAnalyticsDTO;
import com.datagami.edudron.student.dto.SectionComparisonDTO;
import com.datagami.edudron.student.dto.SkippedLectureDTO;
import com.datagami.edudron.student.service.AnalyticsService;
import com.datagami.edudron.student.service.LectureViewSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@Tag(name = "Analytics", description = "Analytics and reporting endpoints")
public class AnalyticsController {

    @Autowired
    private AnalyticsService analyticsService;
    
    @Autowired
    private LectureViewSessionService sessionService;

    @GetMapping("/lectures/{lectureId}/analytics")
    @Operation(summary = "Get lecture analytics", description = "Get detailed analytics for a specific lecture")
    public ResponseEntity<LectureAnalyticsDTO> getLectureAnalytics(@PathVariable String lectureId) {
        LectureAnalyticsDTO analytics = analyticsService.getLectureEngagementMetrics(lectureId);
        return ResponseEntity.ok(analytics);
    }

    @GetMapping("/courses/{courseId}/analytics")
    @Operation(summary = "Get course analytics", description = "Get comprehensive analytics for a course")
    public ResponseEntity<CourseAnalyticsDTO> getCourseAnalytics(@PathVariable String courseId) {
        CourseAnalyticsDTO analytics = analyticsService.getCourseEngagementMetrics(courseId);
        return ResponseEntity.ok(analytics);
    }

    @GetMapping("/courses/{courseId}/analytics/skipped")
    @Operation(summary = "Get skipped lectures", description = "Get list of lectures that are commonly skipped")
    public ResponseEntity<List<SkippedLectureDTO>> getSkippedLectures(@PathVariable String courseId) {
        CourseAnalyticsDTO courseAnalytics = analyticsService.getCourseEngagementMetrics(courseId);
        return ResponseEntity.ok(courseAnalytics.getSkippedLectures());
    }
    
    @DeleteMapping("/courses/{courseId}/analytics/cache")
    @Operation(summary = "Clear course analytics cache", description = "Manually clear the cached analytics for a specific course")
    public ResponseEntity<Map<String, String>> clearCourseAnalyticsCache(@PathVariable String courseId) {
        sessionService.evictCourseAnalyticsCache(courseId);
        return ResponseEntity.ok(Map.of("message", "Cache cleared for course: " + courseId));
    }

    // ==================== SECTION ANALYTICS ENDPOINTS ====================

    @GetMapping("/sections/{sectionId}/analytics")
    @Operation(summary = "Get section analytics", description = "Get comprehensive analytics for a section (aggregated across all courses)")
    public ResponseEntity<SectionAnalyticsDTO> getSectionAnalytics(@PathVariable String sectionId) {
        SectionAnalyticsDTO analytics = analyticsService.getSectionEngagementMetrics(sectionId);
        return ResponseEntity.ok(analytics);
    }

    @GetMapping("/sections/{sectionId}/analytics/skipped")
    @Operation(summary = "Get skipped lectures for section", description = "Get list of lectures commonly skipped in a section")
    public ResponseEntity<List<SkippedLectureDTO>> getSectionSkippedLectures(@PathVariable String sectionId) {
        SectionAnalyticsDTO analytics = analyticsService.getSectionEngagementMetrics(sectionId);
        return ResponseEntity.ok(analytics.getSkippedLectures());
    }

    @DeleteMapping("/sections/{sectionId}/analytics/cache")
    @Operation(summary = "Clear section analytics cache", description = "Manually clear the cached analytics for a specific section")
    public ResponseEntity<Map<String, String>> clearSectionAnalyticsCache(@PathVariable String sectionId) {
        sessionService.evictSectionAnalyticsCache(sectionId);
        return ResponseEntity.ok(Map.of("message", "Cache cleared for section: " + sectionId));
    }

    // ==================== CLASS ANALYTICS ENDPOINTS ====================

    @GetMapping("/classes/{classId}/analytics")
    @Operation(summary = "Get class analytics", description = "Get comprehensive analytics for a class (aggregated across all sections and courses)")
    public ResponseEntity<ClassAnalyticsDTO> getClassAnalytics(@PathVariable String classId) {
        ClassAnalyticsDTO analytics = analyticsService.getClassEngagementMetrics(classId);
        return ResponseEntity.ok(analytics);
    }

    @GetMapping("/classes/{classId}/analytics/sections/compare")
    @Operation(summary = "Compare sections in a class", description = "Get performance comparison of all sections within a class")
    public ResponseEntity<List<SectionComparisonDTO>> compareSections(@PathVariable String classId) {
        ClassAnalyticsDTO analytics = analyticsService.getClassEngagementMetrics(classId);
        return ResponseEntity.ok(analytics.getSectionComparison());
    }

    @DeleteMapping("/classes/{classId}/analytics/cache")
    @Operation(summary = "Clear class analytics cache", description = "Manually clear the cached analytics for a specific class")
    public ResponseEntity<Map<String, String>> clearClassAnalyticsCache(@PathVariable String classId) {
        sessionService.evictClassAnalyticsCache(classId);
        return ResponseEntity.ok(Map.of("message", "Cache cleared for class: " + classId));
    }
}
