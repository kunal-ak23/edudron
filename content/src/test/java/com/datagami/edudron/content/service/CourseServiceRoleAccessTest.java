package com.datagami.edudron.content.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.content.domain.Course;
import com.datagami.edudron.content.dto.CreateCourseRequest;
import com.datagami.edudron.content.repo.CourseRepository;
import com.datagami.edudron.content.repo.LectureRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test cases for CourseService role-based access control.
 * Covers all scenarios for course creation, update, delete, publish, and unpublish restrictions.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CourseService Role-Based Access Control Tests")
class CourseServiceRoleAccessTest {

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private LectureRepository lectureRepository;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private Authentication authentication;

    @Mock
    private SecurityContext securityContext;

    @InjectMocks
    private CourseService courseService;

    private UUID testClientId;
    private Course testCourse;

    @BeforeEach
    void setUp() {
        testClientId = UUID.randomUUID();
        TenantContext.setClientId(testClientId.toString());
        
        // Setup SecurityContext mock
        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("test@example.com");

        // Setup test course
        testCourse = new Course();
        testCourse.setId("course-123");
        testCourse.setClientId(testClientId);
        testCourse.setTitle("Test Course");
        testCourse.setDescription("Test Description");
        testCourse.setIsPublished(false);

        // Inject RestTemplate via reflection
        ReflectionTestUtils.setField(courseService, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(courseService, "gatewayUrl", "http://localhost:8080");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private void mockUserRole(String role) {
        Map<String, Object> userResponse = new HashMap<>();
        userResponse.put("role", role);
        ResponseEntity<Map> responseEntity = new ResponseEntity<>(userResponse, HttpStatus.OK);
        
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(Map.class)
        )).thenReturn(responseEntity);
    }

    private CreateCourseRequest createCourseRequest() {
        CreateCourseRequest request = new CreateCourseRequest();
        request.setTitle("New Course");
        request.setDescription("Course Description");
        return request;
    }

    // ========== createCourse() Tests ==========

