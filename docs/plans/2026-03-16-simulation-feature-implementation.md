# Simulation Feature Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build immersive, pre-generated branching decision simulations that teach concepts through experiential learning — with interactive decision UIs, admin generation/editing, student play tracking, and cross-tenant import/export.

**Architecture:** Content service (port 8082) handles simulation CRUD, AI generation, and play sessions. Admin dashboard (port 3000) provides generation form and tree editor. Student portal (port 3001) provides immersive play UI. Feature gated behind `SIMULATION` tenant feature flag.

**Tech Stack:** Spring Boot 3.3.4, JPA/Hibernate, Liquibase, Azure OpenAI (via FoundryAIService), Next.js 14, React Query, Tailwind/shadcn, TypeScript, JSONB for tree storage.

---

## Task 1: Feature Flag + Database Schema

**Files:**
- Modify: `identity/src/main/java/com/datagami/edudron/identity/domain/TenantFeatureType.java`
- Create: `content/src/main/resources/db/changelog/db.changelog-0025-simulation-tables.yaml`
- Modify: `content/src/main/resources/db/changelog/db.changelog-master.yaml`
- Modify: `frontend/packages/shared-utils/src/api/tenantFeatures.ts`
- Modify: `frontend/apps/admin-dashboard/src/app/settings/page.tsx`

**Step 1: Add SIMULATION to TenantFeatureType enum**

In `TenantFeatureType.java`, add after `AI_IMAGE_GENERATION`:

```java
AI_IMAGE_GENERATION(false, "Enable AI-powered image generation for courses and lectures"),

/**
 * Controls whether immersive decision simulations are available.
 * Default: false (premium feature, must be explicitly enabled per tenant)
 */
SIMULATION(false, "Enable immersive decision-based simulations for experiential learning");
```

**Step 2: Add SIMULATION to frontend TenantFeatureType enum**

In `tenantFeatures.ts`:

```typescript
export enum TenantFeatureType {
  STUDENT_SELF_ENROLLMENT = 'STUDENT_SELF_ENROLLMENT',
  PSYCHOMETRIC_TEST = 'PSYCHOMETRIC_TEST',
  PROCTORED_EXAMS = 'PROCTORED_EXAMS',
  AI_IMAGE_GENERATION = 'AI_IMAGE_GENERATION',
  SIMULATION = 'SIMULATION'
}
```

**Step 3: Add display name and description in settings page**

In `frontend/apps/admin-dashboard/src/app/settings/page.tsx`, add cases for `TenantFeatureType.SIMULATION` in both the name and description switch blocks:

```typescript
case TenantFeatureType.SIMULATION:
  return 'Simulations'
```

```typescript
case TenantFeatureType.SIMULATION:
  return 'Enable immersive decision-based simulations where students learn concepts through branching scenarios and real-world decision making.'
```

**Step 4: Create Liquibase changelog for simulation tables**

Create `content/src/main/resources/db/changelog/db.changelog-0025-simulation-tables.yaml`:

```yaml
databaseChangeLog:
  - changeSet:
      id: 0025-01-create-simulation-table
      author: system
      changes:
        - createTable:
            tableName: simulation
            schemaName: content
            columns:
              - column:
                  name: id
                  type: varchar(26)
                  constraints:
                    primaryKey: true
                    nullable: false
              - column:
                  name: client_id
                  type: uuid
                  constraints:
                    nullable: false
              - column:
                  name: course_id
                  type: varchar(26)
              - column:
                  name: lecture_id
                  type: varchar(26)
              - column:
                  name: title
                  type: varchar(500)
                  constraints:
                    nullable: false
              - column:
                  name: concept
                  type: varchar(500)
                  constraints:
                    nullable: false
              - column:
                  name: subject
                  type: varchar(255)
                  constraints:
                    nullable: false
              - column:
                  name: audience
                  type: varchar(50)
                  constraints:
                    nullable: false
              - column:
                  name: description
                  type: text
              - column:
                  name: tree_data
                  type: jsonb
              - column:
                  name: target_depth
                  type: int
                  defaultValueNumeric: 15
              - column:
                  name: choices_per_node
                  type: int
                  defaultValueNumeric: 3
              - column:
                  name: max_depth
                  type: int
              - column:
                  name: status
                  type: varchar(20)
                  defaultValue: DRAFT
                  constraints:
                    nullable: false
              - column:
                  name: visibility
                  type: varchar(20)
                  defaultValue: ALL
                  constraints:
                    nullable: false
              - column:
                  name: assigned_to_section_ids
                  type: text[]
              - column:
                  name: created_by
                  type: varchar(255)
              - column:
                  name: published_at
                  type: timestamptz
              - column:
                  name: created_at
                  type: timestamptz
                  defaultValueComputed: now()
              - column:
                  name: updated_at
                  type: timestamptz
                  defaultValueComputed: now()
              - column:
                  name: metadata_json
                  type: jsonb
        - createIndex:
            indexName: idx_simulation_client_id
            tableName: simulation
            schemaName: content
            columns:
              - column:
                  name: client_id
        - createIndex:
            indexName: idx_simulation_course_id
            tableName: simulation
            schemaName: content
            columns:
              - column:
                  name: course_id
        - createIndex:
            indexName: idx_simulation_status
            tableName: simulation
            schemaName: content
            columns:
              - column:
                  name: status

  - changeSet:
      id: 0025-02-create-simulation-play-table
      author: system
      changes:
        - createTable:
            tableName: simulation_play
            schemaName: content
            columns:
              - column:
                  name: id
                  type: varchar(26)
                  constraints:
                    primaryKey: true
                    nullable: false
              - column:
                  name: client_id
                  type: uuid
                  constraints:
                    nullable: false
              - column:
                  name: simulation_id
                  type: varchar(26)
                  constraints:
                    nullable: false
              - column:
                  name: student_id
                  type: varchar(26)
                  constraints:
                    nullable: false
              - column:
                  name: attempt_number
                  type: int
                  defaultValueNumeric: 1
                  constraints:
                    nullable: false
              - column:
                  name: is_primary
                  type: boolean
                  defaultValueBoolean: true
                  constraints:
                    nullable: false
              - column:
                  name: status
                  type: varchar(20)
                  defaultValue: IN_PROGRESS
                  constraints:
                    nullable: false
              - column:
                  name: path_json
                  type: jsonb
              - column:
                  name: current_node_id
                  type: varchar(100)
              - column:
                  name: final_node_id
                  type: varchar(100)
              - column:
                  name: score
                  type: int
              - column:
                  name: decisions_made
                  type: int
                  defaultValueNumeric: 0
              - column:
                  name: started_at
                  type: timestamptz
                  defaultValueComputed: now()
              - column:
                  name: completed_at
                  type: timestamptz
        - createIndex:
            indexName: idx_simulation_play_client_id
            tableName: simulation_play
            schemaName: content
            columns:
              - column:
                  name: client_id
        - createIndex:
            indexName: idx_simulation_play_simulation_id
            tableName: simulation_play
            schemaName: content
            columns:
              - column:
                  name: simulation_id
        - createIndex:
            indexName: idx_simulation_play_student_id
            tableName: simulation_play
            schemaName: content
            columns:
              - column:
                  name: student_id
        - createIndex:
            indexName: idx_simulation_play_student_simulation
            tableName: simulation_play
            schemaName: content
            columns:
              - column:
                  name: student_id
              - column:
                  name: simulation_id
```

