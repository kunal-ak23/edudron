package com.datagami.edudron.identity;

import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;

@SpringBootApplication
@EntityScan(basePackages = {
    "com.datagami.edudron.identity.domain",
    "com.datagami.edudron.identity.entity",
    "com.datagami.edudron.common.domain"
})
public class IdentityApplication {
    private static final Logger logger = LoggerFactory.getLogger(IdentityApplication.class);

    public static void main(String[] args) {
        // Load .env file from project root
        try {
            String currentDir = System.getProperty("user.dir");
            logger.info("Current working directory: {}", currentDir);
            
            // Look for .env in current directory or parent (project root)
            Dotenv dotenv = Dotenv.configure()
                    .filename(".env")
                    .directory(".")
                    .ignoreIfMissing()
                    .load();
            
            // If not found, try parent directory (project root)
            if (dotenv == null || dotenv.entries().isEmpty()) {
                dotenv = Dotenv.configure()
                        .filename(".env")
                        .directory("..")
                        .ignoreIfMissing()
                        .load();
            }
            
            if (dotenv != null && !dotenv.entries().isEmpty()) {
                int loadedCount = 0;
                int skippedCount = 0;
                // Set as system properties so Spring Boot can read them via ${VAR_NAME}
                for (var entry : dotenv.entries()) {
                    String key = entry.getKey();
                    String value = entry.getValue();
                    if (value == null || value.isEmpty()) {
                        continue;
                    }
                    // Set as system property if not already set
                    if (System.getProperty(key) == null) {
                        System.setProperty(key, value);
                        loadedCount++;
                    } else {
                        skippedCount++;
                    }
                }
                logger.info("✅ Loaded {} environment variables from .env file ({} set, {} already existed)", 
                    dotenv.entries().size(), loadedCount, skippedCount);
            } else {
                logger.warn("⚠️  No .env file found. Using system environment variables or defaults.");
            }
        } catch (Exception e) {
            logger.error("❌ Error loading .env file: {}", e.getMessage(), e);
        }
        
        SpringApplication.run(IdentityApplication.class, args);
    }
    
    private static String maskSensitiveValue(String key, String value) {
        if (key != null && (key.contains("KEY") || key.contains("SECRET") || key.contains("PASSWORD"))) {
            if (value != null && value.length() > 8) {
                return value.substring(0, 4) + "..." + value.substring(value.length() - 4);
            }
            return "****";
        }
        return value;
    }
}

