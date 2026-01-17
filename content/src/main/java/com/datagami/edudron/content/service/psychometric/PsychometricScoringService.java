package com.datagami.edudron.content.service.psychometric;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Scoring Service for Hybrid Psychometric Test
 * Handles RIASEC, Indicator, and Stream scoring
 */
@Service
public class PsychometricScoringService {
    
    private static final Logger logger = LoggerFactory.getLogger(PsychometricScoringService.class);
    
    // Likert scale mapping
    private static final Map<String, Integer> LIKERT_MAPPING = Map.of(
        "Strongly Agree", 2,
        "Agree", 1,
        "Not Sure", 0,
        "Disagree", -1,
        "Strongly Disagree", -2
    );
    
    /**
     * Score a single answer against all its scoring tags
     */
    public void scoreAnswer(Answer answer, Question question, SessionState state) {
        if (question == null || question.getScoringTags() == null) {
            return;
        }
        
        String answerValue = answer.getValue();
        
        for (ScoringTag tag : question.getScoringTags()) {
            String category = tag.getCategory();
            String name = tag.getName();
            Map<String, Integer> valueMapping = tag.getValueMapping();
            
            // Get score contribution for this answer
            Integer contribution = valueMapping.get(answerValue);
            if (contribution == null) {
                // For micro-subjective questions, contribution might be 0 or handled by AI
                contribution = 0;
            }
            
            // Add to appropriate score map
            if ("RIASEC".equals(category)) {
                state.getRiasecScores().merge(name, contribution.doubleValue(), Double::sum);
            } else if ("INDICATOR".equals(category)) {
                state.getIndicatorScores().merge(name, contribution.doubleValue(), Double::sum);
            } else if ("STREAM".equals(category)) {
                state.getStreamScores().merge(name, contribution.doubleValue(), Double::sum);
            }
        }
    }
    
    /**
     * Score all core answers and compute initial scores
     */
    public void scoreCoreAnswers(SessionState state, List<Question> coreQuestions) {
        // Reset scores
        state.setRiasecScores(new HashMap<>());
        state.setIndicatorScores(new HashMap<>());
        state.setStreamScores(new HashMap<>());
        
        // Initialize all RIASEC scores to 0
        for (String code : Arrays.asList("R", "I", "A", "S", "E", "C")) {
            state.getRiasecScores().put(code, 0.0);
        }
        
        // Initialize all indicator scores to 0
        for (String indicator : Arrays.asList("math_confidence", "language_confidence", "logic_confidence", 
                "teamwork", "leadership", "expression_confidence")) {
            state.getIndicatorScores().put(indicator, 0.0);
        }
        
        // Initialize all stream scores to 0
        for (String stream : Arrays.asList("Science", "Commerce", "Arts")) {
            state.getStreamScores().put(stream, 0.0);
        }
        
        // Score each answer
        for (Answer answer : state.getCoreAnswers()) {
            Question question = coreQuestions.stream()
                .filter(q -> q.getId().equals(answer.getQuestionId()))
                .findFirst()
                .orElse(null);
            
            if (question != null) {
                scoreAnswer(answer, question, state);
            }
        }
        
        logger.info("Scored core answers. RIASEC: {}, Indicators: {}, Streams: {}", 
            state.getRiasecScores(), state.getIndicatorScores(), state.getStreamScores());
    }
    
    /**
     * Detect prime candidates (top 2-3 streams + RIASEC themes)
     */
    public void detectPrimeCandidates(SessionState state) {
        List<String> candidates = new ArrayList<>();
        
        // Get top streams
        List<Map.Entry<String, Double>> topStreams = state.getStreamScores().entrySet().stream()
            .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
            .limit(2)
            .collect(Collectors.toList());
        
        // Get top RIASEC themes
        List<Map.Entry<String, Double>> topRiasec = state.getRiasecScores().entrySet().stream()
            .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
            .limit(2)
            .collect(Collectors.toList());
        
        // Combine into prime candidates
        for (Map.Entry<String, Double> stream : topStreams) {
            candidates.add("STREAM:" + stream.getKey());
        }
        
        for (Map.Entry<String, Double> riasec : topRiasec) {
            candidates.add("RIASEC:" + riasec.getKey());
        }
        
        state.setPrimeCandidates(candidates);
        
        // Calculate confidence score (gap between top scores)
        double confidence = calculateConfidenceScore(state);
        state.setConfidenceScore(confidence);
        
        logger.info("Detected prime candidates: {}, confidence: {}", candidates, confidence);
    }
    
