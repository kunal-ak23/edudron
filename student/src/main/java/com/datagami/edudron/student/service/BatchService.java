package com.datagami.edudron.student.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.common.UlidGenerator;
import com.datagami.edudron.student.client.IdentityUserClient;
import com.datagami.edudron.student.domain.Batch;
import com.datagami.edudron.student.dto.BatchDTO;
import com.datagami.edudron.student.dto.BatchProgressDTO;
import com.datagami.edudron.student.dto.CoordinatorResponse;
import com.datagami.edudron.student.dto.CreateBatchRequest;
import com.datagami.edudron.student.dto.StudentProgressDTO;
import com.datagami.edudron.student.repo.BatchRepository;
import com.datagami.edudron.student.repo.EnrollmentRepository;
import com.datagami.edudron.student.repo.ProgressRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class BatchService {
    
    @Autowired
    private BatchRepository batchRepository;
    
    @Autowired
    private EnrollmentRepository enrollmentRepository;
    
    @Autowired
    private ProgressRepository progressRepository;

    @Autowired
    private StudentAuditService auditService;

    @Autowired
    private IdentityUserClient identityUserClient;
    
    public BatchDTO createBatch(CreateBatchRequest request) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        Batch batch = new Batch();
        batch.setId(UlidGenerator.nextUlid());
        batch.setClientId(clientId);
        batch.setName(request.getName());
        batch.setDescription(request.getDescription());
        batch.setCourseId(request.getCourseId());
        batch.setStartDate(request.getStartDate());
        batch.setEndDate(request.getEndDate());
        batch.setMaxStudents(request.getMaxStudents());
        batch.setIsActive(true);
        
        Batch saved = batchRepository.save(batch);
        auditService.logCrud(clientId, "CREATE", "Batch", saved.getId(), null, null,
            java.util.Map.of("name", saved.getName(), "courseId", saved.getCourseId() != null ? saved.getCourseId() : ""));
        return toDTO(saved, clientId);
    }
    
    public BatchDTO getBatch(String batchId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        Batch batch = batchRepository.findByIdAndClientId(batchId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Batch not found: " + batchId));
        
        return toDTO(batch, clientId);
    }
    
    public List<BatchDTO> getBatchesByCourse(String courseId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        List<Batch> batches = batchRepository.findByClientIdAndCourseId(clientId, courseId);
        return batches.stream()
            .map(batch -> toDTO(batch, clientId))
            .collect(Collectors.toList());
    }
    
    public List<BatchDTO> getActiveBatchesByCourse(String courseId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        List<Batch> batches = batchRepository.findByClientIdAndCourseIdAndIsActive(clientId, courseId, true);
        return batches.stream()
            .map(batch -> toDTO(batch, clientId))
            .collect(Collectors.toList());
    }
    
    public BatchDTO updateBatch(String batchId, CreateBatchRequest request) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        Batch batch = batchRepository.findByIdAndClientId(batchId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Batch not found: " + batchId));
        
        batch.setName(request.getName());
        batch.setDescription(request.getDescription());
        batch.setStartDate(request.getStartDate());
        batch.setEndDate(request.getEndDate());
        batch.setMaxStudents(request.getMaxStudents());
        
        Batch saved = batchRepository.save(batch);
        auditService.logCrud(clientId, "UPDATE", "Batch", batchId, null, null,
            java.util.Map.of("name", saved.getName(), "courseId", saved.getCourseId() != null ? saved.getCourseId() : ""));
        return toDTO(saved, clientId);
    }
    
    public void deactivateBatch(String batchId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        Batch batch = batchRepository.findByIdAndClientId(batchId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Batch not found: " + batchId));
        
        batch.setIsActive(false);
        batchRepository.save(batch);
        auditService.logCrud(clientId, "UPDATE", "Batch", batchId, null, null,
            java.util.Map.of("action", "DEACTIVATE", "name", batch.getName()));
    }
    
    public BatchProgressDTO getBatchProgress(String batchId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        Batch batch = batchRepository.findByIdAndClientId(batchId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Batch not found: " + batchId));
        
        // Get all enrollments in this batch
        List<com.datagami.edudron.student.domain.Enrollment> enrollments = 
            enrollmentRepository.findByClientIdAndBatchId(clientId, batchId);
        
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
        
        // Calculate batch averages
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
            
            // Get total lectures from first student (assuming all students in batch have same course)
            if (!enrollments.isEmpty()) {
                String firstStudentId = enrollments.get(0).getStudentId();
                String courseId = enrollments.get(0).getCourseId();
                List<com.datagami.edudron.student.domain.Progress> firstStudentProgress = 
                    progressRepository.findLectureProgressByClientIdAndStudentIdAndCourseId(clientId, firstStudentId, courseId);
                totalLectures = firstStudentProgress.size();
            }
        }
        
        BatchProgressDTO batchProgress = new BatchProgressDTO();
        batchProgress.setBatchId(batchId);
        batchProgress.setBatchName(batch.getName());
        batchProgress.setCourseId(batch.getCourseId());
        batchProgress.setTotalStudents(totalStudents);
        batchProgress.setTotalLectures(totalLectures);
        batchProgress.setCompletedLectures(totalCompletedLectures);
        batchProgress.setAverageCompletionPercentage(totalCompletion);
        batchProgress.setTotalTimeSpentSeconds(totalTimeSpent);
        batchProgress.setStudentProgress(studentProgressList);
        
        return batchProgress;
    }
    
    public CoordinatorResponse assignBatchCoordinator(String batchId, String coordinatorUserId, String actorEmail) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);

        Batch batch = batchRepository.findByIdAndClientId(batchId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Batch not found: " + batchId));

        // Validate the coordinator user exists and has INSTRUCTOR role
        JsonNode userNode = identityUserClient.getUser(coordinatorUserId);
        if (userNode == null) {
            throw new IllegalArgumentException("User not found: " + coordinatorUserId);
        }

        String role = userNode.has("role") ? userNode.get("role").asText() : null;
        if (!"INSTRUCTOR".equals(role)) {
            throw new IllegalArgumentException("User " + coordinatorUserId + " does not have INSTRUCTOR role");
        }

        Boolean active = userNode.has("active") ? userNode.get("active").asBoolean() : null;
        if (active == null || !active) {
            throw new IllegalArgumentException("User " + coordinatorUserId + " is not active");
        }

        String previousCoordinator = batch.getCoordinatorUserId();
        String operation = (previousCoordinator == null) ? "CREATE" : "UPDATE";

        batch.setCoordinatorUserId(coordinatorUserId);
        batchRepository.save(batch);

        String coordinatorName = userNode.has("name") ? userNode.get("name").asText() : null;
        String coordinatorEmail = userNode.has("email") ? userNode.get("email").asText() : null;

        java.util.Map<String, Object> meta = new java.util.HashMap<>();
        meta.put("batchName", batch.getName());
        meta.put("coordinatorUserId", coordinatorUserId);
        if (coordinatorName != null) meta.put("coordinatorName", coordinatorName);
        if (previousCoordinator != null) meta.put("previousCoordinatorUserId", previousCoordinator);
        auditService.logCrud(clientId, operation, "BatchCoordinator", batchId, null, actorEmail, meta);

        return new CoordinatorResponse(coordinatorUserId, coordinatorName, coordinatorEmail);
    }

    public void removeBatchCoordinator(String batchId, String actorEmail) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);

        Batch batch = batchRepository.findByIdAndClientId(batchId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Batch not found: " + batchId));

        if (batch.getCoordinatorUserId() == null) {
            throw new IllegalArgumentException("Batch " + batchId + " does not have a coordinator assigned");
        }

        String previousCoordinator = batch.getCoordinatorUserId();
        batch.setCoordinatorUserId(null);
        batchRepository.save(batch);

        auditService.logCrud(clientId, "DELETE", "BatchCoordinator", batchId, null, actorEmail,
            java.util.Map.of("batchName", batch.getName(), "removedCoordinatorUserId", previousCoordinator));
    }

    @Transactional(readOnly = true)
    public CoordinatorResponse getBatchCoordinator(String batchId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);

        Batch batch = batchRepository.findByIdAndClientId(batchId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Batch not found: " + batchId));

        if (batch.getCoordinatorUserId() == null) {
            return null;
        }

        JsonNode userNode = identityUserClient.getUser(batch.getCoordinatorUserId());
        String coordinatorName = null;
        String coordinatorEmail = null;
        if (userNode != null) {
            coordinatorName = userNode.has("name") ? userNode.get("name").asText() : null;
            coordinatorEmail = userNode.has("email") ? userNode.get("email").asText() : null;
        }

        return new CoordinatorResponse(batch.getCoordinatorUserId(), coordinatorName, coordinatorEmail);
    }

    private BatchDTO toDTO(Batch batch, UUID clientId) {
        BatchDTO dto = new BatchDTO();
        dto.setId(batch.getId());
        dto.setClientId(batch.getClientId());
        dto.setName(batch.getName());
        dto.setDescription(batch.getDescription());
        dto.setCourseId(batch.getCourseId());
        dto.setStartDate(batch.getStartDate());
        dto.setEndDate(batch.getEndDate());
        dto.setMaxStudents(batch.getMaxStudents());
        dto.setIsActive(batch.getIsActive());
        dto.setCoordinatorUserId(batch.getCoordinatorUserId());
        dto.setCreatedAt(batch.getCreatedAt());
        dto.setUpdatedAt(batch.getUpdatedAt());
        
        // Get student count
        long studentCount = batchRepository.countStudentsInBatch(clientId, batch.getId());
        dto.setStudentCount(studentCount);
        
        return dto;
    }
}


