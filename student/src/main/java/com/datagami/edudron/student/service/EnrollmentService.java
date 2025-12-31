package com.datagami.edudron.student.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.common.UlidGenerator;
import com.datagami.edudron.student.domain.Batch;
import com.datagami.edudron.student.domain.Enrollment;
import com.datagami.edudron.student.dto.CreateEnrollmentRequest;
import com.datagami.edudron.student.dto.EnrollmentDTO;
import com.datagami.edudron.student.repo.BatchRepository;
import com.datagami.edudron.student.repo.EnrollmentRepository;
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
    private BatchRepository batchRepository;
    
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
        
        // Validate batch if provided
        if (request.getBatchId() != null && !request.getBatchId().isBlank()) {
            Batch batch = batchRepository.findByIdAndClientId(request.getBatchId(), clientId)
                .orElseThrow(() -> new IllegalArgumentException("Batch not found: " + request.getBatchId()));
            
            if (!batch.getCourseId().equals(request.getCourseId())) {
                throw new IllegalArgumentException("Batch does not belong to the specified course");
            }
            
            if (!batch.getIsActive()) {
                throw new IllegalArgumentException("Batch is not active");
            }
            
            // Check batch capacity
            if (batch.getMaxStudents() != null) {
                long currentCount = batchRepository.countStudentsInBatch(clientId, batch.getId());
                if (currentCount >= batch.getMaxStudents()) {
                    throw new IllegalArgumentException("Batch is full");
                }
            }
        }
        
        Enrollment enrollment = new Enrollment();
        enrollment.setId(UlidGenerator.nextUlid());
        enrollment.setClientId(clientId);
        enrollment.setStudentId(studentId);
        enrollment.setCourseId(request.getCourseId());
        enrollment.setBatchId(request.getBatchId());
        
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
        dto.setBatchId(enrollment.getBatchId());
        dto.setEnrolledAt(enrollment.getEnrolledAt());
        return dto;
    }
}

