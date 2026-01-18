package com.datagami.edudron.content.psychtest.service;

import com.datagami.edudron.content.psychtest.domain.PsychTestAnswer;
import com.datagami.edudron.content.psychtest.domain.PsychTestOption;
import com.datagami.edudron.content.psychtest.domain.PsychTestQuestion;
import com.datagami.edudron.content.psychtest.domain.PsychTestSession;
import com.datagami.edudron.content.psychtest.repo.PsychTestAnswerRepository;
import com.datagami.edudron.content.psychtest.repo.PsychTestOptionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class ResultExplanationService {
    private final PsychTestAnswerRepository answerRepository;
    private final PsychTestOptionRepository optionRepository;
    private final ScoringService scoringService;
    private final MappingService mappingService;

    public ResultExplanationService(
        PsychTestAnswerRepository answerRepository,
        PsychTestOptionRepository optionRepository,
        ScoringService scoringService,
        MappingService mappingService
    ) {
        this.answerRepository = answerRepository;
        this.optionRepository = optionRepository;
        this.scoringService = scoringService;
        this.mappingService = mappingService;
    }

    public record AnswerBreakdownItem(
        int index,
        String questionId,
        String questionType,
        String prompt,
        String selectedOptionId,
        String selectedLabel,
        Integer optionValue,
        Boolean reverseScored,
        Double weight,
        List<String> impactedDomains,
        Map<String, Double> scoreDelta0To100,
        Map<String, Double> scoreAfter0To100,
        String text,
        boolean affectsRiasecScores,
        String meaning
    ) {}

    public record CareerSuggestion(String title, String reason) {}

    public record SuggestionsExplanation(
        String streamSuggestion,
        String streamReason,
        List<CareerSuggestion> primaryCareerPaths,
        List<CareerSuggestion> alternateCareerPaths,
        List<String> roleModelsInAlignedFields
    ) {}

    public record ResultExplanation(
        List<AnswerBreakdownItem> answers,
        SuggestionsExplanation suggestions
    ) {}

    public ResultExplanation explain(PsychTestSession session, String overallConfidence, List<String> topDomains) {
        List<PsychTestAnswer> answers = answerRepository.findBySessionIdOrdered(session.getId());

        // Batch load options used in answers so we can show the chosen label/value deterministically.
        Set<String> optionIds = new HashSet<>();
        for (PsychTestAnswer a : answers) {
            JsonNode aj = a.getAnswerJson();
            if (aj != null && aj.hasNonNull("selectedOptionId")) {
                optionIds.add(aj.get("selectedOptionId").asText());
            }
        }
        Map<String, PsychTestOption> optionById = optionRepository.findAllById(optionIds).stream()
            .collect(Collectors.toMap(PsychTestOption::getId, o -> o));

        List<AnswerBreakdownItem> breakdown = new ArrayList<>();
        Map<String, Double> prevScores = null;

        for (int i = 0; i < answers.size(); i++) {
            PsychTestAnswer a = answers.get(i);
            PsychTestQuestion q = a.getQuestion();
            JsonNode aj = a.getAnswerJson();

            String selectedOptionId = (aj != null && aj.hasNonNull("selectedOptionId")) ? aj.get("selectedOptionId").asText() : null;
            PsychTestOption selectedOption = (selectedOptionId != null) ? optionById.get(selectedOptionId) : null;

            String selectedLabel = selectedOption != null ? selectedOption.getLabel() : null;
            Integer optionValue = selectedOption != null ? selectedOption.getValue() : null;
            String text = (aj != null && aj.hasNonNull("text")) ? aj.get("text").asText() : null;

            List<String> impactedDomains = Collections.emptyList();
            Boolean reverse = q != null ? q.getReverseScored() : null;
            Double weight = q != null ? q.getWeight() : null;

            boolean affectsRiasec = q != null && (q.getType() == PsychTestQuestion.Type.LIKERT || q.getType() == PsychTestQuestion.Type.SCENARIO_MCQ);

            if (q != null && q.getType() == PsychTestQuestion.Type.LIKERT) {
                impactedDomains = q.getDomainTags() != null ? q.getDomainTags() : Collections.emptyList();
            } else if (q != null && q.getType() == PsychTestQuestion.Type.SCENARIO_MCQ) {
                impactedDomains = selectedOption != null && selectedOption.getDomainTags() != null ? selectedOption.getDomainTags() : Collections.emptyList();
            }

            // Compute per-answer impact by looking at score deltas across the timeline.
            ScoringService.ScoringSnapshot snapAfter = scoringService.computeSnapshot(session, answers.subList(0, i + 1));
            Map<String, Double> afterScores = snapAfter.domains().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().score0To100()));

            Map<String, Double> delta = new HashMap<>();
            if (prevScores != null) {
                for (String d : ScoringService.RIASEC) {
                    delta.put(d, afterScores.getOrDefault(d, 0.0) - prevScores.getOrDefault(d, 0.0));
                }
            } else {
                for (String d : ScoringService.RIASEC) {
                    delta.put(d, afterScores.getOrDefault(d, 0.0));
                }
            }

            // Build an easy-to-understand "bigger picture" meaning for this answer.
            String meaning = buildMeaning(q, selectedLabel, optionValue, reverse, impactedDomains, affectsRiasec);

            breakdown.add(new AnswerBreakdownItem(
                i + 1,
                q != null ? q.getId() : null,
                q != null && q.getType() != null ? q.getType().name() : null,
                q != null ? q.getPrompt() : null,
                selectedOptionId,
                selectedLabel,
                optionValue,
                affectsRiasec ? reverse : null,
                affectsRiasec ? weight : null,
                impactedDomains,
                delta,
                afterScores,
                text,
                affectsRiasec,
                meaning
            ));

            prevScores = afterScores;
        }

        MappingService.MappingExplanation mapping = mappingService.mapWithRationale(
            topDomains != null && topDomains.size() > 0 ? topDomains.get(0) : null,
            topDomains != null && topDomains.size() > 1 ? topDomains.get(1) : null,
            overallConfidence,
            session.getGrade()
        );

        SuggestionsExplanation suggestions = new SuggestionsExplanation(
            mapping.streamSuggestion(),
            mapping.streamReason(),
            mapping.primaryCareerPaths().stream().map(c -> new CareerSuggestion(c.title(), c.reason())).toList(),
            mapping.alternateCareerPaths().stream().map(c -> new CareerSuggestion(c.title(), c.reason())).toList(),
            mapping.roleModelsInAlignedFields()
        );

        return new ResultExplanation(breakdown, suggestions);
    }

    private static String buildMeaning(
        PsychTestQuestion q,
        String selectedLabel,
        Integer optionValue,
        Boolean reverseScored,
        List<String> impactedDomains,
        boolean affectsRiasec
    ) {
        if (q == null || q.getType() == null) {
            return "This answer is stored for your session history.";
        }

        if (!affectsRiasec) {
            // OPEN_ENDED: supporting-only (no impact on deterministic scoring).
            return "This is an open-ended response. It does NOT change your RIASEC scores; it is stored as supporting context for review.";
        }

        String domains = (impactedDomains != null && !impactedDomains.isEmpty())
            ? String.join(", ", impactedDomains)
            : "your profile";

        if (q.getType() == PsychTestQuestion.Type.LIKERT) {
            if (optionValue == null) {
                return "This Likert response contributes to " + domains + " as one small signal among many questions.";
            }
            int effective = optionValue;
            if (Boolean.TRUE.equals(reverseScored)) effective = -effective;

            String strength;
            if (effective >= 2) strength = "a strong positive signal";
            else if (effective == 1) strength = "a positive signal";
            else if (effective == 0) strength = "a neutral signal";
            else if (effective == -1) strength = "a negative signal";
            else strength = "a strong negative signal";

            String reversalNote = Boolean.TRUE.equals(reverseScored)
                ? " (this item is reverse-scored, so agreement decreases the score and disagreement increases it)"
                : "";

            String label = (selectedLabel != null && !selectedLabel.isBlank()) ? ("You chose \"" + selectedLabel + "\". ") : "";
            return label
                + "In the bigger picture, this is " + strength + " for " + domains + " based on your preference. "
                + "The test looks for consistency across multiple questions to build confidence." + reversalNote + ".";
        }

        if (q.getType() == PsychTestQuestion.Type.SCENARIO_MCQ) {
            String label = (selectedLabel != null && !selectedLabel.isBlank()) ? ("You chose \"" + selectedLabel + "\". ") : "";
            return label
                + "In the bigger picture, scenario choices provide supporting evidence and can gently reinforce domains like " + domains + ". "
                + "Your overall pattern across many items matters more than any single scenario choice.";
        }

        return "This answer contributes to your overall profile as one small signal among many questions.";
    }
}

