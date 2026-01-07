package com.datagami.edudron.content.repo;

import com.datagami.edudron.content.domain.LectureContent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LectureContentRepository extends JpaRepository<LectureContent, String> {
    
    List<LectureContent> findByLectureIdAndClientIdOrderBySequenceAsc(String lectureId, UUID clientId);
    
    Optional<LectureContent> findByIdAndClientId(String id, UUID clientId);
    
    void deleteByLectureIdAndClientId(String lectureId, UUID clientId);
    
    @org.springframework.data.jpa.repository.Query("SELECT MAX(lc.sequence) FROM LectureContent lc WHERE lc.lectureId = :lectureId AND lc.clientId = :clientId")
    Integer findMaxSequenceByLectureIdAndClientId(String lectureId, UUID clientId);
}


