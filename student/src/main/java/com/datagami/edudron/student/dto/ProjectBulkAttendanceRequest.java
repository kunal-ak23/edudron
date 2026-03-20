package com.datagami.edudron.student.dto;

import java.util.List;

public class ProjectBulkAttendanceRequest {
    private List<AttendanceEntry> entries;

    public ProjectBulkAttendanceRequest() {}

    public List<AttendanceEntry> getEntries() { return entries; }
    public void setEntries(List<AttendanceEntry> entries) { this.entries = entries; }

    public static class AttendanceEntry {
        private String studentId;
        private Boolean present;

        public AttendanceEntry() {}

        public String getStudentId() { return studentId; }
        public void setStudentId(String studentId) { this.studentId = studentId; }

        public Boolean getPresent() { return present; }
        public void setPresent(Boolean present) { this.present = present; }
    }
}
