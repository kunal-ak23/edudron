# Course-to-Simulation Smart Suggest Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a "Smart Suggest" button to the simulation generation form that analyzes course content and auto-fills simulation parameters via a single AI call.

**Architecture:** New synchronous backend endpoint reads course/lecture content, queries existing simulations, calls Azure OpenAI, returns suggestion. Frontend form reordered with course at top, lecture multi-select, and smart suggest button.

**Tech Stack:** Spring Boot (existing SimulationService), Azure OpenAI (existing FoundryAIService), Next.js 14, React, shared-utils API class.

---

## Task 1: Create Request/Response DTOs

**Files:**
- Create: `content/src/main/java/com/datagami/edudron/content/simulation/dto/SimulationSuggestionRequest.java`
- Create: `content/src/main/java/com/datagami/edudron/content/simulation/dto/SimulationSuggestionResponse.java`

**SimulationSuggestionRequest:**
```java
package com.datagami.edudron.content.simulation.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public class SimulationSuggestionRequest {
    @NotBlank
    private String courseId;
    private List<String> lectureIds;  // optional

    public String getCourseId() { return courseId; }
    public void setCourseId(String courseId) { this.courseId = courseId; }
    public List<String> getLectureIds() { return lectureIds; }
    public void setLectureIds(List<String> lectureIds) { this.lectureIds = lectureIds; }
}
```

**SimulationSuggestionResponse:**
```java
package com.datagami.edudron.content.simulation.dto;

import java.util.List;
import java.util.Map;

public class SimulationSuggestionResponse {
    private String concept;
    private String subject;
    private String audience;
    private String description;
    private List<Map<String, Object>> existingSimulations;

    // getters and setters for all fields
}
```

**Commit:** `feat(suggest): add request/response DTOs for simulation suggestion`

---

## Task 2: Add suggestFromCourse Method to SimulationService

**Files:**
- Modify: `content/src/main/java/com/datagami/edudron/content/simulation/service/SimulationService.java`

**Implementation:**

```java
@Transactional(readOnly = true)
@SuppressWarnings("unchecked")
public SimulationSuggestionResponse suggestFromCourse(SimulationSuggestionRequest request) {
    UUID clientId = UUID.fromString(TenantContext.getClientId());

    // 1. Load course
    Course course = courseRepository.findByIdAndClientId(request.getCourseId(), clientId)
            .orElseThrow(() -> new IllegalArgumentException("Course not found"));

    // 2. Build course context
    StringBuilder courseContext = new StringBuilder();
    courseContext.append("Course: ").append(course.getTitle()).append("\n");
    if (course.getDescription() != null) {
        courseContext.append("Description: ").append(course.getDescription()).append("\n");
    }

    // 3. Load lectures
    if (request.getLectureIds() != null && !request.getLectureIds().isEmpty()) {
        // Specific lectures with content
        List<Lecture> lectures = lectureRepository.findAllById(request.getLectureIds());
        courseContext.append("\nSelected Lectures:\n");
        for (Lecture lecture : lectures) {
            courseContext.append("- ").append(lecture.getTitle()).append("\n");
            // Load lecture content if available
            List<LectureContent> contents = lectureContentRepository
                    .findByLectureIdOrderBySortOrder(lecture.getId());
            for (LectureContent content : contents) {
                if (content.getContent() != null && content.getContent().length() < 2000) {
                    courseContext.append("  Content: ").append(content.getContent(), 0,
                            Math.min(content.getContent().length(), 500)).append("\n");
                }
            }
        }
    } else {
        // All lecture titles only
        List<Lecture> allLectures = lectureRepository.findByCourseIdOrderByOrder(request.getCourseId());
        if (!allLectures.isEmpty()) {
            courseContext.append("\nLecture Topics:\n");
            for (Lecture lecture : allLectures) {
                courseContext.append("- ").append(lecture.getTitle()).append("\n");
            }
        }
    }

    // 4. Query existing simulations
    List<Simulation> existing = simulationRepository.findByCourseIdAndClientId(
            request.getCourseId(), clientId);

    StringBuilder existingContext = new StringBuilder();
    List<Map<String, Object>> existingList = new ArrayList<>();
    for (Simulation sim : existing) {
        existingContext.append("- ").append(sim.getConcept()).append("\n");
        Map<String, Object> simInfo = new LinkedHashMap<>();
        simInfo.put("id", sim.getId());
        simInfo.put("title", sim.getTitle());
        simInfo.put("concept", sim.getConcept());
        simInfo.put("status", sim.getStatus().name());
        existingList.add(simInfo);
    }

    // 5. Call AI
    String systemPrompt = "You are analyzing course content to suggest a simulation concept.\n\n" +
            courseContext + "\n" +
            (existingContext.length() > 0
                    ? "Existing simulations for this course (DO NOT duplicate these):\n" + existingContext + "\n"
                    : "") +
            "Suggest a NEW simulation that:\n" +
            "1. Teaches a core concept from this course material\n" +
            "2. Is different from any existing simulations listed above\n" +
            "3. Can be experienced as a 5-year career journey with real-world decisions\n" +
            "4. Has a vivid, specific professional scenario\n\n" +
            "Return ONLY a JSON object:\n" +
            "{\n" +
            "  \"concept\": \"the underlying concept being taught (never revealed to students until debrief)\",\n" +
            "  \"subject\": \"the academic subject area\",\n" +
            "  \"audience\": \"UNDERGRADUATE or MBA or GRADUATE (infer from course level)\",\n" +
            "  \"description\": \"2-3 sentence student-facing description of the simulation experience\"\n" +
            "}";

    String userPrompt = "Analyze this course and suggest a simulation concept. Return ONLY the JSON object.";

    String response = foundryAIService.callOpenAI(systemPrompt, userPrompt);
    // Reuse existing JSON extraction from SimulationGenerationService
    String json = extractJson(response);

    try {
        Map<String, Object> parsed = objectMapper.readValue(json, new TypeReference<>() {});
        SimulationSuggestionResponse result = new SimulationSuggestionResponse();
        result.setConcept((String) parsed.get("concept"));
        result.setSubject((String) parsed.get("subject"));
        result.setAudience((String) parsed.get("audience"));
        result.setDescription((String) parsed.get("description"));
        result.setExistingSimulations(existingList);
        return result;
    } catch (Exception e) {
        throw new RuntimeException("Failed to parse AI suggestion response", e);
    }
}
```

