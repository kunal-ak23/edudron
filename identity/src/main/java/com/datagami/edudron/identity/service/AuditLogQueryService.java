package com.datagami.edudron.identity.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.common.domain.AuditLog;
import com.datagami.edudron.identity.dto.AuditLogDTO;
import com.datagami.edudron.identity.repo.AuditLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class AuditLogQueryService {

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Transactional(readOnly = true)
    public Page<AuditLogDTO> getAuditLogs(Pageable pageable, String entity, String action, String actor,
                                          OffsetDateTime from, OffsetDateTime to) {
        String clientIdStr = TenantContext.getClientId();
        UUID clientId = null;
        if (clientIdStr != null && !"SYSTEM".equals(clientIdStr) && !"PENDING_TENANT_SELECTION".equals(clientIdStr)) {
            try {
                clientId = UUID.fromString(clientIdStr);
            } catch (IllegalArgumentException ignored) {
            }
        }

        Specification<AuditLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (clientId != null) {
                predicates.add(cb.equal(root.get("clientId"), clientId));
            }
            if (entity != null && !entity.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("entity")), entity.toLowerCase().trim()));
            }
            if (action != null && !action.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("action")), action.toLowerCase().trim()));
            }
            if (actor != null && !actor.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("actor")), "%" + actor.toLowerCase().trim() + "%"));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return auditLogRepository.findAll(spec, pageable).map(this::toDTO);
    }

    private AuditLogDTO toDTO(AuditLog log) {
        AuditLogDTO dto = new AuditLogDTO();
        dto.setId(log.getId());
        dto.setClientId(log.getClientId());
        dto.setActor(log.getActor());
        dto.setAction(log.getAction());
        dto.setEntity(log.getEntity());
        dto.setEntityId(log.getEntityId());
        dto.setMeta(log.getMeta());
        dto.setCreatedAt(log.getCreatedAt());
        return dto;
    }
}
