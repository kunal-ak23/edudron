package com.datagami.edudron.content.simulation.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.content.domain.Course;
import com.datagami.edudron.content.domain.Lecture;
import com.datagami.edudron.content.domain.LectureContent;
import com.datagami.edudron.content.repo.CourseRepository;
import com.datagami.edudron.content.repo.LectureContentRepository;
import com.datagami.edudron.content.repo.LectureRepository;
import com.datagami.edudron.content.service.FoundryAIService;
import com.datagami.edudron.content.simulation.domain.Simulation;
import com.datagami.edudron.content.simulation.domain.SimulationPlay;
import com.datagami.edudron.content.simulation.dto.DebriefDTO;
import com.datagami.edudron.content.simulation.dto.DecisionInputDTO;
import com.datagami.edudron.content.simulation.dto.SimulationDecisionDTO;
import com.datagami.edudron.content.simulation.dto.SimulationDTO;
import com.datagami.edudron.content.simulation.dto.SimulationExportDTO;
import com.datagami.edudron.content.simulation.dto.SimulationPlayDTO;
import com.datagami.edudron.content.simulation.dto.SimulationStateDTO;
import com.datagami.edudron.content.simulation.dto.SimulationSuggestionRequest;
import com.datagami.edudron.content.simulation.dto.SimulationSuggestionResponse;
import com.datagami.edudron.content.simulation.dto.YearEndReviewDTO;
import com.datagami.edudron.content.simulation.repo.SimulationPlayRepository;
import com.datagami.edudron.content.simulation.repo.SimulationRepository;
import java.util.Arrays;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SimulationService {

    private static final Logger logger = LoggerFactory.getLogger(SimulationService.class);

    @Autowired
    private SimulationRepository simulationRepository;

    @Autowired
    private SimulationPlayRepository playRepository;

    @Autowired
    private DecisionMappingService decisionMappingService;

    @Autowired
    private BudgetCalculationService budgetCalculationService;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private LectureRepository lectureRepository;

    @Autowired
    private LectureContentRepository lectureContentRepository;

    @Autowired
    private FoundryAIService foundryAIService;

    @Autowired
    private SimulationGenerationService simulationGenerationService;

    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    // ============ ADMIN OPERATIONS ============

    @Transactional(readOnly = true)
    public SimulationDTO getSimulation(String id) {
        UUID clientId = UUID.fromString(TenantContext.getClientId());
        Simulation sim = simulationRepository.findByIdAndClientId(id, clientId)
                .orElseThrow(() -> new IllegalArgumentException("Simulation not found"));
        SimulationDTO dto = SimulationDTO.fromEntity(sim);
        dto.setTotalPlays((int) playRepository.countBySimulationIdAndClientId(id, clientId));
        return dto;
    }

    @Transactional(readOnly = true)
    public Page<SimulationDTO> listSimulations(Pageable pageable, String status) {
        UUID clientId = UUID.fromString(TenantContext.getClientId());
        Page<Simulation> page;
        if (status != null && !status.isEmpty()) {
            page = simulationRepository.findByClientIdAndStatusOrderByCreatedAtDesc(
                    clientId, Simulation.SimulationStatus.valueOf(status), pageable);
        } else {
            page = simulationRepository.findByClientIdOrderByCreatedAtDesc(clientId, pageable);
        }
        return page.map(sim -> {
            SimulationDTO dto = SimulationDTO.fromEntity(sim);
            dto.setTotalPlays((int) playRepository.countBySimulationIdAndClientId(sim.getId(), clientId));
            return dto;
        });
    }

    @Transactional
    public SimulationDTO updateSimulation(String id, SimulationDTO updates) {
        UUID clientId = UUID.fromString(TenantContext.getClientId());
        Simulation sim = simulationRepository.findByIdAndClientId(id, clientId)
                .orElseThrow(() -> new IllegalArgumentException("Simulation not found"));

        if (updates.getTitle() != null) {
            sim.setTitle(updates.getTitle());
        }
        if (updates.getDescription() != null) {
            sim.setDescription(updates.getDescription());
        }
        if (updates.getConcept() != null) {
            sim.setConcept(updates.getConcept());
        }
        if (updates.getSubject() != null) {
            sim.setSubject(updates.getSubject());
        }
        if (updates.getAudience() != null) {
            sim.setAudience(updates.getAudience());
        }
        if (updates.getVisibility() != null) {
            sim.setVisibility(Simulation.SimulationVisibility.valueOf(updates.getVisibility()));
        }
        if (updates.getAssignedToSectionIds() != null) {
            sim.setAssignedToSectionIds(updates.getAssignedToSectionIds().toArray(new String[0]));
        }
        if (updates.getCourseId() != null) {
            sim.setCourseId(updates.getCourseId());
        }
        if (updates.getLectureId() != null) {
            sim.setLectureId(updates.getLectureId());
        }
        if (updates.getTargetYears() != null) {
            sim.setTargetYears(updates.getTargetYears());
        }
        if (updates.getDecisionsPerYear() != null) {
            sim.setDecisionsPerYear(updates.getDecisionsPerYear());
        }

        simulationRepository.save(sim);

        SimulationDTO dto = SimulationDTO.fromEntity(sim);
        dto.setTotalPlays((int) playRepository.countBySimulationIdAndClientId(id, clientId));
        return dto;
    }

    @Transactional
    public SimulationDTO updateSimulationData(String id, Map<String, Object> simulationData) {
        UUID clientId = UUID.fromString(TenantContext.getClientId());
        Simulation sim = simulationRepository.findByIdAndClientId(id, clientId)
                .orElseThrow(() -> new IllegalArgumentException("Simulation not found"));

        sim.setSimulationData(simulationData);
        simulationRepository.save(sim);

        SimulationDTO dto = SimulationDTO.fromEntity(sim);
        dto.setTotalPlays((int) playRepository.countBySimulationIdAndClientId(id, clientId));
        return dto;
    }

    @Transactional
    public SimulationDTO publish(String id) {
        UUID clientId = UUID.fromString(TenantContext.getClientId());
        Simulation sim = simulationRepository.findByIdAndClientId(id, clientId)
                .orElseThrow(() -> new IllegalArgumentException("Simulation not found"));

        if (sim.getStatus() != Simulation.SimulationStatus.REVIEW) {
            throw new IllegalStateException(
                    "Cannot publish simulation in status: " + sim.getStatus()
                            + ". Must be in REVIEW status.");
        }

        sim.setStatus(Simulation.SimulationStatus.PUBLISHED);
        sim.setPublishedAt(OffsetDateTime.now());
        simulationRepository.save(sim);

        SimulationDTO dto = SimulationDTO.fromEntity(sim);
        dto.setTotalPlays((int) playRepository.countBySimulationIdAndClientId(id, clientId));
        return dto;
    }

    @Transactional
    public SimulationDTO archive(String id) {
        UUID clientId = UUID.fromString(TenantContext.getClientId());
        Simulation sim = simulationRepository.findByIdAndClientId(id, clientId)
                .orElseThrow(() -> new IllegalArgumentException("Simulation not found"));

        sim.setStatus(Simulation.SimulationStatus.ARCHIVED);
        simulationRepository.save(sim);

        SimulationDTO dto = SimulationDTO.fromEntity(sim);
        dto.setTotalPlays((int) playRepository.countBySimulationIdAndClientId(id, clientId));
        return dto;
    }

    @Transactional
    public void abandonPlay(String playId, String studentId) {
        SimulationPlay play = playRepository.findByIdAndStudentId(playId, studentId)
                .orElseThrow(() -> new IllegalArgumentException("Play not found"));
        if (play.getStatus() == SimulationPlay.PlayStatus.IN_PROGRESS) {
            play.setStatus(SimulationPlay.PlayStatus.ABANDONED);
            playRepository.save(play);
        }
    }

    @Transactional
    public void deleteSimulation(String id) {
        UUID clientId = UUID.fromString(TenantContext.getClientId());
        Simulation sim = simulationRepository.findByIdAndClientId(id, clientId)
                .orElseThrow(() -> new IllegalArgumentException("Simulation not found"));
        // Delete all plays for this simulation first
        playRepository.deleteBySimulationIdAndClientId(id, clientId);
        simulationRepository.delete(sim);
    }

    @Transactional
    public SimulationDTO moveToDraft(String id) {
        UUID clientId = UUID.fromString(TenantContext.getClientId());
        Simulation sim = simulationRepository.findByIdAndClientId(id, clientId)
                .orElseThrow(() -> new IllegalArgumentException("Simulation not found"));

        if (sim.getStatus() == Simulation.SimulationStatus.GENERATING) {
            throw new IllegalStateException("Cannot move a simulation that is currently generating.");
        }

        sim.setStatus(Simulation.SimulationStatus.REVIEW);
        sim.setPublishedAt(null);
        simulationRepository.save(sim);

        SimulationDTO dto = SimulationDTO.fromEntity(sim);
        dto.setTotalPlays((int) playRepository.countBySimulationIdAndClientId(id, clientId));
        return dto;
    }

    @Transactional
    public SimulationDTO moveToPublished(String id) {
        UUID clientId = UUID.fromString(TenantContext.getClientId());
        Simulation sim = simulationRepository.findByIdAndClientId(id, clientId)
                .orElseThrow(() -> new IllegalArgumentException("Simulation not found"));

        if (sim.getStatus() == Simulation.SimulationStatus.GENERATING) {
            throw new IllegalStateException("Cannot publish a simulation that is currently generating.");
        }
        if (sim.getSimulationData() == null) {
            throw new IllegalStateException("Cannot publish a simulation without generated content.");
        }

        sim.setStatus(Simulation.SimulationStatus.PUBLISHED);
        if (sim.getPublishedAt() == null) {
            sim.setPublishedAt(OffsetDateTime.now());
        }
        simulationRepository.save(sim);

        SimulationDTO dto = SimulationDTO.fromEntity(sim);
        dto.setTotalPlays((int) playRepository.countBySimulationIdAndClientId(id, clientId));
        return dto;
    }

    @Transactional(readOnly = true)
    public SimulationExportDTO exportSimulation(String id) {
        UUID clientId = UUID.fromString(TenantContext.getClientId());
        Simulation sim = simulationRepository.findByIdAndClientId(id, clientId)
                .orElseThrow(() -> new IllegalArgumentException("Simulation not found"));

        SimulationExportDTO.SimulationData data = new SimulationExportDTO.SimulationData();
        data.setTitle(sim.getTitle());
        data.setConcept(sim.getConcept());
        data.setSubject(sim.getSubject());
        data.setAudience(sim.getAudience());
        data.setDescription(sim.getDescription());
        data.setSimulationData(sim.getSimulationData());
        data.setTargetYears(sim.getTargetYears());
        data.setDecisionsPerYear(sim.getDecisionsPerYear());
        data.setMetadataJson(sim.getMetadataJson());

        SimulationExportDTO export = new SimulationExportDTO();
        export.setExportedAt(OffsetDateTime.now());
        export.setSimulation(data);
        return export;
    }

    @Transactional
    public SimulationDTO importSimulation(SimulationExportDTO exportData, String createdBy) {
        UUID clientId = UUID.fromString(TenantContext.getClientId());
        SimulationExportDTO.SimulationData data = exportData.getSimulation();

        if (data == null) {
            throw new IllegalArgumentException("Export data must contain simulation data");
        }

        Simulation sim = new Simulation();
        sim.setClientId(clientId);
        sim.setTitle(data.getTitle());
        sim.setConcept(data.getConcept());
        sim.setSubject(data.getSubject());
        sim.setAudience(data.getAudience());
        sim.setDescription(data.getDescription());
        sim.setSimulationData(data.getSimulationData());
        sim.setTargetYears(data.getTargetYears());
        sim.setDecisionsPerYear(data.getDecisionsPerYear());
        sim.setMetadataJson(data.getMetadataJson());
        sim.setStatus(Simulation.SimulationStatus.REVIEW);
        sim.setCreatedBy(createdBy);

        simulationRepository.save(sim);

        SimulationDTO dto = SimulationDTO.fromEntity(sim);
        dto.setTotalPlays(0);
        return dto;
    }

    // ============ SMART SUGGEST ============

    @Transactional(readOnly = true)
    public SimulationSuggestionResponse suggestFromCourse(SimulationSuggestionRequest request) {
        UUID clientId = UUID.fromString(TenantContext.getClientId());

        Course course = courseRepository.findByIdAndClientId(request.getCourseId(), clientId)
                .orElseThrow(() -> new IllegalArgumentException("Course not found"));

        // Build course context
        StringBuilder courseContext = new StringBuilder();
        courseContext.append("Course: ").append(course.getTitle()).append("\n");
        if (course.getDescription() != null) {
            courseContext.append("Description: ").append(course.getDescription()).append("\n");
        }

        // Load lectures
        if (request.getLectureIds() != null && !request.getLectureIds().isEmpty()) {
            courseContext.append("\nSelected Lectures:\n");
            for (String lectureId : request.getLectureIds()) {
                lectureRepository.findById(lectureId).ifPresent(lecture -> {
                    courseContext.append("- ").append(lecture.getTitle()).append("\n");
                    List<LectureContent> contents = lectureContentRepository
                            .findByLectureIdAndClientIdOrderBySequenceAsc(lecture.getId(), clientId);
                    for (LectureContent content : contents) {
                        if (content.getTextContent() != null) {
                            String text = content.getTextContent();
                            if (text.length() > 500) text = text.substring(0, 500) + "...";
                            courseContext.append("  Content: ").append(text).append("\n");
                        }
                    }
                });
            }
        } else {
            List<Lecture> allLectures = lectureRepository
                    .findByCourseIdAndClientIdOrderBySequenceAsc(request.getCourseId(), clientId);
            if (!allLectures.isEmpty()) {
                courseContext.append("\nLecture Topics:\n");
                for (Lecture lecture : allLectures) {
                    courseContext.append("- ").append(lecture.getTitle()).append("\n");
                }
            }
        }

        // Query existing simulations
        List<Simulation> existing = simulationRepository.findByCourseIdAndClientId(
                request.getCourseId(), clientId);

        StringBuilder existingContext = new StringBuilder();
        List<Map<String, Object>> existingList = new ArrayList<>();
        for (Simulation sim : existing) {
            if (sim.getConcept() != null) {
                existingContext.append("- ").append(sim.getConcept()).append("\n");
            }
            Map<String, Object> simInfo = new LinkedHashMap<>();
            simInfo.put("id", sim.getId());
            simInfo.put("title", sim.getTitle());
            simInfo.put("concept", sim.getConcept());
            simInfo.put("status", sim.getStatus().name());
            existingList.add(simInfo);
        }

        // Call AI
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
        String json = simulationGenerationService.extractJsonObject(response);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(json,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
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

    // ============ STUDENT OPERATIONS ============

    @Transactional(readOnly = true)
    public List<SimulationDTO> getAvailableSimulations(String studentId, String studentSectionId) {
        UUID clientId = UUID.fromString(TenantContext.getClientId());
        List<Simulation> published = simulationRepository.findByClientIdAndStatus(
                clientId, Simulation.SimulationStatus.PUBLISHED);

        return published.stream()
                .filter(sim -> isSimulationAccessible(sim, studentSectionId))
                .map(sim -> {
                    SimulationDTO dto = SimulationDTO.fromEntity(sim);
                    dto.setSimulationData(null); // Strip data from list view
                    dto.setConcept(null);  // Concept must never be visible to students before play
                    dto.setCreatedBy(null);
                    dto.setTotalPlays((int) playRepository.countBySimulationIdAndClientId(sim.getId(), clientId));
                    return dto;
                })
                .collect(Collectors.toList());
    }

    /**
     * Check if a simulation is accessible to a student based on visibility and section assignment.
     * - ALL visibility: accessible to everyone
     * - ASSIGNED_ONLY: accessible only if student's section is in assignedToSectionIds,
     *   or if no sections are assigned (course-wide access)
     */
    private boolean isSimulationAccessible(Simulation sim, String studentSectionId) {
        if (sim.getVisibility() == Simulation.SimulationVisibility.ALL) {
            return true;
        }
        // ASSIGNED_ONLY — check section assignment
        String[] assignedSections = sim.getAssignedToSectionIds();
        if (assignedSections == null || assignedSections.length == 0) {
            return true; // No sections assigned = accessible to all
        }
        if (studentSectionId == null) {
            return false; // Student has no section but simulation requires one
        }
        return Arrays.asList(assignedSections).contains(studentSectionId);
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public SimulationPlayDTO startPlay(String simulationId, String studentId, String studentSectionId) {
        UUID clientId = UUID.fromString(TenantContext.getClientId());
        Simulation sim = simulationRepository.findByIdAndClientId(simulationId, clientId)
                .orElseThrow(() -> new IllegalArgumentException("Simulation not found"));

        if (sim.getStatus() != Simulation.SimulationStatus.PUBLISHED) {
            throw new IllegalStateException("Simulation is not published");
        }

        if (!isSimulationAccessible(sim, studentSectionId)) {
            throw new IllegalStateException("This simulation is not available for your section");
        }

        int existingPlays = playRepository.countBySimulationIdAndStudentId(simulationId, studentId);
        boolean isPrimary = (existingPlays == 0);

        // If not primary (replay), verify student has at least one completed/fired play
        if (!isPrimary) {
            List<SimulationPlay> plays = playRepository
                    .findBySimulationIdAndStudentIdOrderByAttemptNumberDesc(simulationId, studentId);
            boolean hasFinished = plays.stream()
                    .anyMatch(p -> p.getStatus() == SimulationPlay.PlayStatus.COMPLETED
                            || p.getStatus() == SimulationPlay.PlayStatus.FIRED);
            if (!hasFinished) {
                throw new IllegalStateException("Must complete simulation before replaying");
            }
        }

        // Get role progression from simulation data to set initial role
        Map<String, Object> simData = sim.getSimulationData();
        String initialRole = null;
        if (simData != null) {
            List<String> roleProgression = (List<String>) simData.get("roleProgression");
            if (roleProgression != null && !roleProgression.isEmpty()) {
                initialRole = roleProgression.get(0);
            }
        }

        SimulationPlay play = new SimulationPlay();
        play.setClientId(clientId);
        play.setSimulationId(simulationId);
        play.setStudentId(studentId);
        play.setAttemptNumber(existingPlays + 1);
        play.setIsPrimary(isPrimary);
        play.setCurrentYear(1);
        play.setCurrentDecision(0);
        play.setCurrentRole(initialRole);
        play.setCumulativeScore(0);
        play.setConsecutiveStruggling(0);
        play.setPerformanceBand("STEADY"); // First year starts as STEADY
        play.setDecisionsJson(new ArrayList<>());
        play.setYearScoresJson(new ArrayList<>());

        // v3: Initialize budget from financial model
        if (simData != null) {
            Map<String, Object> financialModel = (Map<String, Object>) simData.get("financialModel");
            if (financialModel != null) {
                play.setCurrentBudget(budgetCalculationService.getYearStartBudget(financialModel, null));
                play.setBudgetHistoryJson(new ArrayList<>());
            }
        }

        playRepository.save(play);
        return SimulationPlayDTO.fromEntity(play, sim.getTitle());
    }

    // ============ V2 PLAY FLOW ============

    /**
     * Get the current state of a play session. Returns the appropriate phase:
     * - DEBRIEF if completed
     * - FIRED if fired
     * - YEAR_END_REVIEW if all decisions for the year are done
     * - DECISION if there are decisions remaining (includes openingNarrative at start of year)
     */
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public SimulationStateDTO getCurrentState(String playId, String studentId) {
        SimulationPlay play = playRepository.findByIdAndStudentId(playId, studentId)
                .orElseThrow(() -> new IllegalArgumentException("Play not found"));

        UUID clientId = UUID.fromString(TenantContext.getClientId());
        if (!play.getClientId().equals(clientId)) {
            throw new IllegalArgumentException("Play not found");
        }

        Simulation sim = simulationRepository.findByIdAndClientId(play.getSimulationId(), clientId)
                .orElseThrow(() -> new IllegalArgumentException("Simulation not found"));

        Map<String, Object> simData = sim.getSimulationData();
        int decisionsPerYear = sim.getDecisionsPerYear();

        SimulationStateDTO state = new SimulationStateDTO();
        state.setCurrentYear(play.getCurrentYear());
        state.setCurrentDecision(play.getCurrentDecision());
        state.setTotalDecisions(decisionsPerYear);
        state.setTotalYears(sim.getTargetYears());
        state.setCurrentRole(play.getCurrentRole());
        state.setCumulativeScore(play.getCumulativeScore());
        state.setYearScore(calculateCurrentYearScore(play));
        state.setPerformanceBand(play.getPerformanceBand());
        // Set budget with fallback to startingBudget from financialModel
        BigDecimal budget = play.getCurrentBudget();
        if ((budget == null || budget.compareTo(BigDecimal.ZERO) == 0) && simData != null) {
            Map<String, Object> financialModel = (Map<String, Object>) simData.get("financialModel");
            if (financialModel != null && financialModel.get("startingBudget") != null) {
                budget = new BigDecimal(financialModel.get("startingBudget").toString());
            }
        }
        state.setCurrentBudget(budget);

        // Build decision history from decisionsJson
        List<Map<String, Object>> history = new ArrayList<>();
        int goodCount = 0, badCount = 0, neutralCount = 0;
        List<String> insights = new ArrayList<>();

        if (play.getDecisionsJson() != null) {
            List<Map<String, Object>> years = (List<Map<String, Object>>) simData.get("years");

            for (Map<String, Object> decision : play.getDecisionsJson()) {
                Map<String, Object> entry = new LinkedHashMap<>();
                int year = ((Number) decision.get("year")).intValue();
                int decIndex = ((Number) decision.get("decisionIndex")).intValue();
                String choiceId = (String) decision.get("choiceId");
                int quality = ((Number) decision.get("quality")).intValue();
                int points = ((Number) decision.getOrDefault("points", 0)).intValue();

                // Get the decision narrative snippet from simulation data
                String label = choiceId; // fallback
                try {
                    if (years != null && year > 0 && year <= years.size()) {
                        Map<String, Object> yearData = years.get(year - 1);
                        List<Map<String, Object>> decisions = (List<Map<String, Object>>) yearData.get("decisions");
                        if (decisions != null && decIndex >= 0 && decIndex < decisions.size()) {
                            Map<String, Object> decData = decisions.get(decIndex);
                            // Find the choice text
                            List<Map<String, Object>> choices = (List<Map<String, Object>>) decData.get("choices");
                            if (choices != null) {
                                for (Map<String, Object> choice : choices) {
                                    if (choiceId.equals(choice.get("id"))) {
                                        label = (String) choice.get("text");
                                        break;
                                    }
                                }
                            }
                            // Get advisor reaction as insight
                            Map<String, Object> reactions = (Map<String, Object>) decData.get("advisorReaction");
                            if (reactions != null) {
                                Map<String, Object> reaction = (Map<String, Object>) reactions.get("quality_" + quality);
                                if (reaction != null && reaction.get("text") != null) {
                                    String reactionText = (String) reaction.get("text");
                                    if (reactionText.length() > 20) { // Only meaningful reactions
                                        insights.add(reactionText);
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    // Safe fallback — use choiceId as label
                }

                // Truncate label if too long
                if (label != null && label.length() > 60) {
                    label = label.substring(0, 57) + "...";
                }

                entry.put("year", year);
                entry.put("decision", decIndex + 1);
                entry.put("label", label);
                entry.put("quality", quality == 3 ? "GOOD" : quality == 2 ? "MEDIUM" : "BAD");
                entry.put("points", points);
                entry.put("choiceId", choiceId);

                history.add(entry);

                if (quality == 3) goodCount++;
                else if (quality == 2) neutralCount++;
                else badCount++;
            }
        }

        state.setDecisionHistory(history);
        state.setGoodDecisionCount(goodCount);
        state.setBadDecisionCount(badCount);
        state.setNeutralDecisionCount(neutralCount);
        // Keep only latest 5 insights
        state.setKeyInsights(insights.size() > 5 ? insights.subList(insights.size() - 5, insights.size()) : insights);

        // v3: Include advisor dialog from current decision
        Map<String, Object> advisorCharacter = (Map<String, Object>) simData.get("advisorCharacter");

        // 1. Completed → DEBRIEF
        if (play.getStatus() == SimulationPlay.PlayStatus.COMPLETED) {
            state.setPhase("DEBRIEF");
            state.setDebrief(buildDebrief(simData, play.getPerformanceBand()));
            return state;
        }

        // 2. Fired → FIRED
        if (play.getStatus() == SimulationPlay.PlayStatus.FIRED) {
            state.setPhase("FIRED");
            state.setDebrief(buildFiredDebrief(simData));
            return state;
        }

        // 3. Year-end review: all decisions for the year are done
        if (play.getCurrentDecision() >= decisionsPerYear) {
            state.setPhase("YEAR_END_REVIEW");
            String yearBand = calculateBand(calculateCurrentYearScore(play), decisionsPerYear);
            state.setYearEndReview(buildYearEndReview(simData, play.getCurrentYear(), yearBand,
                    sim, play));

            // v3: Include financial report from budget history if available
            if (play.getBudgetHistoryJson() != null) {
                for (Map<String, Object> hist : play.getBudgetHistoryJson()) {
                    if (((Number) hist.get("year")).intValue() == play.getCurrentYear()
                            && hist.containsKey("returns")) {
                        Map<String, Object> report = new LinkedHashMap<>();
                        report.put("departments", hist.get("returns"));
                        report.put("totalInvested", hist.get("totalInvested"));
                        report.put("totalReturns", hist.get("totalReturns"));
                        report.put("endingBudget", hist.get("endingBudget"));
                        state.setFinancialReport(report);
                        break;
                    }
                }
            }

            return state;
        }

        // 4. Decision phase
        state.setPhase("DECISION");

        // Include opening narrative at the start of each year (decision index 0)
        if (play.getCurrentDecision() == 0) {
            String narrative = getOpeningNarrative(simData, play.getCurrentYear(),
                    play.getPerformanceBand());
            state.setOpeningNarrative(narrative);
        }

        // Get the current decision and convert to student-facing DTO
        Map<String, Object> decision = getDecision(simData, play.getCurrentYear(),
                play.getCurrentDecision());
        state.setDecision(toStudentDecision(decision));

        // v3: Include advisor dialog for this decision
        if (decision.get("advisorDialog") != null) {
            Map<String, Object> dialog = new LinkedHashMap<>();
            dialog.put("mood", decision.get("advisorMood"));
            dialog.put("text", decision.get("advisorDialog"));
            if (advisorCharacter != null) {
                dialog.put("advisorName", advisorCharacter.get("name"));
                if (advisorCharacter.get("characterId") != null) {
                    dialog.put("characterId", advisorCharacter.get("characterId"));
                }
                // Mentor retirement story arc metadata
                if (advisorCharacter.get("retirementYear") != null) {
                    dialog.put("retirementYear", advisorCharacter.get("retirementYear"));
                }
                if (advisorCharacter.get("farewellMessage") != null) {
                    dialog.put("farewellMessage", advisorCharacter.get("farewellMessage"));
                }
            }
            state.setAdvisorDialog(dialog);
        }

        return state;
    }

    /**
     * Submit a decision for the current play session.
     * Resolves the choice, calculates points, advances the decision counter,
     * and returns the next state.
     */
    @Transactional
    @SuppressWarnings("unchecked")
    public SimulationStateDTO submitDecision(String playId, String studentId, DecisionInputDTO input) {
        SimulationPlay play = playRepository.findByIdAndStudentId(playId, studentId)
                .orElseThrow(() -> new IllegalArgumentException("Play not found"));

        UUID clientId = UUID.fromString(TenantContext.getClientId());
        if (!play.getClientId().equals(clientId)) {
            throw new IllegalArgumentException("Play not found");
        }

        if (play.getStatus() != SimulationPlay.PlayStatus.IN_PROGRESS) {
            throw new IllegalStateException("Play is not in progress");
        }

        Simulation sim = simulationRepository.findByIdAndClientId(play.getSimulationId(), clientId)
                .orElseThrow(() -> new IllegalArgumentException("Simulation not found"));

        Map<String, Object> simData = sim.getSimulationData();
        int decisionsPerYear = sim.getDecisionsPerYear();

        if (play.getCurrentDecision() >= decisionsPerYear) {
            throw new IllegalStateException(
                    "All decisions for this year are complete. Call advanceYear to proceed.");
        }

        // Get the decision configuration
        Map<String, Object> decision = getDecision(simData, play.getCurrentYear(),
                play.getCurrentDecision());

        // Verify the submitted decision ID matches
        String expectedDecisionId = (String) decision.get("id");
        if (input.getDecisionId() != null && !input.getDecisionId().equals(expectedDecisionId)) {
            throw new IllegalArgumentException("Decision ID mismatch. Expected: " + expectedDecisionId);
        }

        // Resolve the choice via the mapping service
        String resolvedChoiceId = decisionMappingService.resolveChoice(
                decision, input.getChoiceId(), input.getInput());

        // Find the choice and its quality
        List<Map<String, Object>> choices = (List<Map<String, Object>>) decision.get("choices");
        Map<String, Object> selectedChoice = choices.stream()
                .filter(c -> resolvedChoiceId.equals(c.get("id")))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Resolved choice not found"));

        int quality = ((Number) selectedChoice.get("quality")).intValue();
        int points = qualityToPoints(quality);

        // v3: If INVESTMENT_PORTFOLIO, save allocations to budget history (one entry per year)
        String decisionType = (String) decision.get("decisionType");
        if ("INVESTMENT_PORTFOLIO".equals(decisionType) && input.getInput() != null) {
            List<Map<String, Object>> budgetHistory = play.getBudgetHistoryJson();
            if (budgetHistory == null) {
                budgetHistory = new ArrayList<>();
            }
            // Update existing year entry or create new one (prevents duplicates)
            boolean found = false;
            for (Map<String, Object> hist : budgetHistory) {
                if (((Number) hist.get("year")).intValue() == play.getCurrentYear()) {
                    hist.put("allocations", input.getInput());
                    found = true;
                    break;
                }
            }
            if (!found) {
                Map<String, Object> yearEntry = new LinkedHashMap<>();
                yearEntry.put("year", play.getCurrentYear());
                yearEntry.put("allocations", input.getInput());
                budgetHistory.add(yearEntry);
            }
            play.setBudgetHistoryJson(budgetHistory);
        }

        // Update play state
        play.setCumulativeScore(play.getCumulativeScore() + points);
        play.setCurrentDecision(play.getCurrentDecision() + 1);

        // Record the decision in decisionsJson
        List<Map<String, Object>> decisions = play.getDecisionsJson();
        if (decisions == null) {
            decisions = new ArrayList<>();
        }
        Map<String, Object> decisionRecord = new LinkedHashMap<>();
        decisionRecord.put("year", play.getCurrentYear());
        decisionRecord.put("decisionIndex", play.getCurrentDecision() - 1); // 0-based index of the decision just made
        decisionRecord.put("decisionId", expectedDecisionId);
        decisionRecord.put("choiceId", resolvedChoiceId);
        decisionRecord.put("quality", quality);
        decisionRecord.put("points", points);
        if (input.getInput() != null) {
            decisionRecord.put("rawInput", input.getInput());
        }
        decisions.add(decisionRecord);
        play.setDecisionsJson(decisions);

        // v3: Store advisor reaction for the decision just made
        Map<String, Object> advisorReactionData = (Map<String, Object>) decision.get("advisorReaction");
        String reactionKey = "quality_" + quality;

        playRepository.save(play);

        // Return the next state with advisor reaction attached
        SimulationStateDTO nextState = getCurrentState(playId, studentId);
        if (advisorReactionData != null && advisorReactionData.containsKey(reactionKey)) {
            Map<String, Object> reaction = (Map<String, Object>) advisorReactionData.get(reactionKey);
            if (reaction != null) {
                nextState.setAdvisorReaction(reaction);
            }
        }
        return nextState;
    }

    /**
     * Advance to the next year after the student has seen the year-end review.
     * Handles promotions, firing, and completion.
     */
    @Transactional
    @SuppressWarnings("unchecked")
    public SimulationStateDTO advanceYear(String playId, String studentId) {
        SimulationPlay play = playRepository.findByIdAndStudentId(playId, studentId)
                .orElseThrow(() -> new IllegalArgumentException("Play not found"));

        UUID clientId = UUID.fromString(TenantContext.getClientId());
        if (!play.getClientId().equals(clientId)) {
            throw new IllegalArgumentException("Play not found");
        }

        if (play.getStatus() != SimulationPlay.PlayStatus.IN_PROGRESS) {
            throw new IllegalStateException("Play is not in progress");
        }

        Simulation sim = simulationRepository.findByIdAndClientId(play.getSimulationId(), clientId)
                .orElseThrow(() -> new IllegalArgumentException("Simulation not found"));

        Map<String, Object> simData = sim.getSimulationData();
        int decisionsPerYear = sim.getDecisionsPerYear();

        // Verify we're at the end of the year
        if (play.getCurrentDecision() < decisionsPerYear) {
            throw new IllegalStateException(
                    "Cannot advance year — not all decisions have been made for the current year.");
        }

        // Calculate the year's band
        int yearScore = calculateCurrentYearScore(play);
        String yearBand = calculateBand(yearScore, decisionsPerYear);

        // v3: Calculate year-end budget returns if financial model exists
        Map<String, Object> financialModel = (Map<String, Object>) simData.get("financialModel");
        if (financialModel != null && play.getBudgetHistoryJson() != null) {
            // Find this year's allocations from budget history
            Map<String, BigDecimal> allocations = new LinkedHashMap<>();
            for (Map<String, Object> hist : play.getBudgetHistoryJson()) {
                if (((Number) hist.get("year")).intValue() == play.getCurrentYear()) {
                    Map<String, Object> allocs = (Map<String, Object>) hist.get("allocations");
                    if (allocs != null) {
                        allocs.forEach((k, v) -> allocations.put(k, new BigDecimal(v.toString())));
                    }
                    break;
                }
            }

            if (!allocations.isEmpty()) {
                Map<String, Object> financialReport = budgetCalculationService.calculateYearEndReturns(
                        allocations, financialModel, yearBand,
                        play.getBudgetHistoryJson(), play.getId(), play.getCurrentYear());

                // Update budget history with returns
                for (Map<String, Object> hist : play.getBudgetHistoryJson()) {
                    if (((Number) hist.get("year")).intValue() == play.getCurrentYear()) {
                        hist.put("returns", financialReport.get("departments"));
                        hist.put("totalInvested", financialReport.get("totalInvested"));
                        hist.put("totalReturns", financialReport.get("totalReturns"));
                        hist.put("endingBudget", financialReport.get("endingBudget"));
                        break;
                    }
                }

                play.setCurrentBudget((BigDecimal) financialReport.get("endingBudget"));
            }
        }

        // Store year result in yearScoresJson
        List<Map<String, Object>> yearScores = play.getYearScoresJson();
        if (yearScores == null) {
            yearScores = new ArrayList<>();
        }
        Map<String, Object> yearResult = new LinkedHashMap<>();
        yearResult.put("year", play.getCurrentYear());
        yearResult.put("score", yearScore);
        yearResult.put("band", yearBand);
        yearResult.put("role", play.getCurrentRole());
        yearScores.add(yearResult);
        play.setYearScoresJson(yearScores);

        // Handle consecutive struggling logic
        if ("STRUGGLING".equals(yearBand)) {
            play.setConsecutiveStruggling(play.getConsecutiveStruggling() + 1);
            if (play.getConsecutiveStruggling() >= 2) {
                // FIRED
                play.setStatus(SimulationPlay.PlayStatus.FIRED);
                play.setPerformanceBand(yearBand);
                play.setFinalScore(calculateFinalScore(play, sim));
                play.setCompletedAt(OffsetDateTime.now());
                playRepository.save(play);
                return getCurrentState(playId, studentId);
            }
        } else {
            play.setConsecutiveStruggling(0);
        }

        // Handle promotion on THRIVING
        if ("THRIVING".equals(yearBand)) {
            List<String> roleProgression = getRoleProgression(simData);
            if (roleProgression != null && play.getCurrentRole() != null) {
                int currentRoleIndex = roleProgression.indexOf(play.getCurrentRole());
                if (currentRoleIndex >= 0 && currentRoleIndex < roleProgression.size() - 1) {
                    play.setCurrentRole(roleProgression.get(currentRoleIndex + 1));
                }
            }
        }

        // Check if simulation is complete
        if (play.getCurrentYear() >= sim.getTargetYears()) {
            play.setStatus(SimulationPlay.PlayStatus.COMPLETED);
            play.setPerformanceBand(yearBand);
            play.setFinalScore(calculateFinalScore(play, sim));
            play.setCompletedAt(OffsetDateTime.now());
            playRepository.save(play);
            return getCurrentState(playId, studentId);
        }

        // Advance to next year
        play.setCurrentYear(play.getCurrentYear() + 1);
        play.setCurrentDecision(0);
        play.setPerformanceBand(yearBand);

        playRepository.save(play);
        return getCurrentState(playId, studentId);
    }

    @Transactional(readOnly = true)
    public List<SimulationPlayDTO> getPlayHistory(String simulationId, String studentId) {
        UUID clientId = UUID.fromString(TenantContext.getClientId());
        Simulation sim = simulationRepository.findByIdAndClientId(simulationId, clientId)
                .orElseThrow(() -> new IllegalArgumentException("Simulation not found"));
        List<SimulationPlay> plays = playRepository
                .findBySimulationIdAndStudentIdOrderByAttemptNumberDesc(simulationId, studentId);
        return plays.stream()
                .map(p -> SimulationPlayDTO.fromEntity(p, sim.getTitle()))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<SimulationPlayDTO> getAllPlayHistory(String studentId) {
        UUID clientId = UUID.fromString(TenantContext.getClientId());
        List<SimulationPlay> plays = playRepository
                .findByStudentIdAndClientIdOrderByStartedAtDesc(studentId, clientId);
        return plays.stream()
                .map(p -> {
                    String title = simulationRepository.findByIdAndClientId(p.getSimulationId(), clientId)
                            .map(Simulation::getTitle).orElse("Unknown");
                    return SimulationPlayDTO.fromEntity(p, title);
                })
                .collect(Collectors.toList());
    }

    // ============ SIMULATION DATA HELPERS ============

    @SuppressWarnings("unchecked")
    private Map<String, Object> getYear(Map<String, Object> simData, int yearNum) {
        List<Map<String, Object>> years = (List<Map<String, Object>>) simData.get("years");
        if (years == null || yearNum < 1 || yearNum > years.size()) {
            throw new IllegalStateException("Year " + yearNum + " not found in simulation data");
        }
        return years.get(yearNum - 1); // 1-based to 0-based
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getDecision(Map<String, Object> simData, int yearNum, int decisionIndex) {
        Map<String, Object> year = getYear(simData, yearNum);
        List<Map<String, Object>> decisions = (List<Map<String, Object>>) year.get("decisions");
        if (decisions == null || decisionIndex < 0 || decisionIndex >= decisions.size()) {
            throw new IllegalStateException(
                    "Decision " + decisionIndex + " not found in year " + yearNum);
        }
        return decisions.get(decisionIndex);
    }

    @SuppressWarnings("unchecked")
    private String getOpeningNarrative(Map<String, Object> simData, int yearNum, String band) {
        Map<String, Object> year = getYear(simData, yearNum);
        Map<String, String> narratives = (Map<String, String>) year.get("openingNarrative");
        if (narratives == null) {
            return "";
        }
        return narratives.getOrDefault(band, narratives.getOrDefault("STEADY", ""));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getYearEndReviewData(Map<String, Object> simData, int yearNum,
            String band) {
        Map<String, Object> year = getYear(simData, yearNum);
        Map<String, Object> reviews = (Map<String, Object>) year.get("yearEndReview");
        if (reviews == null) {
            return Map.of();
        }
        // Map performance band to review key: THRIVING→STRONG, STEADY→MID, STRUGGLING→POOR
        String reviewKey = bandToReviewKey(band);
        Map<String, Object> review = (Map<String, Object>) reviews.get(reviewKey);
        if (review == null) {
            // Fallback to MID
            review = (Map<String, Object>) reviews.get("MID");
        }
        return review != null ? review : Map.of();
    }

    @SuppressWarnings("unchecked")
    private List<String> getRoleProgression(Map<String, Object> simData) {
        return (List<String>) simData.get("roleProgression");
    }

    private String calculateBand(int yearScore, int decisionsPerYear) {
        double ratio = (double) yearScore / (decisionsPerYear * 10);
        if (ratio >= 0.67) return "THRIVING";
        if (ratio >= 0.33) return "STEADY";
        return "STRUGGLING";
    }

    private String bandToReviewKey(String band) {
        return switch (band) {
            case "THRIVING" -> "STRONG";
            case "STRUGGLING" -> "POOR";
            default -> "MID";
        };
    }

    private int qualityToPoints(int quality) {
        return switch (quality) {
            case 3 -> 10;
            case 2 -> 5;
            default -> 0;
        };
    }

    /**
     * Calculate the score for the current year based on decisions made so far.
     */
    @SuppressWarnings("unchecked")
    private int calculateCurrentYearScore(SimulationPlay play) {
        List<Map<String, Object>> decisions = play.getDecisionsJson();
        if (decisions == null || decisions.isEmpty()) {
            return 0;
        }

        int currentYear = play.getCurrentYear();
        return decisions.stream()
                .filter(d -> {
                    Object yearObj = d.get("year");
                    return yearObj != null && ((Number) yearObj).intValue() == currentYear;
                })
                .mapToInt(d -> {
                    Object pointsObj = d.get("points");
                    return pointsObj != null ? ((Number) pointsObj).intValue() : 0;
                })
                .sum();
    }

    /**
     * Calculate the final normalized score (0-100) based on cumulative score vs max possible.
     */
    private int calculateFinalScore(SimulationPlay play, Simulation sim) {
        int maxPossible = sim.getTargetYears() * sim.getDecisionsPerYear() * 10;
        if (maxPossible == 0) return 0;
        return (int) Math.round((double) play.getCumulativeScore() / maxPossible * 100);
    }

    /**
     * Convert a decision config map to a student-facing SimulationDecisionDTO,
     * stripping quality scores and mapping configuration from choices.
     */
    @SuppressWarnings("unchecked")
    private SimulationDecisionDTO toStudentDecision(Map<String, Object> decision) {
        SimulationDecisionDTO dto = new SimulationDecisionDTO();
        dto.setDecisionId((String) decision.get("id"));
        dto.setNarrative((String) decision.get("narrative"));
        dto.setDecisionType((String) decision.get("decisionType"));
        dto.setDisplayLabel((String) decision.get("displayLabel"));

        // Include decision config but strip mappings (server-side only)
        Map<String, Object> config = (Map<String, Object>) decision.get("decisionConfig");
        if (config != null) {
            Map<String, Object> studentConfig = new LinkedHashMap<>(config);
            studentConfig.remove("mappings"); // Mappings are server-side only
            dto.setDecisionConfig(studentConfig);
        }

        // Pass through concept keywords
        List<Map<String, String>> conceptKeywords = (List<Map<String, String>>) decision.get("conceptKeywords");
        if (conceptKeywords != null) {
            dto.setConceptKeywords(conceptKeywords);
        }

        // Pass through mentor guidance (for guided years 1-3)
        Map<String, Object> mentorGuidance = (Map<String, Object>) decision.get("mentorGuidance");
        if (mentorGuidance != null) {
            dto.setMentorGuidance(mentorGuidance);
        }

        // Convert choices, stripping quality scores
        List<Map<String, Object>> choices = (List<Map<String, Object>>) decision.get("choices");
        if (choices != null) {
            List<SimulationDecisionDTO.ChoiceDTO> studentChoices = choices.stream()
                    .map(c -> new SimulationDecisionDTO.ChoiceDTO(
                            (String) c.get("id"),
                            (String) c.get("text")))
                    .collect(Collectors.toList());
            dto.setChoices(studentChoices);
        }

        return dto;
    }

    /**
     * Build a YearEndReviewDTO from simulation data for the given year and band.
     */
    @SuppressWarnings("unchecked")
    private YearEndReviewDTO buildYearEndReview(Map<String, Object> simData, int yearNum,
            String band, Simulation sim, SimulationPlay play) {
        Map<String, Object> reviewData = getYearEndReviewData(simData, yearNum, band);

        YearEndReviewDTO review = new YearEndReviewDTO();
        review.setYear(yearNum);
        review.setBand(band);
        review.setMetrics((Map<String, Object>) reviewData.get("metrics"));
        review.setFeedback((Map<String, String>) reviewData.get("feedback"));

        // Determine if student will be promoted (THRIVING)
        if ("THRIVING".equals(band)) {
            List<String> roleProgression = getRoleProgression(simData);
            if (roleProgression != null && play.getCurrentRole() != null) {
                int currentRoleIndex = roleProgression.indexOf(play.getCurrentRole());
                if (currentRoleIndex >= 0 && currentRoleIndex < roleProgression.size() - 1) {
                    review.setPromotionTitle(roleProgression.get(currentRoleIndex + 1));
                }
            }
        }

        // Determine if student will be fired (2nd consecutive STRUGGLING)
        if ("STRUGGLING".equals(band) && play.getConsecutiveStruggling() >= 1) {
            // This would be the 2nd consecutive struggling year
            review.setFired(true);
        }

        return review;
    }

    /**
     * Build a DebriefDTO for completed simulations based on final performance band.
     */
    @SuppressWarnings("unchecked")
    private DebriefDTO buildDebrief(Map<String, Object> simData, String band) {
        Map<String, Object> finalDebrief = (Map<String, Object>) simData.get("finalDebrief");
        if (finalDebrief == null) {
            return new DebriefDTO();
        }

        // Map band to debrief key
        Map<String, Object> bandDebrief = (Map<String, Object>) finalDebrief.get(band);
        if (bandDebrief == null) {
            bandDebrief = (Map<String, Object>) finalDebrief.get("STEADY");
        }
        if (bandDebrief == null) {
            return new DebriefDTO();
        }

        return new DebriefDTO(
                (String) bandDebrief.get("yourPath"),
                (String) bandDebrief.get("conceptAtWork"),
                (String) bandDebrief.get("theGap"),
                (String) bandDebrief.get("playAgain"));
    }

    /**
     * Build a DebriefDTO for fired students.
     */
    @SuppressWarnings("unchecked")
    private DebriefDTO buildFiredDebrief(Map<String, Object> simData) {
        // FIRED debrief is stored under finalDebrief.FIRED (not a separate firedDebrief key)
        Map<String, Object> finalDebrief = (Map<String, Object>) simData.get("finalDebrief");
        if (finalDebrief == null) {
            return new DebriefDTO();
        }

        Map<String, Object> firedDebrief = (Map<String, Object>) finalDebrief.get("FIRED");
        if (firedDebrief == null) {
            return new DebriefDTO();
        }

        return new DebriefDTO(
                (String) firedDebrief.get("yourPath"),
                (String) firedDebrief.get("conceptAtWork"),
                (String) firedDebrief.get("theGap"),
                (String) firedDebrief.get("playAgain"));
    }
}