    /**
     * Calculate confidence score based on gap between top scores
     */
    private double calculateConfidenceScore(SessionState state) {
        // Stream confidence
        List<Double> streamScores = new ArrayList<>(state.getStreamScores().values());
        Collections.sort(streamScores, Collections.reverseOrder());
        
        double streamConfidence = 0.0;
        if (streamScores.size() >= 2) {
            double gap = streamScores.get(0) - streamScores.get(1);
            streamConfidence = Math.min(gap / 10.0, 1.0); // Normalize to 0-1
        } else if (streamScores.size() == 1) {
            streamConfidence = 1.0;
        }
        
        // RIASEC confidence
        List<Double> riasecScores = new ArrayList<>(state.getRiasecScores().values());
        Collections.sort(riasecScores, Collections.reverseOrder());
        
        double riasecConfidence = 0.0;
        if (riasecScores.size() >= 2) {
            double gap = riasecScores.get(0) - riasecScores.get(1);
            riasecConfidence = Math.min(gap / 10.0, 1.0); // Normalize to 0-1
        } else if (riasecScores.size() == 1) {
            riasecConfidence = 1.0;
        }
        
        // Average confidence
        return (streamConfidence + riasecConfidence) / 2.0;
    }
    
    /**
     * Select adaptive modules based on prime candidates
     * Returns list of module names (e.g., "SCIENCE", "COMMERCE", "ARTS")
     */
    public List<String> selectAdaptiveModules(SessionState state) {
        List<String> modules = new ArrayList<>();
        
        // Get top stream
        Optional<Map.Entry<String, Double>> topStream = state.getStreamScores().entrySet().stream()
            .max(Map.Entry.comparingByValue());
        
        if (topStream.isPresent()) {
            String stream = topStream.get().getKey();
            modules.add(stream.toUpperCase());
        }
        
        // Get second stream if scores are close (within 3 points)
        List<Map.Entry<String, Double>> sortedStreams = state.getStreamScores().entrySet().stream()
            .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
            .collect(Collectors.toList());
        
        if (sortedStreams.size() >= 2) {
            double topScore = sortedStreams.get(0).getValue();
            double secondScore = sortedStreams.get(1).getValue();
            
            if (topScore - secondScore <= 3.0 && !modules.contains(sortedStreams.get(1).getKey().toUpperCase())) {
                modules.add(sortedStreams.get(1).getKey().toUpperCase());
            }
        }
        
        // Limit to 2 modules
        if (modules.size() > 2) {
            modules = modules.subList(0, 2);
        }
        
        logger.info("Selected adaptive modules: {}", modules);
        return modules;
    }
    
    /**
     * Determine primary and secondary streams from final scores
     */
    public Map<String, String> determineStreams(SessionState state) {
        Map<String, String> result = new HashMap<>();
        
        List<Map.Entry<String, Double>> sortedStreams = state.getStreamScores().entrySet().stream()
            .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
            .collect(Collectors.toList());
        
        if (!sortedStreams.isEmpty()) {
            result.put("primary", sortedStreams.get(0).getKey());
            
            // Secondary stream only if scores are close (within 5 points)
            if (sortedStreams.size() >= 2) {
                double primaryScore = sortedStreams.get(0).getValue();
                double secondaryScore = sortedStreams.get(1).getValue();
                
                if (primaryScore - secondaryScore <= 5.0) {
                    result.put("secondary", sortedStreams.get(1).getKey());
                }
            }
        }
        
        return result;
    }
    
    /**
     * Get top 2 RIASEC themes with explanations
     */
    public List<RiasecTheme> getTopRiasecThemes(SessionState state) {
        List<Map.Entry<String, Double>> topRiasec = state.getRiasecScores().entrySet().stream()
            .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
            .limit(2)
            .collect(Collectors.toList());
        
        Map<String, String> riasecNames = Map.of(
            "R", "Realistic",
            "I", "Investigative",
            "A", "Artistic",
            "S", "Social",
            "E", "Enterprising",
            "C", "Conventional"
        );
        
        Map<String, String> riasecExplanations = Map.of(
            "R", "You prefer working with tools, machines, and hands-on activities",
            "I", "You enjoy research, analysis, and solving complex problems",
            "A", "You are creative and enjoy expressing yourself through art, music, or writing",
            "S", "You like helping others and working with people",
            "E", "You are ambitious and enjoy leading, persuading, and business activities",
            "C", "You prefer organized, structured work with clear procedures"
        );
        
        List<RiasecTheme> themes = new ArrayList<>();
        for (Map.Entry<String, Double> entry : topRiasec) {
            String code = entry.getKey();
            RiasecTheme theme = new RiasecTheme(
                code,
                riasecNames.getOrDefault(code, code),
                entry.getValue(),
                riasecExplanations.getOrDefault(code, "")
            );
            themes.add(theme);
        }
        
        return themes;
    }
}
