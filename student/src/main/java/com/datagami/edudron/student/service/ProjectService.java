package com.datagami.edudron.student.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.common.TenantContextRestTemplateInterceptor;
import com.datagami.edudron.common.UlidGenerator;
import com.datagami.edudron.student.domain.*;
import com.datagami.edudron.student.dto.*;
import com.datagami.edudron.student.repo.*;
import com.datagami.edudron.student.util.UserUtil;
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
    private ProjectSubmissionHistoryRepository submissionHistoryRepository;

    @Autowired
    private ProjectAttachmentRepository projectAttachmentRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private CommonEventService eventService;

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

    // ======================== Submission History ========================

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getSubmissionHistory(String projectId, String groupId) {
        UUID clientId = getClientId();
        List<ProjectSubmissionHistory> history = submissionHistoryRepository
                .findByGroupIdAndClientIdOrderBySubmittedAtDesc(groupId, clientId);

        // Resolve submitter names
        List<String> submitterIds = history.stream().map(ProjectSubmissionHistory::getSubmittedBy).distinct().collect(Collectors.toList());
        Map<String, String[]> nameMap = resolveStudentInfo(submitterIds);

        List<ProjectAttachmentDTO> submissionAttachmentDTOs = getSubmissionAttachments(groupId, clientId);

        return history.stream().map(h -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", h.getId());
            entry.put("submissionUrl", h.getSubmissionUrl());
            entry.put("submittedBy", h.getSubmittedBy());
            String[] info = nameMap.get(h.getSubmittedBy());
            entry.put("submittedByName", info != null ? info[0] : null);
            entry.put("submittedAt", h.getSubmittedAt());
            entry.put("action", h.getAction());
            entry.put("attachments", submissionAttachmentDTOs);
            return entry;
        }).collect(Collectors.toList());
    }

    // ======================== User Lookup ========================

    private Map<String, String[]> resolveStudentInfo(List<String> studentIds) {
        Map<String, String[]> infoMap = new HashMap<>(); // studentId -> [name, email]
        for (String studentId : studentIds) {
            try {
                String url = gatewayUrl + "/idp/users/" + studentId;
                ResponseEntity<Map<String, Object>> resp = getRestTemplate().exchange(
                        url, HttpMethod.GET, new HttpEntity<>(new HttpHeaders()),
                        new ParameterizedTypeReference<Map<String, Object>>() {}
                );
                Map<String, Object> user = resp.getBody();
                if (user != null) {
                    String name = (String) user.get("name");
                    String email = (String) user.get("email");
                    infoMap.put(studentId, new String[]{name, email});
                }
            } catch (Exception e) {
                log.debug("Could not resolve student {}: {}", studentId, e.getMessage());
            }
        }
        return infoMap;
    }

    private List<ProjectGroupDTO.MemberInfo> resolveMemberInfo(List<ProjectGroupMember> members) {
        List<String> studentIds = members.stream().map(ProjectGroupMember::getStudentId).collect(Collectors.toList());
        Map<String, String[]> infoMap = resolveStudentInfo(studentIds);
        return members.stream().map(m -> {
            String[] info = infoMap.get(m.getStudentId());
            String name = info != null ? info[0] : null;
            String email = info != null ? info[1] : null;
            return new ProjectGroupDTO.MemberInfo(m.getStudentId(), name, email);
        }).collect(Collectors.toList());
    }

    // ======================== Queries ========================

    @Transactional(readOnly = true)
    public List<String> getSectionIdsByCourse(String courseId) {
        UUID clientId = getClientId();
        return enrollmentRepository.findDistinctBatchIdsByClientIdAndCourseId(clientId, courseId);
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

        saveAttachments(project.getId(), null, ProjectAttachment.AttachmentContext.STATEMENT,
                request.getStatementAttachments(), UserUtil.getCurrentUserEmail());

        String userId = UserUtil.getCurrentUserId();
        String userEmail = UserUtil.getCurrentUserEmail();
        eventService.logUserAction("PROJECT_CREATED", userId, userEmail, "/api/projects", Map.of(
                "projectId", project.getId(),
                "projectTitle", project.getTitle(),
                "courseId", project.getCourseId() != null ? project.getCourseId() : "",
                "sectionId", project.getSectionId() != null ? project.getSectionId() : "",
                "status", project.getStatus().name()
        ));

        return ProjectDTO.fromEntity(project);
    }

    @Transactional
    public ProjectDTO bulkSetup(BulkProjectSetupRequest request) {
        UUID clientId = getClientId();
        String createdBy = UserUtil.getCurrentUserEmail();

        // 1. Create the project
        Project project = new Project();
        project.setId(UlidGenerator.nextUlid());
        project.setClientId(clientId);
        project.setCourseId(request.getCourseId());
        project.setSectionId(String.join(",", request.getSectionIds()));
        project.setTitle(request.getTitle());
        project.setDescription(request.getDescription());
        project.setMaxMarks(request.getMaxMarks() != null ? request.getMaxMarks() : 100);
        project.setSubmissionCutoff(request.getSubmissionCutoff());
        project.setLateSubmissionAllowed(request.getLateSubmissionAllowed() != null ? request.getLateSubmissionAllowed() : false);
        project.setStatus(Project.ProjectStatus.DRAFT);
        project.setCreatedBy(createdBy);
        project = projectRepository.save(project);

        saveAttachments(project.getId(), null, ProjectAttachment.AttachmentContext.STATEMENT,
                request.getStatementAttachments(), createdBy);

        // 2. Collect students and create groups
        int groupSize = request.getGroupSize();
        boolean mixSections = Boolean.TRUE.equals(request.getMixSections());
        Map<String, String> sectionNames = request.getSectionNames() != null ? request.getSectionNames() : Map.of();
        Map<String, Integer> sectionGroupCounts = request.getSectionGroupCounts() != null ? request.getSectionGroupCounts() : Map.of();
        List<ProjectGroup> allGroups = new ArrayList<>();
        int totalStudents = 0;
        int globalGroupNumber = 0;

        if (mixSections) {
            // Cross-section mode: pool all students, shuffle, distribute
            List<String> allStudentIds = new ArrayList<>();
            for (String sectionId : request.getSectionIds()) {
                List<String> sectionStudents = enrollmentRepository.findByClientIdAndBatchId(clientId, sectionId)
                        .stream().map(e -> e.getStudentId()).distinct().collect(Collectors.toList());
                allStudentIds.addAll(sectionStudents);
            }
            allStudentIds = allStudentIds.stream().distinct().collect(Collectors.toList());
            totalStudents = allStudentIds.size();

            if (!allStudentIds.isEmpty()) {
                Collections.shuffle(allStudentIds);
                // Use frontend-specified group count if available, else compute
                int numGroups = request.getTotalGroupCount() != null && request.getTotalGroupCount() > 0
                        ? request.getTotalGroupCount()
                        : (int) Math.ceil((double) allStudentIds.size() / groupSize);
                for (int i = 0; i < numGroups; i++) {
                    globalGroupNumber++;
                    ProjectGroup group = new ProjectGroup();
                    group.setId(UlidGenerator.nextUlid());
                    group.setClientId(clientId);
                    group.setProjectId(project.getId());
                    group.setGroupNumber(globalGroupNumber);
                    group.setGroupName("Group " + globalGroupNumber);
                    allGroups.add(projectGroupRepository.save(group));
                }
                for (int i = 0; i < allStudentIds.size(); i++) {
                    ProjectGroupMember member = new ProjectGroupMember();
                    member.setId(UlidGenerator.nextUlid());
                    member.setClientId(clientId);
                    member.setGroupId(allGroups.get(i % allGroups.size()).getId());
                    member.setStudentId(allStudentIds.get(i));
                    projectGroupMemberRepository.save(member);
                }
            }
        } else {
            // Per-section mode (default): groups created within each section
            for (String sectionId : request.getSectionIds()) {
                List<String> sectionStudents = enrollmentRepository.findByClientIdAndBatchId(clientId, sectionId)
                        .stream().map(e -> e.getStudentId()).distinct().collect(Collectors.toList());
                if (sectionStudents.isEmpty()) continue;

                totalStudents += sectionStudents.size();
                Collections.shuffle(sectionStudents);

                String sectionName = sectionNames.getOrDefault(sectionId, sectionId);
                // Use frontend-specified group count if available, else compute
                int numSectionGroups = sectionGroupCounts.containsKey(sectionId) && sectionGroupCounts.get(sectionId) > 0
                        ? sectionGroupCounts.get(sectionId)
                        : (int) Math.ceil((double) sectionStudents.size() / groupSize);

                List<ProjectGroup> sectionGroups = new ArrayList<>();
                for (int i = 0; i < numSectionGroups; i++) {
                    globalGroupNumber++;
                    ProjectGroup group = new ProjectGroup();
                    group.setId(UlidGenerator.nextUlid());
                    group.setClientId(clientId);
                    group.setProjectId(project.getId());
                    group.setGroupNumber(globalGroupNumber);
                    group.setGroupName(sectionName + " Group " + (i + 1));
                    sectionGroups.add(projectGroupRepository.save(group));
                }

                for (int i = 0; i < sectionStudents.size(); i++) {
                    ProjectGroupMember member = new ProjectGroupMember();
                    member.setId(UlidGenerator.nextUlid());
                    member.setClientId(clientId);
                    member.setGroupId(sectionGroups.get(i % numSectionGroups).getId());
                    member.setStudentId(sectionStudents.get(i));
                    projectGroupMemberRepository.save(member);
                }
                allGroups.addAll(sectionGroups);
            }
        }

        if (totalStudents == 0) {
            log.warn("No students found in selected sections for bulk setup");
            return ProjectDTO.fromEntity(project);
        }

        log.info("Bulk setup: created project '{}' with {} groups from {} students across {} sections (mixSections={})",
                project.getTitle(), allGroups.size(), totalStudents, request.getSectionIds().size(), mixSections);

        eventService.logUserAction("PROJECT_BULK_SETUP", createdBy, createdBy, "/api/projects/bulk-setup", Map.of(
                "projectId", project.getId(),
                "projectTitle", project.getTitle(),
                "courseId", project.getCourseId() != null ? project.getCourseId() : "",
                "sectionCount", request.getSectionIds().size(),
                "studentCount", totalStudents,
                "groupCount", allGroups.size(),
                "groupSize", request.getGroupSize(),
                "mixSections", mixSections
        ));

        // 5. Assign problem statements (filtered by selected IDs if provided)
        try {
            if (request.getSelectedQuestionIds() != null && !request.getSelectedQuestionIds().isEmpty()) {
                assignStatementsFromIds(project.getId(), request.getSelectedQuestionIds());
            } else {
                assignStatements(project.getId());
            }
        } catch (Exception e) {
            log.warn("Problem statement assignment failed (can be done later): {}", e.getMessage());
        }

        // 6. Create events if provided
        if (request.getEventsBySectionId() != null && !request.getEventsBySectionId().isEmpty()) {
            for (Map.Entry<String, List<BulkProjectSetupRequest.EventInput>> entry : request.getEventsBySectionId().entrySet()) {
                String sectionId = "_global".equals(entry.getKey()) ? null : entry.getKey();
                List<BulkProjectSetupRequest.EventInput> sectionEvents = entry.getValue();
                for (int i = 0; i < sectionEvents.size(); i++) {
                    BulkProjectSetupRequest.EventInput ev = sectionEvents.get(i);
                    if (ev.getName() == null || ev.getName().isBlank()) continue;
                    OffsetDateTime dateTime = ev.getDateTime() != null && !ev.getDateTime().isBlank()
                            ? OffsetDateTime.parse(ev.getDateTime())
                            : null;
                    addEvent(project.getId(), ev.getName(), dateTime, ev.getZoomLink(),
                            ev.getHasMarks(), ev.getMaxMarks(), ev.getSequence() != null ? ev.getSequence() : i + 1, sectionId);
                }
            }
            log.info("Created per-section events for project {}", project.getId());
        } else if (request.getEvents() != null && !request.getEvents().isEmpty()) {
            for (int i = 0; i < request.getEvents().size(); i++) {
                BulkProjectSetupRequest.EventInput ev = request.getEvents().get(i);
                if (ev.getName() == null || ev.getName().isBlank()) continue;
                OffsetDateTime dateTime = ev.getDateTime() != null && !ev.getDateTime().isBlank()
                        ? OffsetDateTime.parse(ev.getDateTime())
                        : null;
                addEvent(project.getId(), ev.getName(), dateTime, ev.getZoomLink(),
                        ev.getHasMarks(), ev.getMaxMarks(), ev.getSequence() != null ? ev.getSequence() : i + 1, null);
            }
            log.info("Created {} global events for project {}", request.getEvents().size(), project.getId());
        }

        return ProjectDTO.fromEntity(project);
    }

    @Transactional
    public ProjectDTO addSections(String projectId, AddSectionsRequest request) {
        UUID clientId = getClientId();
        Project project = projectRepository.findByIdAndClientId(projectId, clientId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));

        // Get existing group members to exclude
        List<ProjectGroup> existingGroups = projectGroupRepository.findByProjectIdAndClientId(projectId, clientId);
        Set<String> existingStudentIds = new HashSet<>();
        for (ProjectGroup g : existingGroups) {
            List<ProjectGroupMember> members = projectGroupMemberRepository.findByGroupIdAndClientId(g.getId(), clientId);
            members.forEach(m -> existingStudentIds.add(m.getStudentId()));
        }

        // Collect new students from new sections (excluding already grouped)
        List<String> newStudentIds = new ArrayList<>();
        for (String sectionId : request.getSectionIds()) {
            List<String> sectionStudents = enrollmentRepository.findByClientIdAndBatchId(clientId, sectionId)
                    .stream()
                    .map(e -> e.getStudentId())
                    .distinct()
                    .filter(sid -> !existingStudentIds.contains(sid))
                    .collect(Collectors.toList());
            newStudentIds.addAll(sectionStudents);
        }
        newStudentIds = newStudentIds.stream().distinct().collect(Collectors.toList());

        if (newStudentIds.isEmpty()) {
            throw new IllegalStateException("No new students found in selected sections (all may already be in groups)");
        }

        // Determine next group number
        int maxGroupNumber = existingGroups.stream()
                .mapToInt(ProjectGroup::getGroupNumber)
                .max()
                .orElse(0);

        // Generate new groups
        Collections.shuffle(newStudentIds);
        int groupSize = request.getGroupSize();
        int numNewGroups = (int) Math.ceil((double) newStudentIds.size() / groupSize);

        List<ProjectGroup> newGroups = new ArrayList<>();
        for (int i = 0; i < numNewGroups; i++) {
            ProjectGroup group = new ProjectGroup();
            group.setId(UlidGenerator.nextUlid());
            group.setClientId(clientId);
            group.setProjectId(projectId);
            group.setGroupNumber(maxGroupNumber + i + 1);
            newGroups.add(projectGroupRepository.save(group));
        }

        // Assign students to new groups
        for (int i = 0; i < newStudentIds.size(); i++) {
            ProjectGroupMember member = new ProjectGroupMember();
            member.setId(UlidGenerator.nextUlid());
            member.setClientId(clientId);
            member.setGroupId(newGroups.get(i % numNewGroups).getId());
            member.setStudentId(newStudentIds.get(i));
            projectGroupMemberRepository.save(member);
        }

        // Update sectionId on project (append new sections)
        String existingSections = project.getSectionId() != null ? project.getSectionId() : "";
        Set<String> allSectionIds = new LinkedHashSet<>();
        if (!existingSections.isBlank()) {
            allSectionIds.addAll(Arrays.asList(existingSections.split(",")));
        }
        allSectionIds.addAll(request.getSectionIds());
        project.setSectionId(String.join(",", allSectionIds));
        projectRepository.save(project);

        // Assign problem statements to new groups only
        try {
            assignStatementsToGroups(projectId, newGroups);
        } catch (Exception e) {
            log.warn("Problem statement assignment for new groups failed: {}", e.getMessage());
        }

        log.info("Added {} sections to project '{}': {} new students, {} new groups",
                request.getSectionIds().size(), project.getTitle(), newStudentIds.size(), numNewGroups);

        String userId = UserUtil.getCurrentUserId();
        String userEmail = UserUtil.getCurrentUserEmail();
        eventService.logUserAction("PROJECT_SECTIONS_ADDED", userId, userEmail, "/api/projects/" + projectId + "/add-sections", Map.of(
                "projectId", projectId,
                "projectTitle", project.getTitle(),
                "newSectionCount", request.getSectionIds().size(),
                "newStudentCount", newStudentIds.size(),
                "newGroupCount", numNewGroups
        ));

        return ProjectDTO.fromEntity(project);
    }

    @Transactional(readOnly = true)
    public ProjectDTO getProject(String id) {
        UUID clientId = getClientId();
        Project project = projectRepository.findByIdAndClientId(id, clientId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + id));
        ProjectDTO dto = ProjectDTO.fromEntity(project);
        dto.setStatementAttachments(getStatementAttachments(id, clientId));
        return dto;
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

        // Get students from all sections (handles comma-separated sectionIds)
        List<String> studentIds = new ArrayList<>();
        String sectionIdValue = project.getSectionId();
        if (sectionIdValue != null && !sectionIdValue.isBlank()) {
            for (String sid : sectionIdValue.split(",")) {
                String trimmed = sid.trim();
                if (!trimmed.isEmpty()) {
                    enrollmentRepository.findByClientIdAndBatchId(clientId, trimmed)
                            .stream()
                            .map(e -> e.getStudentId())
                            .forEach(studentIds::add);
                }
            }
        }
        studentIds = studentIds.stream().distinct().collect(Collectors.toList());

        if (studentIds.isEmpty()) {
            throw new IllegalStateException("No students found in sections for project " + projectId);
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
            dto.setMembers(resolveMemberInfo(members));
            dto.setSubmissionAttachments(getSubmissionAttachments(g.getId(), clientId));
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
        dto.setMembers(resolveMemberInfo(members));
        return dto;
    }

    @Transactional(readOnly = true)
    public List<ProjectEventDTO> getEvents(String projectId) {
        UUID clientId = getClientId();
        projectRepository.findByIdAndClientId(projectId, clientId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        return projectEventRepository.findByProjectIdAndClientIdOrderBySequenceAsc(projectId, clientId)
                .stream()
                .map(ProjectEventDTO::fromEntity)
                .collect(Collectors.toList());
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

            // Only assign to groups that don't already have a statement
            List<ProjectGroup> unassigned = groups.stream()
                    .filter(g -> g.getProblemStatementId() == null || g.getProblemStatementId().isBlank())
                    .collect(Collectors.toList());

            if (unassigned.isEmpty()) {
                log.info("All groups already have problem statements assigned for project {}", projectId);
                return;
            }

            // Count already-assigned to continue round-robin
            int alreadyAssigned = groups.size() - unassigned.size();

            // Round-robin assign to unassigned groups
            for (int i = 0; i < unassigned.size(); i++) {
                int questionIndex = (alreadyAssigned + i) % questions.size();
                Map<String, Object> question = questions.get(questionIndex);
                ProjectGroup group = unassigned.get(i);
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

    public void assignStatementsFromIds(String projectId, List<String> questionIds) {
        UUID clientId = getClientId();
        projectRepository.findByIdAndClientId(projectId, clientId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));

        List<ProjectGroup> groups = projectGroupRepository.findByProjectIdAndClientId(projectId, clientId);
        if (groups.isEmpty()) {
            throw new IllegalStateException("No groups found for project. Generate groups first.");
        }

        List<ProjectGroup> unassigned = groups.stream()
                .filter(g -> g.getProblemStatementId() == null || g.getProblemStatementId().isBlank())
                .collect(Collectors.toList());

        if (unassigned.isEmpty()) {
            log.info("All groups already have problem statements assigned for project {}", projectId);
            return;
        }

        // Round-robin assign the selected question IDs
        for (int i = 0; i < unassigned.size(); i++) {
            int questionIndex = i % questionIds.size();
            ProjectGroup group = unassigned.get(i);
            group.setProblemStatementId(questionIds.get(questionIndex));
            projectGroupRepository.save(group);
        }

        log.info("Assigned {} selected problem statements to {} groups for project {}",
                questionIds.size(), unassigned.size(), projectId);
    }

    @SuppressWarnings("unchecked")
    private void assignStatementsToGroups(String projectId, List<ProjectGroup> targetGroups) {
        UUID clientId = getClientId();
        Project project = projectRepository.findByIdAndClientId(projectId, clientId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));

        String courseId = project.getCourseId();
        if (courseId == null || courseId.isBlank()) return;

        // Find the highest assignment index from existing groups
        List<ProjectGroup> allGroups = projectGroupRepository.findByProjectIdAndClientId(projectId, clientId);
        int assignedCount = (int) allGroups.stream()
                .filter(g -> g.getProblemStatementId() != null && !g.getProblemStatementId().isBlank())
                .count();

        String url = gatewayUrl + "/api/project-questions?courseId=" + courseId;
        ResponseEntity<List<Map<String, Object>>> response = getRestTemplate().exchange(
                url, HttpMethod.GET, new HttpEntity<>(new HttpHeaders()),
                new ParameterizedTypeReference<List<Map<String, Object>>>() {}
        );

        List<Map<String, Object>> questions = response.getBody();
        if (questions == null || questions.isEmpty()) return;

        // Continue round-robin from where existing assignment left off
        for (int i = 0; i < targetGroups.size(); i++) {
            int questionIndex = (assignedCount + i) % questions.size();
            Map<String, Object> question = questions.get(questionIndex);
            ProjectGroup group = targetGroups.get(i);
            group.setProblemStatementId((String) question.get("id"));
            projectGroupRepository.save(group);
        }
    }

    // ======================== Events ========================

    public ProjectEventDTO addEvent(String projectId, String name, OffsetDateTime dateTime,
                                     String zoomLink, Boolean hasMarks, Integer maxMarks, Integer sequence, String sectionId) {
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
        event.setSectionId(sectionId);

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
        projectEventAttendanceRepository.flush();

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
        projectEventGradeRepository.flush();

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

    // ======================== Get Attendance ========================

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getEventAttendance(String projectId, String eventId) {
        UUID clientId = getClientId();
        List<ProjectEventAttendance> records = projectEventAttendanceRepository.findByEventIdAndClientId(eventId, clientId);
        return records.stream().map(a -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("studentId", a.getStudentId());
            entry.put("groupId", a.getGroupId());
            entry.put("present", a.getPresent());
            return entry;
        }).collect(Collectors.toList());
    }

    // ======================== Get Grades ========================

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getEventGrades(String projectId, String eventId) {
        UUID clientId = getClientId();
        List<ProjectEventGrade> records = projectEventGradeRepository.findByEventIdAndClientId(eventId, clientId);
        return records.stream().map(g -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("studentId", g.getStudentId());
            entry.put("groupId", g.getGroupId());
            entry.put("marks", g.getMarks());
            return entry;
        }).collect(Collectors.toList());
    }

    // ======================== Submission ========================

    public ProjectGroupDTO submitProject(String projectId, String groupId, String studentId,
                                         String submissionUrl, List<SubmitProjectRequest.AttachmentInfo> attachments) {
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

        // Determine action: first submission or edit
        String action = group.getSubmittedAt() != null ? "EDIT" : "SUBMIT";

        group.setSubmissionUrl(submissionUrl);
        group.setSubmittedAt(OffsetDateTime.now());
        group.setSubmittedBy(studentId);
        group = projectGroupRepository.save(group);

        // Save submission history
        ProjectSubmissionHistory history = new ProjectSubmissionHistory();
        history.setId(UlidGenerator.nextUlid());
        history.setClientId(clientId);
        history.setGroupId(groupId);
        history.setProjectId(projectId);
        history.setSubmissionUrl(submissionUrl);
        history.setSubmittedBy(studentId);
        history.setSubmittedAt(group.getSubmittedAt());
        history.setAction(action);
        submissionHistoryRepository.save(history);

        saveAttachments(projectId, groupId, ProjectAttachment.AttachmentContext.SUBMISSION,
                attachments, studentId);

        log.info("Student {} {} project {} for group {}", studentId, action.toLowerCase(), projectId, groupId);

        ProjectGroupDTO dto = ProjectGroupDTO.fromEntity(group);
        dto.setMembers(resolveMemberInfo(members));
        dto.setSubmissionAttachments(getSubmissionAttachments(groupId, clientId));
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
                .filter(p -> p.getStatus() != Project.ProjectStatus.DRAFT)
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
                dto.setMembers(resolveMemberInfo(members));
                dto.setSubmissionAttachments(getSubmissionAttachments(group.getId(), clientId));
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

    // ======================== Attachments ========================

    private void saveAttachments(String projectId, String groupId,
                                 ProjectAttachment.AttachmentContext context,
                                 List<SubmitProjectRequest.AttachmentInfo> attachments,
                                 String uploadedBy) {
        if (attachments == null || attachments.isEmpty()) return;
        UUID clientId = getClientId();

        for (SubmitProjectRequest.AttachmentInfo info : attachments) {
            if (info.getFileUrl() == null || info.getFileUrl().isBlank()) continue;

            ProjectAttachment attachment = new ProjectAttachment();
            attachment.setId(UlidGenerator.nextUlid());
            attachment.setClientId(clientId);
            attachment.setProjectId(projectId);
            attachment.setGroupId(groupId);
            attachment.setContext(context);
            attachment.setFileUrl(info.getFileUrl());
            attachment.setFileName(info.getFileName() != null ? info.getFileName() : "attachment");
            attachment.setFileSizeBytes(info.getFileSizeBytes());
            attachment.setMimeType(info.getMimeType());
            attachment.setUploadedBy(uploadedBy);
            projectAttachmentRepository.save(attachment);
        }
    }

    private List<ProjectAttachmentDTO> getStatementAttachments(String projectId, UUID clientId) {
        return projectAttachmentRepository
                .findByProjectIdAndClientIdAndContext(projectId, clientId, ProjectAttachment.AttachmentContext.STATEMENT)
                .stream()
                .map(ProjectAttachmentDTO::fromEntity)
                .collect(Collectors.toList());
    }

    private List<ProjectAttachmentDTO> getSubmissionAttachments(String groupId, UUID clientId) {
        return projectAttachmentRepository
                .findByGroupIdAndClientIdAndContext(groupId, clientId, ProjectAttachment.AttachmentContext.SUBMISSION)
                .stream()
                .map(ProjectAttachmentDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public ProjectAttachmentDTO addAttachment(String projectId, String groupId,
                                               String contextStr,
                                               SubmitProjectRequest.AttachmentInfo info) {
        UUID clientId = getClientId();
        projectRepository.findByIdAndClientId(projectId, clientId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));

        ProjectAttachment.AttachmentContext context = ProjectAttachment.AttachmentContext.valueOf(contextStr);
        String uploadedBy = UserUtil.getCurrentUserEmail();

        ProjectAttachment attachment = new ProjectAttachment();
        attachment.setId(UlidGenerator.nextUlid());
        attachment.setClientId(clientId);
        attachment.setProjectId(projectId);
        attachment.setGroupId(groupId);
        attachment.setContext(context);
        attachment.setFileUrl(info.getFileUrl());
        attachment.setFileName(info.getFileName() != null ? info.getFileName() : "attachment");
        attachment.setFileSizeBytes(info.getFileSizeBytes());
        attachment.setMimeType(info.getMimeType());
        attachment.setUploadedBy(uploadedBy);

        attachment = projectAttachmentRepository.save(attachment);
        log.info("Added {} attachment '{}' to project {} group {}",
                context, attachment.getFileName(), projectId, groupId);
        return ProjectAttachmentDTO.fromEntity(attachment);
    }

    public void deleteAttachment(String projectId, String attachmentId) {
        UUID clientId = getClientId();
        ProjectAttachment attachment = projectAttachmentRepository.findByIdAndClientId(attachmentId, clientId)
                .orElseThrow(() -> new IllegalArgumentException("Attachment not found: " + attachmentId));

        if (!attachment.getProjectId().equals(projectId)) {
            throw new IllegalArgumentException("Attachment does not belong to project");
        }

        projectAttachmentRepository.delete(attachment);
        log.info("Deleted attachment {} from project {}", attachmentId, projectId);
    }

    public List<ProjectAttachmentDTO> getAttachments(String projectId, String contextStr) {
        UUID clientId = getClientId();
        if (contextStr != null) {
            ProjectAttachment.AttachmentContext context = ProjectAttachment.AttachmentContext.valueOf(contextStr);
            return projectAttachmentRepository
                    .findByProjectIdAndClientIdAndContext(projectId, clientId, context)
                    .stream()
                    .map(ProjectAttachmentDTO::fromEntity)
                    .collect(Collectors.toList());
        }
        return projectAttachmentRepository.findByProjectIdAndClientId(projectId, clientId)
                .stream()
                .map(ProjectAttachmentDTO::fromEntity)
                .collect(Collectors.toList());
    }
}
