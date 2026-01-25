package com.datagami.edudron.content.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.common.UlidGenerator;
import com.datagami.edudron.content.domain.*;
import com.datagami.edudron.content.dto.CourseCopyJobData;
import com.datagami.edudron.content.dto.CourseCopyResultDTO;
import com.datagami.edudron.content.repo.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.BiConsumer;

@Service
@Transactional
public class CourseCopyService {
    
    private static final Logger logger = LoggerFactory.getLogger(CourseCopyService.class);
    
    @Autowired
    private CourseRepository courseRepository;
    
    @Autowired
    private SectionRepository sectionRepository;
    
    @Autowired
    private LectureRepository lectureRepository;
    
    @Autowired
    private LectureContentRepository lectureContentRepository;
    
    @Autowired
    private SubLessonRepository subLessonRepository;
    
    @Autowired
    private AssessmentRepository assessmentRepository;
    
    @Autowired
    private QuizQuestionRepository quizQuestionRepository;
    
    @Autowired
    private QuizOptionRepository quizOptionRepository;
    
    @Autowired
    private CourseResourceRepository courseResourceRepository;
    
    @Autowired
    private LearningObjectiveRepository learningObjectiveRepository;
    
    @Autowired
    private CourseCategoryRepository courseCategoryRepository;
    
    @Autowired
    private CourseTagRepository courseTagRepository;
    
    @Autowired
    private MediaCopyService mediaCopyService;
    
    /**
     * Main orchestration method with progress callback
     */
    public CourseCopyResultDTO copyCourseToTenant(
        String jobId,
        CourseCopyJobData jobData,
        BiConsumer<String, Integer> progressCallback) {
        
        long startTime = System.currentTimeMillis();
        Map<String, Integer> copiedCounts = new HashMap<>();
        
        // Set SYSTEM context to read cross-tenant
        String originalContext = TenantContext.getClientId();
        TenantContext.setClientId("SYSTEM");
        
        try {
            // Step 1: Validate source (5%)
            progressCallback.accept("Validating source course", 5);
            Course source = validateAndFetchSourceCourse(jobData.getSourceCourseId());
            jobData.setSourceClientId(source.getClientId());
            
            // Step 2: Copy course entity (10%)
            progressCallback.accept("Creating course copy", 10);
            Course target = copyCourseEntity(source, jobData);
            courseRepository.save(target);
            
            // Step 3: Categories/tags (15%)
            progressCallback.accept("Processing categories and tags", 15);
            ensureCategoriesAndTags(source, target, jobData.getTargetClientId());
            courseRepository.save(target);
            
            // Step 4: Copy sections (25%)
            progressCallback.accept("Copying sections", 25);
            Map<String, String> sectionIdMap = copySections(source, target);
            copiedCounts.put("sections", sectionIdMap.size());
            
            // Step 5: Copy lectures (40%)
            progressCallback.accept("Copying lectures", 40);
            Map<String, String> lectureIdMap = copyLectures(sectionIdMap, source, target);
            copiedCounts.put("lectures", lectureIdMap.size());
            
            // Step 6: Copy lecture content (50%)
            progressCallback.accept("Copying lecture content", 50);
            int contentCount = copyLectureContent(lectureIdMap, jobData.getSourceClientId(), jobData.getTargetClientId());
            copiedCounts.put("lectureContent", contentCount);
            
            // Step 7: Copy sub-lessons (55%)
            progressCallback.accept("Copying sub-lessons", 55);
            int subLessonCount = copySubLessons(lectureIdMap, jobData.getSourceClientId(), jobData.getTargetClientId());
            copiedCounts.put("subLessons", subLessonCount);
            
            // Step 8: Copy assessments (65%)
            progressCallback.accept("Copying assessments and quizzes", 65);
            int assessmentCount = copyAssessments(source, target, sectionIdMap, lectureIdMap);
            copiedCounts.put("assessments", assessmentCount);
            
            // Step 9: Copy resources (75%)
            progressCallback.accept("Copying course resources", 75);
            int resourceCount = copyCourseResources(source, target, jobData.getTargetClientId());
            copiedCounts.put("resources", resourceCount);
            
            // Step 10: Copy learning objectives (80%)
            progressCallback.accept("Copying learning objectives", 80);
            int objectiveCount = copyLearningObjectives(source, target);
            copiedCounts.put("learningObjectives", objectiveCount);
            
            // Step 11: Duplicate media (85-95% - slowest step)
            progressCallback.accept("Duplicating media files (this may take a while)", 85);
            int mediaCount = mediaCopyService.duplicateAllMedia(target, jobData.getTargetClientId(), 
                (current, total) -> {
                    int mediaProgress = 85 + (int)((current / (double)total) * 10);
                    progressCallback.accept(
                        String.format("Duplicating media files (%d/%d)", current, total),
                        mediaProgress
                    );
                }
            );
            copiedCounts.put("mediaAssets", mediaCount);
            
            // Finalize (95%)
            progressCallback.accept("Finalizing course copy", 95);
            courseRepository.save(target);
            
            // Build result
            long duration = System.currentTimeMillis() - startTime;
            return buildResult(target, source, jobData, copiedCounts, duration);
            
        } finally {
            TenantContext.setClientId(originalContext);
        }
    }
    
