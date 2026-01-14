package com.datagami.edudron.student.dto;

public class StudentClassSectionInfoDTO {
    private String classId;
    private String className;
    private String sectionId;
    private String sectionName;

    // Constructors
    public StudentClassSectionInfoDTO() {}

    public StudentClassSectionInfoDTO(String classId, String className, String sectionId, String sectionName) {
        this.classId = classId;
        this.className = className;
        this.sectionId = sectionId;
        this.sectionName = sectionName;
    }

    // Getters and Setters
    public String getClassId() { return classId; }
    public void setClassId(String classId) { this.classId = classId; }

    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }

    public String getSectionId() { return sectionId; }
    public void setSectionId(String sectionId) { this.sectionId = sectionId; }

    public String getSectionName() { return sectionName; }
    public void setSectionName(String sectionName) { this.sectionName = sectionName; }
}
