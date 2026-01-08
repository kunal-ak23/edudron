package com.datagami.edudron.student.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.common.UlidGenerator;
import com.datagami.edudron.student.domain.Feedback;
import com.datagami.edudron.student.dto.CreateFeedbackRequest;
import com.datagami.edudron.student.dto.FeedbackDTO;
import com.datagami.edudron.student.repo.FeedbackRepository;
import com.datagami.edudron.student.util.UserUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class FeedbackService {
    
    @Autowired
    private FeedbackRepository feedbackRepository;
    
    public FeedbackDTO createOrUpdateFeedback(String studentId, CreateFeedbackRequest request) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        // Check if feedback already exists for this student and lecture
        Optional<Feedback> existing = feedbackRepository.findByClientIdAndStudentIdAndLectureId(
            clientId, studentId, request.getLectureId());
        
        Feedback feedback;
        if (existing.isPresent()) {
            // Update existing feedback
            feedback = existing.get();
            feedback.setType(request.getType());
            feedback.setComment(request.getComment());
        } else {
            // Create new feedback
            feedback = new Feedback();
            feedback.setId(UlidGenerator.nextUlid());
            feedback.setClientId(clientId);
            feedback.setStudentId(studentId);
            feedback.setLectureId(request.getLectureId());
            feedback.setCourseId(request.getCourseId());
            feedback.setType(request.getType());
            feedback.setComment(request.getComment());
        }
        
        Feedback saved = feedbackRepository.save(feedback);
        return toDTO(saved);
    }
    
    public FeedbackDTO getFeedback(String studentId, String lectureId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        Optional<Feedback> feedback = feedbackRepository.findByClientIdAndStudentIdAndLectureId(
            clientId, studentId, lectureId);
        
        return feedback.map(this::toDTO).orElse(null);
    }
    
    public List<FeedbackDTO> getFeedbackByLecture(String lectureId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        List<Feedback> feedbacks = feedbackRepository.findByClientIdAndLectureId(clientId, lectureId);
        return feedbacks.stream().map(this::toDTO).collect(Collectors.toList());
    }
    
    public List<FeedbackDTO> getFeedbackByCourse(String studentId, String courseId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        List<Feedback> feedbacks = feedbackRepository.findByClientIdAndStudentIdAndCourseId(
            clientId, studentId, courseId);
        return feedbacks.stream().map(this::toDTO).collect(Collectors.toList());
    }
    
    public void deleteFeedback(String studentId, String lectureId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        Optional<Feedback> feedback = feedbackRepository.findByClientIdAndStudentIdAndLectureId(
            clientId, studentId, lectureId);
        
        if (feedback.isPresent()) {
            feedbackRepository.delete(feedback.get());
        }
    }
    
    private FeedbackDTO toDTO(Feedback feedback) {
        FeedbackDTO dto = new FeedbackDTO();
        dto.setId(feedback.getId());
        dto.setClientId(feedback.getClientId());
        dto.setStudentId(feedback.getStudentId());
        dto.setLectureId(feedback.getLectureId());
        dto.setCourseId(feedback.getCourseId());
        dto.setType(feedback.getType());
        dto.setComment(feedback.getComment());
        dto.setCreatedAt(feedback.getCreatedAt());
        dto.setUpdatedAt(feedback.getUpdatedAt());
        return dto;
    }
}

