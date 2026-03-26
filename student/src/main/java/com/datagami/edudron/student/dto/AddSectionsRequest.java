package com.datagami.edudron.student.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AddSectionsRequest {

    @NotEmpty(message = "At least one section must be selected")
    private List<String> sectionIds;

    @Min(value = 1, message = "Group size must be at least 1")
    private int groupSize;

    public AddSectionsRequest() {}

    public List<String> getSectionIds() { return sectionIds; }
    public void setSectionIds(List<String> sectionIds) { this.sectionIds = sectionIds; }

    public int getGroupSize() { return groupSize; }
    public void setGroupSize(int groupSize) { this.groupSize = groupSize; }
}
