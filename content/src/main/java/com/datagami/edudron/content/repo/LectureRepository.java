package com.datagami.edudron.content.repo;

import com.datagami.edudron.content.domain.Lecture;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LectureRepository extends JpaRepository<Lecture, String> {
    
    List<Lecture> findBySectionIdAndClientIdOrderBySequenceAsc(String sectionId, UUID clientId);
    
    List<Lecture> findByCourseIdAndClientIdOrderBySequenceAsc(String courseId, UUID clientId);
    
    Optional<Lecture> findByIdAndClientId(String id, UUID clientId);
    
    @Query("SELECT MAX(l.sequence) FROM Lecture l WHERE l.sectionId = :sectionId AND l.clientId = :clientId")
    Integer findMaxSequenceBySectionIdAndClientId(@Param("sectionId") String sectionId, @Param("clientId") UUID clientId);
    
    @Query("SELECT SUM(l.durationSeconds) FROM Lecture l WHERE l.courseId = :courseId AND l.clientId = :clientId")
    Integer sumDurationByCourseIdAndClientId(@Param("courseId") String courseId, @Param("clientId") UUID clientId);
    
    @Query("SELECT COUNT(l) FROM Lecture l WHERE l.courseId = :courseId AND l.clientId = :clientId")
    Long countByCourseIdAndClientId(@Param("courseId") String courseId, @Param("clientId") UUID clientId);
    
    void deleteBySectionIdAndClientId(String sectionId, UUID clientId);
    
    void deleteByCourseIdAndClientId(String courseId, UUID clientId);
}


