package com.datagami.edudron.student.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class UserUtil {
    
    /**
     * Get the current user ID from SecurityContext.
     * For now, we use email as the user identifier.
     * This can be enhanced to extract user ID from JWT claims or call identity service.
     */
    public static String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() != null) {
            return authentication.getName(); // This is typically the email from JWT subject
        }
        throw new IllegalStateException("User not authenticated");
    }
}


