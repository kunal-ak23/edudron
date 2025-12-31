package com.datagami.edudron.content.repo;

import com.datagami.edudron.content.domain.CourseAnnouncement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CourseAnnouncementRepository extends JpaRepository<CourseAnnouncement, String> {
    
    List<CourseAnnouncement> findByCourseIdAndClientIdOrderByIsPinnedDescCreatedAtDesc(String courseId, UUID clientId);
    
    void deleteByCourseIdAndClientId(String courseId, UUID clientId);
}

