package com.datagami.edudron.content.repo;

import com.datagami.edudron.content.domain.SubLesson;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubLessonRepository extends JpaRepository<SubLesson, String> {
    
    List<SubLesson> findByLectureIdAndClientIdOrderBySequenceAsc(String lectureId, UUID clientId);
    
    Optional<SubLesson> findByIdAndClientId(String id, UUID clientId);
    
    void deleteByLectureIdAndClientId(String lectureId, UUID clientId);
}

