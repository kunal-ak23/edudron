package com.datagami.edudron.student.web;

import com.datagami.edudron.student.dto.CourseAnalyticsDTO;
import com.datagami.edudron.student.dto.LectureAnalyticsDTO;
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
}
