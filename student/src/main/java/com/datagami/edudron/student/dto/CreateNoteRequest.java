package com.datagami.edudron.student.dto;

public class CreateNoteRequest {
    private String lectureId;
    private String courseId;
    private String highlightedText;
    private String highlightColor; // Hex color code
    private String noteText; // Optional note
    private String context; // Optional context

    // Constructors
    public CreateNoteRequest() {}

    // Getters and Setters
    public String getLectureId() { return lectureId; }
    public void setLectureId(String lectureId) { this.lectureId = lectureId; }

    public String getCourseId() { return courseId; }
    public void setCourseId(String courseId) { this.courseId = courseId; }

    public String getHighlightedText() { return highlightedText; }
    public void setHighlightedText(String highlightedText) { this.highlightedText = highlightedText; }

    public String getHighlightColor() { return highlightColor; }
    public void setHighlightColor(String highlightColor) { this.highlightColor = highlightColor; }

    public String getNoteText() { return noteText; }
    public void setNoteText(String noteText) { this.noteText = noteText; }

    public String getContext() { return context; }
    public void setContext(String context) { this.context = context; }
}

