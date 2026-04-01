package com.datagami.edudron.student.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public class CertificateGenerateRequest {

    @NotBlank(message = "Course ID is required")
    private String courseId;

    private String sectionId;

    private String classId;

    @NotBlank(message = "Template ID is required")
    private String templateId;

    @NotEmpty(message = "At least one student is required")
    @Valid
    private List<StudentEntry> students;

    public CertificateGenerateRequest() {}

    // Getters and Setters
    public String getCourseId() { return courseId; }
    public void setCourseId(String courseId) { this.courseId = courseId; }

    public String getSectionId() { return sectionId; }
    public void setSectionId(String sectionId) { this.sectionId = sectionId; }

    public String getClassId() { return classId; }
    public void setClassId(String classId) { this.classId = classId; }

    public String getTemplateId() { return templateId; }
    public void setTemplateId(String templateId) { this.templateId = templateId; }

    public List<StudentEntry> getStudents() { return students; }
    public void setStudents(List<StudentEntry> students) { this.students = students; }

    public static class StudentEntry {
        private String name;

        @NotBlank(message = "Student email is required")
        private String email;

        public StudentEntry() {}

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
    }
}