**Step 5: Add changelog to master**

Append to `db.changelog-master.yaml`:

```yaml
  - include:
      file: db/changelog/db.changelog-0025-simulation-tables.yaml
```

**Step 6: Commit**

```bash
git add identity/src/main/java/com/datagami/edudron/identity/domain/TenantFeatureType.java \
  content/src/main/resources/db/changelog/db.changelog-0025-simulation-tables.yaml \
  content/src/main/resources/db/changelog/db.changelog-master.yaml \
  frontend/packages/shared-utils/src/api/tenantFeatures.ts \
  frontend/apps/admin-dashboard/src/app/settings/page.tsx
git commit -m "feat(simulation): add feature flag and database schema"
```

---

## Task 2: Backend Domain Entities + Repository

**Files:**
- Create: `content/src/main/java/com/datagami/edudron/content/simulation/domain/Simulation.java`
- Create: `content/src/main/java/com/datagami/edudron/content/simulation/domain/SimulationPlay.java`
- Create: `content/src/main/java/com/datagami/edudron/content/simulation/repo/SimulationRepository.java`
- Create: `content/src/main/java/com/datagami/edudron/content/simulation/repo/SimulationPlayRepository.java`

**Step 1: Create Simulation entity**

Follow the `PsychTestSession.java` pattern. Key fields:

```java
package com.datagami.edudron.content.simulation.domain;

@Entity
@Table(name = "simulation", schema = "content")
public class Simulation {
    @Id
    private String id;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "course_id")
    private String courseId;

    @Column(name = "lecture_id")
    private String lectureId;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(nullable = false, length = 500)
    private String concept;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false, length = 50)
    private String audience;

    @Column(columnDefinition = "text")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tree_data", columnDefinition = "jsonb")
    private Map<String, Object> treeData;

    @Column(name = "target_depth")
    private Integer targetDepth = 15;

    @Column(name = "choices_per_node")
    private Integer choicesPerNode = 3;

    @Column(name = "max_depth")
    private Integer maxDepth;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SimulationStatus status = SimulationStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SimulationVisibility visibility = SimulationVisibility.ALL;

    @Column(name = "assigned_to_section_ids", columnDefinition = "text[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private String[] assignedToSectionIds;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", columnDefinition = "jsonb")
    private Map<String, Object> metadataJson;

    public enum SimulationStatus {
        DRAFT, GENERATING, REVIEW, PUBLISHED, ARCHIVED
    }

    public enum SimulationVisibility {
        ALL, ASSIGNED_ONLY
    }

    @PrePersist
    public void prePersist() {
        if (id == null) id = UlidGenerator.nextUlid();
        if (createdAt == null) createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    // Getters and setters for all fields
}
```

**Step 2: Create SimulationPlay entity**

```java
package com.datagami.edudron.content.simulation.domain;

@Entity
@Table(name = "simulation_play", schema = "content")
public class SimulationPlay {
    @Id
    private String id;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "simulation_id", nullable = false)
    private String simulationId;

    @Column(name = "student_id", nullable = false)
    private String studentId;

    @Column(name = "attempt_number", nullable = false)
    private Integer attemptNumber = 1;

    @Column(name = "is_primary", nullable = false)
    private Boolean isPrimary = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlayStatus status = PlayStatus.IN_PROGRESS;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "path_json", columnDefinition = "jsonb")
    private List<Map<String, Object>> pathJson;

    @Column(name = "current_node_id", length = 100)
    private String currentNodeId;

    @Column(name = "final_node_id", length = 100)
    private String finalNodeId;

    private Integer score;

    @Column(name = "decisions_made")
    private Integer decisionsMade = 0;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    public enum PlayStatus {
        IN_PROGRESS, COMPLETED, ABANDONED
    }

    @PrePersist
    public void prePersist() {
        if (id == null) id = UlidGenerator.nextUlid();
        if (startedAt == null) startedAt = OffsetDateTime.now();
    }

    // Getters and setters
}
```

