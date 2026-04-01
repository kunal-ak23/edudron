# Results Export & Certificate Generation — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add Excel results export (by section/class/course) and PDF certificate generation with QR-code verification to the EduDron platform.

**Architecture:** Results export lives in the student module (where scores are), making cross-service REST calls to content service for assessment metadata. Certificate generation also lives in student module, using PDFBox for PDF rendering and ZXing for QR codes. PDFs upload to Azure Blob Storage via the existing `AzureStorageConfig`. A new `certificate` schema holds templates, certificates, and visibility settings. Frontend gets new API classes in shared-utils and new pages in both admin-dashboard and student-portal.

**Tech Stack:** Apache POI (already in student/build.gradle), Apache PDFBox 2.0.31, ZXing 3.5.2, Azure Blob Storage (existing), Next.js App Router, shadcn/ui, React Query.

**Design Doc:** `docs/plans/2026-03-29-results-export-and-certificate-design.md`

---

## Phase 1: Database Schema & Entity Layer

### Task 1: Add PDFBox and ZXing dependencies

**Files:**
- Modify: `student/build.gradle`

**Step 1: Add dependencies**

Add to the `dependencies` block in `student/build.gradle`:

```groovy
// PDF generation
implementation 'org.apache.pdfbox:pdfbox:2.0.31'

// QR code generation
implementation 'com.google.zxing:core:3.5.2'
implementation 'com.google.zxing:javase:3.5.2'
```

**Step 2: Verify build compiles**

Run: `cd student && ../gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add student/build.gradle
git commit -m "feat: add PDFBox and ZXing dependencies for certificate generation"
```

---

### Task 2: Liquibase migration for certificate schema

**Files:**
- Create: `student/src/main/resources/db/changelog/db.changelog-0031-certificate-schema.yaml`
- Modify: `student/src/main/resources/db/changelog/student-master.yaml`

**Step 1: Create migration file**

Create `db.changelog-0031-certificate-schema.yaml`:

```yaml
databaseChangeLog:
  - changeSet:
      id: 0031-01-create-certificate-schema
      author: edudron
      changes:
        - sql:
            sql: CREATE SCHEMA IF NOT EXISTS certificate;

  - changeSet:
      id: 0031-02-create-certificate-templates
      author: edudron
      changes:
        - createTable:
            schemaName: certificate
            tableName: certificate_templates
            columns:
              - column:
                  name: id
                  type: VARCHAR(26)
                  constraints:
                    primaryKey: true
                    nullable: false
              - column:
                  name: client_id
                  type: UUID
                  remarks: "Null = system-wide default template"
              - column:
                  name: name
                  type: VARCHAR(255)
                  constraints:
                    nullable: false
              - column:
                  name: description
                  type: TEXT
              - column:
                  name: config
                  type: JSONB
                  constraints:
                    nullable: false
              - column:
                  name: background_image_url
                  type: VARCHAR(500)
              - column:
                  name: is_default
                  type: BOOLEAN
                  defaultValueBoolean: false
              - column:
                  name: is_active
                  type: BOOLEAN
                  defaultValueBoolean: true
              - column:
                  name: created_at
                  type: TIMESTAMPTZ
                  defaultValueComputed: NOW()
              - column:
                  name: updated_at
                  type: TIMESTAMPTZ
                  defaultValueComputed: NOW()
        - createIndex:
            schemaName: certificate
            tableName: certificate_templates
            indexName: idx_cert_templates_client_id
            columns:
              - column:
                  name: client_id

  - changeSet:
      id: 0031-03-create-certificates
      author: edudron
      changes:
        - createTable:
            schemaName: certificate
            tableName: certificates
            columns:
              - column:
                  name: id
                  type: VARCHAR(26)
                  constraints:
                    primaryKey: true
                    nullable: false
              - column:
                  name: client_id
                  type: UUID
                  constraints:
                    nullable: false
              - column:
                  name: student_id
                  type: VARCHAR(26)
                  constraints:
                    nullable: false
              - column:
                  name: course_id
                  type: VARCHAR(26)
                  constraints:
                    nullable: false
              - column:
                  name: section_id
                  type: VARCHAR(26)
              - column:
                  name: class_id
                  type: VARCHAR(26)
              - column:
                  name: template_id
                  type: VARCHAR(26)
                  constraints:
                    nullable: false
                    foreignKeyName: fk_cert_template
                    references: certificate.certificate_templates(id)
              - column:
                  name: credential_id
                  type: VARCHAR(20)
                  constraints:
                    nullable: false
                    unique: true
              - column:
                  name: qr_code_url
                  type: VARCHAR(500)
              - column:
                  name: pdf_url
                  type: VARCHAR(500)
              - column:
                  name: issued_at
                  type: TIMESTAMPTZ
                  defaultValueComputed: NOW()
              - column:
                  name: issued_by
                  type: VARCHAR(26)
                  constraints:
                    nullable: false
              - column:
                  name: revoked_at
                  type: TIMESTAMPTZ
              - column:
                  name: revoked_reason
                  type: TEXT
              - column:
                  name: metadata
                  type: JSONB
              - column:
                  name: is_active
                  type: BOOLEAN
                  defaultValueBoolean: true
              - column:
                  name: created_at
                  type: TIMESTAMPTZ
                  defaultValueComputed: NOW()
              - column:
                  name: updated_at
                  type: TIMESTAMPTZ
                  defaultValueComputed: NOW()
        - createIndex:
            schemaName: certificate
            tableName: certificates
            indexName: idx_certs_client_student
            columns:
              - column:
                  name: client_id
              - column:
                  name: student_id
        - createIndex:
            schemaName: certificate
            tableName: certificates
            indexName: idx_certs_client_course
            columns:
              - column:
                  name: client_id
              - column:
                  name: course_id
        - createIndex:
            schemaName: certificate
            tableName: certificates
            indexName: idx_certs_credential_id
            unique: true
            columns:
              - column:
                  name: credential_id

  - changeSet:
      id: 0031-04-create-certificate-visibility
      author: edudron
      changes:
        - createTable:
            schemaName: certificate
            tableName: certificate_visibility
            columns:
              - column:
                  name: id
                  type: VARCHAR(26)
                  constraints:
                    primaryKey: true
                    nullable: false
              - column:
                  name: client_id
                  type: UUID
                  constraints:
                    nullable: false
              - column:
                  name: student_id
                  type: VARCHAR(26)
                  constraints:
                    nullable: false
              - column:
                  name: certificate_id
                  type: VARCHAR(26)
                  constraints:
                    nullable: false
                    foreignKeyName: fk_visibility_cert
                    references: certificate.certificates(id)
              - column:
                  name: show_scores
                  type: BOOLEAN
                  defaultValueBoolean: true
              - column:
                  name: show_project_details
                  type: BOOLEAN
                  defaultValueBoolean: true
              - column:
                  name: show_overall_percentage
                  type: BOOLEAN
                  defaultValueBoolean: true
              - column:
                  name: show_course_name
                  type: BOOLEAN
                  defaultValueBoolean: true
        - createIndex:
            schemaName: certificate
            tableName: certificate_visibility
            indexName: idx_cert_visibility_cert_id
            unique: true
            columns:
              - column:
                  name: certificate_id

  - changeSet:
      id: 0031-05-seed-default-templates
      author: edudron
      changes:
        - sql:
            sql: |
              INSERT INTO certificate.certificate_templates (id, client_id, name, description, config, is_default, is_active, created_at, updated_at)
              VALUES
              ('DEFAULT_CLASSIC_001', NULL, 'Classic Certificate', 'Traditional certificate layout with centered text',
               '{"fields":[{"type":"customText","x":421,"y":120,"text":"Certificate of Completion","fontSize":32,"fontWeight":"bold","color":"#1E3A5F"},{"type":"studentName","x":421,"y":260,"fontSize":36,"fontWeight":"bold","color":"#1E3A5F"},{"type":"customText","x":421,"y":320,"text":"has successfully completed the course","fontSize":16,"color":"#333333"},{"type":"courseName","x":421,"y":370,"fontSize":22,"fontWeight":"bold","color":"#0891B2"},{"type":"date","x":421,"y":430,"fontSize":14,"format":"MMMM dd, yyyy","color":"#666666"},{"type":"credentialId","x":60,"y":550,"fontSize":10,"color":"#999999"},{"type":"qrCode","x":700,"y":460,"size":100}],"pageSize":{"width":842,"height":595},"orientation":"landscape"}'::jsonb,
               true, true, NOW(), NOW()),
              ('DEFAULT_MODERN_002', NULL, 'Modern Certificate', 'Clean modern layout with accent colors',
               '{"fields":[{"type":"customText","x":421,"y":100,"text":"CERTIFICATE OF COMPLETION","fontSize":28,"fontWeight":"bold","color":"#0891B2"},{"type":"customText","x":421,"y":200,"text":"This certifies that","fontSize":14,"color":"#666666"},{"type":"studentName","x":421,"y":250,"fontSize":34,"fontWeight":"bold","color":"#1E3A5F"},{"type":"customText","x":421,"y":310,"text":"has completed","fontSize":14,"color":"#666666"},{"type":"courseName","x":421,"y":355,"fontSize":22,"fontWeight":"bold","color":"#1E3A5F"},{"type":"date","x":421,"y":420,"fontSize":14,"format":"dd MMMM yyyy","color":"#666666"},{"type":"credentialId","x":60,"y":555,"fontSize":9,"color":"#AAAAAA"},{"type":"qrCode","x":710,"y":470,"size":90}],"pageSize":{"width":842,"height":595},"orientation":"landscape"}'::jsonb,
               true, true, NOW(), NOW());

```

**Step 2: Add migration to master changelog**

Append to the `include` list in `student/src/main/resources/db/changelog/student-master.yaml`:

```yaml
  - include:
      file: db/changelog/db.changelog-0031-certificate-schema.yaml
```

**Step 3: Add certificate schema to LiquibaseConfig**

Modify `core-api/src/main/java/com/datagami/edudron/coreapi/config/LiquibaseConfig.java` — add a new bean for the certificate schema that depends on `studentLiquibase`. Update the `paymentLiquibase` bean to `@DependsOn("certificateLiquibase")` instead of `studentLiquibase`.

```java
@Bean
@DependsOn("studentLiquibase")
public SpringLiquibase certificateLiquibase(DataSource dataSource) {
    SpringLiquibase liquibase = new SpringLiquibase();
    liquibase.setDataSource(dataSource);
    liquibase.setDefaultSchema("certificate");
    liquibase.setLiquibaseSchema("certificate");
    liquibase.setChangeLog("classpath:db/changelog/student-master.yaml");
    return liquibase;
}
```

> **Note:** The certificate tables are defined in the student-master.yaml changelog but specify `schemaName: certificate`. The existing `studentLiquibase` bean will execute these changesets. You do NOT need a separate `certificateLiquibase` bean — just ensure the `student` schema user has `CREATE SCHEMA` privileges, or pre-create the schema. The `0031-01` changeset handles `CREATE SCHEMA IF NOT EXISTS certificate`.

**Step 4: Verify migration runs**

Run: `cd core-api && ../gradlew bootRun` (let it start, verify no Liquibase errors, then stop)
Expected: Tables `certificate.certificate_templates`, `certificate.certificates`, `certificate.certificate_visibility` created. Two default templates seeded.

**Step 5: Commit**

```bash
git add student/src/main/resources/db/changelog/db.changelog-0031-certificate-schema.yaml
git add student/src/main/resources/db/changelog/student-master.yaml
git commit -m "feat: add certificate schema with templates, certificates, and visibility tables"
```

---

### Task 3: JPA entities for certificate tables

**Files:**
- Create: `student/src/main/java/com/datagami/edudron/student/domain/CertificateTemplate.java`
- Create: `student/src/main/java/com/datagami/edudron/student/domain/Certificate.java`
- Create: `student/src/main/java/com/datagami/edudron/student/domain/CertificateVisibility.java`

**Step 1: Create CertificateTemplate entity**

```java
package com.datagami.edudron.student.domain;

import com.datagami.edudron.common.UlidGenerator;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "certificate_templates", schema = "certificate")
public class CertificateTemplate {

    @Id
    private String id;

    @Column(name = "client_id")
    private UUID clientId;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(columnDefinition = "jsonb", nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> config;

    @Column(name = "background_image_url")
    private String backgroundImageUrl;

    @Column(name = "is_default")
    private boolean isDefault;

    @Column(name = "is_active")
    private boolean isActive = true;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (id == null) id = UlidGenerator.generate();
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    // Getters and setters for all fields
    // ... (generate standard getters/setters)
}
```

**Step 2: Create Certificate entity**