    // Private helper methods
    
    private Course validateAndFetchSourceCourse(String courseId) {
        return courseRepository.findById(courseId)
            .orElseThrow(() -> new IllegalArgumentException("Source course not found: " + courseId));
    }
    
    private Course copyCourseEntity(Course source, CourseCopyJobData jobData) {
        Course target = new Course();
        target.setId(UlidGenerator.nextUlid());
        target.setClientId(jobData.getTargetClientId());
        
        // Set title
        if (jobData.getNewCourseTitle() != null && !jobData.getNewCourseTitle().isBlank()) {
            target.setTitle(jobData.getNewCourseTitle());
        } else {
            target.setTitle("Copy of " + source.getTitle());
        }
        
        // Copy all fields except ID, clientId, title, instructors, assignments
        target.setDescription(source.getDescription());
        target.setThumbnailUrl(source.getThumbnailUrl());
        target.setPreviewVideoUrl(source.getPreviewVideoUrl());
        target.setIsFree(source.getIsFree());
        target.setPricePaise(source.getPricePaise());
        target.setCurrency(source.getCurrency());
        target.setDifficultyLevel(source.getDifficultyLevel());
        target.setLanguage(source.getLanguage());
        target.setCertificateEligible(source.getCertificateEligible());
        target.setMaxCompletionDays(source.getMaxCompletionDays());
        
        // Set published state
        Boolean shouldPublish = jobData.getCopyPublishedState() != null ? jobData.getCopyPublishedState() : false;
        target.setIsPublished(shouldPublish);
        target.setIsActive(true);
        target.setPublishedAt(shouldPublish ? OffsetDateTime.now() : null);
        
        // Clear instructor-related and assignment fields
        target.setAssignedToClassIds(new ArrayList<>());
        target.setAssignedToSectionIds(new ArrayList<>());
        
        // Copy statistics
        target.setTotalDurationSeconds(source.getTotalDurationSeconds());
        target.setTotalLecturesCount(source.getTotalLecturesCount());
        target.setTotalStudentsCount(0); // Reset student count for new tenant
        
        return target;
    }
    
