package com.datagami.edudron.identity.web;

import com.datagami.edudron.identity.dto.AuthRequest;
import com.datagami.edudron.identity.dto.AuthResponse;
import com.datagami.edudron.identity.dto.RegisterRequest;
import com.datagami.edudron.identity.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@Tag(name = "Authentication", description = "Authentication endpoints")
public class AuthController {

    @Autowired
    private AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "User login", description = "Authenticate user and return JWT token")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        try {
            AuthResponse response = authService.login(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            throw new RuntimeException("Login failed: " + e.getMessage());
        }
    }

    @PostMapping("/register")
    @Operation(summary = "User registration", description = "Register a new user and return JWT token")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        try {
            AuthResponse response = authService.register(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            throw new RuntimeException("Registration failed: " + e.getMessage());
        }
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh token", description = "Refresh JWT token using refresh token")
    public ResponseEntity<AuthResponse> refreshToken(@RequestParam(required = false) String refreshToken, @RequestBody(required = false) Map<String, String> body) {
        // #region agent log
        try {
            java.io.FileWriter fw = new java.io.FileWriter("/Users/kunalsharma/datagami/edudron/.cursor/debug.log", true);
            fw.write(java.util.Map.of("sessionId", "debug-session", "runId", "run1", "hypothesisId", "A", "location", "AuthController.java:46", "message", "Refresh endpoint called", "data", java.util.Map.of("refreshTokenParam", refreshToken != null ? "present" : "null", "body", body != null ? body.toString() : "null"), "timestamp", System.currentTimeMillis()).toString() + "\n");
            fw.close();
        } catch (Exception e) {}
        // #endregion
        try {
            // Handle both query param and body
            String token = refreshToken;
            if (token == null && body != null) {
                token = body.get("refreshToken");
            }
            // #region agent log
            try {
                java.io.FileWriter fw = new java.io.FileWriter("/Users/kunalsharma/datagami/edudron/.cursor/debug.log", true);
                fw.write(java.util.Map.of("sessionId", "debug-session", "runId", "run1", "hypothesisId", "A", "location", "AuthController.java:54", "message", "Resolved refresh token", "data", java.util.Map.of("token", token != null ? "present" : "null"), "timestamp", System.currentTimeMillis()).toString() + "\n");
                fw.close();
            } catch (Exception e) {}
            // #endregion
            if (token == null || token.isEmpty()) {
                throw new RuntimeException("Refresh token is required");
            }
            AuthResponse response = authService.refreshToken(token);
            // #region agent log
            try {
                java.io.FileWriter fw = new java.io.FileWriter("/Users/kunalsharma/datagami/edudron/.cursor/debug.log", true);
                fw.write(java.util.Map.of("sessionId", "debug-session", "runId", "run1", "hypothesisId", "A", "location", "AuthController.java:62", "message", "Refresh token successful", "data", java.util.Map.of("hasResponse", response != null), "timestamp", System.currentTimeMillis()).toString() + "\n");
                fw.close();
            } catch (Exception ex) {}
            // #endregion
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            // #region agent log
            try {
                java.io.FileWriter fw = new java.io.FileWriter("/Users/kunalsharma/datagami/edudron/.cursor/debug.log", true);
                fw.write(java.util.Map.of("sessionId", "debug-session", "runId", "run1", "hypothesisId", "A", "location", "AuthController.java:68", "message", "Refresh token failed", "data", java.util.Map.of("error", e.getMessage()), "timestamp", System.currentTimeMillis()).toString() + "\n");
                fw.close();
            } catch (Exception ex) {}
            // #endregion
            // Re-throw with more specific error message
            // This will be handled by global exception handler if configured
            throw new RuntimeException("Token refresh failed: " + e.getMessage());
        } catch (Exception e) {
            // #region agent log
            try {
                java.io.FileWriter fw = new java.io.FileWriter("/Users/kunalsharma/datagami/edudron/.cursor/debug.log", true);
                fw.write(java.util.Map.of("sessionId", "debug-session", "runId", "run1", "hypothesisId", "A", "location", "AuthController.java:75", "message", "Refresh token unexpected error", "data", java.util.Map.of("error", e.getClass().getName()), "timestamp", System.currentTimeMillis()).toString() + "\n");
                fw.close();
            } catch (Exception ex) {}
            // #endregion
            // Log unexpected errors but don't expose internal details
            throw new RuntimeException("Token refresh failed: Invalid refresh token");
        }
    }

    @PostMapping("/logout")
    @Operation(summary = "User logout", description = "Logout user (client-side token removal)")
    public ResponseEntity<String> logout() {
        // In a stateless JWT system, logout is handled client-side
        return ResponseEntity.ok("Logged out successfully");
    }
}