```java
package com.datagami.edudron.student.domain;

import com.datagami.edudron.common.UlidGenerator;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "certificates", schema = "certificate")
public class Certificate {

    @Id
    private String id;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "student_id", nullable = false)
    private String studentId;

    @Column(name = "course_id", nullable = false)
    private String courseId;

    @Column(name = "section_id")
    private String sectionId;

    @Column(name = "class_id")
    private String classId;

    @Column(name = "template_id", nullable = false)
    private String templateId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", insertable = false, updatable = false)
    private CertificateTemplate template;

    @Column(name = "credential_id", nullable = false, unique = true, length = 20)
    private String credentialId;

    @Column(name = "qr_code_url")
    private String qrCodeUrl;

    @Column(name = "pdf_url")
    private String pdfUrl;

    @Column(name = "issued_at")
    private OffsetDateTime issuedAt;

    @Column(name = "issued_by", nullable = false)
    private String issuedBy;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @Column(name = "revoked_reason")
    private String revokedReason;

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> metadata;

    @Column(name = "is_active")
    private boolean isActive = true;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @OneToOne(mappedBy = "certificate", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private CertificateVisibility visibility;

    @PrePersist
    public void prePersist() {
        if (id == null) id = UlidGenerator.generate();
        issuedAt = OffsetDateTime.now();
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    // Getters and setters for all fields
}
```

**Step 3: Create CertificateVisibility entity**

```java
package com.datagami.edudron.student.domain;

import com.datagami.edudron.common.UlidGenerator;
import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "certificate_visibility", schema = "certificate")
public class CertificateVisibility {

    @Id
    private String id;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "student_id", nullable = false)
    private String studentId;

    @Column(name = "certificate_id", nullable = false, unique = true)
    private String certificateId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "certificate_id", insertable = false, updatable = false)
    private Certificate certificate;

    @Column(name = "show_scores")
    private boolean showScores = true;

    @Column(name = "show_project_details")
    private boolean showProjectDetails = true;

    @Column(name = "show_overall_percentage")
    private boolean showOverallPercentage = true;

    @Column(name = "show_course_name")
    private boolean showCourseName = true;

    @PrePersist
    public void prePersist() {
        if (id == null) id = UlidGenerator.generate();
    }

    // Getters and setters for all fields
}
```

**Step 4: Verify compilation**

Run: `cd student && ../gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add student/src/main/java/com/datagami/edudron/student/domain/CertificateTemplate.java
git add student/src/main/java/com/datagami/edudron/student/domain/Certificate.java
git add student/src/main/java/com/datagami/edudron/student/domain/CertificateVisibility.java
git commit -m "feat: add JPA entities for certificate templates, certificates, and visibility"
```

---

### Task 4: Repositories for certificate entities

**Files:**
- Create: `student/src/main/java/com/datagami/edudron/student/repo/CertificateTemplateRepository.java`
- Create: `student/src/main/java/com/datagami/edudron/student/repo/CertificateRepository.java`
- Create: `student/src/main/java/com/datagami/edudron/student/repo/CertificateVisibilityRepository.java`

**Step 1: Create repositories**

```java
// CertificateTemplateRepository.java
package com.datagami.edudron.student.repo;

import com.datagami.edudron.student.domain.CertificateTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface CertificateTemplateRepository extends JpaRepository<CertificateTemplate, String> {
    List<CertificateTemplate> findByClientIdAndIsActiveTrue(UUID clientId);
    List<CertificateTemplate> findByClientIdIsNullAndIsDefaultTrueAndIsActiveTrue();
    List<CertificateTemplate> findByIsActiveTrue();
}
```

```java
// CertificateRepository.java
package com.datagami.edudron.student.repo;

import com.datagami.edudron.student.domain.Certificate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CertificateRepository extends JpaRepository<Certificate, String> {
    Page<Certificate> findByClientIdAndIsActiveTrue(UUID clientId, Pageable pageable);
    Page<Certificate> findByClientIdAndCourseIdAndIsActiveTrue(UUID clientId, String courseId, Pageable pageable);
    Page<Certificate> findByClientIdAndSectionIdAndIsActiveTrue(UUID clientId, String sectionId, Pageable pageable);
    Page<Certificate> findByClientIdAndSectionIdAndCourseIdAndIsActiveTrue(UUID clientId, String sectionId, String courseId, Pageable pageable);
    List<Certificate> findByClientIdAndStudentIdAndIsActiveTrue(UUID clientId, String studentId);
    Optional<Certificate> findByCredentialId(String credentialId);
    Optional<Certificate> findByClientIdAndStudentIdAndCourseIdAndIsActiveTrue(UUID clientId, String studentId, String courseId);
    List<Certificate> findByClientIdAndCourseIdAndSectionIdAndIsActiveTrue(UUID clientId, String courseId, String sectionId);
}
```

```java
// CertificateVisibilityRepository.java
package com.datagami.edudron.student.repo;

import com.datagami.edudron.student.domain.CertificateVisibility;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CertificateVisibilityRepository extends JpaRepository<CertificateVisibility, String> {
    Optional<CertificateVisibility> findByCertificateId(String certificateId);
}
```

**Step 2: Verify compilation**

Run: `cd student && ../gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add student/src/main/java/com/datagami/edudron/student/repo/CertificateTemplateRepository.java
git add student/src/main/java/com/datagami/edudron/student/repo/CertificateRepository.java
git add student/src/main/java/com/datagami/edudron/student/repo/CertificateVisibilityRepository.java
git commit -m "feat: add repositories for certificate entities"
```

---

### Task 5: DTOs for results export and certificates

**Files:**
- Create: `student/src/main/java/com/datagami/edudron/student/dto/ResultsExportRequest.java`
- Create: `student/src/main/java/com/datagami/edudron/student/dto/CertificateDTO.java`
- Create: `student/src/main/java/com/datagami/edudron/student/dto/CertificateTemplateDTO.java`
- Create: `student/src/main/java/com/datagami/edudron/student/dto/CertificateGenerateRequest.java`
- Create: `student/src/main/java/com/datagami/edudron/student/dto/CertificateVisibilityDTO.java`
- Create: `student/src/main/java/com/datagami/edudron/student/dto/CertificateVerificationDTO.java`

**Step 1: Create all DTOs**

```java
// ResultsExportRequest.java — not needed, query params are sufficient

// CertificateTemplateDTO.java
package com.datagami.edudron.student.dto;

import java.time.OffsetDateTime;
import java.util.Map;

public class CertificateTemplateDTO {
    private String id;
    private String name;
    private String description;
    private Map<String, Object> config;
    private String backgroundImageUrl;
    private boolean isDefault;
    private OffsetDateTime createdAt;
    // Getters and setters
}
```

```java
// CertificateDTO.java
package com.datagami.edudron.student.dto;

import java.time.OffsetDateTime;
import java.util.Map;

public class CertificateDTO {
    private String id;
    private String studentId;
    private String studentName;
    private String studentEmail;
    private String courseId;
    private String courseName;
    private String sectionId;
    private String classId;
    private String templateId;
    private String credentialId;
    private String qrCodeUrl;
    private String pdfUrl;
    private OffsetDateTime issuedAt;
    private String issuedBy;
    private boolean revoked;
    private OffsetDateTime revokedAt;
    private String revokedReason;
    private Map<String, Object> metadata;
    private CertificateVisibilityDTO visibility;
    // Getters and setters
}
```

```java
// CertificateGenerateRequest.java
package com.datagami.edudron.student.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public class CertificateGenerateRequest {
    @NotBlank
    private String courseId;
    private String sectionId;
    private String classId;
    @NotBlank
    private String templateId;
    private List<StudentEntry> students;

    public static class StudentEntry {
        private String name;
        private String email;
        // Getters and setters
    }
    // Getters and setters
}
```

```java
// CertificateVisibilityDTO.java
package com.datagami.edudron.student.dto;

public class CertificateVisibilityDTO {
    private boolean showScores;
    private boolean showProjectDetails;
    private boolean showOverallPercentage;
    private boolean showCourseName;
    // Getters and setters
}
```

```java
// CertificateVerificationDTO.java
package com.datagami.edudron.student.dto;

import java.time.OffsetDateTime;
import java.util.Map;

public class CertificateVerificationDTO {
    private String credentialId;
    private String studentName;
    private String courseName;
    private String institutionName;
    private String institutionLogoUrl;
    private OffsetDateTime issuedAt;
    private boolean valid;
    private boolean revoked;
    private OffsetDateTime revokedAt;
    private String revokedReason;
    private String pdfUrl;
    private Map<String, Object> scores;
    private CertificateVisibilityDTO visibility;
    // Getters and setters
}
```

**Step 2: Verify compilation**

Run: `cd student && ../gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add student/src/main/java/com/datagami/edudron/student/dto/Certificate*.java
git commit -m "feat: add DTOs for certificate templates, generation, verification, and visibility"
```

---

## Phase 2: Results Export (Backend)

### Task 6: Content assessment client for cross-service calls

The student module needs assessment metadata (title, type, maxScore) from the content service. Follow the existing `ContentExamClient` pattern.

**Files:**
- Create: `student/src/main/java/com/datagami/edudron/student/client/ContentAssessmentClient.java`

**Step 1: Create the client**

```java
package com.datagami.edudron.student.client;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.common.TenantContextRestTemplateInterceptor;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collections;
import java.util.List;

@Component
public class ContentAssessmentClient {

    private static final Logger log = LoggerFactory.getLogger(ContentAssessmentClient.class);

    @Value("${GATEWAY_URL:http://localhost:8080}")
    private String gatewayUrl;

    private volatile RestTemplate restTemplate;

    private RestTemplate getRestTemplate() {
        if (restTemplate == null) {
            synchronized (this) {
                if (restTemplate == null) {
                    RestTemplate rt = new RestTemplate();
                    rt.getInterceptors().add(new TenantContextRestTemplateInterceptor());
                    rt.getInterceptors().add((request, body, execution) -> {
                        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                        if (attrs != null) {
                            String authHeader = attrs.getRequest().getHeader("Authorization");
                            if (authHeader != null) {
                                request.getHeaders().set("Authorization", authHeader);
                            }
                        }
                        return execution.execute(request, body);
                    });
                    restTemplate = rt;
                }
            }
        }
        return restTemplate;
    }

    /**
     * Fetch all assessments for a course from the content service.
     * Returns a list of JsonNode, each containing: id, title, assessmentType, passingScorePercentage, etc.
     */
    public List<JsonNode> getAssessmentsForCourse(String courseId) {
        try {
            String url = gatewayUrl + "/api/exams?courseId=" + courseId + "&size=200";
            ResponseEntity<JsonNode> response = getRestTemplate().exchange(
                url, HttpMethod.GET, null, JsonNode.class
            );
            JsonNode body = response.getBody();
            if (body != null && body.has("content")) {
                return body.get("content").findValues(""); // Will iterate properly
            }
            // Handle as array or paginated
            if (body != null && body.isArray()) {
                List<JsonNode> result = new java.util.ArrayList<>();
                body.forEach(result::add);
                return result;
            }
            if (body != null && body.has("content") && body.get("content").isArray()) {
                List<JsonNode> result = new java.util.ArrayList<>();
                body.get("content").forEach(result::add);
                return result;
            }
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Failed to fetch assessments for course {}: {}", courseId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Fetch course details (name, etc.) from content service.
     */
    public JsonNode getCourse(String courseId) {
        try {
            String url = gatewayUrl + "/content/courses/" + courseId;
            ResponseEntity<JsonNode> response = getRestTemplate().exchange(
                url, HttpMethod.GET, null, JsonNode.class
            );
            return response.getBody();
        } catch (Exception e) {
            log.error("Failed to fetch course {}: {}", courseId, e.getMessage());
            return null;
        }
    }
}
```

> **Important:** Check the actual exam listing endpoint path and response shape. The content service `ExamController` is at `/api/exams`. The response is a Spring Data `Page`, so access `content` array. Adjust parsing based on actual response format during integration testing.

**Step 2: Verify compilation**

Run: `cd student && ../gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add student/src/main/java/com/datagami/edudron/student/client/ContentAssessmentClient.java
git commit -m "feat: add ContentAssessmentClient for cross-service assessment metadata fetching"
```

---

### Task 7: ResultsExportService

**Files:**
- Create: `student/src/main/java/com/datagami/edudron/student/service/ResultsExportService.java`

**Step 1: Create the service**

This is a large service. Key responsibilities:
1. Resolve scope (section/class/course) → list of students + list of courses
2. For each course, fetch assessment metadata from content service
3. For each student+course, fetch assessment submissions + project event grades
4. Generate Excel workbook with summary sheet + per-course detail sheets

