package com.datagami.edudron.content.dto;

import java.time.OffsetDateTime;

public class TestMessageDTO {
    private String role; // "user" or "assistant"
    private String content;
    private OffsetDateTime timestamp;
    
    public TestMessageDTO() {}
    
    public TestMessageDTO(String role, String content, OffsetDateTime timestamp) {
        this.role = role;
        this.content = content;
        this.timestamp = timestamp;
    }
    
    // Getters and Setters
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    
    public OffsetDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(OffsetDateTime timestamp) { this.timestamp = timestamp; }
}
