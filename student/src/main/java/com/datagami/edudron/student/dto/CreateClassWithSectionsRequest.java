package com.datagami.edudron.student.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public class CreateClassWithSectionsRequest {
    @NotBlank(message = "Class name is required")
    private String name;
    
    @NotBlank(message = "Class code is required")
    private String code;
    
    @NotBlank(message = "Institute ID is required")
    private String instituteId;
    
    private String academicYear;
    
    private String grade;
    
    private String level;
    
    private Boolean isActive = true;
    
    @NotEmpty(message = "At least one section is required")
    @Size(min = 1, max = 50, message = "Must have between 1 and 50 sections")
    @Valid
    private List<CreateSectionForClassRequest> sections;

    // Getters and Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getInstituteId() { return instituteId; }
    public void setInstituteId(String instituteId) { this.instituteId = instituteId; }

    public String getAcademicYear() { return academicYear; }
    public void setAcademicYear(String academicYear) { this.academicYear = academicYear; }

    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }

    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public List<CreateSectionForClassRequest> getSections() { return sections; }
    public void setSections(List<CreateSectionForClassRequest> sections) { this.sections = sections; }
}