    private void ensureCategoriesAndTags(Course source, Course target, UUID targetClientId) {
        // Copy category if exists
        if (source.getCategoryId() != null && !source.getCategoryId().isEmpty()) {
            Optional<CourseCategory> sourceCategory = courseCategoryRepository.findById(source.getCategoryId());
            if (sourceCategory.isPresent()) {
                // Try to find matching category in target tenant
                List<CourseCategory> allTargetCategories = courseCategoryRepository.findByClientIdAndIsActiveOrderBySequenceAsc(
                    targetClientId, true
                );
                List<CourseCategory> targetCategories = allTargetCategories.stream()
                    .filter(c -> c.getName().equals(sourceCategory.get().getName()))
                    .toList();
                
                if (!targetCategories.isEmpty()) {
                    target.setCategoryId(targetCategories.get(0).getId());
                } else {
                    // Create new category in target tenant
                    CourseCategory newCategory = createCategory(sourceCategory.get(), targetClientId);
                    target.setCategoryId(newCategory.getId());
                }
            }
        }
        
        // Copy tags
        if (source.getTags() != null && !source.getTags().isEmpty()) {
            List<String> targetTags = new ArrayList<>();
            for (String tagName : source.getTags()) {
                // Auto-create tag if it doesn't exist
                Optional<CourseTag> existingTag = courseTagRepository.findByClientIdAndName(targetClientId, tagName);
                if (existingTag.isEmpty()) {
                    CourseTag newTag = new CourseTag();
                    newTag.setId(UlidGenerator.nextUlid());
                    newTag.setClientId(targetClientId);
                    newTag.setName(tagName);
                    newTag.setUsageCount(1);
                    courseTagRepository.save(newTag);
                }
                targetTags.add(tagName);
            }
            target.setTags(targetTags);
        }
        
        // Copy other tag arrays
        if (source.getStreamTags() != null) {
            target.setStreamTags(new ArrayList<>(source.getStreamTags()));
        }
        if (source.getRiasecTags() != null) {
            target.setRiasecTags(new ArrayList<>(source.getRiasecTags()));
        }
        if (source.getSkillTags() != null) {
            target.setSkillTags(new ArrayList<>(source.getSkillTags()));
        }
    }
    
    private CourseCategory createCategory(CourseCategory source, UUID targetClientId) {
        CourseCategory newCategory = new CourseCategory();
        newCategory.setId(UlidGenerator.nextUlid());
        newCategory.setClientId(targetClientId);
        newCategory.setName(source.getName());
        newCategory.setDescription(source.getDescription());
        newCategory.setIconUrl(source.getIconUrl());
        newCategory.setSequence(source.getSequence());
        newCategory.setIsActive(source.getIsActive());
        return courseCategoryRepository.save(newCategory);
    }
    
    private Map<String, String> copySections(Course source, Course target) {
        List<Section> sourceSections = sectionRepository.findByCourseIdAndClientIdOrderBySequenceAsc(
            source.getId(),
            source.getClientId()
        );
        Map<String, String> idMap = new HashMap<>();
        
        for (Section sourceSection : sourceSections) {
            Section targetSection = new Section();
            String newId = UlidGenerator.nextUlid();
            
            targetSection.setId(newId);
            targetSection.setCourseId(target.getId());
            targetSection.setClientId(target.getClientId());
            targetSection.setTitle(sourceSection.getTitle());
            targetSection.setDescription(sourceSection.getDescription());
            targetSection.setSequence(sourceSection.getSequence());
            targetSection.setIsPublished(sourceSection.getIsPublished());
            
            sectionRepository.save(targetSection);
            idMap.put(sourceSection.getId(), newId);
        }
        
        return idMap;
    }
    
    private Map<String, String> copyLectures(Map<String, String> sectionIdMap, Course source, Course target) {
        Map<String, String> lectureIdMap = new HashMap<>();
        
        for (Map.Entry<String, String> entry : sectionIdMap.entrySet()) {
            String sourceSectionId = entry.getKey();
            String targetSectionId = entry.getValue();
            
            // Get source lecture's clientId from the first section
            UUID sourceClientId = sectionIdMap.isEmpty() ? source.getClientId() : source.getClientId();
            List<Lecture> sourceLectures = lectureRepository.findBySectionIdAndClientIdOrderBySequenceAsc(
                sourceSectionId,
                sourceClientId
            );
            
            for (Lecture sourceLecture : sourceLectures) {
                Lecture targetLecture = new Lecture();
                String newId = UlidGenerator.nextUlid();
                
                targetLecture.setId(newId);
                targetLecture.setSectionId(targetSectionId);
                targetLecture.setCourseId(target.getId());
                targetLecture.setClientId(target.getClientId());
                targetLecture.setTitle(sourceLecture.getTitle());
                targetLecture.setDescription(sourceLecture.getDescription());
                targetLecture.setContentType(sourceLecture.getContentType());
                targetLecture.setSequence(sourceLecture.getSequence());
                targetLecture.setDurationSeconds(sourceLecture.getDurationSeconds());
                targetLecture.setIsPreview(sourceLecture.getIsPreview());
                targetLecture.setIsPublished(sourceLecture.getIsPublished());
                
                // Lecture doesn't have media fields - those are in LectureContent
                // We'll copy those separately
                
                lectureRepository.save(targetLecture);
                lectureIdMap.put(sourceLecture.getId(), newId);
            }
        }
        
        return lectureIdMap;
    }
    
