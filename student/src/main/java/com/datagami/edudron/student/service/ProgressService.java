package com.datagami.edudron.student.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.common.UlidGenerator;
import com.datagami.edudron.student.domain.Enrollment;
import com.datagami.edudron.student.domain.Progress;
import com.datagami.edudron.student.dto.CourseProgressDTO;
import com.datagami.edudron.student.dto.ProgressDTO;
import com.datagami.edudron.student.dto.UpdateProgressRequest;
import com.datagami.edudron.student.repo.EnrollmentRepository;
import com.datagami.edudron.student.repo.ProgressRepository;
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
public class ProgressService {
    
    @Autowired
    private ProgressRepository progressRepository;
    
    @Autowired
    private EnrollmentRepository enrollmentRepository;
    
    public ProgressDTO updateProgress(String studentId, String courseId, UpdateProgressRequest request) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        // Verify enrollment
        Enrollment enrollment = enrollmentRepository.findByClientIdAndStudentIdAndCourseId(clientId, studentId, courseId)
            .orElseThrow(() -> new IllegalArgumentException("Student is not enrolled in this course"));
        
        Progress progress;
        
        if (request.getLectureId() != null) {
            // Lecture progress
            progress = progressRepository.findByClientIdAndStudentIdAndLectureId(clientId, studentId, request.getLectureId())
                .orElseGet(() -> {
                    Progress p = new Progress();
                    p.setId(UlidGenerator.nextUlid());
                    p.setClientId(clientId);
                    p.setEnrollmentId(enrollment.getId());
                    p.setStudentId(studentId);
                    p.setCourseId(courseId);
                    p.setLectureId(request.getLectureId());
                    return p;
                });
        } else if (request.getSectionId() != null) {
            // Section progress
            List<Progress> sectionProgress = progressRepository.findSectionProgressByClientIdAndStudentIdAndCourseId(
                clientId, studentId, courseId);
            progress = sectionProgress.stream()
                .filter(p -> request.getSectionId().equals(p.getSectionId()))
                .findFirst()
                .orElseGet(() -> {
                    Progress p = new Progress();
                    p.setId(UlidGenerator.nextUlid());
                    p.setClientId(clientId);
                    p.setEnrollmentId(enrollment.getId());
                    p.setStudentId(studentId);
                    p.setCourseId(courseId);
                    p.setSectionId(request.getSectionId());
                    return p;
                });
        } else {
            throw new IllegalArgumentException("Either lectureId or sectionId must be provided");
        }
        
        // Update progress
        if (request.getIsCompleted() != null) {
            progress.setIsCompleted(request.getIsCompleted());
        }
        if (request.getProgressPercentage() != null) {
            progress.setProgressPercentage(request.getProgressPercentage());
        }
        if (request.getTimeSpentSeconds() != null) {
            progress.setTimeSpentSeconds(progress.getTimeSpentSeconds() + request.getTimeSpentSeconds());
        }
        progress.setLastAccessedAt(java.time.OffsetDateTime.now());
        
        Progress saved = progressRepository.save(progress);
        return toDTO(saved);
    }
    
    public CourseProgressDTO getCourseProgress(String studentId, String courseId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        // Verify enrollment
        Enrollment enrollment = enrollmentRepository.findByClientIdAndStudentIdAndCourseId(clientId, studentId, courseId)
            .orElseThrow(() -> new IllegalArgumentException("Student is not enrolled in this course"));
        
        List<Progress> lectureProgress = progressRepository.findLectureProgressByClientIdAndStudentIdAndCourseId(
            clientId, studentId, courseId);
        List<Progress> sectionProgress = progressRepository.findSectionProgressByClientIdAndStudentIdAndCourseId(
            clientId, studentId, courseId);
        
        long completedLectures = progressRepository.countCompletedLectures(clientId, studentId, courseId);
        long totalLectures = lectureProgress.size();
        
        BigDecimal completionPercentage = totalLectures > 0
            ? BigDecimal.valueOf(completedLectures)
                .divide(BigDecimal.valueOf(totalLectures), 2, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
            : BigDecimal.ZERO;
        
        int totalTimeSpent = lectureProgress.stream()
            .mapToInt(p -> p.getTimeSpentSeconds() != null ? p.getTimeSpentSeconds() : 0)
            .sum();
        
        CourseProgressDTO dto = new CourseProgressDTO();
        dto.setEnrollmentId(enrollment.getId());
        dto.setCourseId(courseId);
        dto.setTotalLectures(totalLectures);
        dto.setCompletedLectures(completedLectures);
        dto.setCompletionPercentage(completionPercentage);
        dto.setTotalTimeSpentSeconds(totalTimeSpent);
        dto.setLectureProgress(lectureProgress.stream().map(this::toDTO).collect(Collectors.toList()));
        dto.setSectionProgress(sectionProgress.stream().map(this::toDTO).collect(Collectors.toList()));
        
        return dto;
    }
    
    public List<ProgressDTO> getLectureProgress(String studentId, String courseId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        List<Progress> progress = progressRepository.findLectureProgressByClientIdAndStudentIdAndCourseId(
            clientId, studentId, courseId);
        return progress.stream().map(this::toDTO).collect(Collectors.toList());
    }
    
    private ProgressDTO toDTO(Progress progress) {
        ProgressDTO dto = new ProgressDTO();
        dto.setId(progress.getId());
        dto.setClientId(progress.getClientId());
        dto.setEnrollmentId(progress.getEnrollmentId());
        dto.setStudentId(progress.getStudentId());
        dto.setCourseId(progress.getCourseId());
        dto.setSectionId(progress.getSectionId());
        dto.setLectureId(progress.getLectureId());
        dto.setIsCompleted(progress.getIsCompleted());
        dto.setProgressPercentage(progress.getProgressPercentage());
        dto.setTimeSpentSeconds(progress.getTimeSpentSeconds());
        dto.setLastAccessedAt(progress.getLastAccessedAt());
        dto.setCompletedAt(progress.getCompletedAt());
        dto.setCreatedAt(progress.getCreatedAt());
        dto.setUpdatedAt(progress.getUpdatedAt());
        return dto;
    }
}