**Step 3: Create SimulationRepository**

```java
package com.datagami.edudron.content.simulation.repo;

public interface SimulationRepository extends JpaRepository<Simulation, String> {
    Page<Simulation> findByClientIdOrderByCreatedAtDesc(UUID clientId, Pageable pageable);
    Page<Simulation> findByClientIdAndStatusOrderByCreatedAtDesc(UUID clientId, Simulation.SimulationStatus status, Pageable pageable);
    List<Simulation> findByClientIdAndStatus(UUID clientId, Simulation.SimulationStatus status);
    Optional<Simulation> findByIdAndClientId(String id, UUID clientId);
    List<Simulation> findByCourseIdAndClientIdAndStatus(String courseId, UUID clientId, Simulation.SimulationStatus status);
}
```

**Step 4: Create SimulationPlayRepository**

```java
package com.datagami.edudron.content.simulation.repo;

public interface SimulationPlayRepository extends JpaRepository<SimulationPlay, String> {
    List<SimulationPlay> findBySimulationIdAndStudentIdOrderByAttemptNumberDesc(String simulationId, String studentId);
    Optional<SimulationPlay> findByIdAndStudentId(String id, String studentId);
    int countBySimulationIdAndStudentId(String simulationId, String studentId);
    List<SimulationPlay> findByStudentIdAndClientIdOrderByStartedAtDesc(String studentId, UUID clientId);
    Optional<SimulationPlay> findTopBySimulationIdAndStudentIdOrderByScoreDesc(String simulationId, String studentId);
}
```

**Step 5: Commit**

```bash
git add content/src/main/java/com/datagami/edudron/content/simulation/
git commit -m "feat(simulation): add domain entities and repositories"
```

---

## Task 3: Backend DTOs

**Files:**
- Create: `content/src/main/java/com/datagami/edudron/content/simulation/dto/SimulationDTO.java`
- Create: `content/src/main/java/com/datagami/edudron/content/simulation/dto/SimulationPlayDTO.java`
- Create: `content/src/main/java/com/datagami/edudron/content/simulation/dto/GenerateSimulationRequest.java`
- Create: `content/src/main/java/com/datagami/edudron/content/simulation/dto/SimulationNodeDTO.java`
- Create: `content/src/main/java/com/datagami/edudron/content/simulation/dto/DecisionInputDTO.java`
- Create: `content/src/main/java/com/datagami/edudron/content/simulation/dto/SimulationExportDTO.java`

**Step 1: Create DTOs**

`GenerateSimulationRequest.java`:
```java
public class GenerateSimulationRequest {
    @NotBlank private String concept;
    @NotBlank private String subject;
    @NotBlank private String audience;  // UNDERGRADUATE, MBA, GRADUATE
    private String courseId;            // optional link
    private String lectureId;          // optional link
    private Integer targetDepth;       // default 15, range 10-30
    private Integer choicesPerNode;    // default 3, range 2-4
    private String description;        // optional student-facing description
    // getters/setters
}
```

`SimulationDTO.java` — admin view (includes tree, quality, scores):
```java
public class SimulationDTO {
    private String id;
    private String title;
    private String concept;
    private String subject;
    private String audience;
    private String description;
    private String courseId;
    private String lectureId;
    private Map<String, Object> treeData;  // full tree for admin
    private Integer targetDepth;
    private Integer choicesPerNode;
    private Integer maxDepth;
    private String status;
    private String visibility;
    private String[] assignedToSectionIds;
    private String createdBy;
    private OffsetDateTime publishedAt;
    private OffsetDateTime createdAt;
    private int totalPlays;  // computed
    // static fromEntity(Simulation s)
}
```

`SimulationNodeDTO.java` — student view of a single node (stripped of quality/mappings):
```java
public class SimulationNodeDTO {
    private String nodeId;
    private String type;        // SCENARIO or TERMINAL
    private String narrative;
    private String decisionType;
    private Map<String, Object> decisionConfig;  // mappings stripped
    private List<ChoiceDTO> choices;              // quality stripped
    private DebriefDTO debrief;                   // only on TERMINAL nodes
    private Integer score;                        // only on TERMINAL nodes

    public static class ChoiceDTO {
        private String id;
        private String text;
        // NO nextNodeId, NO quality — these are server-side only
    }

    public static class DebriefDTO {
        private String yourPath;
        private String conceptAtWork;
        private String theGap;
        private String playAgain;
    }
}
```

`DecisionInputDTO.java`:
```java
public class DecisionInputDTO {
    private String nodeId;
    private String choiceId;        // for NARRATIVE_CHOICE (direct selection)
    private Map<String, Object> input; // for interactive types (slider values, rankings, allocations)
}
```

`SimulationPlayDTO.java`:
```java
public class SimulationPlayDTO {
    private String id;
    private String simulationId;
    private String simulationTitle;
    private int attemptNumber;
    private boolean isPrimary;
    private String status;
    private int decisionsMade;
    private Integer score;
    private OffsetDateTime startedAt;
    private OffsetDateTime completedAt;
}
```

