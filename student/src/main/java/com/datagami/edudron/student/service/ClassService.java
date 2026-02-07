package com.datagami.edudron.student.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.common.UlidGenerator;
import com.datagami.edudron.student.domain.Class;
import com.datagami.edudron.student.domain.Institute;
import com.datagami.edudron.student.domain.Section;
import com.datagami.edudron.student.dto.*;
import com.datagami.edudron.student.repo.ClassRepository;
import com.datagami.edudron.student.repo.EnrollmentRepository;
import com.datagami.edudron.student.repo.InstituteRepository;
import com.datagami.edudron.student.repo.SectionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class ClassService {
    
    @Autowired
    private ClassRepository classRepository;
    
    @Autowired
    private InstituteRepository instituteRepository;
    
    @Autowired
    private SectionRepository sectionRepository;
    
    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private StudentAuditService auditService;
    
    public ClassDTO createClass(String instituteId, CreateClassRequest request) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        // Validate institute exists and belongs to client
        Institute institute = instituteRepository.findByIdAndClientId(instituteId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Institute not found: " + instituteId));
        
        if (!institute.getIsActive()) {
            throw new IllegalArgumentException("Institute is not active");
        }
        
        // Check if code already exists for this institute
        if (classRepository.existsByInstituteIdAndCode(instituteId, request.getCode())) {
            throw new IllegalArgumentException("Class with code '" + request.getCode() + "' already exists in this institute");
        }
        
        Class classEntity = new Class();
        classEntity.setId(UlidGenerator.nextUlid());
        classEntity.setInstituteId(instituteId);
        classEntity.setClientId(clientId);
        classEntity.setName(request.getName());
        classEntity.setCode(request.getCode());
        classEntity.setAcademicYear(request.getAcademicYear());
        classEntity.setGrade(request.getGrade());
        classEntity.setLevel(request.getLevel());
        classEntity.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);
        
        Class saved = classRepository.save(classEntity);
        return toDTO(saved);
    }
    
    public ClassDTO getClass(String classId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        Class classEntity = classRepository.findByIdAndClientId(classId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Class not found: " + classId));
        
        return toDTO(classEntity);
    }
    
    public List<ClassDTO> getAllClasses() {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        List<Class> classes = classRepository.findByClientId(clientId);
        return classes.stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }
    
    public List<ClassDTO> getClassesByInstitute(String instituteId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        // Validate institute belongs to client
        instituteRepository.findByIdAndClientId(instituteId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Institute not found: " + instituteId));
        
        List<Class> classes = classRepository.findByInstituteId(instituteId);
        return classes.stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }
    
    public List<ClassDTO> getActiveClassesByInstitute(String instituteId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        // Validate institute belongs to client
        instituteRepository.findByIdAndClientId(instituteId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Institute not found: " + instituteId));
        
        List<Class> classes = classRepository.findByInstituteIdAndIsActive(instituteId, true);
        return classes.stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }
    
    public List<ClassDTO> getClassesByCourse(String courseId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        // Get all enrollments for this course
        List<com.datagami.edudron.student.domain.Enrollment> enrollments = 
            enrollmentRepository.findByClientIdAndCourseId(clientId, courseId);
        
        // Get unique section IDs (batchId represents sectionId)
        Set<String> sectionIds = enrollments.stream()
            .map(com.datagami.edudron.student.domain.Enrollment::getBatchId)
            .filter(batchId -> batchId != null && !batchId.isEmpty())
            .collect(Collectors.toSet());
        
        if (sectionIds.isEmpty()) {
            return new ArrayList<>();
        }
        
        // Get unique class IDs from these sections
        Set<String> classIds = new HashSet<>();
        for (String sectionId : sectionIds) {
            sectionRepository.findByIdAndClientId(sectionId, clientId)
                .ifPresent(section -> {
                    if (section.getClassId() != null && !section.getClassId().isEmpty()) {
                        classIds.add(section.getClassId());
                    }
                });
        }
        
        if (classIds.isEmpty()) {
            return new ArrayList<>();
        }
        
        // Fetch all classes
        List<Class> classes = new ArrayList<>();
        for (String classId : classIds) {
            classRepository.findByIdAndClientId(classId, clientId)
                .ifPresent(classes::add);
        }
        
        // Sort by name
        classes.sort(Comparator.comparing(Class::getName));
        
        return classes.stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }
    
    public ClassDTO updateClass(String classId, CreateClassRequest request) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        Class classEntity = classRepository.findByIdAndClientId(classId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Class not found: " + classId));
        
        // Validate institute if changed
        if (!classEntity.getInstituteId().equals(request.getInstituteId())) {
            Institute institute = instituteRepository.findByIdAndClientId(request.getInstituteId(), clientId)
                .orElseThrow(() -> new IllegalArgumentException("Institute not found: " + request.getInstituteId()));
            
            if (!institute.getIsActive()) {
                throw new IllegalArgumentException("Institute is not active");
            }
        }
        
        // Check if code is being changed and if new code already exists
        if (!classEntity.getCode().equals(request.getCode())) {
            if (classRepository.existsByInstituteIdAndCode(request.getInstituteId(), request.getCode())) {
                throw new IllegalArgumentException("Class with code '" + request.getCode() + "' already exists in this institute");
            }
        }
        
        classEntity.setInstituteId(request.getInstituteId());
        classEntity.setName(request.getName());
        classEntity.setCode(request.getCode());
        classEntity.setAcademicYear(request.getAcademicYear());
        classEntity.setGrade(request.getGrade());
        classEntity.setLevel(request.getLevel());
        if (request.getIsActive() != null) {
            classEntity.setIsActive(request.getIsActive());
        }
        
        Class saved = classRepository.save(classEntity);
        auditService.logCrud(clientId, "UPDATE", "Class", classId, null, null,
            java.util.Map.of("name", saved.getName(), "code", saved.getCode(), "instituteId", saved.getInstituteId()));
        return toDTO(saved);
    }
    
    public void deactivateClass(String classId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        Class classEntity = classRepository.findByIdAndClientId(classId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Class not found: " + classId));
        
        classEntity.setIsActive(false);
        classRepository.save(classEntity);
        auditService.logCrud(clientId, "UPDATE", "Class", classId, null, null,
            java.util.Map.of("action", "DEACTIVATE", "name", classEntity.getName()));
    }

    @Transactional(readOnly = true)
    public long countClasses(boolean activeOnly) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);

        return activeOnly
            ? classRepository.countByClientIdAndIsActive(clientId, true)
            : classRepository.countByClientId(clientId);
    }
    
    public ClassWithSectionsDTO createClassWithSections(String instituteId, CreateClassWithSectionsRequest request) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        // Validate institute exists and belongs to client
        Institute institute = instituteRepository.findByIdAndClientId(instituteId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Institute not found: " + instituteId));
        
        if (!institute.getIsActive()) {
            throw new IllegalArgumentException("Institute is not active");
        }
        
        // Check if code already exists for this institute
        if (classRepository.existsByInstituteIdAndCode(instituteId, request.getCode())) {
            throw new IllegalArgumentException("Class with code '" + request.getCode() + "' already exists in this institute");
        }
        
        // Validate section names are unique within the batch
        Set<String> sectionNames = new HashSet<>();
        for (int i = 0; i < request.getSections().size(); i++) {
            String sectionName = request.getSections().get(i).getName();
            if (!sectionNames.add(sectionName)) {
                throw new IllegalArgumentException("Duplicate section name at index " + i + ": '" + sectionName + "'");
            }
        }
        
        // Create the class
        Class classEntity = new Class();
        classEntity.setId(UlidGenerator.nextUlid());
        classEntity.setInstituteId(instituteId);
        classEntity.setClientId(clientId);
        classEntity.setName(request.getName());
        classEntity.setCode(request.getCode());
        classEntity.setAcademicYear(request.getAcademicYear());
        classEntity.setGrade(request.getGrade());
        classEntity.setLevel(request.getLevel());
        classEntity.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);
        
        Class savedClass = classRepository.save(classEntity);
        
        // Create sections
        List<Section> sections = new ArrayList<>();
        for (CreateSectionForClassRequest sectionRequest : request.getSections()) {
            Section section = new Section();
            section.setId(UlidGenerator.nextUlid());
            section.setClientId(clientId);
            section.setName(sectionRequest.getName());
            section.setDescription(sectionRequest.getDescription());
            section.setClassId(savedClass.getId());
            section.setStartDate(sectionRequest.getStartDate());
            section.setEndDate(sectionRequest.getEndDate());
            section.setMaxStudents(sectionRequest.getMaxStudents());
            section.setIsActive(true);
            
            sections.add(sectionRepository.save(section));
        }
        
        // Convert to DTOs
        ClassDTO classDTO = toDTO(savedClass);
        List<SectionDTO> sectionDTOs = sections.stream()
            .map(section -> toSectionDTO(section, clientId))
            .collect(Collectors.toList());
        
        return new ClassWithSectionsDTO(classDTO, sectionDTOs);
    }
    
    public BatchCreateClassesResponse batchCreateClasses(String instituteId, BatchCreateClassesRequest request) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        // Validate institute exists and belongs to client once upfront
        Institute institute = instituteRepository.findByIdAndClientId(instituteId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Institute not found: " + instituteId));
        
        if (!institute.getIsActive()) {
            throw new IllegalArgumentException("Institute is not active");
        }
        
        // Pre-validate: check for duplicate codes within the batch
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < request.getClasses().size(); i++) {
            String code = request.getClasses().get(i).getCode();
            if (!codes.add(code)) {
                throw new IllegalArgumentException("Duplicate class code at index " + i + ": '" + code + "'");
            }
        }
        
        // Pre-validate: check if any codes already exist in the database
        for (int i = 0; i < request.getClasses().size(); i++) {
            String code = request.getClasses().get(i).getCode();
            if (classRepository.existsByInstituteIdAndCode(instituteId, code)) {
                throw new IllegalArgumentException("Class with code '" + code + "' already exists at index " + i);
            }
        }
        
        // Create all classes
        List<Class> classes = new ArrayList<>();
        for (CreateClassRequest classRequest : request.getClasses()) {
            Class classEntity = new Class();
            classEntity.setId(UlidGenerator.nextUlid());
            classEntity.setInstituteId(instituteId);
            classEntity.setClientId(clientId);
            classEntity.setName(classRequest.getName());
            classEntity.setCode(classRequest.getCode());
            classEntity.setAcademicYear(classRequest.getAcademicYear());
            classEntity.setGrade(classRequest.getGrade());
            classEntity.setLevel(classRequest.getLevel());
            classEntity.setIsActive(classRequest.getIsActive() != null ? classRequest.getIsActive() : true);
            
            classes.add(classRepository.save(classEntity));
        }
        
        // Convert to DTOs
        List<ClassDTO> classDTOs = classes.stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
        
        return new BatchCreateClassesResponse(
            classDTOs,
            classDTOs.size(),
            "Successfully created " + classDTOs.size() + " classes"
        );
    }
    
    private SectionDTO toSectionDTO(Section section, UUID clientId) {
        SectionDTO dto = new SectionDTO();
        dto.setId(section.getId());
        dto.setClientId(section.getClientId());
        dto.setName(section.getName());
        dto.setDescription(section.getDescription());
        dto.setClassId(section.getClassId());
        dto.setStartDate(section.getStartDate());
        dto.setEndDate(section.getEndDate());
        dto.setMaxStudents(section.getMaxStudents());
        dto.setIsActive(section.getIsActive());
        dto.setCreatedAt(section.getCreatedAt());
        dto.setUpdatedAt(section.getUpdatedAt());
        dto.setStudentCount(0L); // New section has no students
        
        return dto;
    }
    
    private ClassDTO toDTO(Class classEntity) {
        ClassDTO dto = new ClassDTO();
        dto.setId(classEntity.getId());
        dto.setInstituteId(classEntity.getInstituteId());
        dto.setClientId(classEntity.getClientId());
        dto.setName(classEntity.getName());
        dto.setCode(classEntity.getCode());
        dto.setAcademicYear(classEntity.getAcademicYear());
        dto.setGrade(classEntity.getGrade());
        dto.setLevel(classEntity.getLevel());
        dto.setIsActive(classEntity.getIsActive());
        dto.setCreatedAt(classEntity.getCreatedAt());
        dto.setUpdatedAt(classEntity.getUpdatedAt());
        
        // Get section count and student count
        String clientIdStr = TenantContext.getClientId();
        UUID clientId = clientIdStr != null ? UUID.fromString(clientIdStr) : classEntity.getClientId();
        
        long sectionCount = sectionRepository.findByClientIdAndClassId(clientId, classEntity.getId()).size();
        dto.setSectionCount(sectionCount);
        
        // Count distinct students in this class (includes students in sections and class-level only)
        long studentCount = enrollmentRepository.countDistinctStudentsByClientIdAndClassId(clientId, classEntity.getId());
        dto.setStudentCount(studentCount);
        
        return dto;
    }
}

