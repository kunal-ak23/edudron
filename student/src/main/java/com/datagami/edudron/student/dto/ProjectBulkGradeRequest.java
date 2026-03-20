package com.datagami.edudron.student.dto;

import java.util.List;

public class ProjectBulkGradeRequest {
    private List<GradeEntry> entries;

    public ProjectBulkGradeRequest() {}

    public List<GradeEntry> getEntries() { return entries; }
    public void setEntries(List<GradeEntry> entries) { this.entries = entries; }

    public static class GradeEntry {
        private String studentId;
        private Integer marks;

        public GradeEntry() {}

        public String getStudentId() { return studentId; }
        public void setStudentId(String studentId) { this.studentId = studentId; }

        public Integer getMarks() { return marks; }
        public void setMarks(Integer marks) { this.marks = marks; }
    }
}
