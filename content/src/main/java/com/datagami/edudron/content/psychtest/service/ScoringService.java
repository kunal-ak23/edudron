package com.datagami.edudron.content.psychtest.service;

import com.datagami.edudron.content.psychtest.domain.PsychTestAnswer;
import com.datagami.edudron.content.psychtest.domain.PsychTestOption;
import com.datagami.edudron.content.psychtest.domain.PsychTestQuestion;
import com.datagami.edudron.content.psychtest.domain.PsychTestSession;
import com.datagami.edudron.content.psychtest.repo.PsychTestOptionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ScoringService {
    private static final Logger logger = LoggerFactory.getLogger(ScoringService.class);

    public static final List<String> RIASEC = List.of("R", "I", "A", "S", "E", "C");

    private final PsychTestOptionRepository optionRepository;

    public ScoringService(PsychTestOptionRepository optionRepository) {
        this.optionRepository = optionRepository;
    }

    public record DomainStats(
        double score0To100,
        double confidence0To1,
        int primaryAnsweredCount,
        double variance
    ) {}

    public record ScoringSnapshot(
        Map<String, DomainStats> domains,
        List<String> topDomains,
        double topMargin,
        double overallConfidenceScore,
        String overallConfidenceLevel,
        int answeredCount
    ) {}

    public ScoringSnapshot computeSnapshot(PsychTestSession session, List<PsychTestAnswer> answers) {
        Map<String, Double> primarySum = new HashMap<>();
        Map<String, Integer> primaryCount = new HashMap<>();
        Map<String, List<Double>> primaryValues = new HashMap<>();

        Map<String, Double> secondarySum = new HashMap<>();
        Map<String, Integer> secondaryCount = new HashMap<>();

        for (String d : RIASEC) {
            primarySum.put(d, 0.0);
            primaryCount.put(d, 0);
            primaryValues.put(d, new ArrayList<>());
            secondarySum.put(d, 0.0);
            secondaryCount.put(d, 0);
        }

        // Batch load option ids used in answers (for value/domain tags lookups)
        Set<String> optionIds = new HashSet<>();
        for (PsychTestAnswer a : answers) {
            JsonNode aj = a.getAnswerJson();
            if (aj != null && aj.hasNonNull("selectedOptionId")) {
                optionIds.add(aj.get("selectedOptionId").asText());
            }
        }
        Map<String, PsychTestOption> optionById = optionRepository.findAllById(optionIds).stream()
            .collect(Collectors.toMap(PsychTestOption::getId, o -> o));

        for (PsychTestAnswer a : answers) {
            PsychTestQuestion q = a.getQuestion();
            if (q == null || q.getType() == null) continue;

            JsonNode aj = a.getAnswerJson();
            String selectedOptionId = (aj != null && aj.hasNonNull("selectedOptionId")) ? aj.get("selectedOptionId").asText() : null;
            PsychTestOption selectedOption = (selectedOptionId != null) ? optionById.get(selectedOptionId) : null;

            if (q.getType() == PsychTestQuestion.Type.LIKERT) {
                int v = 0;
                if (selectedOption != null && selectedOption.getValue() != null) {
                    v = selectedOption.getValue();
                } else if (aj != null && aj.hasNonNull("value")) {
                    v = aj.get("value").asInt();
                } else {
                    continue;
                }

                if (Boolean.TRUE.equals(q.getReverseScored())) {
                    v = -v;
                }

                double weighted = v * (q.getWeight() != null ? q.getWeight() : 1.0);
                List<String> tags = q.getDomainTags() != null ? q.getDomainTags() : Collections.emptyList();
                for (String d : tags) {
                    if (!primarySum.containsKey(d)) continue;
                    primarySum.put(d, primarySum.get(d) + weighted);
                    primaryCount.put(d, primaryCount.get(d) + 1);
                    primaryValues.get(d).add((double) v);
                }
            } else if (q.getType() == PsychTestQuestion.Type.SCENARIO_MCQ) {
                if (selectedOption == null || selectedOption.getValue() == null) continue;
                int v = selectedOption.getValue();
                List<String> tags = selectedOption.getDomainTags() != null ? selectedOption.getDomainTags() : Collections.emptyList();
                for (String d : tags) {
                    if (!secondarySum.containsKey(d)) continue;
                    secondarySum.put(d, secondarySum.get(d) + v);
                    secondaryCount.put(d, secondaryCount.get(d) + 1);
                }
            } else {
                // OPEN_ENDED does not affect core RIASEC scores
            }
        }

        // Normalize to 0–100 using primary (Likert) average mapped from [-2..2] -> [0..100]
        // Secondary adds up to +10 points (supporting evidence), but never changes ranking drastically.
        Map<String, DomainStats> out = new HashMap<>();
        for (String d : RIASEC) {
            int n = primaryCount.get(d);
            double sum = primarySum.get(d);
            double avg = (n > 0) ? (sum / n) : 0.0; // avg in [-2..2] roughly
            double base = ((avg + 2.0) / 4.0) * 100.0;
            base = clamp(base, 0.0, 100.0);

            int sn = secondaryCount.get(d);
            double ssum = secondarySum.get(d);
            double secondaryBoost = 0.0;
            if (sn > 0) {
                // options use value 0..2; scale to 0..10
                secondaryBoost = clamp((ssum / (sn * 2.0)) * 10.0, 0.0, 10.0);
            }

            double score = clamp(base + secondaryBoost, 0.0, 100.0);

            double variance = variance(primaryValues.get(d));
            double countFactor = clamp(n / 4.0, 0.0, 1.0); // ~4 primary items gives good baseline
            double consistencyFactor = 1.0 - clamp(variance / 4.0, 0.0, 1.0); // varMax ~4 in [-2..2]
            double confidence = clamp(0.6 * countFactor + 0.4 * consistencyFactor, 0.0, 1.0);

            out.put(d, new DomainStats(score, confidence, n, variance));
        }

        List<Map.Entry<String, DomainStats>> sorted = out.entrySet().stream()
            .sorted(Comparator.comparingDouble((Map.Entry<String, DomainStats> e) -> e.getValue().score0To100()).reversed())
            .collect(Collectors.toList());

        List<String> topDomains = new ArrayList<>();
        if (!sorted.isEmpty()) {
            double topScore = sorted.get(0).getValue().score0To100();
            for (Map.Entry<String, DomainStats> e : sorted) {
                if (topDomains.size() >= 3) break;
                if (topDomains.isEmpty() || (topScore - e.getValue().score0To100()) <= 3.0) {
                    topDomains.add(e.getKey());
                }
            }
            if (topDomains.size() < 2 && sorted.size() >= 2) {
                topDomains.add(sorted.get(1).getKey());
            }
        }

        double topMargin = 0.0;
        if (sorted.size() >= 2) {
            topMargin = sorted.get(0).getValue().score0To100() - sorted.get(1).getValue().score0To100();
        }

        double avgDomainConfidence = out.values().stream()
            .mapToDouble(DomainStats::confidence0To1)
            .average()
            .orElse(0.0);

        double tieFactor = clamp(topMargin / 10.0, 0.0, 1.0); // bigger gap => higher confidence
        double overallConfidenceScore = clamp(0.75 * avgDomainConfidence + 0.25 * tieFactor, 0.0, 1.0);
        String overall = overallConfidenceScore >= 0.75 ? "HIGH" : overallConfidenceScore >= 0.55 ? "MEDIUM" : "LOW";

        logger.debug("Scoring snapshot session={} answered={} overall={}({}) top={}",
            session != null ? session.getId() : null,
            answers.size(),
            overall,
            overallConfidenceScore,
            topDomains);

        return new ScoringSnapshot(out, topDomains, topMargin, overallConfidenceScore, overall, answers.size());
    }

    private static double variance(List<Double> values) {
        if (values == null || values.size() < 2) return 0.0;
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double sumSq = 0.0;
        for (double v : values) {
            double d = v - mean;
            sumSq += d * d;
        }
        return sumSq / (values.size() - 1);
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}

