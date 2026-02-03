package com.datagami.edudron.identity.web;

import com.datagami.edudron.identity.dto.AuditLogDTO;
import com.datagami.edudron.identity.service.AuditLogQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;

@RestController
@RequestMapping("/idp/audit-logs")
@Tag(name = "Audit Logs", description = "CRUD audit log query (admin). Who changed what, when.")
public class AuditLogController {

    @Autowired
    private AuditLogQueryService auditLogQueryService;

    @GetMapping
    @Operation(summary = "List audit logs (paginated)", description = "Get paginated CRUD audit logs. Optional filters: entity, action, actor, from, to.")
    public ResponseEntity<Page<AuditLogDTO>> getAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String entity,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        Page<AuditLogDTO> result = auditLogQueryService.getAuditLogs(pageable, entity, action, actor, from, to);
        return ResponseEntity.ok(result);
    }
}
