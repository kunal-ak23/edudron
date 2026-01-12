package com.datagami.edudron.student.util;

import java.security.SecureRandom;

public class PasswordGenerator {
    private static final String UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijklmnopqrstuvwxyz";
    private static final String DIGITS = "0123456789";
    private static final String SPECIAL = "!@#$%^&*";
    private static final String ALL = UPPER + LOWER + DIGITS + SPECIAL;
    
    private static final SecureRandom RANDOM = new SecureRandom();
    
    /**
     * Generates a secure random password of the specified length.
     * Password will contain at least one uppercase, one lowercase, one digit, and one special character.
     * 
     * @param length Minimum length of the password (default 12)
     * @return A secure random password
     */
    public static String generatePassword(int length) {
        if (length < 8) {
            length = 12; // Default to 12 if too short
        }
        
        StringBuilder password = new StringBuilder(length);
        
        // Ensure at least one character from each category
        password.append(UPPER.charAt(RANDOM.nextInt(UPPER.length())));
        password.append(LOWER.charAt(RANDOM.nextInt(LOWER.length())));
        password.append(DIGITS.charAt(RANDOM.nextInt(DIGITS.length())));
        password.append(SPECIAL.charAt(RANDOM.nextInt(SPECIAL.length())));
        
        // Fill the rest with random characters
        for (int i = password.length(); i < length; i++) {
            password.append(ALL.charAt(RANDOM.nextInt(ALL.length())));
        }
        
        // Shuffle the password to avoid predictable pattern
        char[] passwordArray = password.toString().toCharArray();
        for (int i = passwordArray.length - 1; i > 0; i--) {
            int j = RANDOM.nextInt(i + 1);
            char temp = passwordArray[i];
            passwordArray[i] = passwordArray[j];
            passwordArray[j] = temp;
        }
        
        return new String(passwordArray);
    }
    
    /**
     * Generates a secure random password with default length of 12.
     */
    public static String generatePassword() {
        return generatePassword(12);
    }
}