```java
package com.datagami.edudron.student.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.student.client.ContentAssessmentClient;
import com.datagami.edudron.student.domain.*;
import com.datagami.edudron.student.repo.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ResultsExportService {

    private static final Logger log = LoggerFactory.getLogger(ResultsExportService.class);

    private final EnrollmentRepository enrollmentRepository;
    private final AssessmentSubmissionRepository assessmentSubmissionRepository;
    private final ProjectEventRepository projectEventRepository;
    private final ProjectEventGradeRepository projectEventGradeRepository;
    private final SectionRepository sectionRepository;
    private final ClassRepository classRepository;
    private final ContentAssessmentClient contentAssessmentClient;
    private final StudentAuditService auditService;

    // Constructor injection with all dependencies

    @Transactional(readOnly = true)
    public byte[] exportBySection(String sectionId) throws IOException {
        UUID clientId = UUID.fromString(TenantContext.getClientId());
        Section section = sectionRepository.findById(sectionId)
            .orElseThrow(() -> new IllegalArgumentException("Section not found"));

        // Get enrollments for this section
        List<Enrollment> enrollments = enrollmentRepository
            .findByClientIdAndSectionIdAndActiveTrue(clientId, sectionId);

        // Get unique course IDs from enrollments
        List<String> courseIds = enrollments.stream()
            .map(Enrollment::getCourseId)
            .distinct()
            .collect(Collectors.toList());

        return generateWorkbook(clientId, enrollments, courseIds, "Section: " + sectionId);
    }

    @Transactional(readOnly = true)
    public byte[] exportByClass(String classId) throws IOException {
        UUID clientId = UUID.fromString(TenantContext.getClientId());

        // Get all sections in this class
        List<Section> sections = sectionRepository.findByClientIdAndClassIdAndIsActiveTrue(clientId, classId);
        List<String> sectionIds = sections.stream().map(Section::getId).collect(Collectors.toList());

        // Get enrollments across all sections
        List<Enrollment> enrollments = new ArrayList<>();
        for (String sectionId : sectionIds) {
            enrollments.addAll(enrollmentRepository
                .findByClientIdAndSectionIdAndActiveTrue(clientId, sectionId));
        }

        List<String> courseIds = enrollments.stream()
            .map(Enrollment::getCourseId)
            .distinct()
            .collect(Collectors.toList());

        return generateWorkbook(clientId, enrollments, courseIds, "Class: " + classId);
    }

    @Transactional(readOnly = true)
    public byte[] exportByCourse(String courseId) throws IOException {
        UUID clientId = UUID.fromString(TenantContext.getClientId());

        List<Enrollment> enrollments = enrollmentRepository
            .findByClientIdAndCourseId(clientId, courseId);

        return generateWorkbook(clientId, enrollments, List.of(courseId), "Course: " + courseId);
    }

    private byte[] generateWorkbook(UUID clientId, List<Enrollment> enrollments,
                                     List<String> courseIds, String scopeLabel) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            // Style setup
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle percentStyle = workbook.createCellStyle();
            percentStyle.setDataFormat(workbook.createDataFormat().getFormat("0.00"));

            // Build student info map: studentId -> {name, email} from enrollments
            // (enrollment has student reference)
            Map<String, Enrollment> studentEnrollments = new LinkedHashMap<>();
            for (Enrollment e : enrollments) {
                studentEnrollments.putIfAbsent(e.getStudentId(), e);
            }
            List<String> studentIds = new ArrayList<>(studentEnrollments.keySet());

            // Course data: courseId -> {name, assessments, projectEvents}
            Map<String, CourseData> courseDataMap = new LinkedHashMap<>();
            for (String courseId : courseIds) {
                CourseData cd = new CourseData();
                JsonNode courseNode = contentAssessmentClient.getCourse(courseId);
                cd.courseName = courseNode != null && courseNode.has("title")
                    ? courseNode.get("title").asText() : courseId;

                // Fetch assessments from content service
                cd.assessments = contentAssessmentClient.getAssessmentsForCourse(courseId);

                // Fetch project events from student DB
                cd.projectEvents = projectEventRepository
                    .findByClientIdAndCourseIdOrderBySequence(clientId, courseId);

                courseDataMap.put(courseId, cd);
            }

            // === Summary Sheet ===
            Sheet summary = workbook.createSheet("Summary");
            int summaryRow = 0;
            Row headerRow = summary.createRow(summaryRow++);
            int col = 0;
            createCell(headerRow, col++, "Student Name", headerStyle);
            createCell(headerRow, col++, "Email", headerStyle);
            for (String courseId : courseIds) {
                String name = courseDataMap.get(courseId).courseName;
                createCell(headerRow, col++, name + " Total", headerStyle);
                createCell(headerRow, col++, name + " %", headerStyle);
            }
            createCell(headerRow, col++, "Grand Total", headerStyle);
            createCell(headerRow, col++, "Overall %", headerStyle);

            for (String studentId : studentIds) {
                Row row = summary.createRow(summaryRow++);
                Enrollment enrollment = studentEnrollments.get(studentId);
                int c = 0;
                row.createCell(c++).setCellValue(enrollment.getStudentName() != null
                    ? enrollment.getStudentName() : studentId);
                row.createCell(c++).setCellValue(enrollment.getStudentEmail() != null
                    ? enrollment.getStudentEmail() : "");

                BigDecimal grandTotal = BigDecimal.ZERO;
                BigDecimal grandMax = BigDecimal.ZERO;

                for (String courseId : courseIds) {
                    BigDecimal[] courseScores = calculateCourseScores(
                        clientId, studentId, courseId, courseDataMap.get(courseId));
                    BigDecimal courseTotal = courseScores[0];
                    BigDecimal courseMax = courseScores[1];

                    row.createCell(c++).setCellValue(courseTotal.doubleValue());
                    Cell pctCell = row.createCell(c++);
                    pctCell.setCellValue(courseMax.compareTo(BigDecimal.ZERO) > 0
                        ? courseTotal.multiply(BigDecimal.valueOf(100))
                            .divide(courseMax, 2, RoundingMode.HALF_UP).doubleValue()
                        : 0);
                    pctCell.setCellStyle(percentStyle);

                    grandTotal = grandTotal.add(courseTotal);
                    grandMax = grandMax.add(courseMax);
                }

                row.createCell(c++).setCellValue(grandTotal.doubleValue());
                Cell overallPct = row.createCell(c++);
                overallPct.setCellValue(grandMax.compareTo(BigDecimal.ZERO) > 0
                    ? grandTotal.multiply(BigDecimal.valueOf(100))
                        .divide(grandMax, 2, RoundingMode.HALF_UP).doubleValue()
                    : 0);
                overallPct.setCellStyle(percentStyle);
            }

            // === Per-Course Detail Sheets ===
            for (String courseId : courseIds) {
                CourseData cd = courseDataMap.get(courseId);
                String sheetName = sanitizeSheetName(cd.courseName);
                Sheet sheet = workbook.createSheet(sheetName);
                writeCourseDetailSheet(sheet, clientId, studentIds, studentEnrollments,
                    courseId, cd, headerStyle, percentStyle);
            }

            // Auto-size columns on summary
            for (int i = 0; i < col; i++) {
                summary.autoSizeColumn(i);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            workbook.write(baos);
            return baos.toByteArray();
        }
    }

    private void writeCourseDetailSheet(Sheet sheet, UUID clientId, List<String> studentIds,
                                         Map<String, Enrollment> enrollmentMap, String courseId,
                                         CourseData cd, CellStyle headerStyle, CellStyle percentStyle) {
        int rowNum = 0;
        Row header = sheet.createRow(rowNum++);
        int col = 0;
        createCell(header, col++, "Student Name", headerStyle);
        createCell(header, col++, "Email", headerStyle);

        // Assessment columns
        for (JsonNode assessment : cd.assessments) {
            String title = assessment.has("title") ? assessment.get("title").asText() : "Assessment";
            createCell(header, col++, title + " (Score/Max)", headerStyle);
        }

        // Project columns
        if (!cd.projectEvents.isEmpty()) {
            createCell(header, col++, "Project Total (Score/Max)", headerStyle);
            for (ProjectEvent pe : cd.projectEvents) {
                createCell(header, col++, pe.getName(), headerStyle);
            }
        }

        createCell(header, col++, "Course Total", headerStyle);
        createCell(header, col++, "Course %", headerStyle);

        // Data rows
        for (String studentId : studentIds) {
            Enrollment enrollment = enrollmentMap.get(studentId);
            Row row = sheet.createRow(rowNum++);
            int c = 0;
            row.createCell(c++).setCellValue(enrollment.getStudentName() != null
                ? enrollment.getStudentName() : studentId);
            row.createCell(c++).setCellValue(enrollment.getStudentEmail() != null
                ? enrollment.getStudentEmail() : "");

            BigDecimal totalScore = BigDecimal.ZERO;
            BigDecimal totalMax = BigDecimal.ZERO;

            // Assessment scores
            List<AssessmentSubmission> submissions = assessmentSubmissionRepository
                .findByClientIdAndStudentIdAndCourseId(clientId, studentId, courseId);
            Map<String, AssessmentSubmission> submissionMap = new HashMap<>();
            for (AssessmentSubmission sub : submissions) {
                // Keep best submission per assessment
                submissionMap.merge(sub.getAssessmentId(), sub, (existing, incoming) ->
                    incoming.getScore() != null && (existing.getScore() == null ||
                        incoming.getScore().compareTo(existing.getScore()) > 0)
                        ? incoming : existing);
            }

            for (JsonNode assessment : cd.assessments) {
                String assessmentId = assessment.get("id").asText();
                BigDecimal maxScore = assessment.has("maxScore")
                    ? new BigDecimal(assessment.get("maxScore").asText()) : BigDecimal.ZERO;
                AssessmentSubmission sub = submissionMap.get(assessmentId);
                BigDecimal score = sub != null && sub.getScore() != null ? sub.getScore() : BigDecimal.ZERO;

                row.createCell(c++).setCellValue(score + "/" + maxScore);
                totalScore = totalScore.add(score);
                totalMax = totalMax.add(maxScore);
            }

            // Project scores
            if (!cd.projectEvents.isEmpty()) {
                BigDecimal projectTotal = BigDecimal.ZERO;
                BigDecimal projectMax = BigDecimal.ZERO;
                List<String> eventScores = new ArrayList<>();

                for (ProjectEvent pe : cd.projectEvents) {
                    ProjectEventGrade grade = projectEventGradeRepository
                        .findByClientIdAndEventIdAndStudentId(clientId, pe.getId(), studentId)
                        .orElse(null);
                    int marks = grade != null && grade.getMarks() != null ? grade.getMarks() : 0;
                    int maxMarks = pe.getMaxMarks() != null ? pe.getMaxMarks() : 0;
                    eventScores.add(marks + "/" + maxMarks);
                    projectTotal = projectTotal.add(BigDecimal.valueOf(marks));
                    projectMax = projectMax.add(BigDecimal.valueOf(maxMarks));
                }

                row.createCell(c++).setCellValue(projectTotal + "/" + projectMax);
                for (String es : eventScores) {
                    row.createCell(c++).setCellValue(es);
                }

                totalScore = totalScore.add(projectTotal);
                totalMax = totalMax.add(projectMax);
            }

            row.createCell(c++).setCellValue(totalScore.doubleValue());
            Cell pctCell = row.createCell(c++);
            pctCell.setCellValue(totalMax.compareTo(BigDecimal.ZERO) > 0
                ? totalScore.multiply(BigDecimal.valueOf(100))
                    .divide(totalMax, 2, RoundingMode.HALF_UP).doubleValue()
                : 0);
            pctCell.setCellStyle(percentStyle);
        }
    }

    private BigDecimal[] calculateCourseScores(UUID clientId, String studentId,
                                                String courseId, CourseData cd) {
        BigDecimal totalScore = BigDecimal.ZERO;
        BigDecimal totalMax = BigDecimal.ZERO;

        // Assessment scores
        List<AssessmentSubmission> submissions = assessmentSubmissionRepository
            .findByClientIdAndStudentIdAndCourseId(clientId, studentId, courseId);
        Map<String, AssessmentSubmission> best = new HashMap<>();
        for (AssessmentSubmission sub : submissions) {
            best.merge(sub.getAssessmentId(), sub, (existing, incoming) ->
                incoming.getScore() != null && (existing.getScore() == null ||
                    incoming.getScore().compareTo(existing.getScore()) > 0)
                    ? incoming : existing);
        }
        for (JsonNode assessment : cd.assessments) {
            String assessmentId = assessment.get("id").asText();
            BigDecimal maxScore = assessment.has("maxScore")
                ? new BigDecimal(assessment.get("maxScore").asText()) : BigDecimal.ZERO;
            AssessmentSubmission sub = best.get(assessmentId);
            BigDecimal score = sub != null && sub.getScore() != null ? sub.getScore() : BigDecimal.ZERO;
            totalScore = totalScore.add(score);
            totalMax = totalMax.add(maxScore);
        }

        // Project scores
        for (ProjectEvent pe : cd.projectEvents) {
            ProjectEventGrade grade = projectEventGradeRepository
                .findByClientIdAndEventIdAndStudentId(clientId, pe.getId(), studentId)
                .orElse(null);
            int marks = grade != null && grade.getMarks() != null ? grade.getMarks() : 0;
            int maxMarks = pe.getMaxMarks() != null ? pe.getMaxMarks() : 0;
            totalScore = totalScore.add(BigDecimal.valueOf(marks));
            totalMax = totalMax.add(BigDecimal.valueOf(maxMarks));
        }

        return new BigDecimal[]{totalScore, totalMax};
    }

    private CellStyle createHeaderStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private void createCell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private String sanitizeSheetName(String name) {
        // Excel sheet names: max 31 chars, no []:*?/\
        String sanitized = name.replaceAll("[\\[\\]:*?/\\\\]", "");
        return sanitized.length() > 31 ? sanitized.substring(0, 31) : sanitized;
    }

    // Inner class to hold course data during export
    private static class CourseData {
        String courseName;
        List<JsonNode> assessments = new ArrayList<>();
        List<ProjectEvent> projectEvents = new ArrayList<>();
    }
}
```

