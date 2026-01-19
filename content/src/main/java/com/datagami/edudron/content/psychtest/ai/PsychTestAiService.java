package com.datagami.edudron.content.psychtest.ai;

import com.datagami.edudron.content.psychtest.service.RecommendationService.RecommendedCourse;
import com.datagami.edudron.content.psychtest.service.ScoringService;
import com.datagami.edudron.content.service.FoundryAIService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PsychTestAiService {
    private static final Logger logger = LoggerFactory.getLogger(PsychTestAiService.class);

    private final FoundryAIService foundryAIService;
    private final ObjectMapper objectMapper;

    public PsychTestAiService(FoundryAIService foundryAIService, ObjectMapper objectMapper) {
        this.foundryAIService = foundryAIService;
        this.objectMapper = objectMapper;
    }

    public boolean isConfigured() {
        return foundryAIService.isConfigured();
    }

    public record AdaptivePick(String chosenQuestionId, String rationale) {}

    public record AnswerMeaning(String questionId, String meaning) {}

    public record PersonalizedQuestion(String prompt, Map<String, String> optionLabelsById) {}

    public AdaptivePick chooseNextQuestionId(
        List<String> eligibleQuestionIds,
        Set<String> answeredQuestionIds,
        ScoringService.ScoringSnapshot snapshot
    ) {
        if (!isConfigured()) return null;

        String system = """
            You are selecting the next question in an adaptive RIASEC psychometric test.

            CRITICAL RULES:
            - You MUST choose ONLY from eligible_question_ids.
            - Do NOT invent new questions.
            - Return JSON only. No markdown, no extra text.
            - If unsure, choose a question that best disambiguates the top domains and increases confidence.

            Output schema:
            {
              "chosen_question_id": "string",
              "rationale": "short string"
            }
            """;

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eligible_question_ids", eligibleQuestionIds);
        payload.put("answered_question_ids", answeredQuestionIds);
        payload.put("top_domains", snapshot.topDomains());
        payload.put("top_margin", snapshot.topMargin());
        payload.put("overall_confidence_level", snapshot.overallConfidenceLevel());
        payload.put("overall_confidence_score", snapshot.overallConfidenceScore());
        payload.put("domain_scores_0_100", snapshot.domains().entrySet().stream()
            .collect(java.util.stream.Collectors.toMap(
                Map.Entry::getKey,
                e -> e.getValue().score0To100(),
                (a, b) -> a,
                LinkedHashMap::new
            )));

        String user;
        try {
            user = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return null;
        }

        String raw;
        try {
            raw = foundryAIService.callOpenAI(system, user);
        } catch (Exception e) {
            logger.warn("Adaptive selection AI call failed: {}", e.getMessage());
            return null;
        }

        AdaptivePick parsed = parseAdaptivePick(raw);
        if (parsed == null) return null;

        if (!eligibleQuestionIds.contains(parsed.chosenQuestionId())) {
            logger.warn("AI returned invalid chosen_question_id={} not in eligible list", parsed.chosenQuestionId());
            return null;
        }

        return parsed;
    }

    /**
     * Personalize a question prompt + option labels without changing meaning.
     * The output MUST keep the same option ids (labels can change).
     */
    public PersonalizedQuestion personalizeQuestion(
        String questionId,
        String questionType,
        String basePrompt,
        List<Map<String, Object>> options,
        Map<String, Object> personalizationContext
    ) {
        if (!isConfigured()) return null;

        String system = """
            You are rewriting a psychometric question to feel more personal and conversational, without changing meaning.

            GOAL:
            - Personalize the prompt using the student's name (if provided) and optionally refer to their previous answer (if provided).
            - Keep it short and clear.
            - You MAY also rewrite option labels to be more natural, but MUST preserve each option's intent.

            SPECIAL INSTRUCTIONS FOR LIKERT:
            - If question_type is "LIKERT", option labels MUST stay as an ordered agreement scale (5-point or 7-point).
            - Use the provided "value" to preserve direction (higher value = stronger agreement / stronger presence).
            - Make each label feel tied to the prompt (e.g., "Mostly — I prefer tasks that allow imagination and flexibility").
            - Keep each label concise (<= 90 chars), and do NOT add extra options.

            CRITICAL RULES:
            - Do NOT change the scoring semantics.
            - Do NOT change the number of options.
            - Do NOT change option ids (you will be given option_id for each option).
            - Do NOT invent new options.
            - Do NOT infer sensitive traits (gender, caste, religion, diagnosis, mental health labels).
            - Avoid stereotypes.
            - Return JSON only. No markdown, no extra text.

            Output schema:
            {
              "prompt": "string",
              "options": [
                { "option_id": "string", "label": "string" }
              ]
            }
            """;

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("question_id", questionId);
        input.put("question_type", questionType);
        input.put("base_prompt", basePrompt);
        input.put("options", options);
        input.put("personalization_context", personalizationContext);

        String user;
        try {
            user = objectMapper.writeValueAsString(input);
        } catch (Exception e) {
            return null;
        }

        String raw;
        try {
            raw = foundryAIService.callOpenAI(system, user);
        } catch (Exception e) {
            logger.warn("Question personalization AI call failed: {}", e.getMessage());
            return null;
        }

        try {
            String json = extractJsonObject(raw);
            JsonNode root = objectMapper.readTree(json);
            String prompt = root.path("prompt").asText(null);
            JsonNode opts = root.path("options");
            if (prompt == null || prompt.isBlank() || !opts.isArray()) return null;

            Map<String, String> labels = new LinkedHashMap<>();
            for (JsonNode o : opts) {
                String id = o.path("option_id").asText(null);
                String label = o.path("label").asText(null);
                if (id == null || id.isBlank() || label == null || label.isBlank()) continue;
                labels.put(id, label);
            }
            return new PersonalizedQuestion(prompt, labels);
        } catch (Exception e) {
            logger.warn("Failed to parse question personalization JSON: {}", e.getMessage());
            return null;
        }
    }

    public String generateNarrativeReportJson(
        List<String> topDomains,
        String overallConfidence,
        String streamSuggestion,
        List<String> careerFields,
        List<RecommendedCourse> courses
    ) {
        if (!isConfigured()) return null;

        String system = """
            You are generating a career guidance report for a student based on deterministic RIASEC scoring and curated courses.

            SAFETY + GUARDRAILS:
            - Do NOT infer sensitive traits (gender, caste, religion, diagnosis, mental health labels).
            - Avoid stereotypes.
            - Do NOT invent courses. Use ONLY the provided recommended_courses list.
            - Do NOT invent new career fields outside the provided career_fields list.
            - Tone: supportive, non-deterministic ("may", "could", "suggested"), age-appropriate.
            - Include the disclaimer exactly: "guidance, not diagnosis"

            Output JSON only (no markdown).
            Output schema:
            {
              "disclaimer": "guidance, not diagnosis",
              "summary": "string",
              "why_this_fits": ["string", "..."],
              "strengths": ["string", "..."],
              "growth_areas": ["string", "..."],
              "next_steps": ["string", "..."],
              "recommended_courses": [{"courseId":"...","title":"...","reason":"..."}]
            }
            """;

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("top_domains", topDomains);
        input.put("overall_confidence", overallConfidence);
        input.put("stream_suggestion", streamSuggestion);
        input.put("career_fields", careerFields);
        input.put("recommended_courses", courses.stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("courseId", c.courseId());
            m.put("title", c.title());
            m.put("reason", c.reason());
            return m;
        }).toList());

        String user;
        try {
            user = objectMapper.writeValueAsString(input);
        } catch (Exception e) {
            return null;
        }

        String raw;
        try {
            raw = foundryAIService.callOpenAI(system, user);
        } catch (Exception e) {
            logger.warn("Report AI call failed: {}", e.getMessage());
            return null;
        }

        try {
            String json = extractJsonObject(raw);
            JsonNode n = objectMapper.readTree(json);
            if (!n.isObject()) return null;
            if (!"guidance, not diagnosis".equalsIgnoreCase(n.path("disclaimer").asText(""))) {
                ((com.fasterxml.jackson.databind.node.ObjectNode) n).put("disclaimer", "guidance, not diagnosis");
            }
            return objectMapper.writeValueAsString(n);
        } catch (Exception e) {
            logger.warn("Failed to parse report JSON: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Generate per-answer explanations once (store them; do not call repeatedly on results page loads).
     * Returns a map questionId -> meaning. Null on failure.
     */
    public Map<String, String> generatePerAnswerMeanings(
        List<Map<String, Object>> answersForAi
    ) {
        if (!isConfigured()) return null;

        String system = """
            You are generating short, concrete explanations for each answer in a RIASEC career guidance test.

            GOAL:
            - For each answer, explain what the selection suggests (in plain language) and how it relates to the domain(s).
            - Keep it specific to the prompt and the chosen option.

            GUARDRAILS:
            - Do NOT infer sensitive traits (gender, caste, religion, diagnosis, mental health labels).
            - Avoid stereotypes.
            - Do NOT claim certainty; use "may", "suggests", "often".
            - If scored=false, clearly say it is supporting context and does not change scores.
            - Return JSON only. No markdown, no extra text.

            Output schema:
            {
              "meanings": [
                { "question_id": "string", "meaning": "1-2 sentences (max 240 chars)" }
              ]
            }
            """;

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("answers", answersForAi);

        String user;
        try {
            user = objectMapper.writeValueAsString(input);
        } catch (Exception e) {
            return null;
        }

        String raw;
        try {
            raw = foundryAIService.callOpenAI(system, user);
        } catch (Exception e) {
            logger.warn("Per-answer meanings AI call failed: {}", e.getMessage());
            return null;
        }

        try {
            String json = extractJsonObject(raw);
            JsonNode root = objectMapper.readTree(json);
            JsonNode arr = root.path("meanings");
            if (!arr.isArray()) return null;

            Map<String, String> out = new LinkedHashMap<>();
            for (JsonNode n : arr) {
                String qid = n.path("question_id").asText(null);
                String meaning = n.path("meaning").asText(null);
                if (qid == null || qid.isBlank() || meaning == null || meaning.isBlank()) continue;
                if (meaning.length() > 400) {
                    meaning = meaning.substring(0, 400);
                }
                out.put(qid, meaning);
            }
            return out.isEmpty() ? null : out;
        } catch (Exception e) {
            logger.warn("Failed to parse per-answer meanings JSON: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Generate a short narrative per RIASEC domain once (store it; do not call repeatedly on results page loads).
     * Returns a map with keys R,I,A,S,E,C -> narrative text. Null on failure.
     */
    public Map<String, String> generatePerDomainNarratives(
        Map<String, Object> domainScores0To100,
        List<String> topDomains,
        String overallConfidence,
        Map<String, List<Map<String, Object>>> domainEvidence
    ) {
        if (!isConfigured()) return null;

        String system = """
            You are generating short, concrete per-domain narratives for a student's RIASEC results.

            INPUTS:
            - domain_scores_0_100: numeric scores per domain
            - top_domains: list of the student's top domains
            - overall_confidence: HIGH|MEDIUM|LOW
            - domain_evidence: per-domain snippets (prompt + selected_label/value) from the student's answers

            GOAL:
            - For each domain (R,I,A,S,E,C), write 2–4 sentences that explain what the score suggests in practical terms.
            - Reference the evidence snippets when helpful (without quoting too much).
            - Avoid vague phrasing like "strong positive signal"—make it meaningful and specific.
            - Use supportive, non-deterministic language ("may", "often", "suggests").

            GUARDRAILS:
            - Do NOT infer sensitive traits (gender, caste, religion, diagnosis, mental health labels).
            - Avoid stereotypes.
            - Do NOT mention any real person's psychometric result.
            - Return JSON only. No markdown, no extra text.

            Output schema:
            {
              "domains": {
                "R": "string",
                "I": "string",
                "A": "string",
                "S": "string",
                "E": "string",
                "C": "string"
              },
              "overall_summary": "string (3-5 sentences, practical next steps)"
            }
            """;

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("domain_scores_0_100", domainScores0To100);
        input.put("top_domains", topDomains);
        input.put("overall_confidence", overallConfidence);
        input.put("domain_evidence", domainEvidence);

        String user;
        try {
            user = objectMapper.writeValueAsString(input);
        } catch (Exception e) {
            return null;
        }

        String raw;
        try {
            raw = foundryAIService.callOpenAI(system, user);
        } catch (Exception e) {
            logger.warn("Per-domain narratives AI call failed: {}", e.getMessage());
            return null;
        }

        try {
            String json = extractJsonObject(raw);
            JsonNode root = objectMapper.readTree(json);
            JsonNode domains = root.path("domains");
            if (!domains.isObject()) return null;

            Map<String, String> out = new LinkedHashMap<>();
            for (String k : List.of("R", "I", "A", "S", "E", "C")) {
                String v = domains.path(k).asText(null);
                if (v != null && !v.isBlank()) out.put(k, v);
            }
            String overall = root.path("overall_summary").asText(null);
            if (overall != null && !overall.isBlank()) {
                out.put("overall_summary", overall);
            }
            return out.isEmpty() ? null : out;
        } catch (Exception e) {
            logger.warn("Failed to parse per-domain narratives JSON: {}", e.getMessage());
            return null;
        }
    }

    private AdaptivePick parseAdaptivePick(String raw) {
        try {
            String json = extractJsonObject(raw);
            JsonNode n = objectMapper.readTree(json);
            String id = n.path("chosen_question_id").asText(null);
            String rationale = n.path("rationale").asText("");
            if (id == null || id.isBlank()) return null;
            return new AdaptivePick(id, rationale);
        } catch (Exception e) {
            logger.warn("Failed to parse adaptive pick JSON: {}", e.getMessage());
            return null;
        }
    }

    private String extractJsonObject(String raw) {
        if (raw == null) throw new IllegalArgumentException("raw is null");
        String t = raw.trim();
        if (t.startsWith("```")) {
            int firstNewline = t.indexOf('\n');
            if (firstNewline > 0) t = t.substring(firstNewline + 1).trim();
            if (t.endsWith("```")) t = t.substring(0, t.length() - 3).trim();
        }
        int start = t.indexOf('{');
        int end = t.lastIndexOf('}');
        if (start >= 0 && end > start) return t.substring(start, end + 1);
        return t;
    }
}

