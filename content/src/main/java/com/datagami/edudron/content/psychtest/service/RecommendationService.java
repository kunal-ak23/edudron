package com.datagami.edudron.content.psychtest.service;

import com.datagami.edudron.content.domain.Course;
import com.datagami.edudron.content.repo.CourseRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class RecommendationService {
    private final CourseRepository courseRepository;

    public RecommendationService(CourseRepository courseRepository) {
        this.courseRepository = courseRepository;
    }

    public record RecommendedCourse(String courseId, String title, String description, String reason) {}

    public List<RecommendedCourse> recommend(
        UUID clientId,
        String streamSuggestion,
        List<String> topDomains,
        String weakestIndicatorTag,
        List<String> careerFields
    ) {
        Map<String, RecommendedCourse> picked = new LinkedHashMap<>();

        String top1 = topDomains != null && !topDomains.isEmpty() ? topDomains.get(0) : null;

        // 1) Top interest
        if (streamSuggestion != null && top1 != null) {
            List<Course> courses = courseRepository.findCuratedByStreamAndRiasec(clientId, streamSuggestion, top1, 3);
            for (Course c : courses) {
                picked.putIfAbsent(c.getId(),
                    new RecommendedCourse(c.getId(), c.getTitle(), c.getDescription(), "Top interest match (" + streamSuggestion + " + " + top1 + ")"));
                if (picked.size() >= 1) break;
            }
        }

        // 2) Skill strengthening (optional)
        if (weakestIndicatorTag != null) {
            List<Course> courses = courseRepository.findCuratedBySkill(clientId, weakestIndicatorTag, 3);
            for (Course c : courses) {
                picked.putIfAbsent(c.getId(),
                    new RecommendedCourse(c.getId(), c.getTitle(), c.getDescription(), "Skill strengthening (" + weakestIndicatorTag + ")"));
                if (picked.size() >= 2) break;
            }
        }

        // 3) Aligned career field (best-effort heuristic via stream + top1)
        if (picked.size() < 3 && streamSuggestion != null) {
            List<Course> courses = courseRepository.findCuratedByStreamAndRiasec(clientId, streamSuggestion, null, 5);
            for (Course c : courses) {
                picked.putIfAbsent(c.getId(),
                    new RecommendedCourse(c.getId(), c.getTitle(), c.getDescription(), "Aligned with your suggested stream"));
                if (picked.size() >= 3) break;
            }
        }

        return new ArrayList<>(picked.values()).subList(0, Math.min(3, picked.size()));
    }
}