> **Important implementation notes:**
> - The exact repository method names (`findByClientIdAndSectionIdAndActiveTrue`, `findByClientIdAndCourseId`, etc.) need to be verified against actual `EnrollmentRepository`. Check if these methods exist and add them if missing.
> - `Enrollment` may or may not have `studentName`/`studentEmail` fields. If not, you'll need to resolve student names via `IdentityUserClient`. Check the `Enrollment` entity fields.
> - `ProjectEventRepository.findByClientIdAndCourseIdOrderBySequence` may not exist — add it if needed. Projects may link to sections, not courses directly. Check the `ProjectEvent` entity for the actual relationship.

**Step 2: Verify compilation and fix any missing repository methods**

Run: `cd student && ../gradlew compileJava`
Fix any compilation errors (missing repository methods, field name mismatches).

**Step 3: Commit**

```bash
git add student/src/main/java/com/datagami/edudron/student/service/ResultsExportService.java
git commit -m "feat: add ResultsExportService with Excel generation for section/class/course scopes"
```

---

### Task 8: ResultsExportController

**Files:**
- Create: `student/src/main/java/com/datagami/edudron/student/web/ResultsExportController.java`

**Step 1: Create controller**

```java
package com.datagami.edudron.student.web;

import com.datagami.edudron.student.service.ResultsExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/results")
@Tag(name = "Results Export", description = "Export student results as Excel")
public class ResultsExportController {

    private final ResultsExportService resultsExportService;

    public ResultsExportController(ResultsExportService resultsExportService) {
        this.resultsExportService = resultsExportService;
    }

    @GetMapping("/export")
    @Operation(summary = "Export results as Excel workbook")
    public ResponseEntity<byte[]> exportResults(
            @RequestParam(required = false) String sectionId,
            @RequestParam(required = false) String classId,
            @RequestParam(required = false) String courseId) throws Exception {

        if (sectionId == null && classId == null && courseId == null) {
            throw new IllegalArgumentException("One of sectionId, classId, or courseId is required");
        }

        byte[] workbook;
        String filename;

        if (sectionId != null) {
            workbook = resultsExportService.exportBySection(sectionId);
            filename = "results-section-" + sectionId + ".xlsx";
        } else if (classId != null) {
            workbook = resultsExportService.exportByClass(classId);
            filename = "results-class-" + classId + ".xlsx";
        } else {
            workbook = resultsExportService.exportByCourse(courseId);
            filename = "results-course-" + courseId + ".xlsx";
        }

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(workbook);
    }
}
```

**Step 2: Verify compilation**

Run: `cd student && ../gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add student/src/main/java/com/datagami/edudron/student/web/ResultsExportController.java
git commit -m "feat: add ResultsExportController with section/class/course export endpoints"
```

---

## Phase 3: Certificate Generation (Backend)

### Task 9: CertificateTemplateService and Controller

**Files:**
- Create: `student/src/main/java/com/datagami/edudron/student/service/CertificateTemplateService.java`
- Create: `student/src/main/java/com/datagami/edudron/student/web/CertificateTemplateController.java`

**Step 1: Create CertificateTemplateService**

```java
package com.datagami.edudron.student.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.student.domain.CertificateTemplate;
import com.datagami.edudron.student.dto.CertificateTemplateDTO;
import com.datagami.edudron.student.repo.CertificateTemplateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class CertificateTemplateService {

    private final CertificateTemplateRepository templateRepository;
    private final StudentAuditService auditService;

    public CertificateTemplateService(CertificateTemplateRepository templateRepository,
                                       StudentAuditService auditService) {
        this.templateRepository = templateRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<CertificateTemplateDTO> listTemplates() {
        UUID clientId = UUID.fromString(TenantContext.getClientId());
        List<CertificateTemplate> tenantTemplates = templateRepository
            .findByClientIdAndIsActiveTrue(clientId);
        List<CertificateTemplate> systemDefaults = templateRepository
            .findByClientIdIsNullAndIsDefaultTrueAndIsActiveTrue();

        List<CertificateTemplate> all = new ArrayList<>(systemDefaults);
        all.addAll(tenantTemplates);
        return all.stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional
    public CertificateTemplateDTO createTemplate(CertificateTemplateDTO request) {
        UUID clientId = UUID.fromString(TenantContext.getClientId());
        CertificateTemplate template = new CertificateTemplate();
        template.setClientId(clientId);
        template.setName(request.getName());
        template.setDescription(request.getDescription());
        template.setConfig(request.getConfig());
        template.setBackgroundImageUrl(request.getBackgroundImageUrl());
        template.setDefault(false);
        template = templateRepository.save(template);

        auditService.logCrud("CREATE", "CertificateTemplate", template.getId(),
            null, Map.of("name", template.getName()));
        return toDTO(template);
    }

    @Transactional
    public CertificateTemplateDTO updateTemplate(String templateId, CertificateTemplateDTO request) {
        CertificateTemplate template = templateRepository.findById(templateId)
            .orElseThrow(() -> new IllegalArgumentException("Template not found"));
        template.setName(request.getName());
        template.setDescription(request.getDescription());
        template.setConfig(request.getConfig());
        template.setBackgroundImageUrl(request.getBackgroundImageUrl());
        template = templateRepository.save(template);

        auditService.logCrud("UPDATE", "CertificateTemplate", template.getId(),
            null, Map.of("name", template.getName()));
        return toDTO(template);
    }

    @Transactional(readOnly = true)
    public CertificateTemplate getTemplateEntity(String templateId) {
        return templateRepository.findById(templateId)
            .orElseThrow(() -> new IllegalArgumentException("Template not found"));
    }

    private CertificateTemplateDTO toDTO(CertificateTemplate t) {
        CertificateTemplateDTO dto = new CertificateTemplateDTO();
        dto.setId(t.getId());
        dto.setName(t.getName());
        dto.setDescription(t.getDescription());
        dto.setConfig(t.getConfig());
        dto.setBackgroundImageUrl(t.getBackgroundImageUrl());
        dto.setDefault(t.isDefault());
        dto.setCreatedAt(t.getCreatedAt());
        return dto;
    }
}
```

**Step 2: Create CertificateTemplateController**

```java
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
    @Operation(summary = "List available templates (tenant + system defaults)")
    public ResponseEntity<List<CertificateTemplateDTO>> listTemplates() {
        return ResponseEntity.ok(templateService.listTemplates());
    }

    @PostMapping
    @Operation(summary = "Create a custom template")
    public ResponseEntity<CertificateTemplateDTO> createTemplate(
            @RequestBody CertificateTemplateDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(templateService.createTemplate(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a template")
    public ResponseEntity<CertificateTemplateDTO> updateTemplate(
            @PathVariable String id, @RequestBody CertificateTemplateDTO request) {
        return ResponseEntity.ok(templateService.updateTemplate(id, request));
    }
}
```

**Step 3: Verify compilation**

Run: `cd student && ../gradlew compileJava`

**Step 4: Commit**

```bash
git add student/src/main/java/com/datagami/edudron/student/service/CertificateTemplateService.java
git add student/src/main/java/com/datagami/edudron/student/web/CertificateTemplateController.java
git commit -m "feat: add CertificateTemplateService and controller for template CRUD"
```

---

### Task 10: PDF generation utility

**Files:**
- Create: `student/src/main/java/com/datagami/edudron/student/service/CertificatePdfGenerator.java`

**Step 1: Create PDF generator**

Uses PDFBox to render a certificate PDF based on template config. Renders text fields at configured positions, generates QR code via ZXing, and overlays it.

```java
package com.datagami.edudron.student.service;

import com.datagami.edudron.student.domain.CertificateTemplate;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Component
public class CertificatePdfGenerator {

    private static final Logger log = LoggerFactory.getLogger(CertificatePdfGenerator.class);

    /**
     * Generate a certificate PDF.
     *
     * @param template       The certificate template with config
     * @param studentName    Student's full name
     * @param courseName     Course title
     * @param credentialId   Unique credential ID (e.g., EDU-2026-A3K9X)
     * @param verificationUrl Public verification URL for QR code
     * @param issuedAt       Issue date
     * @return PDF bytes
     */
    public byte[] generatePdf(CertificateTemplate template, String studentName,
                               String courseName, String credentialId,
                               String verificationUrl, OffsetDateTime issuedAt) throws Exception {

        Map<String, Object> config = template.getConfig();
        Map<String, Object> pageSize = (Map<String, Object>) config.get("pageSize");
        float width = ((Number) pageSize.get("width")).floatValue();
        float height = ((Number) pageSize.get("height")).floatValue();

        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle(width, height));
            document.addPage(page);

            // Draw background image if present
            if (template.getBackgroundImageUrl() != null) {
                // Background image loading would use URL fetch — skip for v1 if not set
                // TODO: Download background from Azure Blob and draw as full-page image
            }

            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                List<Map<String, Object>> fields = (List<Map<String, Object>>) config.get("fields");

                for (Map<String, Object> field : fields) {
                    String type = (String) field.get("type");
                    float x = ((Number) field.get("x")).floatValue();
                    // PDFBox uses bottom-left origin, config uses top-left
                    float y = height - ((Number) field.get("y")).floatValue();
                    float fontSize = field.containsKey("fontSize")
                        ? ((Number) field.get("fontSize")).floatValue() : 14;
                    String color = (String) field.getOrDefault("color", "#000000");

                    String text = resolveFieldText(type, field, studentName, courseName,
                        credentialId, issuedAt);

                    if ("qrCode".equals(type)) {
                        int size = field.containsKey("size") ? ((Number) field.get("size")).intValue() : 100;
                        byte[] qrBytes = generateQrCode(verificationUrl, size);
                        PDImageXObject qrImage = PDImageXObject.createFromByteArray(
                            document, qrBytes, "qr");
                        content.drawImage(qrImage, x, y - size, size, size);
                    } else if ("image".equals(type) || "logo".equals(type)) {
                        // Skip image/logo rendering for v1 — requires URL download
                        // TODO: Download image from URL and render
                    } else if (text != null) {
                        // Parse hex color
                        java.awt.Color awtColor = java.awt.Color.decode(color);
                        content.setNonStrokingColor(
                            awtColor.getRed() / 255f,
                            awtColor.getGreen() / 255f,
                            awtColor.getBlue() / 255f);

                        PDType1Font font = "bold".equals(field.get("fontWeight"))
                            ? new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)
                            : new PDType1Font(Standard14Fonts.FontName.HELVETICA);

                        // Center text at x position
                        float textWidth = font.getStringWidth(text) / 1000 * fontSize;
                        float startX = x - (textWidth / 2);

                        content.beginText();
                        content.setFont(font, fontSize);
                        content.newLineAtOffset(startX, y);
                        content.showText(text);
                        content.endText();
                    }
                }
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            document.save(baos);
            return baos.toByteArray();
        }
    }

    private String resolveFieldText(String type, Map<String, Object> field,
                                     String studentName, String courseName,
                                     String credentialId, OffsetDateTime issuedAt) {
        return switch (type) {
            case "studentName" -> studentName;
            case "courseName" -> courseName;
            case "credentialId" -> credentialId;
            case "date" -> {
                String format = (String) field.getOrDefault("format", "MMMM dd, yyyy");
                yield issuedAt.format(DateTimeFormatter.ofPattern(format));
            }
            case "customText" -> (String) field.get("text");
            default -> null;
        };
    }

    private byte[] generateQrCode(String text, int size) throws Exception {
        QRCodeWriter writer = new QRCodeWriter();
        BitMatrix matrix = writer.encode(text, BarcodeFormat.QR_CODE, size, size);
        BufferedImage image = MatrixToImageWriter.toBufferedImage(matrix);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", baos);
        return baos.toByteArray();
    }
}
```

