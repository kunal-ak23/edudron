package com.datagami.edudron.student.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.common.UlidGenerator;
import com.datagami.edudron.student.domain.Class;
import com.datagami.edudron.student.domain.Section;
import com.datagami.edudron.student.dto.*;
import com.datagami.edudron.student.repo.ClassRepository;
import com.datagami.edudron.student.repo.EnrollmentRepository;
import com.datagami.edudron.student.repo.ProgressRepository;
import com.datagami.edudron.student.repo.SectionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class SectionService {
    
    @Autowired
    private SectionRepository sectionRepository;
    
    @Autowired
    private ClassRepository classRepository;
    
    @Autowired
    private EnrollmentRepository enrollmentRepository;
    
    @Autowired
    private ProgressRepository progressRepository;
    
    public SectionDTO createSection(String classId, CreateSectionRequest request) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        // Validate class exists and belongs to client
        Class classEntity = classRepository.findByIdAndClientId(classId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Class not found: " + classId));
        
        if (!classEntity.getIsActive()) {
            throw new IllegalArgumentException("Class is not active");
        }
        
        Section section = new Section();
        section.setId(UlidGenerator.nextUlid());
        section.setClientId(clientId);
        section.setName(request.getName());
        section.setDescription(request.getDescription());
        section.setClassId(classId);
        section.setStartDate(request.getStartDate());
        section.setEndDate(request.getEndDate());
        section.setMaxStudents(request.getMaxStudents());
        section.setIsActive(true);
        
        Section saved = sectionRepository.save(section);
        return toDTO(saved, clientId);
    }
    
    public SectionDTO getSection(String sectionId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        Section section = sectionRepository.findByIdAndClientId(sectionId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Section not found: " + sectionId));
        
        return toDTO(section, clientId);
    }
    
    public List<SectionDTO> getSectionsByClass(String classId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        // Validate class belongs to client
        classRepository.findByIdAndClientId(classId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Class not found: " + classId));
        
        List<Section> sections = sectionRepository.findByClientIdAndClassId(clientId, classId);
        return sections.stream()
            .map(section -> toDTO(section, clientId))
            .collect(Collectors.toList());
    }
    
    public List<SectionDTO> getActiveSectionsByClass(String classId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        // Validate class belongs to client
        classRepository.findByIdAndClientId(classId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Class not found: " + classId));
        
        List<Section> sections = sectionRepository.findByClientIdAndClassIdAndIsActive(clientId, classId, true);
        return sections.stream()
            .map(section -> toDTO(section, clientId))
            .collect(Collectors.toList());
    }
    
    public SectionDTO updateSection(String sectionId, CreateSectionRequest request) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        Section section = sectionRepository.findByIdAndClientId(sectionId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Section not found: " + sectionId));
        
        // Validate class if changed
        if (!section.getClassId().equals(request.getClassId())) {
            Class classEntity = classRepository.findByIdAndClientId(request.getClassId(), clientId)
                .orElseThrow(() -> new IllegalArgumentException("Class not found: " + request.getClassId()));
            
            if (!classEntity.getIsActive()) {
                throw new IllegalArgumentException("Class is not active");
            }
        }
        
        section.setName(request.getName());
        section.setDescription(request.getDescription());
        section.setClassId(request.getClassId());
        section.setStartDate(request.getStartDate());
        section.setEndDate(request.getEndDate());
        section.setMaxStudents(request.getMaxStudents());
        
        Section saved = sectionRepository.save(section);
        return toDTO(saved, clientId);
    }
    
    public void deactivateSection(String sectionId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        Section section = sectionRepository.findByIdAndClientId(sectionId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Section not found: " + sectionId));
        
        section.setIsActive(false);
        sectionRepository.save(section);
    }

    @Transactional(readOnly = true)
    public long countSections(boolean activeOnly) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);

        return activeOnly
            ? sectionRepository.countByClientIdAndIsActive(clientId, true)
            : sectionRepository.countByClientId(clientId);
    }
    
    public BatchCreateSectionsResponse batchCreateSections(String classId, BatchCreateSectionsRequest request) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        // Validate class exists and belongs to client once upfront
        Class classEntity = classRepository.findByIdAndClientId(classId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Class not found: " + classId));
        
        if (!classEntity.getIsActive()) {
            throw new IllegalArgumentException("Class is not active");
        }
        
        // Pre-validate: check for duplicate section names within the batch
        Set<String> names = new HashSet<>();
        for (int i = 0; i < request.getSections().size(); i++) {
            String name = request.getSections().get(i).getName();
            if (!names.add(name)) {
                throw new IllegalArgumentException("Duplicate section name at index " + i + ": '" + name + "'");
            }
        }
        
        // Get existing section names for this class to check for duplicates
        List<Section> existingSections = sectionRepository.findByClientIdAndClassId(clientId, classId);
        Set<String> existingNames = existingSections.stream()
            .map(Section::getName)
            .collect(Collectors.toSet());
        
        // Check if any section names already exist
        for (int i = 0; i < request.getSections().size(); i++) {
            String name = request.getSections().get(i).getName();
            if (existingNames.contains(name)) {
                throw new IllegalArgumentException("Section with name '" + name + "' already exists at index " + i);
            }
        }
        
        // Create all sections
        List<Section> sections = new ArrayList<>();
        for (CreateSectionRequest sectionRequest : request.getSections()) {
            Section section = new Section();
            section.setId(UlidGenerator.nextUlid());
            section.setClientId(clientId);
            section.setName(sectionRequest.getName());
            section.setDescription(sectionRequest.getDescription());
            section.setClassId(classId);
            section.setStartDate(sectionRequest.getStartDate());
            section.setEndDate(sectionRequest.getEndDate());
            section.setMaxStudents(sectionRequest.getMaxStudents());
            section.setIsActive(true);
            
            sections.add(sectionRepository.save(section));
        }
        
        // Convert to DTOs
        List<SectionDTO> sectionDTOs = sections.stream()
            .map(section -> toDTO(section, clientId))
            .collect(Collectors.toList());
        
        return new BatchCreateSectionsResponse(
            sectionDTOs,
            sectionDTOs.size(),
            "Successfully created " + sectionDTOs.size() + " sections"
        );
    }
    
    public SectionProgressDTO getSectionProgress(String sectionId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        Section section = sectionRepository.findByIdAndClientId(sectionId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Section not found: " + sectionId));
        
        // Get all enrollments in this section (using batchId for backward compatibility)
        List<com.datagami.edudron.student.domain.Enrollment> enrollments = 
            enrollmentRepository.findByClientIdAndBatchId(clientId, sectionId);
        
        long totalStudents = enrollments.size();
        BigDecimal totalCompletion = BigDecimal.ZERO;
        long totalCompletedLectures = 0;
        int totalTimeSpent = 0;
        long totalLectures = 0;
        
        List<StudentProgressDTO> studentProgressList = enrollments.stream().map(enrollment -> {
            String studentId = enrollment.getStudentId();
            String courseId = enrollment.getCourseId();
            
            // Get student progress
            List<com.datagami.edudron.student.domain.Progress> lectureProgress = 
                progressRepository.findLectureProgressByClientIdAndStudentIdAndCourseId(clientId, studentId, courseId);
            
            long completedLectures = progressRepository.countCompletedLectures(clientId, studentId, courseId);
            long studentTotalLectures = lectureProgress.size();
            
            BigDecimal completionPercentage = studentTotalLectures > 0
                ? BigDecimal.valueOf(completedLectures)
                    .divide(BigDecimal.valueOf(studentTotalLectures), 2, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;
            
            int timeSpent = lectureProgress.stream()
                .mapToInt(p -> p.getTimeSpentSeconds() != null ? p.getTimeSpentSeconds() : 0)
                .sum();
            
            StudentProgressDTO studentProgress = new StudentProgressDTO();
            studentProgress.setStudentId(studentId);
            studentProgress.setCompletedLectures(completedLectures);
            studentProgress.setCompletionPercentage(completionPercentage);
            studentProgress.setTimeSpentSeconds(timeSpent);
            
            return studentProgress;
        }).collect(Collectors.toList());
        
        // Calculate section averages
        if (!studentProgressList.isEmpty()) {
            totalCompletion = studentProgressList.stream()
                .map(StudentProgressDTO::getCompletionPercentage)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(studentProgressList.size()), 2, RoundingMode.HALF_UP);
            
            totalCompletedLectures = studentProgressList.stream()
                .mapToLong(StudentProgressDTO::getCompletedLectures)
                .sum();
            
            totalTimeSpent = studentProgressList.stream()
                .mapToInt(StudentProgressDTO::getTimeSpentSeconds)
                .sum();
            
            // Get total lectures from first student (assuming all students in section have same course)
            if (!enrollments.isEmpty()) {
                String firstStudentId = enrollments.get(0).getStudentId();
                String courseId = enrollments.get(0).getCourseId();
                List<com.datagami.edudron.student.domain.Progress> firstStudentProgress = 
                    progressRepository.findLectureProgressByClientIdAndStudentIdAndCourseId(clientId, firstStudentId, courseId);
                totalLectures = firstStudentProgress.size();
            }
        }
        
        SectionProgressDTO sectionProgress = new SectionProgressDTO();
        sectionProgress.setSectionId(sectionId);
        sectionProgress.setSectionName(section.getName());
        sectionProgress.setClassId(section.getClassId());
        sectionProgress.setTotalStudents(totalStudents);
        sectionProgress.setTotalLectures(totalLectures);
        sectionProgress.setCompletedLectures(totalCompletedLectures);
        sectionProgress.setAverageCompletionPercentage(totalCompletion);
        sectionProgress.setTotalTimeSpentSeconds(totalTimeSpent);
        sectionProgress.setStudentProgress(studentProgressList);
        
        return sectionProgress;
    }
    
    private SectionDTO toDTO(Section section, UUID clientId) {
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
        
        // Get student count (using batchId for backward compatibility)
        long studentCount = sectionRepository.countStudentsInSection(clientId, section.getId());
        dto.setStudentCount(studentCount);
        
        return dto;
    }
}


