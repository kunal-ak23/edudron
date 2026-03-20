package com.datagami.edudron.student.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.common.TenantContextRestTemplateInterceptor;
import com.datagami.edudron.common.UlidGenerator;
import com.datagami.edudron.student.domain.*;
import com.datagami.edudron.student.dto.*;
import com.datagami.edudron.student.repo.*;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class ProjectService {

    private static final Logger log = LoggerFactory.getLogger(ProjectService.class);

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectGroupRepository projectGroupRepository;

    @Autowired
    private ProjectGroupMemberRepository projectGroupMemberRepository;

    @Autowired
    private ProjectEventRepository projectEventRepository;

    @Autowired
    private ProjectEventAttendanceRepository projectEventAttendanceRepository;

    @Autowired
    private ProjectEventGradeRepository projectEventGradeRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Value("${GATEWAY_URL:http://localhost:8080}")
    private String gatewayUrl;

    private volatile RestTemplate restTemplate;
    private final Object restTemplateLock = new Object();

    private RestTemplate getRestTemplate() {
        if (restTemplate == null) {
            synchronized (restTemplateLock) {
                if (restTemplate == null) {
                    RestTemplate template = new RestTemplate();
                    List<ClientHttpRequestInterceptor> interceptors = new ArrayList<>();
                    interceptors.add(new TenantContextRestTemplateInterceptor());
                    interceptors.add((request, body, execution) -> {
                        ServletRequestAttributes attributes =
                                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                        if (attributes != null) {
                            HttpServletRequest currentRequest = attributes.getRequest();
                            String authHeader = currentRequest.getHeader("Authorization");
                            if (authHeader != null && !authHeader.isBlank()) {
                                if (!request.getHeaders().containsKey("Authorization")) {
                                    request.getHeaders().add("Authorization", authHeader);
                                }
                            }
                        }
                        return execution.execute(request, body);
                    });
                    template.setInterceptors(interceptors);
                    restTemplate = template;
                }
            }
        }
        return restTemplate;
    }

    private UUID getClientId() {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        return UUID.fromString(clientIdStr);
    }

    // ======================== CRUD ========================

    public ProjectDTO createProject(CreateProjectRequest request) {
        UUID clientId = getClientId();

        Project project = new Project();
        project.setId(UlidGenerator.nextUlid());
        project.setClientId(clientId);
        project.setCourseId(request.getCourseId());
        project.setSectionId(request.getSectionId());
        project.setTitle(request.getTitle());
        project.setDescription(request.getDescription());
        if (request.getMaxMarks() != null) {
            project.setMaxMarks(request.getMaxMarks());
        }
        project.setSubmissionCutoff(request.getSubmissionCutoff());
        if (request.getLateSubmissionAllowed() != null) {
            project.setLateSubmissionAllowed(request.getLateSubmissionAllowed());
        }
        project.setStatus(Project.ProjectStatus.DRAFT);

        project = projectRepository.save(project);
        log.info("Created project '{}' (id={}) for section {}", project.getTitle(), project.getId(), project.getSectionId());
        return ProjectDTO.fromEntity(project);
    }

    @Transactional(readOnly = true)
    public ProjectDTO getProject(String id) {
        UUID clientId = getClientId();
        Project project = projectRepository.findByIdAndClientId(id, clientId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + id));
        return ProjectDTO.fromEntity(project);
    }

    @Transactional(readOnly = true)
    public List<ProjectDTO> listProjects(String courseId, String sectionId, String status) {
        UUID clientId = getClientId();

        List<Project> projects;
        if (sectionId != null) {
            projects = projectRepository.findByClientIdAndSectionId(clientId, sectionId);
        } else if (status != null) {
            projects = projectRepository.findByClientIdAndStatus(clientId, Project.ProjectStatus.valueOf(status));
        } else {
            projects = projectRepository.findByClientIdOrderByCreatedAtDesc(clientId);
        }

        // Apply additional filters
        if (courseId != null) {
            projects = projects.stream()
                    .filter(p -> courseId.equals(p.getCourseId()))
                    .collect(Collectors.toList());
        }

        return projects.stream()
                .map(ProjectDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public ProjectDTO updateProject(String id, CreateProjectRequest request) {
        UUID clientId = getClientId();
        Project project = projectRepository.findByIdAndClientId(id, clientId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + id));

        project.setTitle(request.getTitle());
        project.setDescription(request.getDescription());
        project.setCourseId(request.getCourseId());
        project.setSectionId(request.getSectionId());
        if (request.getMaxMarks() != null) {
            project.setMaxMarks(request.getMaxMarks());
        }
        project.setSubmissionCutoff(request.getSubmissionCutoff());
        if (request.getLateSubmissionAllowed() != null) {
            project.setLateSubmissionAllowed(request.getLateSubmissionAllowed());
        }

        project = projectRepository.save(project);
        log.info("Updated project '{}' (id={})", project.getTitle(), project.getId());
        return ProjectDTO.fromEntity(project);
    }

    public ProjectDTO activateProject(String id) {
        UUID clientId = getClientId();
        Project project = projectRepository.findByIdAndClientId(id, clientId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + id));
        project.setStatus(Project.ProjectStatus.ACTIVE);
        project = projectRepository.save(project);
        log.info("Activated project {}", id);
        return ProjectDTO.fromEntity(project);
    }

    public ProjectDTO completeProject(String id) {
        UUID clientId = getClientId();
        Project project = projectRepository.findByIdAndClientId(id, clientId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + id));
        project.setStatus(Project.ProjectStatus.COMPLETED);
        project = projectRepository.save(project);
        log.info("Completed project {}", id);
        return ProjectDTO.fromEntity(project);
    }

    // ======================== Group Generation ========================

    public List<ProjectGroupDTO> generateGroups(String projectId, int groupSize) {
        UUID clientId = getClientId();
        Project project = projectRepository.findByIdAndClientId(projectId, clientId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));

        // Delete existing groups and members for this project
        List<ProjectGroup> existingGroups = projectGroupRepository.findByProjectIdAndClientId(projectId, clientId);
        for (ProjectGroup g : existingGroups) {
            projectGroupMemberRepository.deleteByGroupId(g.getId());
        }
        projectGroupRepository.deleteAll(existingGroups);

        // Get students in the section via enrollment repository
        List<String> studentIds = enrollmentRepository.findByClientIdAndBatchId(clientId, project.getSectionId())
                .stream()
                .map(e -> e.getStudentId())
                .distinct()
                .collect(Collectors.toList());

        if (studentIds.isEmpty()) {
            throw new IllegalStateException("No students found in section " + project.getSectionId());
        }

        // Shuffle students
        Collections.shuffle(studentIds);

        // Create groups
        int numGroups = (int) Math.ceil((double) studentIds.size() / groupSize);
        List<ProjectGroup> groups = new ArrayList<>();
        for (int i = 0; i < numGroups; i++) {
            ProjectGroup group = new ProjectGroup();
            group.setId(UlidGenerator.nextUlid());
            group.setClientId(clientId);
            group.setProjectId(projectId);
            group.setGroupNumber(i + 1);
            groups.add(projectGroupRepository.save(group));
        }

        // Assign students round-robin to groups
        for (int i = 0; i < studentIds.size(); i++) {
            ProjectGroupMember member = new ProjectGroupMember();
            member.setId(UlidGenerator.nextUlid());
            member.setClientId(clientId);
            member.setGroupId(groups.get(i % numGroups).getId());
            member.setStudentId(studentIds.get(i));
            projectGroupMemberRepository.save(member);
        }

        log.info("Generated {} groups for project {} with {} students", numGroups, projectId, studentIds.size());
        return getGroups(projectId);
    }

    @Transactional(readOnly = true)
    public List<ProjectGroupDTO> getGroups(String projectId) {
        UUID clientId = getClientId();
        List<ProjectGroup> groups = projectGroupRepository.findByProjectIdAndClientId(projectId, clientId);

        return groups.stream().map(g -> {
            ProjectGroupDTO dto = ProjectGroupDTO.fromEntity(g);
            List<ProjectGroupMember> members = projectGroupMemberRepository.findByGroupIdAndClientId(g.getId(), clientId);
            dto.setMembers(members.stream().map(m ->
                    new ProjectGroupDTO.MemberInfo(m.getStudentId(), null, null)
            ).collect(Collectors.toList()));
            return dto;
        }).collect(Collectors.toList());
    }

    public ProjectGroupDTO updateGroup(String projectId, String groupId, ProjectGroupDTO updateData) {
        UUID clientId = getClientId();
        ProjectGroup group = projectGroupRepository.findByIdAndClientId(groupId, clientId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found: " + groupId));

        if (!group.getProjectId().equals(projectId)) {
            throw new IllegalArgumentException("Group does not belong to project");
        }

        if (updateData.getProblemStatementId() != null) {
            group.setProblemStatementId(updateData.getProblemStatementId());
        }
        if (updateData.getSubmissionUrl() != null) {
            group.setSubmissionUrl(updateData.getSubmissionUrl());
        }

        group = projectGroupRepository.save(group);
        ProjectGroupDTO dto = ProjectGroupDTO.fromEntity(group);
        List<ProjectGroupMember> members = projectGroupMemberRepository.findByGroupIdAndClientId(group.getId(), clientId);
        dto.setMembers(members.stream().map(m ->
                new ProjectGroupDTO.MemberInfo(m.getStudentId(), null, null)
        ).collect(Collectors.toList()));
        return dto;
    }

    @SuppressWarnings("unchecked")
    public void assignStatements(String projectId) {
        UUID clientId = getClientId();
        Project project = projectRepository.findByIdAndClientId(projectId, clientId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));

        List<ProjectGroup> groups = projectGroupRepository.findByProjectIdAndClientId(projectId, clientId);
        if (groups.isEmpty()) {
            throw new IllegalStateException("No groups found for project. Generate groups first.");
        }

        // Fetch question bank entries from content service
        String courseId = project.getCourseId();
        if (courseId == null || courseId.isBlank()) {
            throw new IllegalStateException("Project must have a courseId to assign problem statements");
        }

        String url = gatewayUrl + "/api/project-questions?courseId=" + courseId;
        try {
            ResponseEntity<List<Map<String, Object>>> response = getRestTemplate().exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(new HttpHeaders()),
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {}
            );

            List<Map<String, Object>> questions = response.getBody();
            if (questions == null || questions.isEmpty()) {
                throw new IllegalStateException("No project questions found for course " + courseId);
            }

            // Shuffle questions
            Collections.shuffle(questions);

            // Round-robin assign to groups
            for (int i = 0; i < groups.size(); i++) {
                Map<String, Object> question = questions.get(i % questions.size());
                ProjectGroup group = groups.get(i);
                group.setProblemStatementId((String) question.get("id"));
                projectGroupRepository.save(group);
            }

            log.info("Assigned {} problem statements to {} groups for project {}",
                    questions.size(), groups.size(), projectId);
        } catch (Exception e) {
            log.error("Failed to fetch project questions from content service: {}", e.getMessage());
            throw new IllegalStateException("Failed to assign problem statements: " + e.getMessage());
        }
    }

    // ======================== Events ========================

    public ProjectEventDTO addEvent(String projectId, String name, OffsetDateTime dateTime,
                                     String zoomLink, Boolean hasMarks, Integer maxMarks, Integer sequence) {
        UUID clientId = getClientId();
        // Verify project exists
        projectRepository.findByIdAndClientId(projectId, clientId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));

        ProjectEvent event = new ProjectEvent();
        event.setId(UlidGenerator.nextUlid());
        event.setClientId(clientId);
        event.setProjectId(projectId);
        event.setName(name);
        event.setDateTime(dateTime);
        event.setZoomLink(zoomLink);
        event.setHasMarks(hasMarks != null ? hasMarks : false);
        event.setMaxMarks(maxMarks);
        event.setSequence(sequence);

        event = projectEventRepository.save(event);
        log.info("Added event '{}' to project {}", name, projectId);
        return ProjectEventDTO.fromEntity(event);
    }

    public ProjectEventDTO updateEvent(String projectId, String eventId, String name,
                                        OffsetDateTime dateTime, String zoomLink,
                                        Boolean hasMarks, Integer maxMarks, Integer sequence) {
        UUID clientId = getClientId();
        ProjectEvent event = projectEventRepository.findByIdAndClientId(eventId, clientId)
                .orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventId));

        if (!event.getProjectId().equals(projectId)) {
            throw new IllegalArgumentException("Event does not belong to project");
        }

        if (name != null) event.setName(name);
        if (dateTime != null) event.setDateTime(dateTime);
        if (zoomLink != null) event.setZoomLink(zoomLink);
        if (hasMarks != null) event.setHasMarks(hasMarks);
        if (maxMarks != null) event.setMaxMarks(maxMarks);
        if (sequence != null) event.setSequence(sequence);

        event = projectEventRepository.save(event);
        log.info("Updated event {} for project {}", eventId, projectId);
        return ProjectEventDTO.fromEntity(event);
    }

    public void deleteEvent(String projectId, String eventId) {
        UUID clientId = getClientId();
        ProjectEvent event = projectEventRepository.findByIdAndClientId(eventId, clientId)
                .orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventId));

        if (!event.getProjectId().equals(projectId)) {
            throw new IllegalArgumentException("Event does not belong to project");
        }

        // Delete attendance and grades for this event
        List<ProjectEventAttendance> attendances = projectEventAttendanceRepository.findByEventIdAndClientId(eventId, clientId);
        projectEventAttendanceRepository.deleteAll(attendances);

        List<ProjectEventGrade> grades = projectEventGradeRepository.findByEventIdAndClientId(eventId, clientId);
        projectEventGradeRepository.deleteAll(grades);

        projectEventRepository.delete(event);
        log.info("Deleted event {} and its attendance/grades for project {}", eventId, projectId);
    }

    // ======================== Attendance ========================

    public void saveAttendance(String projectId, String eventId,
                               List<ProjectBulkAttendanceRequest.AttendanceEntry> entries) {
        UUID clientId = getClientId();

        // Verify event exists and belongs to project
        ProjectEvent event = projectEventRepository.findByIdAndClientId(eventId, clientId)
                .orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventId));
        if (!event.getProjectId().equals(projectId)) {
            throw new IllegalArgumentException("Event does not belong to project");
        }

        // Delete existing attendance for this event
        List<ProjectEventAttendance> existing = projectEventAttendanceRepository.findByEventIdAndClientId(eventId, clientId);
        projectEventAttendanceRepository.deleteAll(existing);

        // Build student-to-group map for this project
        Map<String, String> studentGroupMap = buildStudentGroupMap(projectId, clientId);

        // Create new attendance records
        for (ProjectBulkAttendanceRequest.AttendanceEntry entry : entries) {
            ProjectEventAttendance attendance = new ProjectEventAttendance();
            attendance.setId(UlidGenerator.nextUlid());
            attendance.setClientId(clientId);
            attendance.setEventId(eventId);
            attendance.setStudentId(entry.getStudentId());
            attendance.setPresent(entry.getPresent() != null ? entry.getPresent() : false);

            String groupId = studentGroupMap.get(entry.getStudentId());
            attendance.setGroupId(groupId != null ? groupId : "UNKNOWN");

            projectEventAttendanceRepository.save(attendance);
        }

        log.info("Saved {} attendance records for event {} of project {}", entries.size(), eventId, projectId);
    }

    // ======================== Grades ========================

    public void saveGrades(String projectId, String eventId,
                           List<ProjectBulkGradeRequest.GradeEntry> entries) {
        UUID clientId = getClientId();

        // Verify event exists and belongs to project
        ProjectEvent event = projectEventRepository.findByIdAndClientId(eventId, clientId)
                .orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventId));
        if (!event.getProjectId().equals(projectId)) {
            throw new IllegalArgumentException("Event does not belong to project");
        }

        // Delete existing grades for this event
        List<ProjectEventGrade> existing = projectEventGradeRepository.findByEventIdAndClientId(eventId, clientId);
        projectEventGradeRepository.deleteAll(existing);

        // Build student-to-group map for this project
        Map<String, String> studentGroupMap = buildStudentGroupMap(projectId, clientId);

        // Create new grade records
        for (ProjectBulkGradeRequest.GradeEntry entry : entries) {
            // Validate marks
            if (event.getMaxMarks() != null && entry.getMarks() != null && entry.getMarks() > event.getMaxMarks()) {
                throw new IllegalArgumentException(
                        "Marks (" + entry.getMarks() + ") exceed max marks (" + event.getMaxMarks() +
                                ") for student " + entry.getStudentId());
            }

            ProjectEventGrade grade = new ProjectEventGrade();
            grade.setId(UlidGenerator.nextUlid());
            grade.setClientId(clientId);
            grade.setEventId(eventId);
            grade.setStudentId(entry.getStudentId());
            grade.setMarks(entry.getMarks());

            String groupId = studentGroupMap.get(entry.getStudentId());
            grade.setGroupId(groupId != null ? groupId : "UNKNOWN");

            projectEventGradeRepository.save(grade);
        }

        log.info("Saved {} grade records for event {} of project {}", entries.size(), eventId, projectId);
    }

    // ======================== Submission ========================

    public ProjectGroupDTO submitProject(String projectId, String groupId, String studentId, String submissionUrl) {
        UUID clientId = getClientId();

        ProjectGroup group = projectGroupRepository.findByIdAndClientId(groupId, clientId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found: " + groupId));

        if (!group.getProjectId().equals(projectId)) {
            throw new IllegalArgumentException("Group does not belong to project");
        }

        // Verify student is member of group
        List<ProjectGroupMember> members = projectGroupMemberRepository.findByGroupIdAndClientId(groupId, clientId);
        boolean isMember = members.stream().anyMatch(m -> m.getStudentId().equals(studentId));
        if (!isMember) {
            throw new IllegalArgumentException("Student is not a member of this group");
        }

        // Check cutoff
        Project project = projectRepository.findByIdAndClientId(projectId, clientId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));

        if (project.getSubmissionCutoff() != null &&
                OffsetDateTime.now().isAfter(project.getSubmissionCutoff()) &&
                !Boolean.TRUE.equals(project.getLateSubmissionAllowed())) {
            throw new IllegalStateException("Submission deadline has passed and late submissions are not allowed");
        }

        group.setSubmissionUrl(submissionUrl);
        group.setSubmittedAt(OffsetDateTime.now());
        group.setSubmittedBy(studentId);
        group = projectGroupRepository.save(group);

        log.info("Student {} submitted project {} for group {}", studentId, projectId, groupId);

        ProjectGroupDTO dto = ProjectGroupDTO.fromEntity(group);
        dto.setMembers(members.stream().map(m ->
                new ProjectGroupDTO.MemberInfo(m.getStudentId(), null, null)
        ).collect(Collectors.toList()));
        return dto;
    }

    // ======================== Student-facing ========================

    @Transactional(readOnly = true)
    public List<ProjectDTO> getMyProjects(String studentId) {
        UUID clientId = getClientId();

        // Find groups where student is a member
        List<ProjectGroupMember> memberships = projectGroupMemberRepository.findByStudentIdAndClientId(studentId, clientId);
        if (memberships.isEmpty()) {
            return Collections.emptyList();
        }

        // Get unique project IDs from groups
        Set<String> projectIds = new HashSet<>();
        for (ProjectGroupMember m : memberships) {
            ProjectGroup group = projectGroupRepository.findByIdAndClientId(m.getGroupId(), clientId).orElse(null);
            if (group != null) {
                projectIds.add(group.getProjectId());
            }
        }

        return projectIds.stream()
                .map(pid -> projectRepository.findByIdAndClientId(pid, clientId).orElse(null))
                .filter(Objects::nonNull)
                .map(ProjectDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ProjectGroupDTO getMyGroup(String projectId, String studentId) {
        UUID clientId = getClientId();

        // Find student's memberships
        List<ProjectGroupMember> memberships = projectGroupMemberRepository.findByStudentIdAndClientId(studentId, clientId);

        // Find the group for this project
        for (ProjectGroupMember m : memberships) {
            ProjectGroup group = projectGroupRepository.findByIdAndClientId(m.getGroupId(), clientId).orElse(null);
            if (group != null && group.getProjectId().equals(projectId)) {
                ProjectGroupDTO dto = ProjectGroupDTO.fromEntity(group);
                List<ProjectGroupMember> members = projectGroupMemberRepository.findByGroupIdAndClientId(group.getId(), clientId);
                dto.setMembers(members.stream().map(mem ->
                        new ProjectGroupDTO.MemberInfo(mem.getStudentId(), null, null)
                ).collect(Collectors.toList()));
                return dto;
            }
        }

        throw new IllegalArgumentException("No group found for student in project " + projectId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getMyAttendance(String projectId, String studentId) {
        UUID clientId = getClientId();

        // Get all events for this project
        List<ProjectEvent> events = projectEventRepository.findByProjectIdAndClientIdOrderBySequenceAsc(projectId, clientId);
        List<String> eventIds = events.stream().map(ProjectEvent::getId).collect(Collectors.toList());

        // Get attendance and grades for this student
        List<ProjectEventAttendance> attendances = eventIds.isEmpty() ?
                Collections.emptyList() :
                projectEventAttendanceRepository.findByStudentIdAndEventIdIn(studentId, eventIds);

        List<ProjectEventGrade> grades = eventIds.isEmpty() ?
                Collections.emptyList() :
                projectEventGradeRepository.findByStudentIdAndEventIdIn(studentId, eventIds);

        // Build result
        Map<String, Boolean> attendanceMap = attendances.stream()
                .collect(Collectors.toMap(ProjectEventAttendance::getEventId, ProjectEventAttendance::getPresent));

        Map<String, Integer> gradeMap = grades.stream()
                .collect(Collectors.toMap(ProjectEventGrade::getEventId, ProjectEventGrade::getMarks));

        List<Map<String, Object>> eventResults = new ArrayList<>();
        for (ProjectEvent event : events) {
            Map<String, Object> eventResult = new HashMap<>();
            eventResult.put("event", ProjectEventDTO.fromEntity(event));
            eventResult.put("present", attendanceMap.get(event.getId()));
            eventResult.put("marks", gradeMap.get(event.getId()));
            eventResults.add(eventResult);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("events", eventResults);
        return result;
    }

    // ======================== Helpers ========================

    private Map<String, String> buildStudentGroupMap(String projectId, UUID clientId) {
        List<ProjectGroup> groups = projectGroupRepository.findByProjectIdAndClientId(projectId, clientId);
        Map<String, String> studentGroupMap = new HashMap<>();
        for (ProjectGroup group : groups) {
            List<ProjectGroupMember> members = projectGroupMemberRepository.findByGroupIdAndClientId(group.getId(), clientId);
            for (ProjectGroupMember member : members) {
                studentGroupMap.put(member.getStudentId(), group.getId());
            }
        }
        return studentGroupMap;
    }
}
