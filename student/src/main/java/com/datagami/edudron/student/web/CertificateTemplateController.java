package com.datagami.edudron.student.web;

import com.datagami.edudron.student.dto.CertificateTemplateDTO;
import com.datagami.edudron.student.service.CertificateTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/certificates/templates")
@Tag(name = "Certificate Templates", description = "Manage certificate templates")
public class CertificateTemplateController {

    private final CertificateTemplateService templateService;

    public CertificateTemplateController(CertificateTemplateService templateService) {
        this.templateService = templateService;
    }

    @GetMapping
    @Operation(summary = "List templates", description = "List all certificate templates available to the current tenant (tenant-specific + system defaults)")
    public ResponseEntity<List<CertificateTemplateDTO>> listTemplates() {
        return ResponseEntity.ok(templateService.listTemplates());
    }

    @PostMapping
    @Operation(summary = "Create template", description = "Create a new tenant-scoped certificate template")
    public ResponseEntity<CertificateTemplateDTO> createTemplate(@RequestBody CertificateTemplateDTO dto) {
        CertificateTemplateDTO created = templateService.createTemplate(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update template", description = "Update an existing certificate template")
    public ResponseEntity<CertificateTemplateDTO> updateTemplate(@PathVariable String id,
                                                                  @RequestBody CertificateTemplateDTO dto) {
        return ResponseEntity.ok(templateService.updateTemplate(id, dto));
    }
}
