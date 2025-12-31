package com.datagami.edudron.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;

public record AuthRequest(
    @NotBlank(message = "Email is required")
    @Email(message = "Email should be valid")
    String email,
    
    @NotBlank(message = "Password is required")
    String password
) {}