`SimulationExportDTO.java`:
```java
public class SimulationExportDTO {
    private String version = "1.0";
    private OffsetDateTime exportedAt;
    private Map<String, Object> simulation;  // title, concept, subject, audience, description, treeData, targetDepth, choicesPerNode, metadataJson
}
```

**Step 2: Commit**

```bash
git add content/src/main/java/com/datagami/edudron/content/simulation/dto/
git commit -m "feat(simulation): add DTOs for admin, student, and export views"
```

---

## Task 4: Decision Mapping Engine

**Files:**
- Create: `content/src/main/java/com/datagami/edudron/content/simulation/service/DecisionMappingService.java`

**Step 1: Implement mapping resolution**

This is the core engine that resolves interactive inputs (budget allocations, rankings, sliders) to pre-generated choice IDs. It evaluates the `mappings` rules in `decisionConfig`.

```java
package com.datagami.edudron.content.simulation.service;

@Service
public class DecisionMappingService {

    /**
     * Resolve a student's interactive input to a choiceId.
     * For NARRATIVE_CHOICE, the choiceId comes directly from the input.
     * For interactive types, evaluates mapping conditions against the input data.
     */
    public String resolveChoice(Map<String, Object> node, DecisionInputDTO input) {
        String decisionType = (String) node.get("decisionType");

        if ("NARRATIVE_CHOICE".equals(decisionType)) {
            // Direct selection — validate choiceId exists in choices
            return validateAndReturnChoiceId(node, input.getChoiceId());
        }

        // For interactive types, evaluate mappings
        Map<String, Object> config = (Map<String, Object>) node.get("decisionConfig");
        List<Map<String, Object>> mappings = (List<Map<String, Object>>) config.get("mappings");

        if ("COMPOUND".equals(decisionType)) {
            return resolveCompoundMapping(mappings, input.getInput());
        }

        return resolveSimpleMapping(mappings, input.getInput());
    }

    // Evaluate mapping conditions like "rd >= 50", "top == 'asia'"
    // Returns the choiceId for the first matching condition, or "default" mapping
    private String resolveSimpleMapping(List<Map<String, Object>> mappings, Map<String, Object> input) { ... }

    // Evaluate compound conditions like "step1.marketing >= 40 && step2.top == 'asia'"
    private String resolveCompoundMapping(List<Map<String, Object>> mappings, Map<String, Object> input) { ... }

    // Simple expression evaluator for conditions
    private boolean evaluateCondition(String condition, Map<String, Object> context) { ... }

    private String validateAndReturnChoiceId(Map<String, Object> node, String choiceId) { ... }
}
```

The condition evaluator handles: `>=`, `<=`, `>`, `<`, `==`, `!=`, `&&`, `||`, and `default`. Input values are referenced by key name (e.g., `rd`, `marketing`) or `step1.key`/`step2.key` for compound types.

**Step 2: Commit**

```bash
git add content/src/main/java/com/datagami/edudron/content/simulation/service/DecisionMappingService.java
git commit -m "feat(simulation): add decision mapping engine for interactive inputs"
```

---

## Task 5: Simulation CRUD Service

**Files:**
- Create: `content/src/main/java/com/datagami/edudron/content/simulation/service/SimulationService.java`

**Step 1: Implement CRUD + play logic**

```java
package com.datagami.edudron.content.simulation.service;

@Service
public class SimulationService {

    // === Admin operations ===
    SimulationDTO getSimulation(String id);
    Page<SimulationDTO> listSimulations(Pageable pageable, String status);
    SimulationDTO updateSimulation(String id, SimulationDTO updates);
    SimulationDTO updateTree(String id, Map<String, Object> treeData);
    SimulationDTO publish(String id);
    SimulationDTO archive(String id);
    SimulationExportDTO exportSimulation(String id);
    SimulationDTO importSimulation(SimulationExportDTO exportData);

    // === Student operations ===
    List<SimulationDTO> getAvailableSimulations(String studentId);  // checks section assignments + visibility
    SimulationPlayDTO startPlay(String simulationId, String studentId);
    SimulationNodeDTO getCurrentNode(String playId, String studentId);
    SimulationNodeDTO submitDecision(String playId, String studentId, DecisionInputDTO input);
    SimulationNodeDTO getDebrief(String playId, String studentId);
    List<SimulationPlayDTO> getPlayHistory(String simulationId, String studentId);
    List<SimulationPlayDTO> getAllPlayHistory(String studentId);

    // === Tree helpers ===
    private Map<String, Object> getNode(Map<String, Object> treeData, String nodeId);
    private SimulationNodeDTO toStudentNode(Map<String, Object> node);  // strips quality, mappings, nextNodeId
    private int computeMaxDepth(Map<String, Object> treeData);
    private void validateTree(Map<String, Object> treeData);  // Phase 4 validation
}
```

Key logic in `startPlay`:
- Count existing plays for this student+simulation
- If count == 0: `isPrimary = true`, `attemptNumber = 1`
- If count > 0: check that at least one play is COMPLETED (replay unlocked). `isPrimary = false`, `attemptNumber = count + 1`
- Set `currentNodeId` to `rootNodeId` from tree

