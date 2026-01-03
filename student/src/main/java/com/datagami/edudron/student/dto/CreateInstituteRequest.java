package com.datagami.edudron.student.dto;

import com.datagami.edudron.student.domain.Institute;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class CreateInstituteRequest {
    @NotBlank(message = "Institute name is required")
    private String name;
    
    @NotBlank(message = "Institute code is required")
    private String code;
    
    @NotNull(message = "Institute type is required")
    private Institute.InstituteType type;
    
    private String address;
    
    private Boolean isActive = true;

    // Getters and Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public Institute.InstituteType getType() { return type; }
    public void setType(Institute.InstituteType type) { this.type = type; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
}

