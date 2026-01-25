package com.datagami.edudron.content.service;

import com.azure.core.util.polling.SyncPoller;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlobCopyInfo;
import com.datagami.edudron.content.domain.*;
import com.datagami.edudron.content.repo.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.function.BiConsumer;

@Service
public class MediaCopyService {
    
    private static final Logger logger = LoggerFactory.getLogger(MediaCopyService.class);
    
    @Autowired(required = false)
    private BlobServiceClient blobServiceClient;
    
    @Value("${azure.storage.container-name:edudron-media}")
    private String containerName;
    
    @Autowired
    private CourseRepository courseRepository;
    
    @Autowired
    private LectureRepository lectureRepository;
    
    @Autowired
    private LectureContentRepository lectureContentRepository;
    
    @Autowired
    private SubLessonRepository subLessonRepository;
    
    @Autowired
    private CourseResourceRepository courseResourceRepository;
    
    /**
     * Duplicate all media files for a course
     * @param progressCallback - receives (currentFile, totalFiles) for progress tracking
     */
    public int duplicateAllMedia(Course course, UUID targetClientId, 
                                 BiConsumer<Integer, Integer> progressCallback) {
        // If blob service is not configured, skip media copying
        if (blobServiceClient == null) {
            logger.warn("Azure Blob Storage not configured, skipping media duplication");
            return 0;
        }
        
        List<String> mediaUrls = collectAllMediaUrls(course);
        Map<String, String> urlMapping = new HashMap<>();
        
        int totalFiles = mediaUrls.size();
        int currentFile = 0;
        
        logger.info("Starting media duplication: {} files to copy", totalFiles);
        
        for (String sourceUrl : mediaUrls) {
            currentFile++;
            if (progressCallback != null) {
                progressCallback.accept(currentFile, totalFiles);
            }
            
            try {
                String newUrl = copyMediaFile(sourceUrl, targetClientId, course.getId());
                urlMapping.put(sourceUrl, newUrl);
                logger.debug("Copied media file {} -> {}", sourceUrl, newUrl);
            } catch (Exception e) {
                logger.error("Failed to copy media file: {}", sourceUrl, e);
                // Continue with other files rather than failing entire operation
            }
        }
        
        // Update all entity references with new URLs
        updateMediaReferences(course, urlMapping);
        
        logger.info("Media duplication completed: {} of {} files copied successfully", 
                    urlMapping.size(), totalFiles);
        
        return urlMapping.size();
    }
    
    /**
     * Copy a single media file in Azure Blob Storage
     */
    public String copyMediaFile(String sourceUrl, UUID targetClientId, String targetCourseId) {
        if (sourceUrl == null || sourceUrl.isEmpty()) {
            return null;
        }
        
        try {
            // Parse source URL to extract blob path
            BlobInfo sourceBlobInfo = parseBlobUrl(sourceUrl);
            if (sourceBlobInfo == null) {
                logger.warn("Could not parse blob URL: {}", sourceUrl);
                return sourceUrl; // Return original URL if we can't parse it
            }
            
            // Generate target path: /{targetClientId}/courses/{targetCourseId}/...
            String targetPath = String.format("%s/courses/%s/%s",
                targetClientId.toString(), targetCourseId, sourceBlobInfo.fileName);
            
            // Get container client
            BlobContainerClient containerClient = blobServiceClient
                .getBlobContainerClient(containerName);
            
            // Get source and target blob clients
            BlobClient sourceBlob = containerClient.getBlobClient(sourceBlobInfo.blobPath);
            BlobClient targetBlob = containerClient.getBlobClient(targetPath);
            
            // Check if target already exists
            if (targetBlob.exists()) {
                logger.debug("Target blob already exists: {}", targetPath);
                return targetBlob.getBlobUrl();
            }
            
            // Copy blob (async operation)
            SyncPoller<BlobCopyInfo, Void> poller = targetBlob.beginCopy(sourceBlob.getBlobUrl(), null);
            poller.waitForCompletion();
            
            return targetBlob.getBlobUrl();
            
        } catch (Exception e) {
            logger.error("Failed to copy blob from {} to target client {}", sourceUrl, targetClientId, e);
            // Return original URL so course is still functional even if media copy failed
            return sourceUrl;
        }
    }
    
    /**
     * Parse a blob URL to extract relevant information
     */
    private BlobInfo parseBlobUrl(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        
        try {
            URI uri = new URI(url);
            String path = uri.getPath();
            
            // Expected format: /container-name/clientId/courses/courseId/filename
            // or /container-name/path/to/file
            String[] pathParts = path.split("/", 3); // Split into [, container, rest]
            
            if (pathParts.length < 3) {
                return null;
            }
            
            String blobPath = pathParts[2]; // Everything after container name
            
            // Extract filename from the end of the path
            String[] blobPathParts = blobPath.split("/");
            String fileName = blobPathParts[blobPathParts.length - 1];
            
            BlobInfo info = new BlobInfo();
            info.containerName = pathParts[1];
            info.blobPath = blobPath;
            info.fileName = fileName;
            
            return info;
            
        } catch (URISyntaxException e) {
            logger.warn("Invalid URL format: {}", url, e);
            return null;
        }
    }
    