    @Test
    @DisplayName("SYSTEM_ADMIN can create courses")
    void testSystemAdminCanCreateCourse() {
        mockUserRole("SYSTEM_ADMIN");
        when(courseRepository.save(any(Course.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateCourseRequest request = createCourseRequest();

        assertDoesNotThrow(() -> courseService.createCourse(request),
            "SYSTEM_ADMIN should be able to create courses");
    }

    @Test
    @DisplayName("TENANT_ADMIN can create courses")
    void testTenantAdminCanCreateCourse() {
        mockUserRole("TENANT_ADMIN");
        when(courseRepository.save(any(Course.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateCourseRequest request = createCourseRequest();

        assertDoesNotThrow(() -> courseService.createCourse(request),
            "TENANT_ADMIN should be able to create courses");
    }

    @Test
    @DisplayName("CONTENT_MANAGER can create courses")
    void testContentManagerCanCreateCourse() {
        mockUserRole("CONTENT_MANAGER");
        when(courseRepository.save(any(Course.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateCourseRequest request = createCourseRequest();

        assertDoesNotThrow(() -> courseService.createCourse(request),
            "CONTENT_MANAGER should be able to create courses");
    }

    @Test
    @DisplayName("INSTRUCTOR cannot create courses")
    void testInstructorCannotCreateCourse() {
        mockUserRole("INSTRUCTOR");

        CreateCourseRequest request = createCourseRequest();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> courseService.createCourse(request),
            "INSTRUCTOR should not be able to create courses");

        assertTrue(exception.getMessage().contains("view-only access") &&
                   exception.getMessage().contains("cannot create courses"),
            "Exception message should indicate view-only restriction");
    }

    @Test
    @DisplayName("SUPPORT_STAFF cannot create courses")
    void testSupportStaffCannotCreateCourse() {
        mockUserRole("SUPPORT_STAFF");

        CreateCourseRequest request = createCourseRequest();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> courseService.createCourse(request),
            "SUPPORT_STAFF should not be able to create courses");

        assertTrue(exception.getMessage().contains("view-only access") &&
                   exception.getMessage().contains("cannot create courses"),
            "Exception message should indicate view-only restriction");
    }

    @Test
    @DisplayName("STUDENT cannot create courses")
    void testStudentCannotCreateCourse() {
        mockUserRole("STUDENT");

        CreateCourseRequest request = createCourseRequest();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> courseService.createCourse(request),
            "STUDENT should not be able to create courses");

        assertTrue(exception.getMessage().contains("view-only access") &&
                   exception.getMessage().contains("cannot create courses") &&
                   exception.getMessage().contains("STUDENT"),
            "Exception message should indicate view-only restriction and include STUDENT");
    }

    // ========== updateCourse() Tests ==========

    @Test
    @DisplayName("SYSTEM_ADMIN can update courses")
    void testSystemAdminCanUpdateCourse() {
        mockUserRole("SYSTEM_ADMIN");
        when(courseRepository.findByIdAndClientId("course-123", testClientId))
            .thenReturn(Optional.of(testCourse));
        when(courseRepository.save(any(Course.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateCourseRequest request = createCourseRequest();

        assertDoesNotThrow(() -> courseService.updateCourse("course-123", request),
            "SYSTEM_ADMIN should be able to update courses");
    }

    @Test
    @DisplayName("TENANT_ADMIN can update courses")
    void testTenantAdminCanUpdateCourse() {
        mockUserRole("TENANT_ADMIN");
        when(courseRepository.findByIdAndClientId("course-123", testClientId))
            .thenReturn(Optional.of(testCourse));
        when(courseRepository.save(any(Course.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateCourseRequest request = createCourseRequest();

        assertDoesNotThrow(() -> courseService.updateCourse("course-123", request),
            "TENANT_ADMIN should be able to update courses");
    }

    @Test
    @DisplayName("CONTENT_MANAGER can update courses")
    void testContentManagerCanUpdateCourse() {
        mockUserRole("CONTENT_MANAGER");
        when(courseRepository.findByIdAndClientId("course-123", testClientId))
            .thenReturn(Optional.of(testCourse));
        when(courseRepository.save(any(Course.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateCourseRequest request = createCourseRequest();

        assertDoesNotThrow(() -> courseService.updateCourse("course-123", request),
            "CONTENT_MANAGER should be able to update courses");
    }

    @Test
    @DisplayName("INSTRUCTOR cannot update courses")
    void testInstructorCannotUpdateCourse() {
        mockUserRole("INSTRUCTOR");

        CreateCourseRequest request = createCourseRequest();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> courseService.updateCourse("course-123", request),
            "INSTRUCTOR should not be able to update courses");

        assertTrue(exception.getMessage().contains("view-only access") &&
                   exception.getMessage().contains("cannot update courses"),
            "Exception message should indicate view-only restriction");
    }

    @Test
    @DisplayName("SUPPORT_STAFF cannot update courses")
    void testSupportStaffCannotUpdateCourse() {
        mockUserRole("SUPPORT_STAFF");

        CreateCourseRequest request = createCourseRequest();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> courseService.updateCourse("course-123", request),
            "SUPPORT_STAFF should not be able to update courses");

        assertTrue(exception.getMessage().contains("view-only access") &&
                   exception.getMessage().contains("cannot update courses"),
            "Exception message should indicate view-only restriction");
    }

    // ========== deleteCourse() Tests ==========

    @Test
    @DisplayName("SYSTEM_ADMIN can delete courses")
    void testSystemAdminCanDeleteCourse() {
        mockUserRole("SYSTEM_ADMIN");
        when(courseRepository.findByIdAndClientId("course-123", testClientId))
            .thenReturn(Optional.of(testCourse));
        doNothing().when(courseRepository).delete(any(Course.class));

        assertDoesNotThrow(() -> courseService.deleteCourse("course-123"),
            "SYSTEM_ADMIN should be able to delete courses");
    }

    @Test
    @DisplayName("TENANT_ADMIN can delete courses")
    void testTenantAdminCanDeleteCourse() {
        mockUserRole("TENANT_ADMIN");
        when(courseRepository.findByIdAndClientId("course-123", testClientId))
            .thenReturn(Optional.of(testCourse));
        doNothing().when(courseRepository).delete(any(Course.class));

        assertDoesNotThrow(() -> courseService.deleteCourse("course-123"),
            "TENANT_ADMIN should be able to delete courses");
    }

    @Test
    @DisplayName("CONTENT_MANAGER can delete courses")
    void testContentManagerCanDeleteCourse() {
        mockUserRole("CONTENT_MANAGER");
        when(courseRepository.findByIdAndClientId("course-123", testClientId))
            .thenReturn(Optional.of(testCourse));
        doNothing().when(courseRepository).delete(any(Course.class));

        assertDoesNotThrow(() -> courseService.deleteCourse("course-123"),
            "CONTENT_MANAGER should be able to delete courses");
    }

    @Test
    @DisplayName("INSTRUCTOR cannot delete courses")
    void testInstructorCannotDeleteCourse() {
        mockUserRole("INSTRUCTOR");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> courseService.deleteCourse("course-123"),
            "INSTRUCTOR should not be able to delete courses");

        assertTrue(exception.getMessage().contains("view-only access") &&
                   exception.getMessage().contains("cannot delete courses"),
            "Exception message should indicate view-only restriction");
    }

    @Test
    @DisplayName("SUPPORT_STAFF cannot delete courses")
    void testSupportStaffCannotDeleteCourse() {
        mockUserRole("SUPPORT_STAFF");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> courseService.deleteCourse("course-123"),
            "SUPPORT_STAFF should not be able to delete courses");

        assertTrue(exception.getMessage().contains("view-only access") &&
                   exception.getMessage().contains("cannot delete courses"),
            "Exception message should indicate view-only restriction");
    }

    // ========== publishCourse() Tests ==========

    @Test
    @DisplayName("SYSTEM_ADMIN can publish courses")
    void testSystemAdminCanPublishCourse() {
        mockUserRole("SYSTEM_ADMIN");
        when(courseRepository.findByIdAndClientId("course-123", testClientId))
            .thenReturn(Optional.of(testCourse));
        when(lectureRepository.sumDurationByCourseIdAndClientId("course-123", testClientId))
            .thenReturn(0);
        when(lectureRepository.countByCourseIdAndClientId("course-123", testClientId))
            .thenReturn(0L);
        when(courseRepository.save(any(Course.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertDoesNotThrow(() -> courseService.publishCourse("course-123"),
            "SYSTEM_ADMIN should be able to publish courses");
    }

    @Test
    @DisplayName("TENANT_ADMIN can publish courses")
    void testTenantAdminCanPublishCourse() {
        mockUserRole("TENANT_ADMIN");
        when(courseRepository.findByIdAndClientId("course-123", testClientId))
            .thenReturn(Optional.of(testCourse));
        when(lectureRepository.sumDurationByCourseIdAndClientId("course-123", testClientId))
            .thenReturn(0);
        when(lectureRepository.countByCourseIdAndClientId("course-123", testClientId))
            .thenReturn(0L);
        when(courseRepository.save(any(Course.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertDoesNotThrow(() -> courseService.publishCourse("course-123"),
            "TENANT_ADMIN should be able to publish courses");
    }

    @Test
    @DisplayName("CONTENT_MANAGER can publish courses")
    void testContentManagerCanPublishCourse() {
        mockUserRole("CONTENT_MANAGER");
        when(courseRepository.findByIdAndClientId("course-123", testClientId))
            .thenReturn(Optional.of(testCourse));
        when(lectureRepository.sumDurationByCourseIdAndClientId("course-123", testClientId))
            .thenReturn(0);
        when(lectureRepository.countByCourseIdAndClientId("course-123", testClientId))
            .thenReturn(0L);
        when(courseRepository.save(any(Course.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertDoesNotThrow(() -> courseService.publishCourse("course-123"),
            "CONTENT_MANAGER should be able to publish courses");
    }

    @Test
    @DisplayName("INSTRUCTOR cannot publish courses")
    void testInstructorCannotPublishCourse() {
        mockUserRole("INSTRUCTOR");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> courseService.publishCourse("course-123"),
            "INSTRUCTOR should not be able to publish courses");

        assertTrue(exception.getMessage().contains("view-only access") &&
                   exception.getMessage().contains("cannot publish courses"),
            "Exception message should indicate view-only restriction");
    }

    @Test
    @DisplayName("SUPPORT_STAFF cannot publish courses")
    void testSupportStaffCannotPublishCourse() {
        mockUserRole("SUPPORT_STAFF");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> courseService.publishCourse("course-123"),
            "SUPPORT_STAFF should not be able to publish courses");

        assertTrue(exception.getMessage().contains("view-only access") &&
                   exception.getMessage().contains("cannot publish courses"),
            "Exception message should indicate view-only restriction");
    }

    // ========== unpublishCourse() Tests ==========

    @Test
    @DisplayName("SYSTEM_ADMIN can unpublish courses")
    void testSystemAdminCanUnpublishCourse() {
        mockUserRole("SYSTEM_ADMIN");
        testCourse.setIsPublished(true);
        when(courseRepository.findByIdAndClientId("course-123", testClientId))
            .thenReturn(Optional.of(testCourse));
        when(courseRepository.save(any(Course.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertDoesNotThrow(() -> courseService.unpublishCourse("course-123"),
            "SYSTEM_ADMIN should be able to unpublish courses");
    }

    @Test
    @DisplayName("INSTRUCTOR cannot unpublish courses")
    void testInstructorCannotUnpublishCourse() {
        mockUserRole("INSTRUCTOR");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> courseService.unpublishCourse("course-123"),
            "INSTRUCTOR should not be able to unpublish courses");

        assertTrue(exception.getMessage().contains("view-only access") &&
                   exception.getMessage().contains("cannot unpublish courses"),
            "Exception message should indicate view-only restriction");
    }

    @Test
    @DisplayName("SUPPORT_STAFF cannot unpublish courses")
    void testSupportStaffCannotUnpublishCourse() {
        mockUserRole("SUPPORT_STAFF");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> courseService.unpublishCourse("course-123"),
            "SUPPORT_STAFF should not be able to unpublish courses");

        assertTrue(exception.getMessage().contains("view-only access") &&
                   exception.getMessage().contains("cannot unpublish courses"),
            "Exception message should indicate view-only restriction");
    }
}
