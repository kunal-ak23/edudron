package com.datagami.edudron.identity.dto;

/**
 * Response DTO for admin-initiated password reset.
 * Contains the temporary password that the admin should share with the user.
 */
public class PasswordResetResponse {
    private String temporaryPassword;
    private String message;

    public PasswordResetResponse() {}

    public PasswordResetResponse(String temporaryPassword, String message) {
        this.temporaryPassword = temporaryPassword;
        this.message = message;
    }

    // Getters and Setters
    public String getTemporaryPassword() { return temporaryPassword; }
    public void setTemporaryPassword(String temporaryPassword) { this.temporaryPassword = temporaryPassword; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
