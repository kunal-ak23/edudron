package com.datagami.edudron.content.repo;

import com.datagami.edudron.content.domain.LearningObjective;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LearningObjectiveRepository extends JpaRepository<LearningObjective, String> {
    
    List<LearningObjective> findByCourseIdAndClientIdOrderBySequenceAsc(String courseId, UUID clientId);
    
    void deleteByCourseIdAndClientId(String courseId, UUID clientId);
}

