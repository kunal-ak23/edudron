package com.datagami.edudron.content.repo;

import com.datagami.edudron.content.domain.ProjectQuestionBank;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectQuestionBankRepository extends JpaRepository<ProjectQuestionBank, String> {

    List<ProjectQuestionBank> findByClientIdAndCourseIdAndIsActiveTrue(UUID clientId, String courseId);

    Optional<ProjectQuestionBank> findByIdAndClientId(String id, UUID clientId);

    List<ProjectQuestionBank> findByClientIdOrderByCreatedAtDesc(UUID clientId);
}