**Step 2: Verify compilation**

Run: `cd student && ../gradlew compileJava`

**Step 3: Commit**

```bash
git add student/src/main/java/com/datagami/edudron/student/service/CertificatePdfGenerator.java
git commit -m "feat: add CertificatePdfGenerator with PDFBox rendering and ZXing QR codes"
```

---

### Task 11: CertificateService (generate, revoke, download, verify)

**Files:**
- Create: `student/src/main/java/com/datagami/edudron/student/service/CertificateService.java`

**Step 1: Create the service**

```java
package com.datagami.edudron.student.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.common.UlidGenerator;
import com.datagami.edudron.student.client.ContentAssessmentClient;
import com.datagami.edudron.student.client.IdentityUserClient;
import com.datagami.edudron.student.domain.*;
import com.datagami.edudron.student.dto.*;
import com.datagami.edudron.student.repo.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.Year;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class CertificateService {

    private static final Logger log = LoggerFactory.getLogger(CertificateService.class);
    private static final String CREDENTIAL_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    @Value("${app.verification-base-url:https://student-portal.vercel.app/verify}")
    private String verificationBaseUrl;

    private final CertificateRepository certificateRepository;
    private final CertificateTemplateRepository templateRepository;
    private final CertificateVisibilityRepository visibilityRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final ContentAssessmentClient contentAssessmentClient;
    private final IdentityUserClient identityUserClient;
    private final CertificatePdfGenerator pdfGenerator;
    private final MediaUploadHelper mediaUploadHelper; // See note below
    private final StudentAuditService auditService;

    // Constructor injection

    /**
     * Generate certificates for a list of students for a course.
     * CSV is parsed on the frontend; backend receives structured list.
     */
    @Transactional
    public List<CertificateDTO> generateCertificates(CertificateGenerateRequest request,
                                                      String issuedByUserId, String userRole) {
        UUID clientId = UUID.fromString(TenantContext.getClientId());

        // Bulk limit check
        if (request.getStudents().size() > 150 && !"SYSTEM_ADMIN".equals(userRole)) {
            throw new IllegalArgumentException(
                "Bulk generation of >150 certificates requires SYSTEM_ADMIN role. " +
                "Contact system admin for bulk generation.");
        }

        CertificateTemplate template = templateRepository.findById(request.getTemplateId())
            .orElseThrow(() -> new IllegalArgumentException("Template not found"));

        JsonNode courseNode = contentAssessmentClient.getCourse(request.getCourseId());
        String courseName = courseNode != null && courseNode.has("title")
            ? courseNode.get("title").asText() : request.getCourseId();

        List<CertificateDTO> results = new ArrayList<>();

        for (CertificateGenerateRequest.StudentEntry entry : request.getStudents()) {
            try {
                // Resolve student by email
                String studentId = resolveStudentId(entry.getEmail());
                if (studentId == null) {
                    log.warn("Student not found for email: {}", entry.getEmail());
                    continue;
                }

                // Check if certificate already exists for this student+course
                Optional<Certificate> existing = certificateRepository
                    .findByClientIdAndStudentIdAndCourseIdAndIsActiveTrue(clientId, studentId, request.getCourseId());
                if (existing.isPresent()) {
                    log.info("Certificate already exists for student {} course {}",
                        studentId, request.getCourseId());
                    results.add(toDTO(existing.get(), entry.getName(), entry.getEmail(), courseName));
                    continue;
                }

                // Generate unique credential ID
                String credentialId = generateCredentialId();
                String verificationUrl = verificationBaseUrl + "/" + credentialId;

                // Generate PDF
                byte[] pdfBytes = pdfGenerator.generatePdf(
                    template, entry.getName(), courseName, credentialId,
                    verificationUrl, OffsetDateTime.now());

                // Upload PDF to Azure Blob Storage
                String pdfUrl = mediaUploadHelper.uploadCertificatePdf(
                    clientId.toString(), credentialId, pdfBytes);

                // Create certificate record
                Certificate cert = new Certificate();
                cert.setClientId(clientId);
                cert.setStudentId(studentId);
                cert.setCourseId(request.getCourseId());
                cert.setSectionId(request.getSectionId());
                cert.setClassId(request.getClassId());
                cert.setTemplateId(request.getTemplateId());
                cert.setCredentialId(credentialId);
                cert.setQrCodeUrl(verificationUrl);
                cert.setPdfUrl(pdfUrl);
                cert.setIssuedBy(issuedByUserId);
                cert.setMetadata(Map.of(
                    "studentName", entry.getName(),
                    "studentEmail", entry.getEmail(),
                    "courseName", courseName
                ));

                cert = certificateRepository.save(cert);

                // Create default visibility
                CertificateVisibility visibility = new CertificateVisibility();
                visibility.setClientId(clientId);
                visibility.setStudentId(studentId);
                visibility.setCertificateId(cert.getId());
                visibilityRepository.save(visibility);

                results.add(toDTO(cert, entry.getName(), entry.getEmail(), courseName));

            } catch (Exception e) {
                log.error("Failed to generate certificate for {}: {}", entry.getEmail(), e.getMessage());
            }
        }

        auditService.logCrud("CREATE", "Certificate", null, issuedByUserId,
            Map.of("scope", request.getCourseId(), "studentCount", results.size(),
                "templateId", request.getTemplateId()));

        return results;
    }

    @Transactional
    public void revokeCertificate(String certificateId, String reason, String revokedByUserId) {
        Certificate cert = certificateRepository.findById(certificateId)
            .orElseThrow(() -> new IllegalArgumentException("Certificate not found"));
        cert.setRevokedAt(OffsetDateTime.now());
        cert.setRevokedReason(reason);
        certificateRepository.save(cert);

        auditService.logCrud("UPDATE", "Certificate", certificateId, revokedByUserId,
            Map.of("action", "revoke", "credentialId", cert.getCredentialId(), "reason", reason));
    }

    @Transactional(readOnly = true)
    public Page<CertificateDTO> listCertificates(String sectionId, String courseId, Pageable pageable) {
        UUID clientId = UUID.fromString(TenantContext.getClientId());
        Page<Certificate> page;

        if (sectionId != null && courseId != null) {
            page = certificateRepository.findByClientIdAndSectionIdAndCourseIdAndIsActiveTrue(
                clientId, sectionId, courseId, pageable);
        } else if (sectionId != null) {
            page = certificateRepository.findByClientIdAndSectionIdAndIsActiveTrue(
                clientId, sectionId, pageable);
        } else if (courseId != null) {
            page = certificateRepository.findByClientIdAndCourseIdAndIsActiveTrue(
                clientId, courseId, pageable);
        } else {
            page = certificateRepository.findByClientIdAndIsActiveTrue(clientId, pageable);
        }

        return page.map(c -> toDTO(c, null, null, null));
    }

    @Transactional(readOnly = true)
    public List<CertificateDTO> getStudentCertificates(String studentId) {
        UUID clientId = UUID.fromString(TenantContext.getClientId());
        return certificateRepository.findByClientIdAndStudentIdAndIsActiveTrue(clientId, studentId)
            .stream().map(c -> toDTO(c, null, null, null)).toList();
    }

    @Transactional
    public CertificateVisibilityDTO updateVisibility(String certificateId, String studentId,
                                                      CertificateVisibilityDTO request) {
        CertificateVisibility vis = visibilityRepository.findByCertificateId(certificateId)
            .orElseThrow(() -> new IllegalArgumentException("Visibility settings not found"));
        if (!vis.getStudentId().equals(studentId)) {
            throw new IllegalArgumentException("Not authorized to update this certificate's visibility");
        }
        vis.setShowScores(request.isShowScores());
        vis.setShowProjectDetails(request.isShowProjectDetails());
        vis.setShowOverallPercentage(request.isShowOverallPercentage());
        vis.setShowCourseName(request.isShowCourseName());
        visibilityRepository.save(vis);
        return request;
    }

    /**
     * Public verification — no auth required.
     */
    @Transactional(readOnly = true)
    public CertificateVerificationDTO verify(String credentialId) {
        Certificate cert = certificateRepository.findByCredentialId(credentialId)
            .orElseThrow(() -> new IllegalArgumentException("Certificate not found"));

        CertificateVerificationDTO dto = new CertificateVerificationDTO();
        dto.setCredentialId(cert.getCredentialId());
        dto.setIssuedAt(cert.getIssuedAt());
        dto.setValid(!cert.isRevoked() && cert.isActive());
        dto.setRevoked(cert.isRevoked());
        dto.setRevokedAt(cert.getRevokedAt());
        dto.setRevokedReason(cert.getRevokedReason());
        dto.setPdfUrl(cert.getPdfUrl());

        // Populate from metadata
        Map<String, Object> meta = cert.getMetadata();
        if (meta != null) {
            dto.setStudentName((String) meta.get("studentName"));
            dto.setCourseName((String) meta.get("courseName"));
        }

        // TODO: Fetch institution info from tenant branding

        // Apply visibility settings
        CertificateVisibility vis = visibilityRepository.findByCertificateId(cert.getId())
            .orElse(null);
        if (vis != null) {
            CertificateVisibilityDTO visDto = new CertificateVisibilityDTO();
            visDto.setShowScores(vis.isShowScores());
            visDto.setShowProjectDetails(vis.isShowProjectDetails());
            visDto.setShowOverallPercentage(vis.isShowOverallPercentage());
            visDto.setShowCourseName(vis.isShowCourseName());
            dto.setVisibility(visDto);

            if (!vis.isShowCourseName()) {
                dto.setCourseName(null);
            }
            if (!vis.isShowScores()) {
                dto.setScores(null);
            }
        }

        return dto;
    }

    public byte[] downloadAllAsZip(String sectionId, String courseId) throws Exception {
        UUID clientId = UUID.fromString(TenantContext.getClientId());
        List<Certificate> certs = certificateRepository
            .findByClientIdAndCourseIdAndSectionIdAndIsActiveTrue(clientId, courseId, sectionId);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (Certificate cert : certs) {
                if (cert.getPdfUrl() != null) {
                    // Download PDF from blob storage and add to ZIP
                    byte[] pdfBytes = mediaUploadHelper.downloadFile(cert.getPdfUrl());
                    if (pdfBytes != null) {
                        String entryName = cert.getCredentialId() + ".pdf";
                        zos.putNextEntry(new ZipEntry(entryName));
                        zos.write(pdfBytes);
                        zos.closeEntry();
                    }
                }
            }
        }
        return baos.toByteArray();
    }

    private String generateCredentialId() {
        String year = String.valueOf(Year.now().getValue());
        StringBuilder sb = new StringBuilder("EDU-").append(year).append("-");
        for (int i = 0; i < 5; i++) {
            sb.append(CREDENTIAL_CHARS.charAt(RANDOM.nextInt(CREDENTIAL_CHARS.length())));
        }
        String id = sb.toString();
        // Ensure uniqueness
        if (certificateRepository.findByCredentialId(id).isPresent()) {
            return generateCredentialId(); // Retry on collision
        }
        return id;
    }

    private String resolveStudentId(String email) {
        // Use IdentityUserClient to look up user by email
        // The endpoint may need to be added — check existing identity service endpoints
        try {
            JsonNode user = identityUserClient.getUserByEmail(email);
            return user != null && user.has("id") ? user.get("id").asText() : null;
        } catch (Exception e) {
            log.warn("Could not resolve student by email {}: {}", email, e.getMessage());
            return null;
        }
    }

    private CertificateDTO toDTO(Certificate c, String studentName, String studentEmail, String courseName) {
        CertificateDTO dto = new CertificateDTO();
        dto.setId(c.getId());
        dto.setStudentId(c.getStudentId());
        dto.setCourseId(c.getCourseId());
        dto.setSectionId(c.getSectionId());
        dto.setClassId(c.getClassId());
        dto.setTemplateId(c.getTemplateId());
        dto.setCredentialId(c.getCredentialId());
        dto.setQrCodeUrl(c.getQrCodeUrl());
        dto.setPdfUrl(c.getPdfUrl());
        dto.setIssuedAt(c.getIssuedAt());
        dto.setIssuedBy(c.getIssuedBy());
        dto.setRevoked(c.isRevoked());
        dto.setRevokedAt(c.getRevokedAt());
        dto.setRevokedReason(c.getRevokedReason());
        dto.setMetadata(c.getMetadata());

        // Populate names from metadata if not provided
        Map<String, Object> meta = c.getMetadata();
        if (meta != null) {
            dto.setStudentName(studentName != null ? studentName : (String) meta.get("studentName"));
            dto.setStudentEmail(studentEmail != null ? studentEmail : (String) meta.get("studentEmail"));
            dto.setCourseName(courseName != null ? courseName : (String) meta.get("courseName"));
        }

        return dto;
    }
}
```

