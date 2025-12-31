package com.datagami.edudron.content.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "courses", schema = "content")
public class Course {
    @Id
    private String id; // ULID

    @Column(nullable = false)
    private UUID clientId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false)
    private Boolean isPublished = false;

    // Metadata
    private String thumbnailUrl;
    private String previewVideoUrl;

    // Pricing
    @Column(nullable = false)
    private Boolean isFree = false;
    private Long pricePaise;
    @Column(length = 3)
    private String currency = "INR";

    // Categorization
    private String categoryId;
    @Column(columnDefinition = "text[]")
    private List<String> tags = new ArrayList<>();
    @Column(length = 20)
    private String difficultyLevel;
    @Column(length = 10)
    private String language = "en";

    // Statistics
    @Column(nullable = false)
    private Integer totalDurationSeconds = 0;
    @Column(nullable = false)
    private Integer totalLecturesCount = 0;
    @Column(nullable = false)
    private Integer totalStudentsCount = 0;

    // Settings
    @Column(nullable = false)
    private Boolean certificateEligible = false;
    private Integer maxCompletionDays;

    // Timestamps
    @Column(nullable = false)
    private OffsetDateTime createdAt;
    @Column(nullable = false)
    private OffsetDateTime updatedAt;
    private OffsetDateTime publishedAt;

    // Relationships
    @OneToMany(mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequence ASC")
    private List<Section> sections = new ArrayList<>();

    @OneToMany(mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Assessment> assessments = new ArrayList<>();

    @OneToMany(mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CourseResource> resources = new ArrayList<>();

    @OneToMany(mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequence ASC")
    private List<LearningObjective> learningObjectives = new ArrayList<>();

    @OneToMany(mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CourseInstructor> instructors = new ArrayList<>();

    @OneToMany(mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CoursePrerequisite> prerequisites = new ArrayList<>();

    @OneToMany(mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt DESC")
    private List<CourseAnnouncement> announcements = new ArrayList<>();

    // Constructors
    public Course() {
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public UUID getClientId() { return clientId; }
    public void setClientId(UUID clientId) { this.clientId = clientId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Boolean getIsPublished() { return isPublished; }
    public void setIsPublished(Boolean isPublished) { 
        this.isPublished = isPublished;
        if (isPublished && publishedAt == null) {
            this.publishedAt = OffsetDateTime.now();
        }
    }

    public String getThumbnailUrl() { return thumbnailUrl; }
    public void setThumbnailUrl(String thumbnailUrl) { this.thumbnailUrl = thumbnailUrl; }

    public String getPreviewVideoUrl() { return previewVideoUrl; }
    public void setPreviewVideoUrl(String previewVideoUrl) { this.previewVideoUrl = previewVideoUrl; }

    public Boolean getIsFree() { return isFree; }
    public void setIsFree(Boolean isFree) { this.isFree = isFree; }

    public Long getPricePaise() { return pricePaise; }
    public void setPricePaise(Long pricePaise) { this.pricePaise = pricePaise; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getCategoryId() { return categoryId; }
    public void setCategoryId(String categoryId) { this.categoryId = categoryId; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }

    public String getDifficultyLevel() { return difficultyLevel; }
    public void setDifficultyLevel(String difficultyLevel) { this.difficultyLevel = difficultyLevel; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public Integer getTotalDurationSeconds() { return totalDurationSeconds; }
    public void setTotalDurationSeconds(Integer totalDurationSeconds) { this.totalDurationSeconds = totalDurationSeconds; }

    public Integer getTotalLecturesCount() { return totalLecturesCount; }
    public void setTotalLecturesCount(Integer totalLecturesCount) { this.totalLecturesCount = totalLecturesCount; }

    public Integer getTotalStudentsCount() { return totalStudentsCount; }
    public void setTotalStudentsCount(Integer totalStudentsCount) { this.totalStudentsCount = totalStudentsCount; }

    public Boolean getCertificateEligible() { return certificateEligible; }
    public void setCertificateEligible(Boolean certificateEligible) { this.certificateEligible = certificateEligible; }

    public Integer getMaxCompletionDays() { return maxCompletionDays; }
    public void setMaxCompletionDays(Integer maxCompletionDays) { this.maxCompletionDays = maxCompletionDays; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }

    public OffsetDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(OffsetDateTime publishedAt) { this.publishedAt = publishedAt; }

    public List<Section> getSections() { return sections; }
    public void setSections(List<Section> sections) { this.sections = sections; }

    public List<Assessment> getAssessments() { return assessments; }
    public void setAssessments(List<Assessment> assessments) { this.assessments = assessments; }

    public List<CourseResource> getResources() { return resources; }
    public void setResources(List<CourseResource> resources) { this.resources = resources; }

    public List<LearningObjective> getLearningObjectives() { return learningObjectives; }
    public void setLearningObjectives(List<LearningObjective> learningObjectives) { this.learningObjectives = learningObjectives; }

    public List<CourseInstructor> getInstructors() { return instructors; }
    public void setInstructors(List<CourseInstructor> instructors) { this.instructors = instructors; }

    public List<CoursePrerequisite> getPrerequisites() { return prerequisites; }
    public void setPrerequisites(List<CoursePrerequisite> prerequisites) { this.prerequisites = prerequisites; }

    public List<CourseAnnouncement> getAnnouncements() { return announcements; }
    public void setAnnouncements(List<CourseAnnouncement> announcements) { this.announcements = announcements; }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}

