package com.datagami.edudron.common;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

public class UlidGenerator {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ENCODING = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    
    public static String nextUlid() {
        // Timestamp (48 bits)
        long timestamp = System.currentTimeMillis();
        
        // Random part (80 bits)
        byte[] randomBytes = new byte[10];
        RANDOM.nextBytes(randomBytes);
        
        // Convert to ULID string
        StringBuilder ulid = new StringBuilder(26);
        
        // Encode timestamp (48 bits = 10 characters)
        for (int i = 0; i < 10; i++) {
            int bits = (int) ((timestamp >> (45 - i * 5)) & 0x1F);
            ulid.append(ENCODING.charAt(bits));
        }
        
        // Encode random part (80 bits = 16 characters)
        for (int i = 0; i < 16; i++) {
            int byteIndex = i / 2;
            int bitOffset = (i % 2) * 4;
            int bits;
            if (i % 2 == 0) {
                bits = ((randomBytes[byteIndex] >> 4) & 0x0F);
            } else {
                bits = (randomBytes[byteIndex] & 0x0F);
            }
            ulid.append(ENCODING.charAt(bits));
        }
        
        return ulid.toString();
    }
}