> **Important notes:**
> - `MediaUploadHelper` does not exist yet. You'll need a small helper that wraps the existing Azure Blob upload pattern from the student module's `AzureStorageConfig`. The content service has `MediaUploadService`, but the student module needs its own blob upload capability for certificate PDFs. Create a minimal helper that uses the existing `BlobServiceClient` bean.
> - `IdentityUserClient.getUserByEmail(email)` may not exist — check the identity service for a lookup-by-email endpoint. If missing, you'll need to add one.

**Step 2: Create MediaUploadHelper**

```java
package com.datagami.edudron.student.service;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlobHttpHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

@Component
public class MediaUploadHelper {

    private final BlobServiceClient blobServiceClient;

    @Value("${azure.storage.container-name:edudron-media}")
    private String containerName;

    public MediaUploadHelper(BlobServiceClient blobServiceClient) {
        this.blobServiceClient = blobServiceClient;
    }

    public String uploadCertificatePdf(String tenantId, String credentialId, byte[] pdfBytes) {
        BlobContainerClient container = blobServiceClient.getBlobContainerClient(containerName);
        String blobPath = tenantId + "/certificates/" + credentialId + ".pdf";
        BlobClient blob = container.getBlobClient(blobPath);
        blob.upload(new ByteArrayInputStream(pdfBytes), pdfBytes.length, true);
        blob.setHttpHeaders(new BlobHttpHeaders().setContentType("application/pdf"));
        return blob.getBlobUrl();
    }

    public byte[] downloadFile(String blobUrl) {
        try {
            // Extract blob path from URL
            String blobPath = blobUrl.substring(blobUrl.indexOf(containerName) + containerName.length() + 1);
            BlobContainerClient container = blobServiceClient.getBlobContainerClient(containerName);
            BlobClient blob = container.getBlobClient(blobPath);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            blob.downloadStream(baos);
            return baos.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }
}
```

**Step 3: Verify compilation**

Run: `cd student && ../gradlew compileJava`

**Step 4: Commit**

```bash
git add student/src/main/java/com/datagami/edudron/student/service/CertificateService.java
git add student/src/main/java/com/datagami/edudron/student/service/MediaUploadHelper.java
git commit -m "feat: add CertificateService for generation, revocation, download, and verification"
```

---

### Task 12: CertificateController and VerificationController

**Files:**
- Create: `student/src/main/java/com/datagami/edudron/student/web/CertificateController.java`
- Create: `student/src/main/java/com/datagami/edudron/student/web/CertificateVerificationController.java`

**Step 1: Create CertificateController**

```java
package com.datagami.edudron.student.web;

import com.datagami.edudron.student.dto.*;
import com.datagami.edudron.student.service.CertificateService;
import com.datagami.edudron.student.util.UserUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/certificates")
@Tag(name = "Certificates", description = "Certificate generation and management")
public class CertificateController {

    private final CertificateService certificateService;
    private final UserUtil userUtil;

    public CertificateController(CertificateService certificateService, UserUtil userUtil) {
        this.certificateService = certificateService;
        this.userUtil = userUtil;
    }

    @PostMapping("/generate")
    @Operation(summary = "Generate certificates for students (CSV-parsed list)")
    public ResponseEntity<List<CertificateDTO>> generate(
            @Valid @RequestBody CertificateGenerateRequest request) {
        String userId = userUtil.getCurrentUserId();
        String role = userUtil.getCurrentUserRole();
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(certificateService.generateCertificates(request, userId, role));
    }

    @GetMapping
    @Operation(summary = "List issued certificates (filterable by section/course)")
    public ResponseEntity<Page<CertificateDTO>> list(
            @RequestParam(required = false) String sectionId,
            @RequestParam(required = false) String courseId,
            Pageable pageable) {
        return ResponseEntity.ok(certificateService.listCertificates(sectionId, courseId, pageable));
    }

    @GetMapping("/{id}/download")
    @Operation(summary = "Download individual certificate PDF")
    public ResponseEntity<byte[]> download(@PathVariable String id) throws Exception {
        // Delegate to service — fetch PDF URL, download from blob, return bytes
        // For now, redirect to the PDF URL
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"certificate.pdf\"")
            .contentType(MediaType.APPLICATION_PDF)
            .body(new byte[0]); // TODO: Implement actual download
    }

    @GetMapping("/download-all")
    @Operation(summary = "Download all certificates as ZIP")
    public ResponseEntity<byte[]> downloadAll(
            @RequestParam String sectionId,
            @RequestParam String courseId) throws Exception {
        byte[] zip = certificateService.downloadAllAsZip(sectionId, courseId);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"certificates.zip\"")
            .contentType(MediaType.parseMediaType("application/zip"))
            .body(zip);
    }

    @PostMapping("/{id}/revoke")
    @Operation(summary = "Revoke a certificate")
    public ResponseEntity<Map<String, String>> revoke(
            @PathVariable String id,
            @RequestBody Map<String, String> body) {
        String userId = userUtil.getCurrentUserId();
        certificateService.revokeCertificate(id, body.get("reason"), userId);
        return ResponseEntity.ok(Map.of("status", "revoked"));
    }

    // === Student-facing endpoints ===

    @GetMapping("/my")
    @Operation(summary = "Get current student's certificates")
    public ResponseEntity<List<CertificateDTO>> myCertificates() {
        String studentId = userUtil.getCurrentUserId();
        return ResponseEntity.ok(certificateService.getStudentCertificates(studentId));
    }

    @PutMapping("/{id}/visibility")
    @Operation(summary = "Update certificate visibility settings")
    public ResponseEntity<CertificateVisibilityDTO> updateVisibility(
            @PathVariable String id,
            @RequestBody CertificateVisibilityDTO request) {
        String studentId = userUtil.getCurrentUserId();
        return ResponseEntity.ok(certificateService.updateVisibility(id, studentId, request));
    }
}
```

**Step 2: Create CertificateVerificationController (public, no auth)**

```java
package com.datagami.edudron.student.web;

import com.datagami.edudron.student.dto.CertificateVerificationDTO;
import com.datagami.edudron.student.service.CertificateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/verify")
@Tag(name = "Certificate Verification", description = "Public certificate verification")
public class CertificateVerificationController {

    private final CertificateService certificateService;

    public CertificateVerificationController(CertificateService certificateService) {
        this.certificateService = certificateService;
    }

    @GetMapping("/{credentialId}")
    @Operation(summary = "Verify a certificate by credential ID (public, no auth)")
    public ResponseEntity<CertificateVerificationDTO> verify(@PathVariable String credentialId) {
        return ResponseEntity.ok(certificateService.verify(credentialId));
    }
}
```

**Step 3: Add `/api/verify/**` to public endpoints in SecurityConfig**

Modify `student/src/main/java/com/datagami/edudron/student/config/SecurityConfig.java`:

Add `/api/verify/**` to the `.requestMatchers(...).permitAll()` list alongside existing public endpoints.

**Step 4: Verify compilation**

Run: `cd student && ../gradlew compileJava`

**Step 5: Commit**

```bash
git add student/src/main/java/com/datagami/edudron/student/web/CertificateController.java
git add student/src/main/java/com/datagami/edudron/student/web/CertificateVerificationController.java
git add student/src/main/java/com/datagami/edudron/student/config/SecurityConfig.java
git commit -m "feat: add CertificateController and public CertificateVerificationController"
```

---

### Task 13: Gateway routes

**Files:**
- Modify: `gateway/src/main/resources/application.yml`

**Step 1: Add routes for results, certificates, and verification**

Add these routes to the gateway `application.yml` routes list, **before** any catch-all route:

```yaml
        - id: results-export
          uri: ${CORE_API_SERVICE_URL:http://localhost:8085}
          predicates:
            - Path=/api/results/**

        - id: certificates
          uri: ${CORE_API_SERVICE_URL:http://localhost:8085}
          predicates:
            - Path=/api/certificates/**

        - id: certificate-verification
          uri: ${CORE_API_SERVICE_URL:http://localhost:8085}
          predicates:
            - Path=/api/verify/**
```

**Step 2: Verify gateway starts**

Run: `cd gateway && ../gradlew bootRun`
Expected: Starts without errors, routes are registered.

**Step 3: Commit**

```bash
git add gateway/src/main/resources/application.yml
git commit -m "feat: add gateway routes for results export, certificates, and verification"
```

---

## Phase 4: Frontend — Shared Utils

### Task 14: ResultsApi and CertificatesApi in shared-utils

**Files:**
- Create: `frontend/packages/shared-utils/src/api/results.ts`
- Create: `frontend/packages/shared-utils/src/api/certificates.ts`
- Modify: `frontend/packages/shared-utils/src/index.ts` (add exports)

**Step 1: Create ResultsApi**

```typescript
// frontend/packages/shared-utils/src/api/results.ts
import { ApiClient } from './ApiClient'

export class ResultsApi {
  constructor(private apiClient: ApiClient) {}

  async exportBySection(sectionId: string): Promise<Blob> {
    const response = await this.apiClient.downloadFile(`/api/results/export?sectionId=${sectionId}`)
    return response
  }

  async exportByClass(classId: string): Promise<Blob> {
    const response = await this.apiClient.downloadFile(`/api/results/export?classId=${classId}`)
    return response
  }

  async exportByCourse(courseId: string): Promise<Blob> {
    const response = await this.apiClient.downloadFile(`/api/results/export?courseId=${courseId}`)
    return response
  }
}
```

**Step 2: Create CertificatesApi**

```typescript
// frontend/packages/shared-utils/src/api/certificates.ts
import { ApiClient } from './ApiClient'

export interface CertificateTemplate {
  id: string
  name: string
  description: string
  config: Record<string, unknown>
  backgroundImageUrl?: string
  isDefault: boolean
  createdAt: string
}

export interface Certificate {
  id: string
  studentId: string
  studentName?: string
  studentEmail?: string
  courseId: string
  courseName?: string
  sectionId?: string
  classId?: string
  templateId: string
  credentialId: string
  qrCodeUrl: string
  pdfUrl: string
  issuedAt: string
  issuedBy: string
  revoked: boolean
  revokedAt?: string
  revokedReason?: string
  metadata?: Record<string, unknown>
  visibility?: CertificateVisibility
}

export interface CertificateVisibility {
  showScores: boolean
  showProjectDetails: boolean
  showOverallPercentage: boolean
  showCourseName: boolean
}

export interface CertificateGenerateRequest {
  courseId: string
  sectionId?: string
  classId?: string
  templateId: string
  students: { name: string; email: string }[]
}

export interface CertificateVerification {
  credentialId: string
  studentName: string
  courseName?: string
  institutionName?: string
  institutionLogoUrl?: string
  issuedAt: string
  valid: boolean
  revoked: boolean
  revokedAt?: string
  revokedReason?: string
  pdfUrl: string
  scores?: Record<string, unknown>
  visibility?: CertificateVisibility
}

export class CertificatesApi {
  constructor(private apiClient: ApiClient) {}

  // Templates
  async listTemplates(): Promise<CertificateTemplate[]> {
    const response = await this.apiClient.get<CertificateTemplate[]>('/api/certificates/templates')
    return Array.isArray(response.data) ? response.data : []
  }

  async createTemplate(data: Partial<CertificateTemplate>): Promise<CertificateTemplate> {
    const response = await this.apiClient.post<CertificateTemplate>('/api/certificates/templates', data)
    return response.data
  }

  async updateTemplate(id: string, data: Partial<CertificateTemplate>): Promise<CertificateTemplate> {
    const response = await this.apiClient.put<CertificateTemplate>(`/api/certificates/templates/${id}`, data)
    return response.data
  }

  // Certificate generation & management
  async generate(request: CertificateGenerateRequest): Promise<Certificate[]> {
    const response = await this.apiClient.post<Certificate[]>('/api/certificates/generate', request)
    return Array.isArray(response.data) ? response.data : []
  }

  async list(params?: { sectionId?: string; courseId?: string; page?: number; size?: number }): Promise<{ content: Certificate[]; totalElements: number; totalPages: number }> {
    const query = new URLSearchParams()
    if (params?.sectionId) query.set('sectionId', params.sectionId)
    if (params?.courseId) query.set('courseId', params.courseId)
    if (params?.page !== undefined) query.set('page', String(params.page))
    if (params?.size) query.set('size', String(params.size))
    const response = await this.apiClient.get<any>(`/api/certificates?${query.toString()}`)
    return response.data
  }

  async downloadPdf(id: string): Promise<Blob> {
    return this.apiClient.downloadFile(`/api/certificates/${id}/download`)
  }

  async downloadAllAsZip(sectionId: string, courseId: string): Promise<Blob> {
    return this.apiClient.downloadFile(`/api/certificates/download-all?sectionId=${sectionId}&courseId=${courseId}`)
  }

  async revoke(id: string, reason: string): Promise<void> {
    await this.apiClient.post(`/api/certificates/${id}/revoke`, { reason })
  }

  // Student-facing
  async myCertificates(): Promise<Certificate[]> {
    const response = await this.apiClient.get<Certificate[]>('/api/certificates/my')
    return Array.isArray(response.data) ? response.data : []
  }

  async updateVisibility(id: string, visibility: CertificateVisibility): Promise<CertificateVisibility> {
    const response = await this.apiClient.put<CertificateVisibility>(`/api/certificates/${id}/visibility`, visibility)
    return response.data
  }

  // Public verification (no auth)
  async verify(credentialId: string): Promise<CertificateVerification> {
    const response = await this.apiClient.get<CertificateVerification>(`/api/verify/${credentialId}`)
    return response.data
  }
}
```