Key logic in `submitDecision`:
- Load play session, verify status is IN_PROGRESS
- Load current node from tree
- Call `DecisionMappingService.resolveChoice()` to get choiceId
- Find the choice, get nextNodeId
- Append to `pathJson`
- Update `currentNodeId` to nextNodeId, increment `decisionsMade`
- If next node is TERMINAL: set `status = COMPLETED`, `score`, `finalNodeId`, `completedAt`
- Return the next node (stripped for student view)

Key logic in `getAvailableSimulations`:
- Query published simulations for this tenant
- Filter: `visibility == ALL` OR student's section is in `assignedToSectionIds`
- Section lookup: query enrollments for this student to get their section IDs

**Step 2: Commit**

```bash
git add content/src/main/java/com/datagami/edudron/content/simulation/service/SimulationService.java
git commit -m "feat(simulation): add CRUD service with play logic and tree validation"
```

---

## Task 6: AI Generation Service

**Files:**
- Create: `content/src/main/java/com/datagami/edudron/content/simulation/service/SimulationGenerationService.java`

**Step 1: Implement 4-phase generation pipeline**

```java
package com.datagami.edudron.content.simulation.service;

@Service
public class SimulationGenerationService {

    @Autowired private FoundryAIService foundryAIService;
    @Autowired private SimulationRepository simulationRepository;
    @Autowired private ObjectMapper objectMapper;

    /**
     * Generate a complete simulation tree. Called by AIJobWorker.
     * Updates simulation status: GENERATING -> REVIEW (or DRAFT on failure).
     */
    public void generateSimulation(String simulationId, GenerateSimulationRequest request) {
        Simulation sim = simulationRepository.findById(simulationId).orElseThrow();

        try {
            // Phase 1: Generate golden path
            Map<String, Object> treeData = generateGoldenPath(request);

            // Phase 2: Generate failure + recovery branches
            treeData = generateBranches(treeData, request);

            // Phase 3: Generate debriefs for all terminal nodes
            treeData = generateDebriefs(treeData, request);

            // Phase 4: Validate tree integrity
            validateTree(treeData);

            sim.setTreeData(treeData);
            sim.setMaxDepth(computeMaxDepth(treeData));
            sim.setStatus(Simulation.SimulationStatus.REVIEW);
        } catch (Exception e) {
            sim.setStatus(Simulation.SimulationStatus.DRAFT);
            sim.setMetadataJson(Map.of("generationError", e.getMessage()));
        }
        simulationRepository.save(sim);
    }

    private Map<String, Object> generateGoldenPath(GenerateSimulationRequest request) { ... }
    private Map<String, Object> generateBranches(Map<String, Object> treeData, GenerateSimulationRequest request) { ... }
    private Map<String, Object> generateDebriefs(Map<String, Object> treeData, GenerateSimulationRequest request) { ... }
    private void validateTree(Map<String, Object> treeData) { ... }
    private int computeMaxDepth(Map<String, Object> treeData) { ... }
}
```

Phase 1 system prompt should use the user's simulation designer prompt (stored in a constant or resource file), with `[CONCEPT]`, `[SUBJECT/COURSE]`, `[AUDIENCE]` replaced, plus specific JSON output format instructions requesting varied `decisionType` values.

Phase 2 prompt receives the golden path and generates failure/recovery branches per the rules (quality=1 → terminal in 1-2 steps, quality=2 → recovery path of 2-3 nodes).

Phase 3 prompt receives all terminal nodes and generates debriefs following the exact structure (Your Path, Concept at Work, The Gap, Play Again) plus scores.

Phase 4 is pure code validation — no AI calls.

**Step 2: Commit**

```bash
git add content/src/main/java/com/datagami/edudron/content/simulation/service/SimulationGenerationService.java
git commit -m "feat(simulation): add 4-phase AI generation pipeline"
```

---

## Task 7: Async Job Integration

**Files:**
- Modify: `content/src/main/java/com/datagami/edudron/content/dto/AIGenerationJobDTO.java`
- Modify: `content/src/main/java/com/datagami/edudron/content/service/AIJobQueueService.java`
- Modify: `content/src/main/java/com/datagami/edudron/content/service/AIJobWorker.java`
- Modify: `content/src/main/java/com/datagami/edudron/content/service/AIQueueProcessor.java`

**Step 1: Add SIMULATION_GENERATION job type**

In `AIGenerationJobDTO.java`, add `SIMULATION_GENERATION` to the `JobType` enum.

**Step 2: Add queue and submit method to AIJobQueueService**

Add `SIMULATION_GENERATION_QUEUE = "ai:queue:simulation-generation"` constant and `submitSimulationGenerationJob()` method (same pattern as `submitImageGenerationJob`).

**Step 3: Add processSimulationGenerationJob to AIJobWorker**

```java
@Async("aiJobTaskExecutor")
public void processSimulationGenerationJob(String jobId) {
    // Same pattern as processImageGenerationJob:
    // Load job, set tenant context, update status to PROCESSING,
    // call simulationGenerationService.generateSimulation(),
    // update job to COMPLETED/FAILED, clear tenant context
}
```

**Step 4: Add queue processor**

In `AIQueueProcessor.java`, add `processSimulationGenerationQueue()` method with `@Scheduled(fixedDelay = 2000, initialDelay = 2500)` and its own `processingSimulationQueue` flag.

**Step 5: Commit**

