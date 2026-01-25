package com.datagami.edudron.student.dto;

import java.util.List;

public class ClassWithSectionsDTO {
    private ClassDTO classInfo;
    private List<SectionDTO> sections;

    public ClassWithSectionsDTO() {}

    public ClassWithSectionsDTO(ClassDTO classInfo, List<SectionDTO> sections) {
        this.classInfo = classInfo;
        this.sections = sections;
    }

    // Getters and Setters
    public ClassDTO getClassInfo() { return classInfo; }
    public void setClassInfo(ClassDTO classInfo) { this.classInfo = classInfo; }

    public List<SectionDTO> getSections() { return sections; }
    public void setSections(List<SectionDTO> sections) { this.sections = sections; }
}