**Step 3: Add exports to index.ts**

Add to `frontend/packages/shared-utils/src/index.ts`:

```typescript
export { ResultsApi } from './api/results'
export { CertificatesApi } from './api/certificates'
export type { CertificateTemplate, Certificate, CertificateVisibility, CertificateGenerateRequest, CertificateVerification } from './api/certificates'
```

**Step 4: Build shared-utils**

Run: `cd frontend/packages/shared-utils && npm run build`
Expected: BUILD SUCCESSFUL, no type errors

**Step 5: Commit**

```bash
git add frontend/packages/shared-utils/src/api/results.ts
git add frontend/packages/shared-utils/src/api/certificates.ts
git add frontend/packages/shared-utils/src/index.ts
git commit -m "feat: add ResultsApi and CertificatesApi to shared-utils"
```

---

### Task 15: Register API instances in both apps

**Files:**
- Modify: `frontend/apps/admin-dashboard/src/lib/api.ts`
- Modify: `frontend/apps/student-portal/src/lib/api.ts`

**Step 1: Add to admin-dashboard api.ts**

```typescript
import { ResultsApi, CertificatesApi } from '@kunal-ak23/edudron-shared-utils'

export const resultsApi = new ResultsApi(apiClient)
export const certificatesApi = new CertificatesApi(apiClient)
```

**Step 2: Add to student-portal api.ts**

```typescript
import { CertificatesApi } from '@kunal-ak23/edudron-shared-utils'

export const certificatesApi = new CertificatesApi(apiClient)
```

**Step 3: Commit**

```bash
git add frontend/apps/admin-dashboard/src/lib/api.ts
git add frontend/apps/student-portal/src/lib/api.ts
git commit -m "feat: register ResultsApi and CertificatesApi instances in both apps"
```

---

## Phase 5: Frontend — Admin Dashboard Pages

### Task 16: Results page (`/results`)

**Files:**
- Create: `frontend/apps/admin-dashboard/src/app/results/page.tsx`

**Step 1: Create the results page**

A page with:
- Scope picker: radio buttons for Section / Class / Course
- Dropdown to select the specific section/class/course
- "Export Results" button that downloads the Excel file
- Loading state while generating

```typescript
'use client'
export const dynamic = 'force-dynamic'

import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { resultsApi, sectionsApi, classesApi, coursesApi } from '@/lib/api'
import { Button } from '@/components/ui/button'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group'
import { Label } from '@/components/ui/label'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { useRequireAuth } from '@/hooks/useRequireAuth' // verify hook name

export default function ResultsPage() {
  useRequireAuth({ allowedRoles: ['TENANT_ADMIN', 'INSTRUCTOR'] })

  const [scope, setScope] = useState<'section' | 'class' | 'course'>('section')
  const [selectedId, setSelectedId] = useState<string>('')
  const [exporting, setExporting] = useState(false)

  // Fetch options based on scope
  // Use existing API calls to list sections, classes, courses
  // Adjust query keys and API methods based on what's available

  const handleExport = async () => {
    if (!selectedId) return
    setExporting(true)
    try {
      let blob: Blob
      if (scope === 'section') blob = await resultsApi.exportBySection(selectedId)
      else if (scope === 'class') blob = await resultsApi.exportByClass(selectedId)
      else blob = await resultsApi.exportByCourse(selectedId)

      // Trigger download
      const url = window.URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `results-${scope}-${selectedId}.xlsx`
      a.click()
      window.URL.revokeObjectURL(url)
    } catch (err) {
      console.error('Export failed:', err)
    } finally {
      setExporting(false)
    }
  }

  return (
    <div className="container mx-auto py-6 space-y-6">
      <h1 className="text-2xl font-bold">Export Results</h1>

      <Card>
        <CardHeader>
          <CardTitle>Select Scope</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <RadioGroup value={scope} onValueChange={(v) => { setScope(v as any); setSelectedId('') }}>
            <div className="flex items-center space-x-2">
              <RadioGroupItem value="section" id="section" />
              <Label htmlFor="section">Section</Label>
            </div>
            <div className="flex items-center space-x-2">
              <RadioGroupItem value="class" id="class" />
              <Label htmlFor="class">Class</Label>
            </div>
            <div className="flex items-center space-x-2">
              <RadioGroupItem value="course" id="course" />
              <Label htmlFor="course">Course</Label>
            </div>
          </RadioGroup>

          {/* Dropdown for selecting the specific entity — populate from API */}
          {/* Implementation depends on existing list APIs for sections/classes/courses */}

          <Button onClick={handleExport} disabled={!selectedId || exporting}>
            {exporting ? 'Exporting...' : 'Export Results'}
          </Button>
        </CardContent>
      </Card>
    </div>
  )
}
```

> **Note:** The exact dropdown population depends on existing API methods. Check `sectionsApi.list()`, `classesApi.list()`, `coursesApi.list()` for available methods and adjust. The coordinator role check for section/class scope needs to be handled based on the user's coordinator assignments.

**Step 2: Verify dev server runs**

Run: `cd frontend/apps/admin-dashboard && npm run dev`
Navigate to `http://localhost:3000/results`
Expected: Page renders with scope picker and export button.

**Step 3: Commit**

```bash
git add frontend/apps/admin-dashboard/src/app/results/page.tsx
git commit -m "feat: add results export page with scope picker in admin dashboard"
```

---

### Task 17: Certificates page (`/certificates`)

**Files:**
- Create: `frontend/apps/admin-dashboard/src/app/certificates/page.tsx`

**Step 1: Create the certificates page**

This is a larger page with multiple sections:
1. Template selector
2. CSV upload + student list preview
3. Generate button
4. Table of issued certificates with download/revoke actions

```typescript
'use client'
export const dynamic = 'force-dynamic'

import { useState, useCallback } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { certificatesApi, coursesApi } from '@/lib/api'
import type { CertificateGenerateRequest, Certificate, CertificateTemplate } from '@kunal-ak23/edudron-shared-utils'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import { useRequireAuth } from '@/hooks/useRequireAuth'

export default function CertificatesPage() {
  useRequireAuth({ allowedRoles: ['TENANT_ADMIN', 'SYSTEM_ADMIN'] })

  const queryClient = useQueryClient()
  const [courseId, setCourseId] = useState('')
  const [sectionId, setSectionId] = useState('')
  const [templateId, setTemplateId] = useState('')
  const [students, setStudents] = useState<{ name: string; email: string }[]>([])

  // Fetch templates
  const { data: templates = [] } = useQuery({
    queryKey: ['certificate-templates'],
    queryFn: () => certificatesApi.listTemplates(),
  })

  // Fetch issued certificates
  const { data: certificatesPage } = useQuery({
    queryKey: ['certificates', courseId, sectionId],
    queryFn: () => certificatesApi.list({ courseId: courseId || undefined, sectionId: sectionId || undefined }),
    enabled: !!courseId,
  })

  // CSV upload handler
  const handleCsvUpload = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return
    const reader = new FileReader()
    reader.onload = (event) => {
      const text = event.target?.result as string
      const lines = text.trim().split('\n')
      const parsed: { name: string; email: string }[] = []
      // Skip header
      for (let i = 1; i < lines.length; i++) {
        const [name, email] = lines[i].split(',').map(s => s.trim())
        if (name && email) parsed.push({ name, email })
      }
      setStudents(parsed)
    }
    reader.readAsText(file)
  }, [])

  // Generate mutation
  const generateMutation = useMutation({
    mutationFn: (request: CertificateGenerateRequest) => certificatesApi.generate(request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['certificates'] })
      setStudents([])
    },
  })

  const handleGenerate = () => {
    if (!courseId || !templateId || students.length === 0) return
    generateMutation.mutate({
      courseId,
      sectionId: sectionId || undefined,
      templateId,
      students,
    })
  }

  // Revoke mutation
  const revokeMutation = useMutation({
    mutationFn: ({ id, reason }: { id: string; reason: string }) =>
      certificatesApi.revoke(id, reason),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['certificates'] }),
  })

  return (
    <div className="container mx-auto py-6 space-y-6">
      <h1 className="text-2xl font-bold">Certificates</h1>

      {/* Generation Section */}
      <Card>
        <CardHeader><CardTitle>Generate Certificates</CardTitle></CardHeader>
        <CardContent className="space-y-4">
          {/* Course and section selectors — use existing course/section list APIs */}
          {/* Template selector */}
          <div>
            <label className="text-sm font-medium">Template</label>
            <Select value={templateId} onValueChange={setTemplateId}>
              <SelectTrigger><SelectValue placeholder="Select template" /></SelectTrigger>
              <SelectContent>
                {templates.map((t: CertificateTemplate) => (
                  <SelectItem key={t.id} value={t.id}>{t.name}</SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          {/* CSV Upload */}
          <div>
            <label className="text-sm font-medium">Upload Student CSV</label>
            <Input type="file" accept=".csv" onChange={handleCsvUpload} />
            {students.length > 0 && (
              <p className="text-sm text-muted-foreground mt-1">
                {students.length} students loaded
              </p>
            )}
          </div>

          {/* Student preview */}
          {students.length > 0 && (
            <div className="max-h-40 overflow-y-auto border rounded p-2">
              {students.map((s, i) => (
                <div key={i} className="text-sm">{s.name} — {s.email}</div>
              ))}
            </div>
          )}

          <Button onClick={handleGenerate}
            disabled={!courseId || !templateId || students.length === 0 || generateMutation.isPending}>
            {generateMutation.isPending ? 'Generating...' : `Generate ${students.length} Certificates`}
          </Button>
        </CardContent>
      </Card>

      {/* Issued Certificates Table */}
      <Card>
        <CardHeader><CardTitle>Issued Certificates</CardTitle></CardHeader>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Student</TableHead>
                <TableHead>Course</TableHead>
                <TableHead>Credential ID</TableHead>
                <TableHead>Issued</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {certificatesPage?.content?.map((cert: Certificate) => (
                <TableRow key={cert.id}>
                  <TableCell>{cert.studentName || cert.studentId}</TableCell>
                  <TableCell>{cert.courseName || cert.courseId}</TableCell>
                  <TableCell className="font-mono text-sm">{cert.credentialId}</TableCell>
                  <TableCell>{new Date(cert.issuedAt).toLocaleDateString()}</TableCell>
                  <TableCell>
                    {cert.revoked
                      ? <Badge variant="destructive">Revoked</Badge>
                      : <Badge variant="default">Valid</Badge>}
                  </TableCell>
                  <TableCell className="space-x-2">
                    {cert.pdfUrl && (
                      <Button variant="outline" size="sm"
                        onClick={() => window.open(cert.pdfUrl, '_blank')}>
                        Download
                      </Button>
                    )}
                    {!cert.revoked && (
                      <Button variant="destructive" size="sm"
                        onClick={() => {
                          const reason = prompt('Reason for revocation:')
                          if (reason) revokeMutation.mutate({ id: cert.id, reason })
                        }}>
                        Revoke
                      </Button>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CardContent>
      </Card>
    </div>
  )
}
```

