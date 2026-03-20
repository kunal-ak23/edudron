package com.datagami.edudron.student.dto;

import jakarta.validation.constraints.Min;

public class GenerateGroupsRequest {

    @Min(value = 2, message = "Group size must be at least 2")
    private int groupSize;

    public GenerateGroupsRequest() {}

    public int getGroupSize() { return groupSize; }
    public void setGroupSize(int groupSize) { this.groupSize = groupSize; }
}
