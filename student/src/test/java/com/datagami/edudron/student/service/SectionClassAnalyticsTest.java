package com.datagami.edudron.student.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.student.dto.*;
import com.datagami.edudron.student.repo.LectureViewSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test class for Section and Class Analytics functionality.
 * Tests the new analytics methods for sections and classes.
 */
@ExtendWith(MockitoExtension.class)
class SectionClassAnalyticsTest {

    @Mock
    private LectureViewSessionRepository sessionRepository;

    @Mock
    private SectionService sectionService;

    @Mock
    private ClassService classService;

    @InjectMocks
    private AnalyticsService analyticsService;

    private UUID clientId;
    private String sectionId;
    private String classId;

    @BeforeEach
    void setUp() {
        clientId = UUID.randomUUID();
        sectionId = "section123";
        classId = "class123";

        // Set gateway URL
        ReflectionTestUtils.setField(analyticsService, "gatewayUrl", "http://localhost:8080");

        // Set tenant context
        TenantContext.setClientId(clientId.toString());
    }

    @Test
    void testGetSectionEngagementMetrics_Success() {
        // Arrange
        SectionDTO sectionDTO = new SectionDTO();
        sectionDTO.setId(sectionId);
        sectionDTO.setName("Test Section");
        sectionDTO.setClassId(classId);

        ClassDTO classDTO = new ClassDTO();
        classDTO.setId(classId);
        classDTO.setName("Test Class");

        when(sectionService.getSection(sectionId)).thenReturn(sectionDTO);
        when(classService.getClass(classId)).thenReturn(classDTO);

        // Mock aggregates: totalSessions, uniqueStudents, avgDuration, completedSessions, totalCourses
        Object[] aggregates = new Object[]{100L, 50L, 300.0, 80L, 3L};
        when(sessionRepository.getSectionAggregates(clientId, sectionId)).thenReturn(aggregates);

        // Mock course breakdown
        when(sessionRepository.getCourseBreakdownBySection(clientId, sectionId))
            .thenReturn(new ArrayList<>());

        // Mock lecture aggregates
        when(sessionRepository.getLectureEngagementAggregatesBySection(clientId, sectionId, 60))
            .thenReturn(new ArrayList<>());

        // Mock activity timeline
        when(sessionRepository.getActivityTimelineBySection(clientId, sectionId))
            .thenReturn(new ArrayList<>());

        // Act
        SectionAnalyticsDTO result = analyticsService.getSectionEngagementMetrics(sectionId);

        // Assert
        assertNotNull(result);
        assertEquals(sectionId, result.getSectionId());
        assertEquals("Test Section", result.getSectionName());
        assertEquals(classId, result.getClassId());
        assertEquals("Test Class", result.getClassName());
        assertEquals(3, result.getTotalCourses());
        assertEquals(100L, result.getTotalViewingSessions());
        assertEquals(50L, result.getUniqueStudentsEngaged());
        assertEquals(300, result.getAverageTimePerLectureSeconds());
        assertTrue(result.getOverallCompletionRate().compareTo(BigDecimal.ZERO) > 0);

        verify(sessionRepository).getSectionAggregates(clientId, sectionId);
        verify(sessionRepository).getCourseBreakdownBySection(clientId, sectionId);
        verify(sessionRepository).getLectureEngagementAggregatesBySection(clientId, sectionId, 60);
        verify(sessionRepository).getActivityTimelineBySection(clientId, sectionId);
    }