```bash
git add content/src/main/java/com/datagami/edudron/content/dto/AIGenerationJobDTO.java \
  content/src/main/java/com/datagami/edudron/content/service/AIJobQueueService.java \
  content/src/main/java/com/datagami/edudron/content/service/AIJobWorker.java \
  content/src/main/java/com/datagami/edudron/content/service/AIQueueProcessor.java
git commit -m "feat(simulation): integrate with async AI job queue"
```

---

## Task 8: Admin Controller

**Files:**
- Create: `content/src/main/java/com/datagami/edudron/content/simulation/web/SimulationAdminController.java`

**Step 1: Implement admin endpoints**

```java
@RestController
@RequestMapping("/content/api/simulations")
@Tag(name = "Simulations (Admin)")
public class SimulationAdminController {

    @PostMapping("/generate")      // Submit generation job
    @GetMapping("/generate/jobs/{jobId}")  // Poll status
    @GetMapping                    // List (paginated, filterable)
    @GetMapping("/{id}")           // Get with full tree
    @PutMapping("/{id}")           // Update metadata
    @PutMapping("/{id}/tree")      // Update tree data
    @PostMapping("/{id}/publish")  // Publish
    @PostMapping("/{id}/archive")  // Archive
    @PostMapping("/{id}/export")   // Export JSON
    @PostMapping("/import")        // Import JSON
}
```

All endpoints check `userRole == SYSTEM_ADMIN || TENANT_ADMIN`. Feature flag check at class level or per method.

**Step 2: Commit**

```bash
git add content/src/main/java/com/datagami/edudron/content/simulation/web/SimulationAdminController.java
git commit -m "feat(simulation): add admin controller endpoints"
```

---

## Task 9: Student Controller

**Files:**
- Create: `content/src/main/java/com/datagami/edudron/content/simulation/web/SimulationStudentController.java`

**Step 1: Implement student endpoints**

```java
@RestController
@RequestMapping("/content/api/simulations")
@Tag(name = "Simulations (Student)")
public class SimulationStudentController {

    @GetMapping("/available")                      // List available
    @PostMapping("/{id}/play")                     // Start play
    @GetMapping("/{id}/play/{playId}")             // Get current node
    @PostMapping("/{id}/play/{playId}/decide")     // Submit decision
    @GetMapping("/{id}/play/{playId}/debrief")     // Get debrief
    @GetMapping("/{id}/history")                   // Play history
    @GetMapping("/my-history")                     // All history
}
```

The `/available` endpoint checks section enrollment via the student service RestTemplate call (using existing `TenantContextRestTemplateInterceptor` for header propagation).

**Step 2: Commit**

```bash
git add content/src/main/java/com/datagami/edudron/content/simulation/web/SimulationStudentController.java
git commit -m "feat(simulation): add student controller endpoints"
```

---

## Task 10: Gateway Route

**Files:**
- Modify: `gateway/src/main/resources/application.yml`

**Step 1: Add simulation route**

The simulations endpoints use `/content/api/simulations/**` which is already covered by the existing `content-service` route (`Path=/content/**, /api/content/**, /content/api/**`). Verify this matches. If the path is `/content/api/simulations/**`, it matches `/content/api/**` — already routed.

If needed, add an explicit route before the catch-all:
```yaml
- id: content-service-simulations
  uri: ${CONTENT_SERVICE_URL:http://localhost:8082}
  predicates:
    - Path=/content/api/simulations/**
```

**Step 2: Commit (if route was needed)**

```bash
git add gateway/src/main/resources/application.yml
git commit -m "feat(simulation): add gateway route for simulation endpoints"
```

---

## Task 11: Frontend API Client (shared-utils)

**Files:**
- Create: `frontend/packages/shared-utils/src/api/simulations.ts`
- Modify: `frontend/packages/shared-utils/src/index.ts`

**Step 1: Create SimulationsApi class**

```typescript
export class SimulationsApi {
  constructor(private apiClient: ApiClient) {}

  // Admin
  async generateSimulation(request: GenerateSimulationRequest) { ... }
  async getGenerationJobStatus(jobId: string) { ... }
  async listSimulations(page = 0, size = 20, status?: string) { ... }
  async getSimulation(id: string) { ... }
  async updateSimulation(id: string, data: Partial<SimulationDTO>) { ... }
  async updateTree(id: string, treeData: any) { ... }
  async publishSimulation(id: string) { ... }
  async archiveSimulation(id: string) { ... }
  async exportSimulation(id: string) { ... }
  async importSimulation(data: any) { ... }

  // Student
  async getAvailableSimulations() { ... }
  async startPlay(simulationId: string) { ... }
  async getCurrentNode(simulationId: string, playId: string) { ... }
  async submitDecision(simulationId: string, playId: string, input: DecisionInput) { ... }
  async getDebrief(simulationId: string, playId: string) { ... }
  async getPlayHistory(simulationId: string) { ... }
  async getAllPlayHistory() { ... }
}

export interface GenerateSimulationRequest { ... }
export interface SimulationDTO { ... }
export interface SimulationNodeDTO { ... }
export interface DecisionInput { ... }
export interface SimulationPlayDTO { ... }
```

**Step 2: Export from index.ts**

```typescript
export { SimulationsApi } from './api/simulations'
export type { GenerateSimulationRequest, SimulationDTO, SimulationNodeDTO, SimulationPlayDTO, DecisionInput } from './api/simulations'
```

**Step 3: Build shared-utils**

```bash
cd frontend/packages/shared-utils && npm run build
```

**Step 4: Add API instance to both apps**