    /**
     * Collect all media URLs from a course and its related entities
     */
    private List<String> collectAllMediaUrls(Course course) {
        Set<String> urls = new HashSet<>(); // Use Set to avoid duplicates
        
        // Course-level media
        addUrlIfPresent(urls, course.getThumbnailUrl());
        addUrlIfPresent(urls, course.getPreviewVideoUrl());
        
        // Lectures
        List<Lecture> lectures = lectureRepository.findByCourseIdAndClientIdOrderBySequenceAsc(
            course.getId(),
            course.getClientId()
        );
        for (Lecture lecture : lectures) {
            // Lecture doesn't have media fields directly - those are in LectureContent
            
            // Lecture content
            List<LectureContent> contents = lectureContentRepository.findByLectureIdAndClientIdOrderBySequenceAsc(
                lecture.getId(),
                lecture.getClientId()
            );
            for (LectureContent content : contents) {
                addUrlIfPresent(urls, content.getFileUrl());
                addUrlIfPresent(urls, content.getVideoUrl());
                addUrlIfPresent(urls, content.getTranscriptUrl());
                addUrlIfPresent(urls, content.getThumbnailUrl());
                if (content.getSubtitleUrls() != null) {
                    for (Object subtitleUrl : content.getSubtitleUrls()) {
                        addUrlIfPresent(urls, subtitleUrl.toString());
                    }
                }
            }
            
            // Sub-lessons
            List<SubLesson> subLessons = subLessonRepository.findByLectureIdAndClientIdOrderBySequenceAsc(
                lecture.getId(),
                lecture.getClientId()
            );
            for (SubLesson subLesson : subLessons) {
                addUrlIfPresent(urls, subLesson.getFileUrl());
            }
        }
        
        // Course resources
        List<CourseResource> resources = courseResourceRepository.findByCourseIdAndClientId(
            course.getId(),
            course.getClientId()
        );
        for (CourseResource resource : resources) {
            addUrlIfPresent(urls, resource.getFileUrl());
        }
        
        return new ArrayList<>(urls);
    }
    
    /**
     * Add URL to set if it's not null and not empty and appears to be an Azure blob URL
     */
    private void addUrlIfPresent(Set<String> urls, String url) {
        if (url != null && !url.isEmpty() && url.contains("blob.core.windows.net")) {
            urls.add(url);
        }
    }
    
    /**
     * Update all entity references with new URLs
     */
    private void updateMediaReferences(Course course, Map<String, String> urlMapping) {
        if (urlMapping.isEmpty()) {
            return;
        }
        
        // Update course
        boolean courseUpdated = false;
        if (course.getThumbnailUrl() != null && urlMapping.containsKey(course.getThumbnailUrl())) {
            course.setThumbnailUrl(urlMapping.get(course.getThumbnailUrl()));
            courseUpdated = true;
        }
        if (course.getPreviewVideoUrl() != null && urlMapping.containsKey(course.getPreviewVideoUrl())) {
            course.setPreviewVideoUrl(urlMapping.get(course.getPreviewVideoUrl()));
            courseUpdated = true;
        }
        if (courseUpdated) {
            courseRepository.save(course);
        }
        
        // Update lectures (Lecture doesn't have media fields directly)
        List<Lecture> lectures = lectureRepository.findByCourseIdAndClientIdOrderBySequenceAsc(
            course.getId(),
            course.getClientId()
        );
        for (Lecture lecture : lectures) {
            // Lecture entity doesn't have media fields - skip
            
            // Update lecture content
            List<LectureContent> contents = lectureContentRepository.findByLectureIdAndClientIdOrderBySequenceAsc(
                lecture.getId(),
                lecture.getClientId()
            );
            for (LectureContent content : contents) {
                boolean contentUpdated = false;
                
                if (content.getFileUrl() != null && urlMapping.containsKey(content.getFileUrl())) {
                    content.setFileUrl(urlMapping.get(content.getFileUrl()));
                    contentUpdated = true;
                }
                if (content.getVideoUrl() != null && urlMapping.containsKey(content.getVideoUrl())) {
                    content.setVideoUrl(urlMapping.get(content.getVideoUrl()));
                    contentUpdated = true;
                }
                if (content.getTranscriptUrl() != null && urlMapping.containsKey(content.getTranscriptUrl())) {
                    content.setTranscriptUrl(urlMapping.get(content.getTranscriptUrl()));
                    contentUpdated = true;
                }
                if (content.getThumbnailUrl() != null && urlMapping.containsKey(content.getThumbnailUrl())) {
                    content.setThumbnailUrl(urlMapping.get(content.getThumbnailUrl()));
                    contentUpdated = true;
                }
                
                if (contentUpdated) {
                    lectureContentRepository.save(content);
                }
            }
            
            // Update sub-lessons
            List<SubLesson> subLessons = subLessonRepository.findByLectureIdAndClientIdOrderBySequenceAsc(
                lecture.getId(),
                lecture.getClientId()
            );
            for (SubLesson subLesson : subLessons) {
                if (subLesson.getFileUrl() != null && urlMapping.containsKey(subLesson.getFileUrl())) {
                    subLesson.setFileUrl(urlMapping.get(subLesson.getFileUrl()));
                    subLessonRepository.save(subLesson);
                }
            }
        }
        
        // Update course resources
        List<CourseResource> resources = courseResourceRepository.findByCourseIdAndClientId(
            course.getId(),
            course.getClientId()
        );
        for (CourseResource resource : resources) {
            if (resource.getFileUrl() != null && urlMapping.containsKey(resource.getFileUrl())) {
                resource.setFileUrl(urlMapping.get(resource.getFileUrl()));
                courseResourceRepository.save(resource);
            }
        }
    }
    
    /**
     * Inner class to hold blob info
     */
    private static class BlobInfo {
        String containerName;
        String blobPath;
        String fileName;
    }
}
