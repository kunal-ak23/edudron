package com.datagami.edudron.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public class UpdateUserRequest {
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    @NotBlank(message = "Name is required")
    private String name;

    private String phone;

    @NotNull(message = "Role is required")
    private String role;

    private List<String> instituteIds;

    private Boolean active = true;

    // Getters and Setters
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public List<String> getInstituteIds() { return instituteIds; }
    public void setInstituteIds(List<String> instituteIds) { this.instituteIds = instituteIds; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
}
