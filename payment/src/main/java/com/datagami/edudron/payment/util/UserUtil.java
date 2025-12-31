package com.datagami.edudron.payment.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class UserUtil {
    
    public static String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() != null) {
            return authentication.getName(); // This is typically the email from JWT subject
        }
        throw new IllegalStateException("User not authenticated");
    }
}