    @Test
    void testGetClassEngagementMetrics_Success() {
        // Arrange
        ClassDTO classDTO = new ClassDTO();
        classDTO.setId(classId);
        classDTO.setName("Test Class");
        classDTO.setInstituteId("institute123");

        when(classService.getClass(classId)).thenReturn(classDTO);

        // Mock aggregates: totalSessions, uniqueStudents, avgDuration, completedSessions, totalCourses, totalSections
        Object[] aggregates = new Object[]{200L, 100L, 350.0, 160L, 5L, 3L};
        when(sessionRepository.getClassAggregates(clientId, classId)).thenReturn(aggregates);

        // Mock course breakdown
        when(sessionRepository.getCourseBreakdownByClass(clientId, classId))
            .thenReturn(new ArrayList<>());

        // Mock lecture aggregates
        when(sessionRepository.getLectureEngagementAggregatesByClass(clientId, classId, 60))
            .thenReturn(new ArrayList<>());

        // Mock activity timeline
        when(sessionRepository.getActivityTimelineByClass(clientId, classId))
            .thenReturn(new ArrayList<>());

        // Mock section comparison
        when(sessionRepository.getSectionComparisonByClass(clientId, classId))
            .thenReturn(new ArrayList<>());

        // Act
        ClassAnalyticsDTO result = analyticsService.getClassEngagementMetrics(classId);

        // Assert
        assertNotNull(result);
        assertEquals(classId, result.getClassId());
        assertEquals("Test Class", result.getClassName());
        assertEquals("institute123", result.getInstituteId());
        assertEquals(3, result.getTotalSections());
        assertEquals(5, result.getTotalCourses());
        assertEquals(200L, result.getTotalViewingSessions());
        assertEquals(100L, result.getUniqueStudentsEngaged());
        assertEquals(350, result.getAverageTimePerLectureSeconds());
        assertTrue(result.getOverallCompletionRate().compareTo(BigDecimal.ZERO) > 0);

        verify(sessionRepository).getClassAggregates(clientId, classId);
        verify(sessionRepository).getCourseBreakdownByClass(clientId, classId);
        verify(sessionRepository).getLectureEngagementAggregatesByClass(clientId, classId, 60);
        verify(sessionRepository).getActivityTimelineByClass(clientId, classId);
        verify(sessionRepository).getSectionComparisonByClass(clientId, classId);
    }

    @Test
    void testGetSectionEngagementMetrics_WithZeroAggregates() {
        // Arrange
        SectionDTO sectionDTO = new SectionDTO();
        sectionDTO.setId(sectionId);
        sectionDTO.setName("Empty Section");

        when(sectionService.getSection(sectionId)).thenReturn(sectionDTO);

        // Mock zero aggregates
        Object[] aggregates = new Object[]{0L, 0L, 0.0, 0L, 0L};
        when(sessionRepository.getSectionAggregates(clientId, sectionId)).thenReturn(aggregates);

        when(sessionRepository.getCourseBreakdownBySection(clientId, sectionId))
            .thenReturn(new ArrayList<>());
        when(sessionRepository.getLectureEngagementAggregatesBySection(clientId, sectionId, 60))
            .thenReturn(new ArrayList<>());
        when(sessionRepository.getActivityTimelineBySection(clientId, sectionId))
            .thenReturn(new ArrayList<>());

        // Act
        SectionAnalyticsDTO result = analyticsService.getSectionEngagementMetrics(sectionId);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.getTotalCourses());
        assertEquals(0L, result.getTotalViewingSessions());
        assertEquals(0L, result.getUniqueStudentsEngaged());
        assertEquals(BigDecimal.ZERO, result.getOverallCompletionRate());
    }

    @Test
    void testGetClassEngagementMetrics_WithZeroAggregates() {
        // Arrange
        ClassDTO classDTO = new ClassDTO();
        classDTO.setId(classId);
        classDTO.setName("Empty Class");

        when(classService.getClass(classId)).thenReturn(classDTO);

        // Mock zero aggregates
        Object[] aggregates = new Object[]{0L, 0L, 0.0, 0L, 0L, 0L};
        when(sessionRepository.getClassAggregates(clientId, classId)).thenReturn(aggregates);

        when(sessionRepository.getCourseBreakdownByClass(clientId, classId))
            .thenReturn(new ArrayList<>());
        when(sessionRepository.getLectureEngagementAggregatesByClass(clientId, classId, 60))
            .thenReturn(new ArrayList<>());
        when(sessionRepository.getActivityTimelineByClass(clientId, classId))
            .thenReturn(new ArrayList<>());
        when(sessionRepository.getSectionComparisonByClass(clientId, classId))
            .thenReturn(new ArrayList<>());

        // Act
        ClassAnalyticsDTO result = analyticsService.getClassEngagementMetrics(classId);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.getTotalSections());
        assertEquals(0, result.getTotalCourses());
        assertEquals(0L, result.getTotalViewingSessions());
        assertEquals(0L, result.getUniqueStudentsEngaged());
        assertEquals(BigDecimal.ZERO, result.getOverallCompletionRate());
    }
}