    private int copyLectureContent(Map<String, String> lectureIdMap, UUID sourceClientId, UUID targetClientId) {
        int count = 0;
        
        for (Map.Entry<String, String> entry : lectureIdMap.entrySet()) {
            String sourceLectureId = entry.getKey();
            String targetLectureId = entry.getValue();
            List<LectureContent> sourceContents = lectureContentRepository.findByLectureIdAndClientIdOrderBySequenceAsc(
                sourceLectureId,
                sourceClientId
            );
            
            for (LectureContent sourceContent : sourceContents) {
                LectureContent targetContent = new LectureContent();
                targetContent.setId(UlidGenerator.nextUlid());
                targetContent.setLectureId(targetLectureId);
                targetContent.setClientId(targetClientId);
                targetContent.setContentType(sourceContent.getContentType());
                targetContent.setTitle(sourceContent.getTitle());
                targetContent.setDescription(sourceContent.getDescription());
                targetContent.setSequence(sourceContent.getSequence());
                
                // Copy all content fields (will be updated by MediaCopyService)
                targetContent.setFileUrl(sourceContent.getFileUrl());
                targetContent.setFileSizeBytes(sourceContent.getFileSizeBytes());
                targetContent.setMimeType(sourceContent.getMimeType());
                targetContent.setVideoUrl(sourceContent.getVideoUrl());
                targetContent.setTranscriptUrl(sourceContent.getTranscriptUrl());
                targetContent.setSubtitleUrls(sourceContent.getSubtitleUrls());
                targetContent.setThumbnailUrl(sourceContent.getThumbnailUrl());
                targetContent.setTextContent(sourceContent.getTextContent());
                targetContent.setExternalUrl(sourceContent.getExternalUrl());
                targetContent.setEmbeddedCode(sourceContent.getEmbeddedCode());
                
                lectureContentRepository.save(targetContent);
                count++;
            }
        }
        
        return count;
    }
    
    private int copySubLessons(Map<String, String> lectureIdMap, UUID sourceClientId, UUID targetClientId) {
        int count = 0;
        
        for (Map.Entry<String, String> entry : lectureIdMap.entrySet()) {
            String sourceLectureId = entry.getKey();
            String targetLectureId = entry.getValue();
            List<SubLesson> sourceSubLessons = subLessonRepository.findByLectureIdAndClientIdOrderBySequenceAsc(
                sourceLectureId,
                sourceClientId
            );
            
            for (SubLesson sourceSubLesson : sourceSubLessons) {
                SubLesson targetSubLesson = new SubLesson();
                targetSubLesson.setId(UlidGenerator.nextUlid());
                targetSubLesson.setLectureId(targetLectureId);
                targetSubLesson.setClientId(targetClientId);
                targetSubLesson.setTitle(sourceSubLesson.getTitle());
                targetSubLesson.setDescription(sourceSubLesson.getDescription());
                targetSubLesson.setContentType(sourceSubLesson.getContentType());
                targetSubLesson.setSequence(sourceSubLesson.getSequence());
                targetSubLesson.setDurationSeconds(sourceSubLesson.getDurationSeconds());
                
                // Copy content fields
                targetSubLesson.setFileUrl(sourceSubLesson.getFileUrl());
                targetSubLesson.setFileSizeBytes(sourceSubLesson.getFileSizeBytes());
                targetSubLesson.setMimeType(sourceSubLesson.getMimeType());
                targetSubLesson.setTextContent(sourceSubLesson.getTextContent());
                targetSubLesson.setExternalUrl(sourceSubLesson.getExternalUrl());
                targetSubLesson.setEmbeddedCode(sourceSubLesson.getEmbeddedCode());
                
                subLessonRepository.save(targetSubLesson);
                count++;
            }
        }
        
        return count;
    }
    
