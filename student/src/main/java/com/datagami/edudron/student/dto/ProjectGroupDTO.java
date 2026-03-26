package com.datagami.edudron.student.dto;

import com.datagami.edudron.student.domain.ProjectGroup;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public class ProjectGroupDTO {
    private String id;
    private String projectId;
    private Integer groupNumber;
    private String groupName;
    private String problemStatementId;
    private String submissionUrl;
    private OffsetDateTime submittedAt;
    private String submittedBy;
    private List<MemberInfo> members = new ArrayList<>();
    private List<ProjectAttachmentDTO> submissionAttachments = new ArrayList<>();

    public ProjectGroupDTO() {}

    public static ProjectGroupDTO fromEntity(ProjectGroup g) {
        ProjectGroupDTO dto = new ProjectGroupDTO();
        dto.setId(g.getId());
        dto.setProjectId(g.getProjectId());
        dto.setGroupNumber(g.getGroupNumber());
        dto.setGroupName(g.getGroupName());
        dto.setProblemStatementId(g.getProblemStatementId());
        dto.setSubmissionUrl(g.getSubmissionUrl());
        dto.setSubmittedAt(g.getSubmittedAt());
        dto.setSubmittedBy(g.getSubmittedBy());
        return dto;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public Integer getGroupNumber() { return groupNumber; }
    public void setGroupNumber(Integer groupNumber) { this.groupNumber = groupNumber; }

    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }

    public String getProblemStatementId() { return problemStatementId; }
    public void setProblemStatementId(String problemStatementId) { this.problemStatementId = problemStatementId; }

    public String getSubmissionUrl() { return submissionUrl; }
    public void setSubmissionUrl(String submissionUrl) { this.submissionUrl = submissionUrl; }

    public OffsetDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(OffsetDateTime submittedAt) { this.submittedAt = submittedAt; }

    public String getSubmittedBy() { return submittedBy; }
    public void setSubmittedBy(String submittedBy) { this.submittedBy = submittedBy; }

    public List<MemberInfo> getMembers() { return members; }
    public void setMembers(List<MemberInfo> members) { this.members = members; }

    public List<ProjectAttachmentDTO> getSubmissionAttachments() { return submissionAttachments; }
    public void setSubmissionAttachments(List<ProjectAttachmentDTO> submissionAttachments) { this.submissionAttachments = submissionAttachments; }

    public static class MemberInfo {
        private String studentId;
        private String name;
        private String email;

        public MemberInfo() {}

        public MemberInfo(String studentId, String name, String email) {
            this.studentId = studentId;
            this.name = name;
            this.email = email;
        }

        public String getStudentId() { return studentId; }
        public void setStudentId(String studentId) { this.studentId = studentId; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
    }
}