In `frontend/apps/admin-dashboard/src/lib/api.ts`:
```typescript
import { SimulationsApi } from '@kunal-ak23/edudron-shared-utils'
export const simulationsApi = new SimulationsApi(apiClient)
```

In `frontend/apps/student-portal/src/lib/api.ts`:
```typescript
import { SimulationsApi } from '@kunal-ak23/edudron-shared-utils'
export const simulationsApi = new SimulationsApi(apiClient)
```

**Step 5: Commit**

```bash
git add frontend/packages/shared-utils/src/api/simulations.ts \
  frontend/packages/shared-utils/src/index.ts \
  frontend/apps/admin-dashboard/src/lib/api.ts \
  frontend/apps/student-portal/src/lib/api.ts
git commit -m "feat(simulation): add frontend API client and types"
```

---

## Task 12: Student Portal — Feature Hook + Simulations List

**Files:**
- Create: `frontend/apps/student-portal/src/hooks/useSimulationFeature.ts`
- Create: `frontend/apps/student-portal/src/app/simulations/page.tsx`
- Modify: Student portal sidebar/navigation to add Simulations link

**Step 1: Create feature hook**

Follow the exact pattern of `usePsychometricTestFeature.ts` but check `TenantFeatureType.SIMULATION`.

**Step 2: Create simulations list page**

Card grid showing available simulations. Each card: title, description, subject, audience badge, best score, attempt count, "Play" button.

**Step 3: Add nav link**

Add "Simulations" to the student portal sidebar (conditionally shown based on `useSimulationFeature()`).

**Step 4: Commit**

```bash
git add frontend/apps/student-portal/src/hooks/useSimulationFeature.ts \
  frontend/apps/student-portal/src/app/simulations/page.tsx \
  [sidebar file]
git commit -m "feat(simulation): add student portal simulations list page"
```

---

## Task 13: Student Portal — Play View

**Files:**
- Create: `frontend/apps/student-portal/src/app/simulations/[id]/play/[playId]/page.tsx`
- Create: `frontend/apps/student-portal/src/components/simulation/NarrativeChoiceInput.tsx`
- Create: `frontend/apps/student-portal/src/components/simulation/BudgetAllocationInput.tsx`
- Create: `frontend/apps/student-portal/src/components/simulation/PriorityRankingInput.tsx`
- Create: `frontend/apps/student-portal/src/components/simulation/TradeoffSliderInput.tsx`
- Create: `frontend/apps/student-portal/src/components/simulation/ResourceAssignmentInput.tsx`
- Create: `frontend/apps/student-portal/src/components/simulation/TimelineChoiceInput.tsx`
- Create: `frontend/apps/student-portal/src/components/simulation/CompoundInput.tsx`

**Step 1: Create play page**

Full-screen immersive layout. Loads current node via API. Renders narrative with typewriter animation (reuse existing `TypewriterText` component). Renders decision input based on `decisionType`. On submit, calls `/decide` and loads next node. On TERMINAL, redirects to debrief.

**Step 2: Create decision input components**

Each component receives `decisionConfig` and calls back with structured input data:

- `NarrativeChoiceInput` — styled cards, returns `{ choiceId }`
- `BudgetAllocationInput` — sliders summing to total, returns `{ bucketId: value, ... }`
- `PriorityRankingInput` — drag-and-drop list (use `@dnd-kit/core`), returns `{ ranking: [id1, id2, ...] }`
- `TradeoffSliderInput` — labeled slider, returns `{ value: 0-100 }`
- `ResourceAssignmentInput` — drag tokens to buckets, returns `{ bucketId: count, ... }`
- `TimelineChoiceInput` — clickable milestones, returns `{ selected: milestoneId }`
- `CompoundInput` — renders 2 steps sequentially, collects both inputs, returns `{ step1: {...}, step2: {...} }`

**Step 3: Commit**

```bash
git add frontend/apps/student-portal/src/app/simulations/ \
  frontend/apps/student-portal/src/components/simulation/
git commit -m "feat(simulation): add student play view with interactive decision components"
```

---

## Task 14: Student Portal — Debrief + History

**Files:**
- Create: `frontend/apps/student-portal/src/app/simulations/[id]/play/[playId]/debrief/page.tsx`
- Create: `frontend/apps/student-portal/src/app/simulations/[id]/history/page.tsx`

**Step 1: Create debrief page**

Four-section reveal: Your Path, The Concept at Work, The Gap, Play Again. Score display. "Play Again" button starts a new play. "View History" link.

**Step 2: Create history page**

Table of attempts: #, date, score, decisions made, outcome badge (success/fail). Click to view path replay (read-only walk through decisions).

**Step 3: Commit**

```bash
git add frontend/apps/student-portal/src/app/simulations/
git commit -m "feat(simulation): add debrief and history pages"
```

---

## Task 15: Admin Dashboard — Simulations List + Generation

**Files:**
- Create: `frontend/apps/admin-dashboard/src/app/simulations/page.tsx`
- Create: `frontend/apps/admin-dashboard/src/app/simulations/generate/page.tsx`
- Modify: `frontend/apps/admin-dashboard/src/components/Sidebar.tsx`

**Step 1: Create list page**

Table: Title, Concept, Course, Status badge, Depth, Total Plays, Created. Filter by status. "Generate New" button. "Import" button.

**Step 2: Create generation page**