    private int copyAssessments(Course source, Course target, Map<String, String> sectionIdMap, Map<String, String> lectureIdMap) {
        List<Assessment> sourceAssessments = assessmentRepository.findByCourseIdAndClientIdOrderBySequenceAsc(
            source.getId(),
            source.getClientId()
        );
        int count = 0;
        
        for (Assessment sourceAssessment : sourceAssessments) {
            Assessment targetAssessment = new Assessment();
            String newAssessmentId = UlidGenerator.nextUlid();
            
            targetAssessment.setId(newAssessmentId);
            targetAssessment.setCourseId(target.getId());
            targetAssessment.setClientId(target.getClientId());
            
            // Map section and lecture IDs
            if (sourceAssessment.getSectionId() != null) {
                targetAssessment.setSectionId(sectionIdMap.get(sourceAssessment.getSectionId()));
            }
            if (sourceAssessment.getLectureId() != null) {
                targetAssessment.setLectureId(lectureIdMap.get(sourceAssessment.getLectureId()));
            }
            
            // Copy assessment fields
            targetAssessment.setAssessmentType(sourceAssessment.getAssessmentType());
            targetAssessment.setTitle(sourceAssessment.getTitle());
            targetAssessment.setDescription(sourceAssessment.getDescription());
            targetAssessment.setInstructions(sourceAssessment.getInstructions());
            targetAssessment.setPassingScorePercentage(sourceAssessment.getPassingScorePercentage());
            targetAssessment.setMaxAttempts(sourceAssessment.getMaxAttempts());
            targetAssessment.setTimeLimitSeconds(sourceAssessment.getTimeLimitSeconds());
            targetAssessment.setIsRequired(sourceAssessment.getIsRequired());
            targetAssessment.setIsPublished(sourceAssessment.getIsPublished());
            targetAssessment.setSequence(sourceAssessment.getSequence());
            
            assessmentRepository.save(targetAssessment);
            count++;
            
            // Copy quiz questions
            copyQuizQuestions(sourceAssessment.getId(), newAssessmentId, source.getClientId(), target.getClientId());
        }
        
        return count;
    }
    
    private void copyQuizQuestions(String sourceAssessmentId, String targetAssessmentId, UUID sourceClientId, UUID targetClientId) {
        List<QuizQuestion> sourceQuestions = quizQuestionRepository.findByAssessmentIdAndClientIdOrderBySequenceAsc(
            sourceAssessmentId,
            sourceClientId
        );
        
        for (QuizQuestion sourceQuestion : sourceQuestions) {
            QuizQuestion targetQuestion = new QuizQuestion();
            String newQuestionId = UlidGenerator.nextUlid();
            
            targetQuestion.setId(newQuestionId);
            targetQuestion.setAssessmentId(targetAssessmentId);
            targetQuestion.setClientId(targetClientId);
            targetQuestion.setQuestionType(sourceQuestion.getQuestionType());
            targetQuestion.setQuestionText(sourceQuestion.getQuestionText());
            targetQuestion.setPoints(sourceQuestion.getPoints());
            targetQuestion.setSequence(sourceQuestion.getSequence());
            targetQuestion.setExplanation(sourceQuestion.getExplanation());
            targetQuestion.setTentativeAnswer(sourceQuestion.getTentativeAnswer());
            targetQuestion.setEditedTentativeAnswer(sourceQuestion.getEditedTentativeAnswer());
            targetQuestion.setUseTentativeAnswerForGrading(sourceQuestion.getUseTentativeAnswerForGrading());
            
            quizQuestionRepository.save(targetQuestion);
            
            // Copy quiz options
            copyQuizOptions(sourceQuestion.getId(), newQuestionId, sourceClientId, targetClientId);
        }
    }
    
