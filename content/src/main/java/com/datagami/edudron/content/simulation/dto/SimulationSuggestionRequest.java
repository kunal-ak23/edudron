package com.datagami.edudron.content.simulation.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public class SimulationSuggestionRequest {
    @NotBlank
    private String courseId;
    private List<String> lectureIds;

    public String getCourseId() { return courseId; }
    public void setCourseId(String courseId) { this.courseId = courseId; }
    public List<String> getLectureIds() { return lectureIds; }
    public void setLectureIds(List<String> lectureIds) { this.lectureIds = lectureIds; }
}
