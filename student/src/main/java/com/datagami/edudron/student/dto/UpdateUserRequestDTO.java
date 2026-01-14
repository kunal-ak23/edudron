package com.datagami.edudron.student.dto;

import java.util.List;

public class UpdateUserRequestDTO {
    private String email;
    private String name;
    private String phone;
    private String role;
    private List<String> instituteIds;
    private Boolean active;

    // Getters and Setters
    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public List<String> getInstituteIds() {
        return instituteIds;
    }

    public void setInstituteIds(List<String> instituteIds) {
        this.instituteIds = instituteIds;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
