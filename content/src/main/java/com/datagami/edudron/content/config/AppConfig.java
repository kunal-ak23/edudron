package com.datagami.edudron.content.config;

import com.fasterxml.jackson.core.StreamWriteConstraints;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {
    
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // Register JavaTimeModule to handle OffsetDateTime and other Java 8 time types
        mapper.registerModule(new JavaTimeModule());
        // Disable writing dates as timestamps - write as ISO-8601 strings instead
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        // Increase nesting depth limit to handle deeply nested JSON structures
        // Default is 1000, increase to 2000 to handle complex submission data
        mapper.getFactory().setStreamWriteConstraints(
            StreamWriteConstraints.builder()
                .maxNestingDepth(2000)
                .build()
        );
        
        return mapper;
    }
}