**Step 2: Verify page renders**

Run: `cd frontend/apps/admin-dashboard && npm run dev`
Navigate to `http://localhost:3000/certificates`

**Step 3: Commit**

```bash
git add frontend/apps/admin-dashboard/src/app/certificates/page.tsx
git commit -m "feat: add certificates page with generation, CSV upload, and management"
```

---

## Phase 6: Frontend — Student Portal Pages

### Task 18: My Certificates page (`/certificates`)

**Files:**
- Create: `frontend/apps/student-portal/src/app/certificates/page.tsx`

**Step 1: Create the page**

```typescript
'use client'
export const dynamic = 'force-dynamic'

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { certificatesApi } from '@/lib/api'
import type { Certificate, CertificateVisibility } from '@kunal-ak23/edudron-shared-utils'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Switch } from '@/components/ui/switch'
import { Label } from '@/components/ui/label'
import { Badge } from '@/components/ui/badge'
import { ProtectedRoute } from '@/components/ProtectedRoute' // verify import path

export default function MyCertificatesPage() {
  const queryClient = useQueryClient()

  const { data: certificates = [], isLoading } = useQuery({
    queryKey: ['my-certificates'],
    queryFn: () => certificatesApi.myCertificates(),
  })

  const visibilityMutation = useMutation({
    mutationFn: ({ id, visibility }: { id: string; visibility: CertificateVisibility }) =>
      certificatesApi.updateVisibility(id, visibility),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['my-certificates'] }),
  })

  return (
    <ProtectedRoute>
      <div className="container mx-auto py-6 space-y-6">
        <h1 className="text-2xl font-bold">My Certificates</h1>

        {isLoading && <p>Loading certificates...</p>}

        {certificates.length === 0 && !isLoading && (
          <p className="text-muted-foreground">No certificates issued yet.</p>
        )}

        {certificates.map((cert: Certificate) => (
          <Card key={cert.id}>
            <CardHeader>
              <div className="flex items-center justify-between">
                <CardTitle>{cert.courseName || 'Certificate'}</CardTitle>
                {cert.revoked
                  ? <Badge variant="destructive">Revoked</Badge>
                  : <Badge variant="default">Valid</Badge>}
              </div>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="text-sm text-muted-foreground">
                <p>Credential ID: <span className="font-mono">{cert.credentialId}</span></p>
                <p>Issued: {new Date(cert.issuedAt).toLocaleDateString()}</p>
              </div>

              {cert.pdfUrl && (
                <Button onClick={() => window.open(cert.pdfUrl, '_blank')}>
                  Download PDF
                </Button>
              )}

              {/* Visibility toggles */}
              {cert.visibility && !cert.revoked && (
                <div className="border-t pt-4 space-y-3">
                  <p className="text-sm font-medium">Public Visibility Settings</p>
                  {(['showScores', 'showProjectDetails', 'showOverallPercentage', 'showCourseName'] as const).map(key => (
                    <div key={key} className="flex items-center justify-between">
                      <Label className="text-sm">{key.replace(/([A-Z])/g, ' $1').replace('show ', 'Show ')}</Label>
                      <Switch
                        checked={cert.visibility![key]}
                        onCheckedChange={(checked) => {
                          visibilityMutation.mutate({
                            id: cert.id,
                            visibility: { ...cert.visibility!, [key]: checked }
                          })
                        }}
                      />
                    </div>
                  ))}
                </div>
              )}
            </CardContent>
          </Card>
        ))}
      </div>
    </ProtectedRoute>
  )
}
```

**Step 2: Commit**

```bash
git add frontend/apps/student-portal/src/app/certificates/page.tsx
git commit -m "feat: add My Certificates page in student portal with visibility toggles"
```

---

### Task 19: Public verification page (`/verify/[credentialId]`)

**Files:**
- Create: `frontend/apps/student-portal/src/app/verify/[credentialId]/page.tsx`

**Step 1: Create the public verification page**

This page does NOT use `ProtectedRoute` — it's public. It calls the public `/api/verify/{credentialId}` endpoint.

```typescript
'use client'
export const dynamic = 'force-dynamic'

import { useEffect, useState } from 'react'
import { useParams } from 'next/navigation'
import { ApiClient } from '@kunal-ak23/edudron-shared-utils'
import type { CertificateVerification } from '@kunal-ak23/edudron-shared-utils'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'

const GATEWAY_URL = process.env.NEXT_PUBLIC_API_GATEWAY_URL || 'http://localhost:8080'

export default function VerifyPage() {
  const params = useParams()
  const credentialId = params.credentialId as string
  const [data, setData] = useState<CertificateVerification | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    async function fetchVerification() {
      try {
        // Direct fetch — no auth needed
        const res = await fetch(`${GATEWAY_URL}/api/verify/${credentialId}`)
        if (!res.ok) throw new Error('Certificate not found')
        const json = await res.json()
        setData(json)
      } catch (err: any) {
        setError(err.message || 'Verification failed')
      } finally {
        setLoading(false)
      }
    }
    if (credentialId) fetchVerification()
  }, [credentialId])

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <p className="text-lg">Verifying certificate...</p>
      </div>
    )
  }

  if (error || !data) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <Card className="max-w-md w-full">
          <CardContent className="pt-6 text-center">
            <p className="text-lg font-semibold text-red-600">Certificate Not Found</p>
            <p className="text-muted-foreground mt-2">
              The credential ID "{credentialId}" could not be verified.
            </p>
          </CardContent>
        </Card>
      </div>
    )
  }

  return (
    <div className="min-h-screen bg-gray-50 flex items-center justify-center p-4">
      <Card className="max-w-lg w-full">
        <CardHeader className="text-center">
          {data.institutionLogoUrl && (
            <img src={data.institutionLogoUrl} alt="Institution" className="h-16 mx-auto mb-4" />
          )}
          {data.institutionName && (
            <p className="text-sm text-muted-foreground">{data.institutionName}</p>
          )}
          <CardTitle className="text-xl">Certificate Verification</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          {/* Status badge */}
          <div className="flex justify-center">
            {data.valid
              ? <Badge className="bg-green-600 text-white text-base px-4 py-1">Valid</Badge>
              : <Badge variant="destructive" className="text-base px-4 py-1">Revoked</Badge>}
          </div>

          {/* Certificate details */}
          <div className="space-y-2 text-center">
            <p className="text-lg font-semibold">{data.studentName}</p>
            {data.courseName && data.visibility?.showCourseName !== false && (
              <p className="text-muted-foreground">{data.courseName}</p>
            )}
            <p className="text-sm text-muted-foreground">
              Issued: {new Date(data.issuedAt).toLocaleDateString('en-US', {
                year: 'numeric', month: 'long', day: 'numeric'
              })}
            </p>
            <p className="text-xs text-muted-foreground font-mono">
              Credential: {data.credentialId}
            </p>
          </div>

          {/* Revocation info */}
          {data.revoked && (
            <div className="bg-red-50 border border-red-200 rounded-lg p-3 text-center">
              <p className="text-red-700 font-medium">This certificate has been revoked</p>
              {data.revokedReason && (
                <p className="text-red-600 text-sm mt-1">Reason: {data.revokedReason}</p>
              )}
              {data.revokedAt && (
                <p className="text-red-500 text-xs mt-1">
                  Revoked on: {new Date(data.revokedAt).toLocaleDateString()}
                </p>
              )}
            </div>
          )}

          {/* Download button */}
          {data.pdfUrl && data.valid && (
            <div className="text-center pt-2">
              <Button onClick={() => window.open(data.pdfUrl, '_blank')}>
                Download Certificate PDF
              </Button>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
```

**Step 2: Verify page renders**

Run: `cd frontend/apps/student-portal && npm run dev`
Navigate to `http://localhost:3001/verify/test-credential`
Expected: Page renders with "Certificate Not Found" (expected since no real data).

**Step 3: Commit**

```bash
git add frontend/apps/student-portal/src/app/verify/
git commit -m "feat: add public certificate verification page in student portal"
```

---

## Phase 7: Integration & Polish

### Task 20: Add "Export Results" button to section/class detail pages

**Files:**
- Modify: `frontend/apps/admin-dashboard/src/app/sections/[id]/page.tsx`
- Modify: `frontend/apps/admin-dashboard/src/app/classes/[id]/page.tsx` (if exists)

**Step 1: Add export button to section detail page**

Read the existing section detail page. Add an "Export Results" button that calls `resultsApi.exportBySection(sectionId)` and triggers a download. Follow the same download pattern used in Task 16.

**Step 2: Add export button to class detail page (if it exists)**

Same pattern with `resultsApi.exportByClass(classId)`.

**Step 3: Commit**

```bash
git add frontend/apps/admin-dashboard/src/app/sections/
git add frontend/apps/admin-dashboard/src/app/classes/
git commit -m "feat: add 'Export Results' quick action to section and class detail pages"
```

---

### Task 21: Add navigation links for new pages

**Files:**
- Modify: Admin dashboard sidebar/navigation component (find the nav config file)
- Modify: Student portal sidebar/navigation component

**Step 1: Find and modify admin dashboard navigation**

Search for the sidebar/nav configuration in admin-dashboard (likely in `src/components/` or `src/app/layout.tsx`). Add links:
- "Results" → `/results`
- "Certificates" → `/certificates`

**Step 2: Find and modify student portal navigation**

Add link:
- "Certificates" → `/certificates`

**Step 3: Commit**

```bash
git commit -m "feat: add navigation links for results and certificates pages"
```

---

### Task 22: End-to-end manual testing checklist

This is not a code task — it's a testing verification step.

**Backend Tests:**
1. Start DB: `docker-compose -f docker-compose.db-only.yml up -d`
2. Start core-api: `cd core-api && ../gradlew bootRun` — verify Liquibase migrations run
3. Check DB: Verify `certificate` schema exists with 3 tables and 2 default templates
4. Test results export: `curl -H "Authorization: Bearer <token>" -H "X-Client-Id: <tenantId>" "http://localhost:8080/api/results/export?sectionId=<id>" -o test.xlsx`
5. Test template listing: `curl -H "Authorization: Bearer <token>" -H "X-Client-Id: <tenantId>" "http://localhost:8080/api/certificates/templates"`
6. Test certificate generation: POST to `/api/certificates/generate` with a test CSV
7. Test verification: `curl "http://localhost:8080/api/verify/<credentialId>"` (no auth)

**Frontend Tests:**
1. Admin dashboard: Navigate to `/results`, select scope, export — verify Excel downloads
2. Admin dashboard: Navigate to `/certificates`, upload CSV, select template, generate — verify certificates appear in list
3. Student portal: Navigate to `/certificates` — verify student sees their certificates
4. Student portal: Navigate to `/verify/<credentialId>` — verify public page renders correctly
5. QR code: Scan QR code on generated certificate PDF — should link to verification page

---

## Known Integration Issues to Resolve During Implementation

These items need investigation and potentially additional code during implementation:

1. **Enrollment entity fields**: Verify that `Enrollment` has `studentName` and `studentEmail` fields. If not, resolve student names via `IdentityUserClient` or join with user data.

2. **ProjectEvent → Course linkage**: Verify how `ProjectEvent` links to courses. The entity has `projectId` — check if `Project` has a `courseId` field, or if projects link through sections.

3. **Repository method names**: Several repository methods in `ResultsExportService` are assumed. Verify they exist or add them (e.g., `findByClientIdAndSectionIdAndActiveTrue` on EnrollmentRepository).

4. **IdentityUserClient.getUserByEmail()**: This method likely doesn't exist. Either:
   - Add a `/idp/users/by-email?email=` endpoint to the identity service
   - Or resolve students through the enrollment table (which already has student IDs)

5. **ApiClient.downloadFile()**: Verify this method exists in shared-utils `ApiClient`. If not, add it using axios with `responseType: 'blob'`.

6. **Assessment maxScore**: The design doc mentions `maxScore` on assessments, but the actual `Assessment` entity may use a calculated total from individual questions. Verify the scoring model.

7. **Azure Blob Storage in student module**: The student module has `AzureStorageConfig` — verify it creates a `BlobServiceClient` bean. If not, the `MediaUploadHelper` will need configuration.

---

Plan complete and saved to `docs/plans/2026-03-29-results-export-and-certificate-impl-plan.md`. Two execution options:

**1. Subagent-Driven (this session)** - I dispatch fresh subagent per task, review between tasks, fast iteration

**2. Parallel Session (separate)** - Open new session with executing-plans, batch execution with checkpoints

Which approach?