Need to inject: `CourseRepository`, `LectureRepository`, `LectureContentRepository`, `FoundryAIService`, `ObjectMapper`.

Add a private `extractJson` helper (or reuse from `SimulationGenerationService` — may need to make it package-accessible or extract to a utility).

**Commit:** `feat(suggest): add suggestFromCourse to SimulationService`

---

## Task 3: Add Endpoint to SimulationAdminController

**Files:**
- Modify: `content/src/main/java/com/datagami/edudron/content/simulation/web/SimulationAdminController.java`

**Add:**
```java
@PostMapping("/suggest-from-course")
public ResponseEntity<SimulationSuggestionResponse> suggestFromCourse(
        @Valid @RequestBody SimulationSuggestionRequest request) {
    SimulationSuggestionResponse response = simulationService.suggestFromCourse(request);
    return ResponseEntity.ok(response);
}
```

**Commit:** `feat(suggest): add suggest-from-course endpoint`

---

## Task 4: Add SimulationRepository Query

**Files:**
- Modify: `content/src/main/java/com/datagami/edudron/content/simulation/repo/SimulationRepository.java`

**Check if `findByCourseIdAndClientId` already exists.** If not, add:

```java
List<Simulation> findByCourseIdAndClientId(String courseId, UUID clientId);
```

**Commit:** `feat(suggest): add findByCourseIdAndClientId to SimulationRepository`

---

## Task 5: Update Frontend Types — shared-utils

**Files:**
- Modify: `frontend/packages/shared-utils/src/api/simulations.ts`

**Add types:**
```typescript
export interface SimulationSuggestionRequest {
  courseId: string
  lectureIds?: string[]
}

export interface SimulationSuggestionResponse {
  concept: string
  subject: string
  audience: string
  description: string
  existingSimulations: Array<{ id: string; title: string; concept: string; status: string }>
}
```

**Add API method to SimulationsApi:**
```typescript
async suggestFromCourse(request: SimulationSuggestionRequest): Promise<SimulationSuggestionResponse> {
  const response = await this.apiClient.post<SimulationSuggestionResponse>(
    '/content/api/simulations/suggest-from-course', request)
  return response.data
}
```

Build: `cd frontend/packages/shared-utils && npm run build`

**Commit:** `feat(suggest): add suggestFromCourse to frontend API types`

---

## Task 6: Redesign Generation Form

**Files:**
- Modify: `frontend/apps/admin-dashboard/src/app/simulations/generate/page.tsx`

**Changes:**

1. **Move course dropdown to top** of the form (before concept field)
2. **Add lecture multi-select** — when course is selected, load lectures via existing courses API, show checkboxes
3. **Add "Smart Suggest" button** — shown when course is selected:
   ```tsx
   <Button
     variant="outline"
     onClick={handleSmartSuggest}
     disabled={suggesting || !selectedCourseId}
   >
     {suggesting ? (
       <><Loader2 className="h-4 w-4 mr-2 animate-spin" /> Analyzing course content...</>
     ) : (
       <><Sparkles className="h-4 w-4 mr-2" /> Smart Suggest</>
     )}
   </Button>
   ```
4. **handleSmartSuggest function:**
   ```typescript
   async function handleSmartSuggest() {
     setSuggesting(true)
     try {
       const response = await simulationsApi.suggestFromCourse({
         courseId: selectedCourseId,
         lectureIds: selectedLectureIds.length > 0 ? selectedLectureIds : undefined,
       })
       setConcept(response.concept)
       setSubject(response.subject)
       setAudience(response.audience)
       setDescription(response.description)
       setExistingSimulations(response.existingSimulations)
     } catch {
       toast.error('Failed to analyze course content')
     } finally {
       setSuggesting(false)
     }
   }
   ```
5. **Existing simulations warning** — show when `existingSimulations.length > 0`:
   ```tsx
   {existingSimulations.length > 0 && (
     <div className="p-3 bg-amber-50 border border-amber-200 rounded-lg text-sm">
       <p className="font-medium text-amber-800">
         {existingSimulations.length} simulation(s) already exist for this course:
       </p>
       {existingSimulations.map(s => (
         <p key={s.id} className="text-amber-700 mt-1">
           • {s.title} ({s.status})
         </p>
       ))}
     </div>
   )}
   ```

**Commit:** `feat(suggest): redesign generation form with Smart Suggest`

---

## Task 7: Build, Verify, Push

1. Build shared-utils: `cd frontend/packages/shared-utils && npm run build`
2. Verify backend: `./gradlew :content:compileJava`
3. Push: `git push origin feat/simulation`

**Commit:** `chore: verify smart suggest builds`

---

## Implementation Order

```
Task 1 (DTOs)                 ──┐
Task 4 (Repository query)    ──┤── Backend foundation
Task 2 (Service method)      ──┘
                                │
Task 3 (Controller endpoint)  ──── Backend wiring (depends on 1, 2)
                                │
Task 5 (Frontend types)       ──── Frontend foundation
                                │
Task 6 (Form redesign)        ──── Frontend (depends on 5)
                                │
Task 7 (Verify + push)        ──── Final
```