    private void copyQuizOptions(String sourceQuestionId, String targetQuestionId, UUID sourceClientId, UUID targetClientId) {
        List<QuizOption> sourceOptions = quizOptionRepository.findByQuestionIdAndClientIdOrderBySequenceAsc(
            sourceQuestionId,
            sourceClientId
        );
        
        for (QuizOption sourceOption : sourceOptions) {
            QuizOption targetOption = new QuizOption();
            targetOption.setId(UlidGenerator.nextUlid());
            targetOption.setQuestionId(targetQuestionId);
            targetOption.setClientId(targetClientId);
            targetOption.setOptionText(sourceOption.getOptionText());
            targetOption.setIsCorrect(sourceOption.getIsCorrect());
            targetOption.setSequence(sourceOption.getSequence());
            
            quizOptionRepository.save(targetOption);
        }
    }
    
    private int copyCourseResources(Course source, Course target, UUID targetClientId) {
        List<CourseResource> sourceResources = courseResourceRepository.findByCourseIdAndClientId(
            source.getId(),
            source.getClientId()
        );
        int count = 0;
        
        for (CourseResource sourceResource : sourceResources) {
            CourseResource targetResource = new CourseResource();
            targetResource.setId(UlidGenerator.nextUlid());
            targetResource.setCourseId(target.getId());
            targetResource.setClientId(targetClientId);
            targetResource.setResourceType(sourceResource.getResourceType());
            targetResource.setTitle(sourceResource.getTitle());
            targetResource.setDescription(sourceResource.getDescription());
            targetResource.setFileUrl(sourceResource.getFileUrl());
            targetResource.setFileSizeBytes(sourceResource.getFileSizeBytes());
            targetResource.setIsDownloadable(sourceResource.getIsDownloadable());
            targetResource.setDownloadCount(0); // Reset download count
            
            courseResourceRepository.save(targetResource);
            count++;
        }
        
        return count;
    }
    
    private int copyLearningObjectives(Course source, Course target) {
        List<LearningObjective> sourceObjectives = learningObjectiveRepository.findByCourseIdAndClientIdOrderBySequenceAsc(
            source.getId(),
            source.getClientId()
        );
        int count = 0;
        
        for (LearningObjective sourceObjective : sourceObjectives) {
            LearningObjective targetObjective = new LearningObjective();
            targetObjective.setId(UlidGenerator.nextUlid());
            targetObjective.setCourseId(target.getId());
            targetObjective.setClientId(target.getClientId());
            targetObjective.setObjectiveText(sourceObjective.getObjectiveText());
            targetObjective.setSequence(sourceObjective.getSequence());
            
            learningObjectiveRepository.save(targetObjective);
            count++;
        }
        
        return count;
    }
    
    private CourseCopyResultDTO buildResult(Course target, Course source, 
                                           CourseCopyJobData jobData,
                                           Map<String, Integer> copiedCounts,
                                           long durationMs) {
        CourseCopyResultDTO result = new CourseCopyResultDTO();
        result.setNewCourseId(target.getId());
        result.setSourceCourseId(source.getId());
        result.setTargetClientId(jobData.getTargetClientId());
        result.setCopiedEntities(copiedCounts);
        result.setCompletedAt(OffsetDateTime.now());
        result.setDuration(formatDuration(durationMs));
        return result;
    }
    
    private String formatDuration(long milliseconds) {
        long seconds = milliseconds / 1000;
        if (seconds < 60) {
            return seconds + "s";
        }
        long minutes = seconds / 60;
        long remainingSeconds = seconds % 60;
        if (minutes < 60) {
            return String.format("%dm %ds", minutes, remainingSeconds);
        }
        long hours = minutes / 60;
        long remainingMinutes = minutes % 60;
        return String.format("%dh %dm", hours, remainingMinutes);
    }
}
