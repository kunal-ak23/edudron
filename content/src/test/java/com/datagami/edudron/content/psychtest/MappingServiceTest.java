package com.datagami.edudron.content.psychtest;

import com.datagami.edudron.content.psychtest.service.MappingService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MappingServiceTest {
    @Test
    void map_shouldReturnStreamAndCareers() {
        MappingService mappingService = new MappingService();
        MappingService.MappingOutput out = mappingService.map("I", "R", "HIGH", 10);
        assertNotNull(out);
        assertNotNull(out.streamSuggestion());
        assertFalse(out.careerFields().isEmpty());
    }
}

