package com.datagami.edudron.content.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.common.UlidGenerator;
import com.datagami.edudron.content.domain.ProjectQuestionBank;
import com.datagami.edudron.content.dto.CreateProjectQuestionRequest;
import com.datagami.edudron.content.dto.ProjectQuestionDTO;
import com.datagami.edudron.content.repo.ProjectQuestionBankRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class ProjectQuestionBankService {

    private static final Logger log = LoggerFactory.getLogger(ProjectQuestionBankService.class);

    @Autowired
    private ProjectQuestionBankRepository repository;

    private UUID getClientId() {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        return UUID.fromString(clientIdStr);
    }

    public ProjectQuestionDTO create(CreateProjectQuestionRequest request) {
        UUID clientId = getClientId();

        ProjectQuestionBank entity = new ProjectQuestionBank();
        entity.setId(UlidGenerator.nextUlid());
        entity.setClientId(clientId);
        entity.setCourseId(request.getCourseId());
        entity.setTitle(request.getTitle());
        entity.setProblemStatement(request.getProblemStatement());
        entity.setKeyTechnologies(request.getKeyTechnologies());
        entity.setTags(request.getTags());
        entity.setDifficulty(request.getDifficulty());
        entity.setIsActive(true);

        entity = repository.save(entity);
        log.info("Created project question '{}' (id={}) for course {}", entity.getTitle(), entity.getId(), entity.getCourseId());
        return ProjectQuestionDTO.fromEntity(entity);
    }

    @Transactional(readOnly = true)
    public ProjectQuestionDTO get(String id) {
        UUID clientId = getClientId();
        ProjectQuestionBank entity = repository.findByIdAndClientId(id, clientId)
                .orElseThrow(() -> new IllegalArgumentException("Project question not found: " + id));
        return ProjectQuestionDTO.fromEntity(entity);
    }

    @Transactional(readOnly = true)
    public List<ProjectQuestionDTO> list(String courseId, String difficulty, Boolean isActive) {
        UUID clientId = getClientId();

        List<ProjectQuestionBank> results;
        if (courseId != null && (isActive == null || isActive)) {
            results = repository.findByClientIdAndCourseIdAndIsActiveTrue(clientId, courseId);
        } else if (courseId != null) {
            // Return all (including inactive) for the course
            results = repository.findByClientIdOrderByCreatedAtDesc(clientId).stream()
                    .filter(q -> courseId.equals(q.getCourseId()))
                    .collect(Collectors.toList());
        } else {
            results = repository.findByClientIdOrderByCreatedAtDesc(clientId);
        }

        // Apply additional filters
        if (difficulty != null) {
            results = results.stream()
                    .filter(q -> difficulty.equals(q.getDifficulty()))
                    .collect(Collectors.toList());
        }

        return results.stream()
                .map(ProjectQuestionDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public ProjectQuestionDTO update(String id, CreateProjectQuestionRequest request) {
        UUID clientId = getClientId();
        ProjectQuestionBank entity = repository.findByIdAndClientId(id, clientId)
                .orElseThrow(() -> new IllegalArgumentException("Project question not found: " + id));

        entity.setTitle(request.getTitle());
        entity.setProblemStatement(request.getProblemStatement());
        entity.setCourseId(request.getCourseId());
        entity.setKeyTechnologies(request.getKeyTechnologies());
        entity.setTags(request.getTags());
        entity.setDifficulty(request.getDifficulty());

        entity = repository.save(entity);
        log.info("Updated project question '{}' (id={})", entity.getTitle(), entity.getId());
        return ProjectQuestionDTO.fromEntity(entity);
    }

    public void delete(String id) {
        UUID clientId = getClientId();
        ProjectQuestionBank entity = repository.findByIdAndClientId(id, clientId)
                .orElseThrow(() -> new IllegalArgumentException("Project question not found: " + id));
        // Soft delete
        entity.setIsActive(false);
        repository.save(entity);
        log.info("Soft deleted project question {}", id);
    }

    public List<ProjectQuestionDTO> bulkCreate(List<CreateProjectQuestionRequest> requests) {
        UUID clientId = getClientId();
        List<ProjectQuestionDTO> results = new ArrayList<>();

        for (CreateProjectQuestionRequest request : requests) {
            ProjectQuestionBank entity = new ProjectQuestionBank();
            entity.setId(UlidGenerator.nextUlid());
            entity.setClientId(clientId);
            entity.setCourseId(request.getCourseId());
            entity.setTitle(request.getTitle());
            entity.setProblemStatement(request.getProblemStatement());
            entity.setKeyTechnologies(request.getKeyTechnologies());
            entity.setTags(request.getTags());
            entity.setDifficulty(request.getDifficulty());
            entity.setIsActive(true);

            entity = repository.save(entity);
            results.add(ProjectQuestionDTO.fromEntity(entity));
        }

        log.info("Bulk created {} project questions", results.size());
        return results;
    }
}
