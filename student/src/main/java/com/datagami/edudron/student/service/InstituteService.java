package com.datagami.edudron.student.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.common.UlidGenerator;
import com.datagami.edudron.student.domain.Institute;
import com.datagami.edudron.student.dto.CreateInstituteRequest;
import com.datagami.edudron.student.dto.InstituteDTO;
import com.datagami.edudron.student.repo.ClassRepository;
import com.datagami.edudron.student.repo.InstituteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class InstituteService {
    
    @Autowired
    private InstituteRepository instituteRepository;
    
    @Autowired
    private ClassRepository classRepository;
    
    public InstituteDTO createInstitute(CreateInstituteRequest request) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        // Check if code already exists for this client
        if (instituteRepository.existsByClientIdAndCode(clientId, request.getCode())) {
            throw new IllegalArgumentException("Institute with code '" + request.getCode() + "' already exists");
        }
        
        Institute institute = new Institute();
        institute.setId(UlidGenerator.nextUlid());
        institute.setClientId(clientId);
        institute.setName(request.getName());
        institute.setCode(request.getCode());
        institute.setType(request.getType());
        institute.setAddress(request.getAddress());
        institute.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);
        
        Institute saved = instituteRepository.save(institute);
        return toDTO(saved, clientId);
    }
    
    public InstituteDTO getInstitute(String instituteId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        Institute institute = instituteRepository.findByIdAndClientId(instituteId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Institute not found: " + instituteId));
        
        return toDTO(institute, clientId);
    }
    
    public List<InstituteDTO> getAllInstitutes() {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        List<Institute> institutes = instituteRepository.findByClientId(clientId);
        return institutes.stream()
            .map(institute -> toDTO(institute, clientId))
            .collect(Collectors.toList());
    }
    
    public List<InstituteDTO> getActiveInstitutes() {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        List<Institute> institutes = instituteRepository.findByClientIdAndIsActive(clientId, true);
        return institutes.stream()
            .map(institute -> toDTO(institute, clientId))
            .collect(Collectors.toList());
    }
    
    public InstituteDTO updateInstitute(String instituteId, CreateInstituteRequest request) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        Institute institute = instituteRepository.findByIdAndClientId(instituteId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Institute not found: " + instituteId));
        
        // Check if code is being changed and if new code already exists
        if (!institute.getCode().equals(request.getCode())) {
            if (instituteRepository.existsByClientIdAndCode(clientId, request.getCode())) {
                throw new IllegalArgumentException("Institute with code '" + request.getCode() + "' already exists");
            }
        }
        
        institute.setName(request.getName());
        institute.setCode(request.getCode());
        institute.setType(request.getType());
        institute.setAddress(request.getAddress());
        if (request.getIsActive() != null) {
            institute.setIsActive(request.getIsActive());
        }
        
        Institute saved = instituteRepository.save(institute);
        return toDTO(saved, clientId);
    }
    
    public void deactivateInstitute(String instituteId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        Institute institute = instituteRepository.findByIdAndClientId(instituteId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Institute not found: " + instituteId));
        
        institute.setIsActive(false);
        instituteRepository.save(institute);
    }
    
    private InstituteDTO toDTO(Institute institute, UUID clientId) {
        InstituteDTO dto = new InstituteDTO();
        dto.setId(institute.getId());
        dto.setClientId(institute.getClientId());
        dto.setName(institute.getName());
        dto.setCode(institute.getCode());
        dto.setType(institute.getType());
        dto.setAddress(institute.getAddress());
        dto.setIsActive(institute.getIsActive());
        dto.setCreatedAt(institute.getCreatedAt());
        dto.setUpdatedAt(institute.getUpdatedAt());
        
        // Get class count
        long classCount = classRepository.countByInstituteId(institute.getId());
        dto.setClassCount(classCount);
        
        return dto;
    }
}

