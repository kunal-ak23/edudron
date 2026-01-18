package com.datagami.edudron.content.psychtest.service;

import com.datagami.edudron.content.psychtest.domain.PsychTestAnswer;
import com.datagami.edudron.content.psychtest.domain.PsychTestQuestion;
import com.datagami.edudron.content.psychtest.domain.PsychTestSession;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class AdaptiveQuestionSelector {
    private static final int START_PHASE_ANSWERS = 12;

    public record Selection(
        PsychTestQuestion question,
        List<String> eligibleQuestionIds,
        boolean earlyStopRecommended
    ) {}

    public Selection selectNextQuestion(
        PsychTestSession session,
        List<PsychTestAnswer> answers,
        List<PsychTestQuestion> activeQuestions,
        ScoringService.ScoringSnapshot snapshot,
        boolean aiAvailable
    ) {
        Set<String> answeredIds = answers.stream().map(a -> a.getQuestion().getId()).collect(Collectors.toSet());

        List<PsychTestQuestion> eligible = activeQuestions.stream()
            .filter(q -> q.getIsActive() != null && q.getIsActive())
            .filter(q -> q.getBankVersion().equals(session.getBankVersion()))
            .filter(q -> !answeredIds.contains(q.getId()))
            .filter(q -> matchesGradeBand(session.getGrade(), q.getGradeBand()))
            .collect(Collectors.toList());

        List<String> eligibleIds = eligible.stream().map(PsychTestQuestion::getId).toList();

        boolean earlyStop = shouldStopEarly(session, answers.size(), snapshot);
        if (earlyStop || eligible.isEmpty()) {
            return new Selection(null, eligibleIds, earlyStop);
        }

        PsychTestQuestion chosen;
        if (answers.size() < START_PHASE_ANSWERS) {
            chosen = chooseBalanced(eligible, snapshot);
        } else {
            chosen = chooseFocused(eligible, snapshot);
        }

        return new Selection(chosen, eligibleIds, false);
    }

    public List<String> eligibleQuestionIds(
        PsychTestSession session,
        List<PsychTestAnswer> answers,
        List<PsychTestQuestion> activeQuestions
    ) {
        Set<String> answeredIds = answers.stream().map(a -> a.getQuestion().getId()).collect(Collectors.toSet());
        List<PsychTestQuestion> eligible = activeQuestions.stream()
            .filter(q -> q.getIsActive() != null && q.getIsActive())
            .filter(q -> q.getBankVersion().equals(session.getBankVersion()))
            .filter(q -> !answeredIds.contains(q.getId()))
            .filter(q -> matchesGradeBand(session.getGrade(), q.getGradeBand()))
            .collect(Collectors.toList());
        return eligible.stream().map(PsychTestQuestion::getId).toList();
    }

    private PsychTestQuestion chooseBalanced(List<PsychTestQuestion> eligible, ScoringService.ScoringSnapshot snapshot) {
        // Ensure coverage across 6 domains using primaryAnsweredCount.
        Map<String, Integer> counts = new HashMap<>();
        for (String d : ScoringService.RIASEC) {
            counts.put(d, snapshot.domains().get(d).primaryAnsweredCount());
        }

        String minDomain = counts.entrySet().stream()
            .min(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue).thenComparing(Map.Entry::getKey))
            .map(Map.Entry::getKey)
            .orElse("I");

        // Prefer LIKERT in start phase.
        for (PsychTestQuestion q : eligible) {
            if (q.getType() == PsychTestQuestion.Type.LIKERT && q.getDomainTags() != null && q.getDomainTags().contains(minDomain)) {
                return q;
            }
        }

        // Fallback: any LIKERT
        for (PsychTestQuestion q : eligible) {
            if (q.getType() == PsychTestQuestion.Type.LIKERT) return q;
        }

        // Otherwise: scenario/open-ended
        return eligible.get(0);
    }

    private PsychTestQuestion chooseFocused(List<PsychTestQuestion> eligible, ScoringService.ScoringSnapshot snapshot) {
        List<String> focus = snapshot.topDomains();
        Set<String> focusSet = new HashSet<>(focus);

        // Prefer LIKERT that targets focus domains and has lower confidence.
        String bestDomain = null;
        double bestNeed = -1;
        for (String d : ScoringService.RIASEC) {
            double domainNeed = (focusSet.contains(d) ? 1.0 : 0.5) * (1.0 - snapshot.domains().get(d).confidence0To1());
            if (domainNeed > bestNeed) {
                bestNeed = domainNeed;
                bestDomain = d;
            }
        }

        if (bestDomain != null) {
            for (PsychTestQuestion q : eligible) {
                if (q.getType() == PsychTestQuestion.Type.LIKERT && q.getDomainTags() != null && q.getDomainTags().contains(bestDomain)) {
                    return q;
                }
            }
        }

        // Use a scenario as a tie-breaker if top margin is small and we have unanswered scenarios.
        if (snapshot.topMargin() < 5.0) {
            for (PsychTestQuestion q : eligible) {
                if (q.getType() == PsychTestQuestion.Type.SCENARIO_MCQ) return q;
            }
        }

        // Otherwise: any LIKERT, then any
        for (PsychTestQuestion q : eligible) {
            if (q.getType() == PsychTestQuestion.Type.LIKERT) return q;
        }
        return eligible.get(0);
    }

    private boolean shouldStopEarly(PsychTestSession session, int answeredCount, ScoringService.ScoringSnapshot snapshot) {
        int max = session.getMaxQuestions() != null ? session.getMaxQuestions() : 30;
        if (answeredCount >= max) return true;

        // Typical length 20–30; allow early stop after 18 if confidence is high and top domains are clearly separated.
        int min = 18;
        if (answeredCount < min) return false;

        return "HIGH".equalsIgnoreCase(snapshot.overallConfidenceLevel()) && snapshot.topMargin() >= 8.0;
    }

    private boolean matchesGradeBand(Integer grade, String band) {
        if (band == null || band.isBlank()) return true;
        if (grade == null) return true;

        String trimmed = band.trim();
        try {
            if (trimmed.contains("-")) {
                String[] parts = trimmed.split("-");
                int lo = Integer.parseInt(parts[0].trim());
                int hi = Integer.parseInt(parts[1].trim());
                return grade >= lo && grade <= hi;
            }
            // exact grade match
            int g = Integer.parseInt(trimmed);
            return grade == g;
        } catch (Exception ignored) {
            // If parsing fails, don't block the question.
            return true;
        }
    }
}

