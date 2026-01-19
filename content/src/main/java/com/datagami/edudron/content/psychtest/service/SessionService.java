package com.datagami.edudron.content.psychtest.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.common.UlidGenerator;
import com.datagami.edudron.content.psychtest.domain.PsychTestAnswer;
import com.datagami.edudron.content.psychtest.domain.PsychTestQuestion;
import com.datagami.edudron.content.psychtest.domain.PsychTestResult;
import com.datagami.edudron.content.psychtest.domain.PsychTestSession;
import com.datagami.edudron.content.psychtest.ai.PsychTestAiService;
import com.datagami.edudron.content.psychtest.repo.PsychTestAnswerRepository;
import com.datagami.edudron.content.psychtest.repo.PsychTestQuestionAskedRepository;
import com.datagami.edudron.content.psychtest.repo.PsychTestOptionRepository;
import com.datagami.edudron.content.psychtest.repo.PsychTestQuestionRepository;
import com.datagami.edudron.content.psychtest.repo.PsychTestResultRepository;
import com.datagami.edudron.content.psychtest.repo.PsychTestSessionRepository;
import com.datagami.edudron.content.psychtest.service.MappingService.MappingOutput;
import com.datagami.edudron.content.psychtest.service.RecommendationService.RecommendedCourse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SessionService {
    private final PsychTestSessionRepository sessionRepository;
    private final PsychTestQuestionRepository questionRepository;
    private final PsychTestOptionRepository optionRepository;
    private final PsychTestAnswerRepository answerRepository;
    private final PsychTestResultRepository resultRepository;
    private final PsychTestQuestionAskedRepository askedRepository;
    private final ScoringService scoringService;
    private final AdaptiveQuestionSelector questionSelector;
    private final MappingService mappingService;
    private final RecommendationService recommendationService;
    private final ReportService reportService;
    private final PsychTestAiService psychTestAiService;
    private final ObjectMapper objectMapper;
    private final ResultExplanationService resultExplanationService;

    public SessionService(
        PsychTestSessionRepository sessionRepository,
        PsychTestQuestionRepository questionRepository,
        PsychTestOptionRepository optionRepository,
        PsychTestAnswerRepository answerRepository,
        PsychTestResultRepository resultRepository,
        PsychTestQuestionAskedRepository askedRepository,
        ScoringService scoringService,
        AdaptiveQuestionSelector questionSelector,
        MappingService mappingService,
        RecommendationService recommendationService,
        ReportService reportService,
        PsychTestAiService psychTestAiService,
        ObjectMapper objectMapper,
        ResultExplanationService resultExplanationService
    ) {
        this.sessionRepository = sessionRepository;
        this.questionRepository = questionRepository;
        this.optionRepository = optionRepository;
        this.answerRepository = answerRepository;
        this.resultRepository = resultRepository;
        this.askedRepository = askedRepository;
        this.scoringService = scoringService;
        this.questionSelector = questionSelector;
        this.mappingService = mappingService;
        this.recommendationService = recommendationService;
        this.reportService = reportService;
        this.psychTestAiService = psychTestAiService;
        this.objectMapper = objectMapper;
        this.resultExplanationService = resultExplanationService;
    }

    public PsychTestSession startOrResume(String userId, Integer grade, String locale, Integer maxQuestions) {
        UUID clientId = requireClientId();

        return sessionRepository.findFirstByClientIdAndUserIdAndStatusOrderByCreatedAtDesc(
            clientId, userId, PsychTestSession.Status.IN_PROGRESS
        ).orElseGet(() -> {
            PsychTestSession s = new PsychTestSession();
            s.setId(UlidGenerator.nextUlid());
            s.setClientId(clientId);
            s.setUserId(userId);
            s.setStatus(PsychTestSession.Status.IN_PROGRESS);
            s.setTestVersion(PsychTestVersions.TEST_VERSION);
            s.setBankVersion(PsychTestVersions.BANK_VERSION);
            s.setScoringVersion(PsychTestVersions.SCORING_VERSION);
            s.setPromptVersion(PsychTestVersions.PROMPT_VERSION);
            s.setGrade(grade);
            s.setLocale(locale != null && !locale.isBlank() ? locale : "en");
            s.setMaxQuestions(maxQuestions != null && maxQuestions > 0 ? maxQuestions : 30);

            ObjectNode meta = objectMapper.createObjectNode();
            meta.set("topDomainsHistory", objectMapper.createArrayNode());
            s.setMetadataJson(meta);

            return sessionRepository.save(s);
        });
    }

    public PsychTestSession getSession(String sessionId, String userId) {
        PsychTestSession s = requireSession(sessionId);
        if (!s.getUserId().equals(userId)) {
            throw new IllegalStateException("Forbidden");
        }
        return s;
    }

    public record NextQuestion(
        String sessionId,
        String questionId,
        String type,
        String prompt,
        List<Option> options,
        int currentQuestionNumber,
        int totalQuestions,
        boolean canStopEarly,
        String personalizationSource,
        String askedId
    ) {}

    public record Option(
        String id,
        String label
    ) {}

    public NextQuestion getNextQuestion(String sessionId, String userId, String userName) {
        PsychTestSession s = getSession(sessionId, userId);
        if (s.getStatus() != PsychTestSession.Status.IN_PROGRESS) {
            throw new IllegalStateException("Session is not in progress");
        }

        List<PsychTestAnswer> answers = answerRepository.findBySessionIdOrdered(sessionId);
        List<PsychTestQuestion> active = questionRepository.findActiveByBankVersion(s.getBankVersion());
        ScoringService.ScoringSnapshot snapshot = scoringService.computeSnapshot(s, answers);

        int questionNumber = answers.size() + 1;
        Optional<com.datagami.edudron.content.psychtest.domain.PsychTestQuestionAsked> previouslyAsked = askedRepository
            .findBySessionIdAndClientIdAndQuestionNumber(sessionId, s.getClientId(), questionNumber);

        // If we already asked this question number before (refresh/double click/retry), serve exactly what was stored.
        if (previouslyAsked.isPresent()) {
            com.datagami.edudron.content.psychtest.domain.PsychTestQuestionAsked a = previouslyAsked.get();
            PsychTestQuestion askedQuestion = a.getQuestion();
            List<Option> options = parseRenderedOptions(a.getRenderedOptionsJson());
            return new NextQuestion(
                s.getId(),
                askedQuestion != null ? askedQuestion.getId() : null,
                askedQuestion != null && askedQuestion.getType() != null ? askedQuestion.getType().name() : null,
                a.getRenderedPrompt(),
                options,
                questionNumber,
                s.getMaxQuestions() != null ? s.getMaxQuestions() : 30,
                false,
                a.getPersonalizationSource(),
                a.getId()
            );
        }

        AdaptiveQuestionSelector.Selection sel = questionSelector.selectNextQuestion(s, answers, active, snapshot, psychTestAiService.isConfigured());
        if (sel.question() == null) {
            return new NextQuestion(
                s.getId(),
                null,
                null,
                null,
                List.of(),
                answers.size(),
                s.getMaxQuestions() != null ? s.getMaxQuestions() : 30,
                true,
                "RAW",
                null
            );
        }

        PsychTestQuestion chosen = sel.question();

        // Optional AI-assisted selection (guardrailed): AI may only choose from eligible IDs.
        List<String> eligibleIds = questionSelector.eligibleQuestionIds(s, answers, active);
        if (psychTestAiService.isConfigured() && eligibleIds != null && !eligibleIds.isEmpty()) {
            Set<String> answeredIds = answers.stream().map(a -> a.getQuestion().getId()).collect(Collectors.toSet());
            PsychTestAiService.AdaptivePick pick = psychTestAiService.chooseNextQuestionId(eligibleIds, answeredIds, snapshot);
            if (pick != null && pick.chosenQuestionId() != null) {
                PsychTestQuestion aiChosen = active.stream()
                    .filter(q -> q.getId().equals(pick.chosenQuestionId()))
                    .findFirst()
                    .orElse(null);
                if (aiChosen != null) {
                    chosen = aiChosen;
                }
            }
        }

        List<com.datagami.edudron.content.psychtest.domain.PsychTestOption> rawOptions = optionRepository.findByQuestionId(chosen.getId());
        RenderedQuestion rendered = renderQuestion(questionNumber, s, chosen, rawOptions, answers, snapshot, userName);

        // persist what we actually served (idempotent under retries)
        String askedId = UlidGenerator.nextUlid();
        String renderedOptionsJsonStr;
        String personalizationJsonStr;
        try {
            renderedOptionsJsonStr = objectMapper.writeValueAsString(rendered.options());
            personalizationJsonStr = rendered.meta() != null ? objectMapper.writeValueAsString(rendered.meta()) : "{}";
        } catch (Exception e) {
            renderedOptionsJsonStr = "[]";
            personalizationJsonStr = "{}";
        }

        int inserted = askedRepository.insertIfAbsent(
            askedId,
            sessionId,
            chosen.getId(),
            questionNumber,
            rendered.prompt(),
            renderedOptionsJsonStr,
            rendered.source(),
            personalizationJsonStr
        );
        if (inserted == 0) {
            askedId = askedRepository.findBySessionIdAndClientIdAndQuestionNumber(sessionId, s.getClientId(), questionNumber)
                .map(com.datagami.edudron.content.psychtest.domain.PsychTestQuestionAsked::getId)
                .orElse(askedId);
        }

        List<Option> options = rendered.options().stream().map(o -> new Option((String) o.get("id"), (String) o.get("label"))).toList();

        return new NextQuestion(
            s.getId(),
            chosen.getId(),
            chosen.getType().name(),
            rendered.prompt(),
            options,
            questionNumber,
            s.getMaxQuestions() != null ? s.getMaxQuestions() : 30,
            sel.earlyStopRecommended(),
            rendered.source(),
            askedId
        );
    }

    public void submitAnswer(String sessionId, String userId, String questionId, String selectedOptionId, String text, Integer timeSpentMs) {
        PsychTestSession s = getSession(sessionId, userId);
        if (s.getStatus() != PsychTestSession.Status.IN_PROGRESS) {
            throw new IllegalStateException("Session is not in progress");
        }

        PsychTestQuestion q = questionRepository.findById(questionId)
            .orElseThrow(() -> new IllegalArgumentException("Question not found"));

        // Enforce that question belongs to active bank version
        if (!s.getBankVersion().equals(q.getBankVersion())) {
            throw new IllegalArgumentException("Question bank version mismatch");
        }

        // Build answer_json
        ObjectNode answerJson = objectMapper.createObjectNode();
        if (selectedOptionId != null && !selectedOptionId.isBlank()) {
            answerJson.put("selectedOptionId", selectedOptionId);
        }
        if (text != null && !text.isBlank()) {
            answerJson.put("text", text);
        }
        answerJson.put("questionType", q.getType().name());

        PsychTestAnswer a = new PsychTestAnswer();
        a.setId(UlidGenerator.nextUlid());
        a.setSession(s);
        a.setQuestion(q);
        a.setAnswerJson(answerJson);
        a.setTimeSpentMs(timeSpentMs);
        answerRepository.save(a);

        // Update session progress + topDomains history for stability checks
        List<PsychTestAnswer> answers = answerRepository.findBySessionIdOrdered(sessionId);
        ScoringService.ScoringSnapshot snapshot = scoringService.computeSnapshot(s, answers);
        ObjectNode meta = (s.getMetadataJson() != null && s.getMetadataJson().isObject())
            ? (ObjectNode) s.getMetadataJson()
            : objectMapper.createObjectNode();
        ArrayNode history = meta.has("topDomainsHistory") && meta.get("topDomainsHistory").isArray()
            ? (ArrayNode) meta.get("topDomainsHistory")
            : objectMapper.createArrayNode();
        history.add(String.join("", snapshot.topDomains()));
        // keep last 10
        while (history.size() > 10) {
            history.remove(0);
        }
        meta.set("topDomainsHistory", history);
        meta.put("lastOverallConfidence", snapshot.overallConfidenceLevel());
        meta.put("lastOverallConfidenceScore", snapshot.overallConfidenceScore());
        s.setMetadataJson(meta);
        s.setCurrentQuestionIndex(answers.size());
        sessionRepository.save(s);
    }

    private record RenderedQuestion(String prompt, List<Map<String, Object>> options, String source, com.fasterxml.jackson.databind.JsonNode meta) {}

    private RenderedQuestion renderQuestion(
        int questionNumber,
        PsychTestSession session,
        PsychTestQuestion q,
        List<com.datagami.edudron.content.psychtest.domain.PsychTestOption> options,
        List<PsychTestAnswer> priorAnswers,
        ScoringService.ScoringSnapshot snapshot,
        String userName
    ) {
        String firstName = extractFirstName(userName);

        // build previous-answer context (best-effort)
        String prevSummary = null;
        if (priorAnswers != null && !priorAnswers.isEmpty()) {
            PsychTestAnswer last = priorAnswers.get(priorAnswers.size() - 1);
            String prevPrompt = last.getQuestion() != null ? last.getQuestion().getPrompt() : null;
            JsonNode aj = last.getAnswerJson();
            String selectedOptionId = (aj != null && aj.hasNonNull("selectedOptionId")) ? aj.get("selectedOptionId").asText() : null;
            String openText = (aj != null && aj.hasNonNull("text")) ? aj.get("text").asText() : null;
            String selLabel = null;
            if (selectedOptionId != null) {
                try {
                    selLabel = optionRepository.findById(selectedOptionId).map(com.datagami.edudron.content.psychtest.domain.PsychTestOption::getLabel).orElse(null);
                } catch (Exception ignored) {}
            }
            String snippet = openText != null && !openText.isBlank()
                ? truncate(openText, 70)
                : (selLabel != null ? selLabel : null);
            if (prevPrompt != null && snippet != null) {
                prevSummary = "Earlier you shared: \"" + truncate(prevPrompt, 60) + "\" → " + "\"" + snippet + "\".";
            }
        }

        String basePrompt = q.getPrompt();
        String prompt = basePrompt;

        List<Map<String, Object>> renderedOptions = new java.util.ArrayList<>();
        for (com.datagami.edudron.content.psychtest.domain.PsychTestOption o : options) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", o.getId());
            m.put("label", o.getLabel());
            m.put("value", o.getValue());
            renderedOptions.add(m);
        }

        ObjectNode meta = objectMapper.createObjectNode();
        meta.put("basePrompt", basePrompt);
        if (firstName != null) meta.put("firstName", firstName);
        if (prevSummary != null) meta.put("previousAnswerSummary", prevSummary);
        meta.put("questionType", q.getType().name());

        String source = "RAW";

        // Deterministic personalization baseline (no AI)
        if (firstName != null && !firstName.isBlank()) {
            prompt = firstName + ", " + prompt;
            source = "TEMPLATE";
        }
        boolean usePrev = shouldUsePreviousAnswerReference(session, questionNumber) && prevSummary != null;
        if (usePrev) {
            prompt = (firstName != null ? firstName + ", " : "")
                + "thinking back to what you shared earlier, " + basePrompt;
            meta.put("usedPreviousAnswer", true);
            meta.put("previousAnswerSummaryUsed", prevSummary);
            source = "TEMPLATE";

            // Persist the throttle state on session so it stays balanced.
            ObjectNode sessionMeta = (session.getMetadataJson() != null && session.getMetadataJson().isObject())
                ? (ObjectNode) session.getMetadataJson()
                : objectMapper.createObjectNode();
            sessionMeta.put("lastPrevRefQuestionNumber", questionNumber);
            session.setMetadataJson(sessionMeta);
            sessionRepository.save(session);
        }

        // Deterministic option labels baseline (LIKERT): ensures stable scale even if AI fails.
        if (q.getType() == PsychTestQuestion.Type.LIKERT) {
            applyLikertScaleLabels(renderedOptions);
        }

        // AI enhancement (rewrite prompt + option labels) if configured.
        if (psychTestAiService.isConfigured()) {
            List<Map<String, Object>> optsForAi = renderedOptions.stream().map(o -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("option_id", o.get("id"));
                m.put("base_label", o.get("label"));
                m.put("value", o.get("value"));
                return m;
            }).toList();

            Map<String, Object> ctx = new HashMap<>();
            if (firstName != null) ctx.put("first_name", firstName);
            if (usePrev && prevSummary != null) ctx.put("previous_answer_summary", prevSummary);
            ctx.put("top_domains", snapshot != null ? snapshot.topDomains() : List.of());

            PsychTestAiService.PersonalizedQuestion pq = psychTestAiService.personalizeQuestion(
                q.getId(),
                q.getType().name(),
                basePrompt,
                optsForAi,
                ctx
            );

            if (pq != null && pq.prompt() != null && !pq.prompt().isBlank()) {
                prompt = pq.prompt();
                source = "AI";

                // Allow AI to rephrase option labels for all types (including LIKERT),
                // but only if it returns valid labels for existing option ids.
                if (pq.optionLabelsById() != null && !pq.optionLabelsById().isEmpty()) {
                    Set<String> allowedOptionIds = renderedOptions.stream()
                        .map(o -> (String) o.get("id"))
                        .filter(v -> v != null && !v.isBlank())
                        .collect(Collectors.toSet());

                    int applied = 0;
                    for (Map<String, Object> o : renderedOptions) {
                        String id = (String) o.get("id");
                        String lbl = pq.optionLabelsById().get(id);
                        if (!allowedOptionIds.contains(id)) continue;
                        if (lbl == null) continue;
                        String cleaned = lbl.trim().replaceAll("\\s+", " ");
                        if (cleaned.isBlank()) continue;
                        if (cleaned.length() > 140) cleaned = cleaned.substring(0, 140);
                        o.put("label", cleaned);
                        applied++;
                    }

                    // If AI returned too few labels, fall back to deterministic baseline for LIKERT.
                    if (q.getType() == PsychTestQuestion.Type.LIKERT && applied < Math.max(2, allowedOptionIds.size() / 2)) {
                        applyLikertScaleLabels(renderedOptions);
                    } else if (applied > 0) {
                        meta.put("aiOptionsRewritten", true);
                    }
                }
                meta.put("aiPersonalized", true);
            }
        }

        // Final safety: ensure prompt not null/blank
        if (prompt == null || prompt.isBlank()) prompt = basePrompt;

        // Only return id+label to client; value is for AI context only.
        List<Map<String, Object>> clientOptions = renderedOptions.stream().map(o -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", o.get("id"));
            m.put("label", o.get("label"));
            return m;
        }).toList();

        return new RenderedQuestion(prompt, clientOptions, source, meta);
    }

    private static String extractFirstName(String userName) {
        if (userName == null) return null;
        String t = userName.trim();
        if (t.isBlank()) return null;
        int sp = t.indexOf(' ');
        return (sp > 0) ? t.substring(0, sp).trim() : t;
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        String t = s.trim().replaceAll("\\s+", " ");
        if (t.length() <= max) return t;
        return t.substring(0, Math.max(0, max - 1)) + "…";
    }

    private static void applyLikertScaleLabels(List<Map<String, Object>> renderedOptions) {
        if (renderedOptions == null || renderedOptions.isEmpty()) return;

        // Sort by numeric value descending so the highest value maps to strongest agreement.
        List<Map<String, Object>> sorted = renderedOptions.stream()
            .sorted((a, b) -> {
                Integer av = (a.get("value") instanceof Number n) ? n.intValue() : 0;
                Integer bv = (b.get("value") instanceof Number n) ? n.intValue() : 0;
                return bv.compareTo(av);
            })
            .toList();

        int n = sorted.size();
        List<String> scale = (n == 7)
            ? List.of("Strongly agree", "Agree", "Slightly agree", "Neutral", "Slightly disagree", "Disagree", "Strongly disagree")
            : (n == 5)
                ? List.of("Definitely", "Mostly", "Not sure", "Not really", "Not at all")
                : null;

        if (scale == null) {
            // Fallback: keep existing labels.
            return;
        }

        for (int i = 0; i < sorted.size(); i++) {
            Map<String, Object> o = sorted.get(i);
            String lbl = (i < scale.size()) ? scale.get(i) : (String) o.get("label");
            if (lbl != null && !lbl.isBlank()) {
                o.put("label", lbl);
            }
        }
    }

    private boolean shouldUsePreviousAnswerReference(PsychTestSession session, int questionNumber) {
        // Balanced rule: at most once every 3 questions and never on the first 2 questions.
        if (questionNumber < 3) return false;

        JsonNode m = session != null ? session.getMetadataJson() : null;
        int last = 0;
        if (m != null && m.hasNonNull("lastPrevRefQuestionNumber")) {
            last = m.get("lastPrevRefQuestionNumber").asInt(0);
        }
        if (last > 0 && (questionNumber - last) < 3) return false;

        // Also only do it every 3rd question to avoid overuse.
        return (questionNumber % 3) == 0;
    }

    private List<Option> parseRenderedOptions(JsonNode renderedOptionsJson) {
        if (renderedOptionsJson == null || !renderedOptionsJson.isArray()) return List.of();
        List<Option> out = new java.util.ArrayList<>();
        for (JsonNode n : renderedOptionsJson) {
            String id = n.path("id").asText(null);
            String label = n.path("label").asText(null);
            if (id != null && label != null) out.add(new Option(id, label));
        }
        return out;
    }

    public PsychTestResult complete(String sessionId, String userId) {
        PsychTestSession s = getSession(sessionId, userId);
        if (s.getStatus() != PsychTestSession.Status.IN_PROGRESS) {
            throw new IllegalStateException("Session is not in progress");
        }

        List<PsychTestAnswer> answers = answerRepository.findBySessionIdOrdered(sessionId);
        ScoringService.ScoringSnapshot snapshot = scoringService.computeSnapshot(s, answers);

        String top1 = snapshot.topDomains().size() > 0 ? snapshot.topDomains().get(0) : null;
        String top2 = snapshot.topDomains().size() > 1 ? snapshot.topDomains().get(1) : null;

        MappingOutput mapping = mappingService.map(top1, top2, snapshot.overallConfidenceLevel(), s.getGrade());
        String weakestIndicatorTag = inferWeakestIndicatorTagFromQuestions(answers);

        List<RecommendedCourse> recs = recommendationService.recommend(
            s.getClientId(),
            mapping.streamSuggestion(),
            snapshot.topDomains(),
            weakestIndicatorTag,
            mapping.careerFields()
        );

        ObjectNode domainScoresJson = objectMapper.createObjectNode();
        for (Map.Entry<String, ScoringService.DomainStats> e : snapshot.domains().entrySet()) {
            ObjectNode d = objectMapper.createObjectNode();
            d.put("score", e.getValue().score0To100());
            d.put("confidence", e.getValue().confidence0To1());
            d.put("answered_primary", e.getValue().primaryAnsweredCount());
            domainScoresJson.set(e.getKey(), d);
        }

        ArrayNode topDomainsJson = objectMapper.createArrayNode();
        snapshot.topDomains().forEach(topDomainsJson::add);

        ArrayNode careerFieldsJson = objectMapper.createArrayNode();
        mapping.careerFields().forEach(careerFieldsJson::add);

        ArrayNode recommendedCoursesJson = objectMapper.createArrayNode();
        for (RecommendedCourse rc : recs) {
            ObjectNode n = objectMapper.createObjectNode();
            n.put("courseId", rc.courseId());
            n.put("title", rc.title());
            n.put("description", rc.description());
            n.put("reason", rc.reason());
            recommendedCoursesJson.add(n);
        }

        String reportJson = null;
        if (psychTestAiService.isConfigured()) {
            reportJson = psychTestAiService.generateNarrativeReportJson(
                snapshot.topDomains(),
                snapshot.overallConfidenceLevel(),
                mapping.streamSuggestion(),
                mapping.careerFields(),
                recs
            );
        }
        if (reportJson == null) {
            reportJson = reportService.buildFallbackReportJson(
                snapshot.topDomains(),
                snapshot.overallConfidenceLevel(),
                mapping.streamSuggestion(),
                mapping.careerFields(),
                recs
            );
        }

        // Build & store explanations once (so /result does not re-call AI).
        ResultExplanationService.ResultExplanation baseExplanation = resultExplanationService.explain(
            s,
            snapshot.overallConfidenceLevel(),
            snapshot.topDomains()
        );
        List<ResultExplanationService.AnswerBreakdownItem> enrichedAnswers = baseExplanation.answers();

        if (psychTestAiService.isConfigured()) {
            // Prepare compact per-answer payload.
            List<Map<String, Object>> answersForAi = enrichedAnswers.stream().map(a -> {
                Map<String, Object> m = new java.util.LinkedHashMap<>();
                m.put("question_id", a.questionId());
                m.put("question_type", a.questionType());
                m.put("prompt", a.prompt());
                m.put("selected_label", a.selectedLabel());
                m.put("selected_value", a.optionValue());
                m.put("domains", a.impactedDomains());
                m.put("scored", a.affectsRiasecScores());
                m.put("open_text", a.text());
                return m;
            }).toList();

            Map<String, String> aiMeanings = psychTestAiService.generatePerAnswerMeanings(answersForAi);
            if (aiMeanings != null && !aiMeanings.isEmpty()) {
                enrichedAnswers = enrichedAnswers.stream().map(a -> {
                    String ai = aiMeanings.get(a.questionId());
                    if (ai == null || ai.isBlank()) return a;
                    return new ResultExplanationService.AnswerBreakdownItem(
                        a.index(),
                        a.questionId(),
                        a.questionType(),
                        a.prompt(),
                        a.selectedOptionId(),
                        a.selectedLabel(),
                        a.optionValue(),
                        a.reverseScored(),
                        a.weight(),
                        a.impactedDomains(),
                        a.scoreDelta0To100(),
                        a.scoreAfter0To100(),
                        a.text(),
                        a.affectsRiasecScores(),
                        ai
                    );
                }).toList();
            }
        }

        Map<String, Object> domainScores0To100 = new LinkedHashMap<>();
        for (Map.Entry<String, ScoringService.DomainStats> e : snapshot.domains().entrySet()) {
            domainScores0To100.put(e.getKey(), e.getValue().score0To100());
        }

        // Evidence for per-domain narrative (keep short: up to 4 scored items per domain).
        Map<String, List<Map<String, Object>>> domainEvidence = new LinkedHashMap<>();
        for (String d : ScoringService.RIASEC) {
            domainEvidence.put(d, new java.util.ArrayList<>());
        }
        for (ResultExplanationService.AnswerBreakdownItem a : enrichedAnswers) {
            if (!a.affectsRiasecScores()) continue;
            List<String> domains = a.impactedDomains() != null ? a.impactedDomains() : List.of();
            for (String d : domains) {
                List<Map<String, Object>> list = domainEvidence.get(d);
                if (list == null) continue;
                if (list.size() >= 4) continue;
                Map<String, Object> ev = new LinkedHashMap<>();
                ev.put("prompt", a.prompt());
                ev.put("selected_label", a.selectedLabel());
                ev.put("selected_value", a.optionValue());
                list.add(ev);
            }
        }

        Map<String, String> domainNarratives = null;
        if (psychTestAiService.isConfigured()) {
            domainNarratives = psychTestAiService.generatePerDomainNarratives(
                domainScores0To100,
                snapshot.topDomains(),
                snapshot.overallConfidenceLevel(),
                domainEvidence
            );
        }
        if (domainNarratives == null || domainNarratives.isEmpty()) {
            domainNarratives = buildFallbackDomainNarratives(domainScores0To100, snapshot.topDomains(), snapshot.overallConfidenceLevel());
        }

        JsonNode explanationsJson = objectMapper.valueToTree(Map.of(
            "generatedAt", OffsetDateTime.now().toString(),
            "answerBreakdown", enrichedAnswers,
            "domainNarratives", domainNarratives,
            "suggestions", baseExplanation.suggestions()
        ));

        PsychTestResult r = new PsychTestResult();
        r.setId(UlidGenerator.nextUlid());
        r.setSession(s);
        r.setOverallConfidence(snapshot.overallConfidenceLevel());
        r.setDomainScoresJson(domainScoresJson);
        r.setTopDomainsJson(topDomainsJson);
        r.setStreamSuggestion(mapping.streamSuggestion());
        r.setCareerFieldsJson(careerFieldsJson);
        r.setRecommendedCoursesJson(recommendedCoursesJson);
        r.setReportText(reportJson);
        r.setExplanationsJson(explanationsJson);
        r.setTestVersion(s.getTestVersion());
        r.setBankVersion(s.getBankVersion());
        r.setScoringVersion(s.getScoringVersion());
        r.setPromptVersion(s.getPromptVersion());

        PsychTestResult saved = resultRepository.save(r);

        s.setStatus(PsychTestSession.Status.COMPLETED);
        s.setCompletedAt(OffsetDateTime.now());
        sessionRepository.save(s);

        return saved;
    }

    public PsychTestResult getResult(String sessionId, String userId) {
        UUID clientId = requireClientId();
        PsychTestSession s = requireSession(sessionId);
        if (!s.getUserId().equals(userId)) {
            throw new IllegalStateException("Forbidden");
        }
        return resultRepository.findBySessionIdAndClientId(sessionId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Result not found"));
    }

    public List<PsychTestResult> listRecentResults(String userId, int limit) {
        UUID clientId = requireClientId();
        int safeLimit = Math.max(1, Math.min(100, limit));
        return resultRepository.findRecentResultsForUser(clientId, userId, PageRequest.of(0, safeLimit));
    }

    private String inferWeakestIndicatorTagFromQuestions(List<PsychTestAnswer> answers) {
        // Best-effort: look at question metadata indicator and pick the least-mentioned one.
        Map<String, Long> counts = answers.stream()
            .map(a -> {
                JsonNode m = a.getQuestion().getMetadataJson();
                return (m != null && m.hasNonNull("indicator")) ? m.get("indicator").asText() : null;
            })
            .filter(v -> v != null && !v.isBlank())
            .collect(Collectors.groupingBy(v -> v, Collectors.counting()));

        return counts.entrySet().stream()
            .sorted(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .findFirst()
            .orElse(null);
    }

    private PsychTestSession requireSession(String sessionId) {
        UUID clientId = requireClientId();
        return sessionRepository.findByIdAndClientId(sessionId, clientId)
            .orElseThrow(() -> new IllegalArgumentException("Session not found"));
    }

    private UUID requireClientId() {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) throw new IllegalStateException("Tenant context is not set");
        return UUID.fromString(clientIdStr);
    }

    /**
     * Regenerates only AI artifacts (report + explanations) for an already completed result.
     * Deterministic scoring/mapping/recommendations stored on the result remain unchanged.
     */
    public PsychTestResult regenerateResultArtifacts(String sessionId, String userId) {
        PsychTestSession s = getSession(sessionId, userId);
        if (s.getStatus() != PsychTestSession.Status.COMPLETED) {
            throw new IllegalStateException("Session is not completed");
        }

        PsychTestResult r = getResult(sessionId, userId);
        List<PsychTestAnswer> answers = answerRepository.findBySessionIdOrdered(sessionId);
        ScoringService.ScoringSnapshot snapshot = scoringService.computeSnapshot(s, answers);

        // Rebuild explanations using current logic, then overwrite meaning texts with AI (once).
        ResultExplanationService.ResultExplanation baseExplanation = resultExplanationService.explain(
            s,
            r.getOverallConfidence(),
            snapshot.topDomains()
        );
        List<ResultExplanationService.AnswerBreakdownItem> enrichedAnswers = baseExplanation.answers();

        if (psychTestAiService.isConfigured()) {
            List<Map<String, Object>> answersForAi = enrichedAnswers.stream().map(a -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("question_id", a.questionId());
                m.put("question_type", a.questionType());
                m.put("prompt", a.prompt());
                m.put("selected_label", a.selectedLabel());
                m.put("selected_value", a.optionValue());
                m.put("domains", a.impactedDomains());
                m.put("scored", a.affectsRiasecScores());
                m.put("open_text", a.text());
                return m;
            }).toList();

            Map<String, String> aiMeanings = psychTestAiService.generatePerAnswerMeanings(answersForAi);
            if (aiMeanings != null && !aiMeanings.isEmpty()) {
                enrichedAnswers = enrichedAnswers.stream().map(a -> {
                    String ai = aiMeanings.get(a.questionId());
                    if (ai == null || ai.isBlank()) return a;
                    return new ResultExplanationService.AnswerBreakdownItem(
                        a.index(),
                        a.questionId(),
                        a.questionType(),
                        a.prompt(),
                        a.selectedOptionId(),
                        a.selectedLabel(),
                        a.optionValue(),
                        a.reverseScored(),
                        a.weight(),
                        a.impactedDomains(),
                        a.scoreDelta0To100(),
                        a.scoreAfter0To100(),
                        a.text(),
                        a.affectsRiasecScores(),
                        ai
                    );
                }).toList();
            }
        }

        Map<String, Object> domainScores0To100 = new LinkedHashMap<>();
        for (Map.Entry<String, ScoringService.DomainStats> e : snapshot.domains().entrySet()) {
            domainScores0To100.put(e.getKey(), e.getValue().score0To100());
        }

        Map<String, List<Map<String, Object>>> domainEvidence = new LinkedHashMap<>();
        for (String d : ScoringService.RIASEC) {
            domainEvidence.put(d, new java.util.ArrayList<>());
        }
        for (ResultExplanationService.AnswerBreakdownItem a : enrichedAnswers) {
            if (!a.affectsRiasecScores()) continue;
            List<String> domains = a.impactedDomains() != null ? a.impactedDomains() : List.of();
            for (String d : domains) {
                List<Map<String, Object>> list = domainEvidence.get(d);
                if (list == null) continue;
                if (list.size() >= 4) continue;
                Map<String, Object> ev = new LinkedHashMap<>();
                ev.put("prompt", a.prompt());
                ev.put("selected_label", a.selectedLabel());
                ev.put("selected_value", a.optionValue());
                list.add(ev);
            }
        }

        Map<String, String> domainNarratives = null;
        if (psychTestAiService.isConfigured()) {
            domainNarratives = psychTestAiService.generatePerDomainNarratives(
                domainScores0To100,
                snapshot.topDomains(),
                r.getOverallConfidence(),
                domainEvidence
            );
        }
        if (domainNarratives == null || domainNarratives.isEmpty()) {
            domainNarratives = buildFallbackDomainNarratives(domainScores0To100, snapshot.topDomains(), r.getOverallConfidence());
        }

        // Re-generate the narrative report too (optional; stored once).
        String reportJson = null;
        if (psychTestAiService.isConfigured()) {
            List<RecommendedCourse> recs = objectMapper.convertValue(
                r.getRecommendedCoursesJson(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, RecommendedCourse.class)
            );
            List<String> careerFields = objectMapper.convertValue(
                r.getCareerFieldsJson(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
            );
            reportJson = psychTestAiService.generateNarrativeReportJson(
                snapshot.topDomains(),
                r.getOverallConfidence(),
                r.getStreamSuggestion(),
                careerFields,
                recs
            );
        }
        if (reportJson == null) {
            reportJson = r.getReportText();
        }

        JsonNode explanationsJson = objectMapper.valueToTree(Map.of(
            "generatedAt", OffsetDateTime.now().toString(),
            "answerBreakdown", enrichedAnswers,
            "domainNarratives", domainNarratives,
            "suggestions", baseExplanation.suggestions()
        ));

        r.setExplanationsJson(explanationsJson);
        r.setReportText(reportJson);
        return resultRepository.save(r);
    }

    private Map<String, String> buildFallbackDomainNarratives(Map<String, Object> domainScores0To100, List<String> topDomains, String overallConfidence) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String d : ScoringService.RIASEC) {
            double s = 0.0;
            Object v = domainScores0To100 != null ? domainScores0To100.get(d) : null;
            if (v instanceof Number n) s = n.doubleValue();

            String level = s >= 70 ? "high" : s >= 45 ? "moderate" : "lower";
            out.put(d,
                "Your " + d + " score is " + level + ". This suggests you may enjoy activities and learning styles commonly linked to this domain. "
                    + "Try small, real-world tasks in this area to validate what feels energizing versus draining.");
        }
        out.put("overall_summary",
            "Your top domains were " + (topDomains != null ? String.join("", topDomains) : "") + ". "
                + "With overall confidence " + overallConfidence + ", use this as guidance: try 1–2 short projects, reflect on what you liked, and adjust. "
                + "Your long-term fit is best discovered by doing, not only by testing.");
        return out;
    }
}

