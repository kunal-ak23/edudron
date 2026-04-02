package com.datagami.edudron.student.web;

import com.datagami.edudron.student.dto.CertificateTemplateDTO;
import com.datagami.edudron.student.service.CertificateTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
    public ResponseEntity<CertificateTemplateDTO> createTemplate(@Valid @RequestBody CertificateTemplateDTO dto) {
        CertificateTemplateDTO created = templateService.createTemplate(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update template", description = "Update an existing certificate template")
    public ResponseEntity<CertificateTemplateDTO> updateTemplate(@PathVariable String id,
                                                                  @Valid @RequestBody CertificateTemplateDTO dto) {
        return ResponseEntity.ok(templateService.updateTemplate(id, dto));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete template", description = "Soft-delete a certificate template")
    public ResponseEntity<Void> deleteTemplate(@PathVariable String id) {
        templateService.deleteTemplate(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/export")
    @Operation(summary = "Export template as ZIP", description = "Download template as a ZIP bundle with config and assets")
    public ResponseEntity<byte[]> exportTemplate(@PathVariable String id) {
        byte[] zipBytes = templateService.exportTemplate(id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", "template-" + id + ".zip");
        return new ResponseEntity<>(zipBytes, headers, HttpStatus.OK);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Import template from ZIP", description = "Upload a ZIP bundle to create a new template")
    public ResponseEntity<CertificateTemplateDTO> importTemplate(@RequestParam("file") MultipartFile file) {
        CertificateTemplateDTO created = templateService.importTemplate(file);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
