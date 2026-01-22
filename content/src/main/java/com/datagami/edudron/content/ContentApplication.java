package com.datagami.edudron.content;

import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
@EntityScan(basePackages = {
    "com.datagami.edudron.content.domain",
    "com.datagami.edudron.content.psychtest.domain",
    "com.datagami.edudron.common.domain"
})
public class ContentApplication {
    private static final Logger logger = LoggerFactory.getLogger(ContentApplication.class);

    public static void main(String[] args) {
        loadEnvFile();
        SpringApplication.run(ContentApplication.class, args);
    }
    
    private static void loadEnvFile() {
        try {
            Dotenv dotenv = Dotenv.configure()
                    .filename(".env")
                    .directory(".")
                    .ignoreIfMissing()
                    .load();
            
            if (dotenv == null || dotenv.entries().isEmpty()) {
                dotenv = Dotenv.configure()
                        .filename(".env")
                        .directory("..")
                        .ignoreIfMissing()
                        .load();
            }
            
            if (dotenv != null && !dotenv.entries().isEmpty()) {
                int loadedCount = 0;
                for (var entry : dotenv.entries()) {
                    String key = entry.getKey();
                    String value = entry.getValue();
                    if (value != null && !value.isEmpty() && System.getProperty(key) == null) {
                        System.setProperty(key, value);
                        loadedCount++;
                    }
                }
                logger.info("✅ Loaded {} environment variables from .env file", loadedCount);
            }
        } catch (Exception e) {
            logger.warn("⚠️  Could not load .env file: {}", e.getMessage());
        }
    }
}

