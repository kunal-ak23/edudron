package com.datagami.edudron.content.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.common.UlidGenerator;
import com.datagami.edudron.content.domain.Assessment;
import com.datagami.edudron.content.repo.AssessmentRepository;
import com.datagami.edudron.content.repo.CourseRepository;
import com.datagami.edudron.content.repo.LectureRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class AssessmentService {
    
    @Autowired
    private AssessmentRepository assessmentRepository;
    
    @Autowired
    private CourseRepository courseRepository;
    
    @Autowired
    private LectureRepository lectureRepository;
    
    public Assessment createAssessment(String courseId, String lectureId, Assessment.AssessmentType assessmentType,
                                      String title, String description, String instructions) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        // Verify course exists
        courseRepository.findByIdAndClientId(courseId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Course not found: " + courseId));
        
        // Verify lecture exists if provided
        if (lectureId != null) {
            lectureRepository.findByIdAndClientId(lectureId, clientId)
                .orElseThrow(() -> new IllegalArgumentException("Lecture not found: " + lectureId));
        }
        
        // Get next sequence
        Integer maxSequence = assessmentRepository.findMaxSequenceByCourseIdAndClientId(courseId, clientId);
        int nextSequence = (maxSequence != null ? maxSequence : 0) + 1;
        
        Assessment assessment = new Assessment();
        assessment.setId(UlidGenerator.nextUlid());
        assessment.setClientId(clientId);
        assessment.setCourseId(courseId);
        assessment.setLectureId(lectureId);
        assessment.setAssessmentType(assessmentType);
        assessment.setTitle(title);
        assessment.setDescription(description);
        assessment.setInstructions(instructions);
        assessment.setSequence(nextSequence);
        
        Assessment saved = assessmentRepository.save(assessment);
        return saved;
    }
    
    public Assessment getAssessmentById(String id) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        return assessmentRepository.findByIdAndClientId(id, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Assessment not found: " + id));
    }
    
    public List<Assessment> getAssessmentsByCourse(String courseId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        return assessmentRepository.findByCourseIdAndClientIdOrderBySequenceAsc(courseId, clientId);
    }
    
    public List<Assessment> getAssessmentsByLecture(String lectureId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        return assessmentRepository.findByLectureIdAndClientIdOrderBySequenceAsc(lectureId, clientId);
    }
    
    public void deleteAssessment(String id) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        Assessment assessment = assessmentRepository.findByIdAndClientId(id, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Assessment not found: " + id));
        
        assessmentRepository.delete(assessment);
    }
}


