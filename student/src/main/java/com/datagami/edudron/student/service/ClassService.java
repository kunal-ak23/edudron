package com.datagami.edudron.student.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.common.UlidGenerator;
import com.datagami.edudron.student.domain.Class;
import com.datagami.edudron.student.domain.Institute;
import com.datagami.edudron.student.dto.ClassDTO;
import com.datagami.edudron.student.dto.CreateClassRequest;
import com.datagami.edudron.student.repo.ClassRepository;
import com.datagami.edudron.student.repo.InstituteRepository;
import com.datagami.edudron.student.repo.SectionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
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
    
    public List<ClassDTO> getClassesByInstitute(String instituteId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        // Validate institute belongs to client
        Institute institute = instituteRepository.findByIdAndClientId(instituteId, clientId)
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
        Institute institute = instituteRepository.findByIdAndClientId(instituteId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Institute not found: " + instituteId));
        
        List<Class> classes = classRepository.findByInstituteIdAndIsActive(instituteId, true);
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
        
        // Get section count
        String clientIdStr = TenantContext.getClientId();
        UUID clientId = clientIdStr != null ? UUID.fromString(clientIdStr) : classEntity.getClientId();
        long sectionCount = sectionRepository.findByClientIdAndClassId(clientId, classEntity.getId()).size();
        dto.setSectionCount(sectionCount);
        
        return dto;
    }
}

