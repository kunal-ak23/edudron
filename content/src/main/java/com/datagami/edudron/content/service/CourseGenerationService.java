package com.datagami.edudron.content.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.common.UlidGenerator;
import com.datagami.edudron.content.domain.LearningObjective;
import com.datagami.edudron.content.domain.Lecture;
import com.datagami.edudron.content.domain.LectureContent;
import com.datagami.edudron.content.dto.CourseDTO;
import com.datagami.edudron.content.dto.CourseGenerationIndexDTO;
import com.datagami.edudron.content.dto.CourseRequirements;
import com.datagami.edudron.content.dto.GenerateCourseRequest;
import com.datagami.edudron.content.repo.LearningObjectiveRepository;
import com.datagami.edudron.content.repo.LectureContentRepository;
import com.datagami.edudron.content.service.FoundryAIService.LectureInfo;
import com.datagami.edudron.content.service.FoundryAIService.SectionInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class CourseGenerationService {
    
    private static final Logger logger = LoggerFactory.getLogger(CourseGenerationService.class);
    
    @Autowired
    private FoundryAIService foundryAIService;
    
    @Autowired
    private CourseService courseService;
    
    @Autowired
    private SectionService sectionService;
    
    @Autowired
    private LectureService lectureService;
    
    @Autowired
    private LearningObjectiveRepository learningObjectiveRepository;
    
    @Autowired
    private LectureContentRepository lectureContentRepository;
    
    @Autowired
    private CourseGenerationIndexService indexService;
    
    public CourseDTO generateCourseFromPrompt(GenerateCourseRequest request) {
        logger.info("Starting course generation from prompt");
        
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        // Step 1: Load reference content and writing format
        String referenceContent = loadReferenceContent(request.getReferenceIndexIds());
        String writingFormat = loadWritingFormat(request.getWritingFormatId(), request.getWritingFormat());
        
        // Step 2: Parse requirements from prompt (with reference content context)
        logger.info("Parsing course requirements from prompt");
        String enhancedPrompt = request.getPrompt();
        if (referenceContent != null && !referenceContent.isEmpty()) {
            enhancedPrompt = "Context:\n" + referenceContent + "\n\nUser Request:\n" + request.getPrompt();
        }
        CourseRequirements requirements = foundryAIService.parseCourseRequirements(enhancedPrompt);
        
        // Apply overrides from request
        if (request.getCategoryId() != null) {
            requirements.setCategoryId(request.getCategoryId());
        }
        if (request.getDifficultyLevel() != null) {
            requirements.setDifficultyLevel(request.getDifficultyLevel());
        }
        if (request.getLanguage() != null) {
            requirements.setLanguage(request.getLanguage());
        }
        if (request.getTags() != null) {
            requirements.setTags(request.getTags());
        }
        if (request.getCertificateEligible() != null) {
            requirements.setCertificateEligible(request.getCertificateEligible());
        }
        if (request.getMaxCompletionDays() != null) {
            requirements.setMaxCompletionDays(request.getMaxCompletionDays());
        }
        
        // Step 3: Create course entity
        logger.info("Creating course: {}", requirements.getTitle());
        com.datagami.edudron.content.dto.CreateCourseRequest createRequest = 
            new com.datagami.edudron.content.dto.CreateCourseRequest();
        createRequest.setTitle(requirements.getTitle());
        createRequest.setDescription(requirements.getDescription());
        createRequest.setDifficultyLevel(requirements.getDifficultyLevel());
        createRequest.setLanguage(requirements.getLanguage());
        createRequest.setCategoryId(requirements.getCategoryId());
        createRequest.setTags(requirements.getTags());
        createRequest.setCertificateEligible(requirements.getCertificateEligible() != null ? requirements.getCertificateEligible() : false);
        createRequest.setMaxCompletionDays(requirements.getMaxCompletionDays());
        createRequest.setIsFree(true); // Default to free for generated courses
        
        CourseDTO course = courseService.createCourse(createRequest);
        String courseId = course.getId();
        
        // Step 4: Generate course structure
        logger.info("Generating course structure");
        List<SectionInfo> sections = foundryAIService.generateCourseStructure(requirements);
        
        String courseContext = String.format("%s: %s", requirements.getTitle(), requirements.getDescription());
        
        // Step 5: Create sections and lectures
        int totalLectures = 0;
        List<String> sectionTitles = new ArrayList<>();
        
        for (SectionInfo sectionInfo : sections) {
            sectionTitles.add(sectionInfo.getTitle());
            
            // Generate section description if not provided
            String sectionDescription = sectionInfo.getDescription();
            if (sectionDescription == null || sectionDescription.isEmpty()) {
                sectionDescription = foundryAIService.generateSectionContent(sectionInfo.getTitle(), courseContext);
            }
            
            // Create section
            logger.info("Creating section: {}", sectionInfo.getTitle());
            com.datagami.edudron.content.dto.SectionDTO section = 
                sectionService.createSection(courseId, sectionInfo.getTitle(), sectionDescription);
            
            // Create lectures for this section
            if (sectionInfo.getLectures() != null) {
                for (LectureInfo lectureInfo : sectionInfo.getLectures()) {
                    logger.info("Creating lecture: {}", lectureInfo.getTitle());
                    
                    // Create lecture
                    com.datagami.edudron.content.dto.LectureDTO lecture = 
                        lectureService.createLecture(
                            section.getId(),
                            lectureInfo.getTitle(),
                            lectureInfo.getDescription(),
                            Lecture.ContentType.TEXT
                        );
                    
                    // Generate full lecture content
                    logger.info("Generating content for lecture: {}", lectureInfo.getTitle());
                    String lectureContent = foundryAIService.generateLectureContent(
                        lectureInfo.getTitle(),
                        sectionInfo.getTitle() + ": " + sectionDescription,
                        courseContext,
                        writingFormat,
                        referenceContent
                    );
                    
                    // Create LectureContent entity
                    LectureContent content = new LectureContent();
                    content.setId(UlidGenerator.nextUlid());
                    content.setClientId(clientId);
                    content.setLectureId(lecture.getId());
                    content.setContentType(LectureContent.ContentType.TEXT);
                    content.setTitle(lectureInfo.getTitle());
                    content.setTextContent(lectureContent);
                    content.setSequence(0);
                    
                    lectureContentRepository.save(content);
                    totalLectures++;
                }
            }
        }
        
        // Step 6: Generate learning objectives
        logger.info("Generating learning objectives");
        List<String> objectiveTexts = foundryAIService.generateLearningObjectives(
            requirements.getTitle(),
            requirements.getDescription(),
            sectionTitles
        );
        
        int sequence = 1;
        for (String objectiveText : objectiveTexts) {
            LearningObjective objective = new LearningObjective();
            objective.setId(UlidGenerator.nextUlid());
            objective.setClientId(clientId);
            objective.setCourseId(courseId);
            objective.setObjectiveText(objectiveText);
            objective.setSequence(sequence++);
            learningObjectiveRepository.save(objective);
        }
        
        // Step 7: Calculate and update course statistics
        // Note: Course statistics are calculated on publish, but we can set initial values
        logger.info("Course generation completed. Course ID: {}", courseId);
        
        // Return the complete course
        return courseService.getCourseById(courseId);
    }
    
    private String loadReferenceContent(List<String> indexIds) {
        if (indexIds == null || indexIds.isEmpty()) {
            return null;
        }
        
        StringBuilder contentBuilder = new StringBuilder();
        for (String indexId : indexIds) {
            try {
                CourseGenerationIndexDTO index = indexService.getIndexById(indexId);
                if (index.getIndexType() == com.datagami.edudron.content.domain.CourseGenerationIndex.IndexType.REFERENCE_CONTENT) {
                    if (index.getExtractedText() != null && !index.getExtractedText().isEmpty()) {
                        if (contentBuilder.length() > 0) {
                            contentBuilder.append("\n\n---\n\n");
                        }
                        contentBuilder.append("Reference: ").append(index.getTitle()).append("\n");
                        contentBuilder.append(index.getExtractedText());
                    }
                }
            } catch (Exception e) {
                logger.warn("Failed to load reference index {}: {}", indexId, e.getMessage());
            }
        }
        
        String content = contentBuilder.toString();
        // Limit total reference content to avoid token limits (50K chars)
        if (content.length() > 50000) {
            content = content.substring(0, 50000) + "... [truncated]";
        }
        return content.isEmpty() ? null : content;
    }
    
    private String loadWritingFormat(String writingFormatId, String directWritingFormat) {
        // Prefer direct writing format if provided
        if (directWritingFormat != null && !directWritingFormat.trim().isEmpty()) {
            return directWritingFormat;
        }
        
        // Otherwise, load from index
        if (writingFormatId != null && !writingFormatId.isEmpty()) {
            try {
                CourseGenerationIndexDTO index = indexService.getIndexById(writingFormatId);
                if (index.getIndexType() == com.datagami.edudron.content.domain.CourseGenerationIndex.IndexType.WRITING_FORMAT) {
                    return index.getWritingFormat();
                }
            } catch (Exception e) {
                logger.warn("Failed to load writing format index {}: {}", writingFormatId, e.getMessage());
            }
        }
        
        return null;
    }
}

