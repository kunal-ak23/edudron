package com.datagami.edudron.student.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.common.UlidGenerator;
import com.datagami.edudron.student.domain.Class;
import com.datagami.edudron.student.domain.Enrollment;
import com.datagami.edudron.student.domain.Institute;
import com.datagami.edudron.student.domain.Section;
import com.datagami.edudron.student.dto.CreateEnrollmentRequest;
import com.datagami.edudron.student.dto.EnrollmentDTO;
import com.datagami.edudron.student.repo.ClassRepository;
import com.datagami.edudron.student.repo.EnrollmentRepository;
import com.datagami.edudron.student.repo.InstituteRepository;
import com.datagami.edudron.student.repo.SectionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class EnrollmentService {
    
    @Autowired
    private EnrollmentRepository enrollmentRepository;
    
    @Autowired
    private SectionRepository sectionRepository;
    
    @Autowired
    private ClassRepository classRepository;
    
    @Autowired
    private InstituteRepository instituteRepository;
    
    public EnrollmentDTO enrollStudent(String studentId, CreateEnrollmentRequest request) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        // Check if already enrolled
        if (enrollmentRepository.existsByClientIdAndStudentIdAndCourseId(clientId, studentId, request.getCourseId())) {
            throw new IllegalArgumentException("Student is already enrolled in this course");
        }
        
        String instituteId = null;
        String classId = null;
        String sectionId = null;
        
        // Validate hierarchy if provided
        if (request.getBatchId() != null && !request.getBatchId().isBlank()) {
            // batchId now represents sectionId
            final String finalSectionId = request.getBatchId();
            Section section = sectionRepository.findByIdAndClientId(finalSectionId, clientId)
                .orElseThrow(() -> new IllegalArgumentException("Section not found: " + finalSectionId));
            
            if (!section.getIsActive()) {
                throw new IllegalArgumentException("Section is not active");
            }
            
            // Get class from section
            final String finalClassId = section.getClassId();
            Class classEntity = classRepository.findByIdAndClientId(finalClassId, clientId)
                .orElseThrow(() -> new IllegalArgumentException("Class not found: " + finalClassId));
            
            if (!classEntity.getIsActive()) {
                throw new IllegalArgumentException("Class is not active");
            }
            
            // Get institute from class
            final String finalInstituteId = classEntity.getInstituteId();
            Institute institute = instituteRepository.findByIdAndClientId(finalInstituteId, clientId)
                .orElseThrow(() -> new IllegalArgumentException("Institute not found: " + finalInstituteId));
            
            if (!institute.getIsActive()) {
                throw new IllegalArgumentException("Institute is not active");
            }
            
            // Check section capacity
            if (section.getMaxStudents() != null) {
                long currentCount = sectionRepository.countStudentsInSection(clientId, section.getId());
                if (currentCount >= section.getMaxStudents()) {
                    throw new IllegalArgumentException("Section is full");
                }
            }
            
            // Assign to outer variables after validation
            sectionId = finalSectionId;
            classId = finalClassId;
            instituteId = finalInstituteId;
        } else if (request.getClassId() != null && !request.getClassId().isBlank()) {
            // If classId is provided but no sectionId, validate class
            final String finalClassId = request.getClassId();
            Class classEntity = classRepository.findByIdAndClientId(finalClassId, clientId)
                .orElseThrow(() -> new IllegalArgumentException("Class not found: " + finalClassId));
            
            if (!classEntity.getIsActive()) {
                throw new IllegalArgumentException("Class is not active");
            }
            
            final String finalInstituteId = classEntity.getInstituteId();
            Institute institute = instituteRepository.findByIdAndClientId(finalInstituteId, clientId)
                .orElseThrow(() -> new IllegalArgumentException("Institute not found: " + finalInstituteId));
            
            if (!institute.getIsActive()) {
                throw new IllegalArgumentException("Institute is not active");
            }
            
            // Assign to outer variables after validation
            classId = finalClassId;
            instituteId = finalInstituteId;
        } else if (request.getInstituteId() != null && !request.getInstituteId().isBlank()) {
            // If only instituteId is provided, validate it
            final String finalInstituteId = request.getInstituteId();
            Institute institute = instituteRepository.findByIdAndClientId(finalInstituteId, clientId)
                .orElseThrow(() -> new IllegalArgumentException("Institute not found: " + finalInstituteId));
            
            if (!institute.getIsActive()) {
                throw new IllegalArgumentException("Institute is not active");
            }
            
            // Assign to outer variable after validation
            instituteId = finalInstituteId;
        }
        
        Enrollment enrollment = new Enrollment();
        enrollment.setId(UlidGenerator.nextUlid());
        enrollment.setClientId(clientId);
        enrollment.setStudentId(studentId);
        enrollment.setCourseId(request.getCourseId());
        enrollment.setBatchId(sectionId); // Keep batchId for backward compatibility
        enrollment.setInstituteId(instituteId);
        enrollment.setClassId(classId);
        
        Enrollment saved = enrollmentRepository.save(enrollment);
        return toDTO(saved);
    }
    
    public EnrollmentDTO getEnrollment(String enrollmentId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
            .orElseThrow(() -> new IllegalArgumentException("Enrollment not found: " + enrollmentId));
        
        if (!enrollment.getClientId().equals(clientId)) {
            throw new IllegalArgumentException("Enrollment not found: " + enrollmentId);
        }
        
        return toDTO(enrollment);
    }
    
    public List<EnrollmentDTO> getStudentEnrollments(String studentId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        List<Enrollment> enrollments = enrollmentRepository.findByClientIdAndStudentId(clientId, studentId);
        return enrollments.stream().map(this::toDTO).collect(Collectors.toList());
    }
    
    public Page<EnrollmentDTO> getStudentEnrollments(String studentId, Pageable pageable) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        Page<Enrollment> enrollments = enrollmentRepository.findByClientIdAndStudentId(clientId, studentId, pageable);
        return enrollments.map(this::toDTO);
    }
    
    public List<EnrollmentDTO> getCourseEnrollments(String courseId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        List<Enrollment> enrollments = enrollmentRepository.findByClientIdAndCourseId(clientId, courseId);
        return enrollments.stream().map(this::toDTO).collect(Collectors.toList());
    }
    
    public boolean isEnrolled(String studentId, String courseId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        return enrollmentRepository.existsByClientIdAndStudentIdAndCourseId(clientId, studentId, courseId);
    }
    
    public void unenroll(String studentId, String courseId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        Enrollment enrollment = enrollmentRepository.findByClientIdAndStudentIdAndCourseId(clientId, studentId, courseId)
            .orElseThrow(() -> new IllegalArgumentException("Enrollment not found"));
        
        enrollmentRepository.delete(enrollment);
    }
    
    private EnrollmentDTO toDTO(Enrollment enrollment) {
        EnrollmentDTO dto = new EnrollmentDTO();
        dto.setId(enrollment.getId());
        dto.setClientId(enrollment.getClientId());
        dto.setStudentId(enrollment.getStudentId());
        dto.setCourseId(enrollment.getCourseId());
        dto.setBatchId(enrollment.getBatchId()); // Keep for backward compatibility
        dto.setInstituteId(enrollment.getInstituteId());
        dto.setClassId(enrollment.getClassId());
        dto.setEnrolledAt(enrollment.getEnrolledAt());
        return dto;
    }
}

