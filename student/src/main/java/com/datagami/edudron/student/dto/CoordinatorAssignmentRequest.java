package com.datagami.edudron.student.dto;

import jakarta.validation.constraints.NotBlank;

public record CoordinatorAssignmentRequest(
    @NotBlank(message = "coordinatorUserId is required")
    String coordinatorUserId
) {}