Form with: Concept (text), Subject (text), Audience (dropdown: Undergraduate/MBA/Graduate), Course (optional dropdown), Target Depth (slider 10-30), Choices per Node (dropdown 2-4). Submit triggers async job. Poll for completion. Redirect to editor on complete.

Follow the same async job polling pattern as `/courses/generate`.

**Step 3: Add sidebar link**

Add "Simulations" under a relevant section in the sidebar menu items. Only show for SYSTEM_ADMIN and TENANT_ADMIN. Check feature flag.

**Step 4: Commit**

```bash
git add frontend/apps/admin-dashboard/src/app/simulations/ \
  frontend/apps/admin-dashboard/src/components/Sidebar.tsx
git commit -m "feat(simulation): add admin simulations list and generation pages"
```

---

## Task 16: Admin Dashboard — Simulation Editor

**Files:**
- Create: `frontend/apps/admin-dashboard/src/app/simulations/[id]/page.tsx`
- Create: `frontend/apps/admin-dashboard/src/components/simulation/SimulationTreeView.tsx`
- Create: `frontend/apps/admin-dashboard/src/components/simulation/SimulationNodeEditor.tsx`

**Step 1: Create editor page**

Split layout:
- **Left panel**: Collapsible tree visualization showing node hierarchy. Click a node to select it. Color-coded: green (golden path), yellow (recovery), red (terminal/failure).
- **Right panel**: Node editor form — edit narrative (textarea/markdown), decision type (dropdown), choices (editable list), debrief fields (for terminal nodes).
- **Header**: Title, status badge, Publish/Archive buttons, Export button.
- **Bottom section**: Assignment — visibility toggle (ALL/ASSIGNED_ONLY), section multi-select (same component pattern as course assignment).

**Step 2: Create tree view component**

Recursive collapsible tree. Each node shows: truncated title (first 50 chars of narrative), node type badge, decision type icon. Click to select. Highlight selected node.

**Step 3: Create node editor component**

Form with: narrative (textarea), decisionType (dropdown), decisionConfig (JSON editor or structured form per type), choices list (add/remove/reorder), debrief fields (for terminals), score (for terminals).

**Step 4: Commit**

```bash
git add frontend/apps/admin-dashboard/src/app/simulations/ \
  frontend/apps/admin-dashboard/src/components/simulation/
git commit -m "feat(simulation): add admin simulation editor with tree view"
```

---

## Task 17: Import/Export UI

**Files:**
- Modify: `frontend/apps/admin-dashboard/src/app/simulations/page.tsx` (add import button/dialog)

**Step 1: Add import functionality**

Import button on list page opens a file upload dialog. Accepts `.json` files. Calls `simulationsApi.importSimulation()`. On success, refreshes list and shows toast.

Export is already a button on the editor page (Task 16) that calls `simulationsApi.exportSimulation()` and triggers a file download.

**Step 2: Commit**

```bash
git add frontend/apps/admin-dashboard/src/app/simulations/page.tsx
git commit -m "feat(simulation): add import/export functionality"
```

---

## Task 18: Lecture Integration

**Files:**
- Modify: `frontend/apps/admin-dashboard/src/app/courses/[id]/lectures/[lectureId]/edit` (or equivalent lecture page)
- Modify: `frontend/apps/student-portal/src/app/courses/[id]/learn/page.tsx` (or equivalent)

**Step 1: Admin — link simulation from lecture editor**

Add a "Linked Simulation" section in the lecture editor. Dropdown to select an existing published simulation. "Generate for this lecture" shortcut that pre-fills the generation form with course/lecture context.

**Step 2: Student — show simulation CTA in lecture view**

When viewing a lecture that has a linked simulation, show a "Start Simulation" card/button below the lecture content. Clicking starts the play flow.

**Step 3: Commit**

```bash
git add frontend/apps/admin-dashboard/src/app/courses/ \
  frontend/apps/student-portal/src/app/courses/
git commit -m "feat(simulation): add lecture integration for embedded simulations"
```

---

## Task 19: Version Bump + Final Verification

**Step 1: Bump content service version**

```bash
./scripts/manage-versions.sh bump content minor
```

**Step 2: Build shared-utils**

```bash
cd frontend/packages/shared-utils && npm run build
```

**Step 3: Verify backend compiles**

```bash
cd content && ../gradlew compileJava
```

**Step 4: Verify frontend builds**

```bash
cd frontend/apps/admin-dashboard && npm run build
cd frontend/apps/student-portal && npm run build
```

**Step 5: Commit version bump**

```bash
git add versions.json
git commit -m "chore: bump content service version for simulation feature"
```

---

## Implementation Order Summary

| Task | Component | Depends On |
|---|---|---|
| 1 | Feature flag + DB schema | — |
| 2 | Domain entities + repos | 1 |
| 3 | DTOs | 2 |
| 4 | Decision mapping engine | 3 |
| 5 | CRUD service | 2, 3, 4 |
| 6 | AI generation service | 2, 3 |
| 7 | Async job integration | 5, 6 |
| 8 | Admin controller | 5, 7 |
| 9 | Student controller | 5 |
| 10 | Gateway route | 8, 9 |
| 11 | Frontend API client | 3 |
| 12 | Student list page | 11 |
| 13 | Student play view | 11, 12 |
| 14 | Student debrief + history | 13 |
| 15 | Admin list + generation | 11 |
| 16 | Admin editor | 15 |
| 17 | Import/export UI | 16 |
| 18 | Lecture integration | 13, 16 |
| 19 | Version bump + verify | All |
