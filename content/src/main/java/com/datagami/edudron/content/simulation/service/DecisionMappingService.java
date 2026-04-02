package com.datagami.edudron.content.simulation.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;;

@Service
public class DecisionMappingService {

    private static final Logger logger = LoggerFactory.getLogger(DecisionMappingService.class);

    /**
     * Resolve a student's input to a choiceId based on the node's decision type and mappings.
     */
    @SuppressWarnings("unchecked")
    public String resolveChoice(Map<String, Object> node, String choiceId, Map<String, Object> input) {
        String decisionType = (String) node.get("decisionType");

        if (decisionType == null || "NARRATIVE_CHOICE".equals(decisionType)) {
            return validateChoiceId(node, choiceId);
        }

        Map<String, Object> config = (Map<String, Object>) node.get("decisionConfig");
        if (config == null) {
            logger.warn("Decision config missing for type {}. Falling back.", decisionType);
            return fallbackChoiceId(node, choiceId, decisionType);
        }

        // NEGOTIATION uses "outcomes" key instead of "mappings"
        List<Map<String, Object>> mappings = (List<Map<String, Object>>) config.get("mappings");
        if (mappings == null || mappings.isEmpty()) {
            mappings = (List<Map<String, Object>>) config.get("outcomes");
        }
        if (mappings == null || mappings.isEmpty()) {
            // Auto-generate mappings for STAKEHOLDER_MEETING and HIRE_FIRE when AI omitted them
            mappings = autoGenerateMappings(node, config, decisionType);
            if (mappings == null || mappings.isEmpty()) {
                logger.warn("Mappings missing for type {} and auto-generation failed. Falling back.", decisionType);
                return fallbackChoiceId(node, choiceId, decisionType);
            }
            logger.info("Auto-generated {} mappings for type {}", mappings.size(), decisionType);
        }

        // INVESTMENT_PORTFOLIO: score based on allocation balance (no condition-based mappings needed)
        if ("INVESTMENT_PORTFOLIO".equals(decisionType) && input != null) {
            return resolveInvestmentPortfolioChoice(node, config, input);
        }

        // STAKEHOLDER_MEETING: score based on stakeholder priority (deterministic, bypasses fragile mappings)
        if ("STAKEHOLDER_MEETING".equals(decisionType) && input != null) {
            return resolveStakeholderMeetingChoice(node, config, input);
        }

        // HIRE_FIRE: score based on candidate fit from mentor guidance (deterministic)
        if ("HIRE_FIRE".equals(decisionType) && input != null) {
            return resolveHireFireChoice(node, config, input);
        }

        Map<String, Object> flatInput = "COMPOUND".equals(decisionType)
                ? flattenCompoundInput(input)
                : flattenInput(input);

        // CRISIS_RESPONSE: if expired, return defaultOnExpiry
        if ("CRISIS_RESPONSE".equals(decisionType) && "true".equals(flatInput.get("expired"))) {
            String defaultChoice = (String) config.get("defaultOnExpiry");
            if (defaultChoice != null) {
                return validateChoiceId(node, defaultChoice);
            }
        }

        // Evaluate mappings in order; return first match
        logger.info("Evaluating mappings for type {}. Flat input keys: {}", decisionType, flatInput.keySet());
        for (Map<String, Object> mapping : mappings) {
            String condition = (String) mapping.get("condition");
            String mappedChoiceId = (String) mapping.get("choiceId");

            if ("default".equals(condition)) {
                logger.info("Matched default mapping → {}", mappedChoiceId);
                return validateChoiceId(node, mappedChoiceId);
            }

            boolean matched = evaluateCondition(condition, flatInput);
            logger.info("Condition [{}] → {} (choiceId: {})", condition, matched, mappedChoiceId);
            if (matched) {
                return validateChoiceId(node, mappedChoiceId);
            }
        }

        // No match and no default — fallback to choiceId if provided (narrative-style)
        if (choiceId != null && !choiceId.isEmpty()) {
            logger.warn("No mapping matched for type {}. Falling back to direct choiceId.", decisionType);
            return validateChoiceId(node, choiceId);
        }

        // Last resort: pick the middle choice (not worst) to avoid unfair zero-point penalties
        List<Map<String, Object>> choices = (List<Map<String, Object>>) node.get("choices");
        if (choices != null && !choices.isEmpty()) {
            int midIdx = choices.size() / 2;
            String midChoiceId = (String) choices.get(midIdx).get("id");
            logger.warn("No mapping matched and no choiceId provided for type {}. Defaulting to middle choice (index {}).", decisionType, midIdx);
            return midChoiceId;
        }

        throw new IllegalStateException("No mapping matched and no choices available");
    }

    /**
     * Fallback when config or mappings are missing.
     * If choiceId is provided (narrative-style), validate it.
     * Otherwise pick the middle choice to avoid unfair zero-point penalties.
     */
    @SuppressWarnings("unchecked")
    private String fallbackChoiceId(Map<String, Object> node, String choiceId, String decisionType) {
        if (choiceId != null && !choiceId.isBlank()) {
            return validateChoiceId(node, choiceId);
        }
        List<Map<String, Object>> choices = (List<Map<String, Object>>) node.get("choices");
        if (choices != null && !choices.isEmpty()) {
            int midIdx = choices.size() / 2;
            String midId = (String) choices.get(midIdx).get("id");
            logger.warn("No choiceId for type {}. Defaulting to middle choice (index {}).", decisionType, midIdx);
            return midId;
        }
        throw new IllegalStateException("No choices available for fallback");
    }

    /**
     * Validate that the given choiceId exists in the node's choices list.
     */
    @SuppressWarnings("unchecked")
    String validateChoiceId(Map<String, Object> node, String choiceId) {
        if (choiceId == null || choiceId.isBlank()) {
            throw new IllegalArgumentException("Choice ID must not be null or blank");
        }

        List<Map<String, Object>> choices = (List<Map<String, Object>>) node.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new IllegalArgumentException("Node has no choices defined");
        }

        boolean exists = choices.stream()
                .anyMatch(c -> choiceId.equals(c.get("id")));

        if (!exists) {
            throw new IllegalArgumentException("Choice ID '" + choiceId + "' not found in node choices");
        }

        return choiceId;
    }

    /**
     * Flatten input for non-compound decision types.
     * Handles PRIORITY_RANKING special case: if input has a "ranking" key (a list),
     * extracts "top" as the first element and indexed entries like ranking[0], ranking[1], etc.
     * For other types, returns input as-is.
     */
    @SuppressWarnings("unchecked")
    Map<String, Object> flattenInput(Map<String, Object> input) {
        if (input == null) {
            return Map.of();
        }

        Object rankingObj = input.get("ranking");
        if (rankingObj instanceof List<?> ranking) {
            Map<String, Object> flat = new HashMap<>(input);
            if (!ranking.isEmpty()) {
                flat.put("top", ranking.get(0));
            }
            for (int i = 0; i < ranking.size(); i++) {
                flat.put("ranking[" + i + "]", ranking.get(i));
            }
            logger.info("flattenInput for type PRIORITY_RANKING: keys={}", flat.keySet());
            return flat;
        }

        Map<String, Object> flat = new HashMap<>(input);

        // HIRE_FIRE: selected candidate
        if (input.containsKey("selected")) {
            flat.put("selected", input.get("selected").toString());
        }

        // STAKEHOLDER_MEETING: selected stakeholders
        if (input.containsKey("selectedStakeholders")) {
            @SuppressWarnings("unchecked")
            List<String> selected = (List<String>) input.get("selectedStakeholders");
            flat.put("selected_list", String.join(",", selected));
            for (String s : selected) {
                flat.put("has_" + s, "true");
            }
        }

        // NEGOTIATION
        if (input.containsKey("finalAmount")) {
            flat.put("final_amount", input.get("finalAmount").toString());
        }
        if (input.containsKey("acceptedRound")) {
            flat.put("accepted_round", input.get("acceptedRound").toString());
        }
        if (input.containsKey("walkedAway")) {
            flat.put("walked_away", input.get("walkedAway").toString());
        }

        // CRISIS_RESPONSE: expired flag
        if (input.containsKey("expired") && Boolean.TRUE.equals(input.get("expired"))) {
            flat.put("expired", "true");
        }

        logger.info("flattenInput for type general: keys={}", flat.keySet());
        return flat;
    }

    /**
     * Flatten compound input from {step1: {key: val}, step2: {key: val}}
     * to {step1.key: val, step2.key: val}.
     */
    @SuppressWarnings("unchecked")
    Map<String, Object> flattenCompoundInput(Map<String, Object> input) {
        if (input == null) {
            return Map.of();
        }

        Map<String, Object> flat = new HashMap<>();
        for (Map.Entry<String, Object> entry : input.entrySet()) {
            String stepKey = entry.getKey();
            Object stepValue = entry.getValue();

            if (stepValue instanceof Map<?, ?> stepMap) {
                // Flatten the nested step's input (handles ranking inside steps)
                Map<String, Object> flatStep = flattenInput((Map<String, Object>) stepMap);
                for (Map.Entry<String, Object> inner : flatStep.entrySet()) {
                    flat.put(stepKey + "." + inner.getKey(), inner.getValue());
                }
            } else {
                flat.put(stepKey, stepValue);
            }
        }

        return flat;
    }

    /**
     * Evaluate a condition string against a context map.
     * Supports compound conditions with && (AND) and || (OR).
     * OR is evaluated first (split on ||), then AND within each OR branch (split on &&).
     */
    boolean evaluateCondition(String condition, Map<String, Object> context) {
        if (condition == null || condition.isBlank()) {
            return false;
        }

        // Split on || for OR groups
        String[] orParts = condition.split("\\|\\|");
        for (String orPart : orParts) {
            // Split on && for AND conditions within each OR group
            String[] andParts = orPart.split("&&");
            boolean allAndMatch = true;
            for (String andPart : andParts) {
                if (!evaluateAtomicCondition(andPart.trim(), context)) {
                    allAndMatch = false;
                    break;
                }
            }
            if (allAndMatch) {
                return true;
            }
        }

        return false;
    }

    /**
     * Evaluate a single atomic condition like "rd >= 50" or "selected == 'launch_now'".
     */
    private boolean evaluateAtomicCondition(String condition, Map<String, Object> context) {
        // Handle selected_contains('x') function
        if (condition.contains("selected_contains")) {
            String id = condition.replaceAll(".*selected_contains\\('([^']+)'\\).*", "$1");
            return "true".equals(context.get("has_" + id));
        }

        // Handle NEGOTIATION conditions: agreement_above_N, agreement_below_N, walked_away
        if (condition.startsWith("agreement_above_")) {
            String threshold = condition.replace("agreement_above_", "");
            Object finalAmount = context.get("final_amount");
            if (finalAmount != null && !"true".equals(context.get("walked_away"))) {
                try { return Double.parseDouble(finalAmount.toString()) >= Double.parseDouble(threshold); }
                catch (NumberFormatException e) { return false; }
            }
            return false;
        }
        if (condition.startsWith("agreement_below_")) {
            String threshold = condition.replace("agreement_below_", "");
            Object finalAmount = context.get("final_amount");
            if (finalAmount != null && !"true".equals(context.get("walked_away"))) {
                try { return Double.parseDouble(finalAmount.toString()) <= Double.parseDouble(threshold); }
                catch (NumberFormatException e) { return false; }
            }
            return false;
        }
        if ("walked_away".equals(condition)) {
            return "true".equals(context.get("walked_away"));
        }

        // Try each operator (longer operators first to avoid partial matches)
        String[][] operators = {
                {">=", ">="},
                {"<=", "<="},
                {"!=", "!="},
                {"==", "=="},
                {">", ">"},
                {"<", "<"}
        };

        for (String[] op : operators) {
            int idx = condition.indexOf(op[0]);
            if (idx > 0) {
                String key = condition.substring(0, idx).trim();
                String rawValue = condition.substring(idx + op[0].length()).trim();
                Object contextValue = context.get(key);

                if (contextValue == null) {
                    logger.warn("Context key '{}' not found when evaluating condition: {}", key, condition);
                    return false;
                }

                return compareValues(contextValue, rawValue, op[1]);
            }
        }

        logger.warn("Could not parse condition: {}", condition);
        return false;
    }

    /**
     * Compare a context value against a raw string value using the given operator.
     * Handles both numeric and string comparisons.
     */
    private boolean compareValues(Object contextValue, String rawValue, String operator) {
        // Strip surrounding single or double quotes for string comparison
        String strValue = rawValue;
        if ((strValue.startsWith("'") && strValue.endsWith("'"))
                || (strValue.startsWith("\"") && strValue.endsWith("\""))) {
            strValue = strValue.substring(1, strValue.length() - 1);
        }

        // Attempt numeric comparison first
        Double contextNum = toDouble(contextValue);
        Double valueNum = toDouble(strValue);

        if (contextNum != null && valueNum != null) {
            return switch (operator) {
                case ">=" -> contextNum >= valueNum;
                case "<=" -> contextNum <= valueNum;
                case ">"  -> contextNum > valueNum;
                case "<"  -> contextNum < valueNum;
                case "==" -> contextNum.equals(valueNum);
                case "!=" -> !contextNum.equals(valueNum);
                default -> false;
            };
        }

        // Fall back to string comparison
        String contextStr = String.valueOf(contextValue);
        return switch (operator) {
            case "==" -> contextStr.equals(strValue);
            case "!=" -> !contextStr.equals(strValue);
            default -> {
                logger.warn("Cannot perform '{}' comparison on non-numeric values: {} vs {}", operator, contextStr, strValue);
                yield false;
            }
        };
    }

    private Double toDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number num) {
            return num.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Auto-generate mappings when the AI omitted them.
     * For STAKEHOLDER_MEETING: maps stakeholder combinations to quality-ordered choices.
     * For HIRE_FIRE: maps candidate selection to quality-ordered choices.
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> autoGenerateMappings(
            Map<String, Object> node, Map<String, Object> config, String decisionType) {

        List<Map<String, Object>> choices = (List<Map<String, Object>>) node.get("choices");
        if (choices == null || choices.isEmpty()) return null;

        // Sort choices by quality descending (best first)
        List<Map<String, Object>> sortedChoices = new ArrayList<>(choices);
        sortedChoices.sort((a, b) -> {
            int qa = a.get("quality") != null ? ((Number) a.get("quality")).intValue() : 1;
            int qb = b.get("quality") != null ? ((Number) b.get("quality")).intValue() : 1;
            return qb - qa;
        });

        if ("STAKEHOLDER_MEETING".equals(decisionType)) {
            List<Map<String, Object>> stakeholders = (List<Map<String, Object>>) config.get("stakeholders");
            if (stakeholders == null || stakeholders.size() < 2) return null;

            int maxSelections = config.get("maxSelections") != null
                    ? ((Number) config.get("maxSelections")).intValue() : 2;

            List<Map<String, Object>> mappings = new ArrayList<>();

            // Strategy: first stakeholders are "best" picks, last are "worst"
            // Best combo (quality 3): first two stakeholders
            // Medium combo (quality 2): first + any other
            // Default (quality 1): anything else
            if (stakeholders.size() >= 2 && sortedChoices.size() >= 2) {
                String bestId1 = (String) stakeholders.get(0).get("id");
                String bestId2 = (String) stakeholders.get(1).get("id");

                if (maxSelections >= 2) {
                    // Best: select both top stakeholders
                    Map<String, Object> bestMapping = new LinkedHashMap<>();
                    bestMapping.put("condition",
                        "selected_contains('" + bestId1 + "') && selected_contains('" + bestId2 + "')");
                    bestMapping.put("choiceId", sortedChoices.get(0).get("id"));
                    mappings.add(bestMapping);

                    // Medium: select at least the first stakeholder
                    if (sortedChoices.size() >= 2) {
                        Map<String, Object> midMapping = new LinkedHashMap<>();
                        midMapping.put("condition", "selected_contains('" + bestId1 + "')");
                        midMapping.put("choiceId", sortedChoices.get(1).get("id"));
                        mappings.add(midMapping);
                    }
                }

                // Default: anything else
                Map<String, Object> defaultMapping = new LinkedHashMap<>();
                defaultMapping.put("condition", "default");
                defaultMapping.put("choiceId", sortedChoices.get(sortedChoices.size() - 1).get("id"));
                mappings.add(defaultMapping);
            }

            logger.info("Auto-generated STAKEHOLDER_MEETING mappings: best=[{},{}], {} total mappings",
                    stakeholders.get(0).get("id"), stakeholders.get(1).get("id"), mappings.size());
            for (Map<String, Object> m : mappings) {
                logger.info("  Generated mapping: condition=[{}], choiceId={}", m.get("condition"), m.get("choiceId"));
            }
            return mappings;

        } else if ("HIRE_FIRE".equals(decisionType)) {
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) config.get("candidates");
            if (candidates == null || candidates.isEmpty()) return null;

            List<Map<String, Object>> mappings = new ArrayList<>();

            // Map each candidate to a choice in quality order
            for (int i = 0; i < candidates.size() && i < sortedChoices.size(); i++) {
                Map<String, Object> mapping = new LinkedHashMap<>();
                String candidateId = (String) candidates.get(i).get("id");
                mapping.put("condition", "selected == '" + candidateId + "'");
                mapping.put("choiceId", sortedChoices.get(i).get("id"));
                mappings.add(mapping);
            }

            // Default
            Map<String, Object> defaultMapping = new LinkedHashMap<>();
            defaultMapping.put("condition", "default");
            defaultMapping.put("choiceId", sortedChoices.get(sortedChoices.size() - 1).get("id"));
            mappings.add(defaultMapping);

            for (Map<String, Object> m : mappings) {
                logger.info("  Generated HIRE_FIRE mapping: condition=[{}], choiceId={}", m.get("condition"), m.get("choiceId"));
            }
            return mappings;
        }

        return null;
    }

    /**
     * Resolve INVESTMENT_PORTFOLIO choice based on allocation balance.
     * Measures how evenly budget is spread across departments.
     * Balanced = best quality, concentrated = worst quality.
     */
    @SuppressWarnings("unchecked")
    private String resolveInvestmentPortfolioChoice(
            Map<String, Object> node, Map<String, Object> config, Map<String, Object> input) {

        List<Map<String, Object>> choices = (List<Map<String, Object>>) node.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new IllegalStateException("INVESTMENT_PORTFOLIO has no choices");
        }

        // Sort choices by quality descending
        List<Map<String, Object>> sortedChoices = new ArrayList<>(choices);
        sortedChoices.sort((a, b) -> {
            int qa = a.get("quality") != null ? ((Number) a.get("quality")).intValue() : 1;
            int qb = b.get("quality") != null ? ((Number) b.get("quality")).intValue() : 1;
            return qb - qa;
        });

        // Calculate how balanced the allocation is using coefficient of variation
        List<Map<String, Object>> departments = (List<Map<String, Object>>) config.get("departments");
        if (departments == null || departments.isEmpty()) {
            return (String) sortedChoices.get(sortedChoices.size() - 1).get("id");
        }

        List<Double> allocations = new ArrayList<>();
        double total = 0;
        for (Map<String, Object> dept : departments) {
            String deptId = (String) dept.get("id");
            Object val = input.get(deptId);
            double amount = val != null ? ((Number) val).doubleValue() : 0;
            allocations.add(amount);
            total += amount;
        }

        if (total <= 0 || allocations.isEmpty()) {
            return (String) sortedChoices.get(sortedChoices.size() - 1).get("id");
        }

        // Calculate coefficient of variation (lower = more balanced)
        double mean = total / allocations.size();
        double variance = 0;
        for (double a : allocations) {
            variance += (a - mean) * (a - mean);
        }
        double stdDev = Math.sqrt(variance / allocations.size());
        double cv = stdDev / mean; // 0 = perfectly balanced, higher = more concentrated

        // Also check: does the largest allocation exceed 50% of total?
        double maxAllocation = allocations.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        double maxPct = maxAllocation / total;

        // Scoring:
        // Quality 3 (best): CV < 0.3 and no single dept > 40%  → balanced
        // Quality 2 (mid):  CV < 0.5 or no single dept > 55%   → moderately balanced
        // Quality 1 (worst): everything else                     → concentrated
        String choiceId;
        if (cv < 0.3 && maxPct <= 0.40) {
            choiceId = (String) sortedChoices.get(0).get("id"); // quality 3
        } else if (cv < 0.5 || maxPct <= 0.55) {
            choiceId = sortedChoices.size() >= 2
                ? (String) sortedChoices.get(1).get("id")   // quality 2
                : (String) sortedChoices.get(0).get("id");
        } else {
            choiceId = (String) sortedChoices.get(sortedChoices.size() - 1).get("id"); // quality 1
        }

        logger.info("INVESTMENT_PORTFOLIO scoring: total={}, cv={:.2f}, maxPct={:.1f}%, choiceId={} (quality={})",
                total, cv, maxPct * 100, choiceId,
                choices.stream().filter(c -> choiceId.equals(c.get("id"))).findFirst()
                    .map(c -> c.get("quality")).orElse("?"));
        return choiceId;
    }

    /**
     * Resolve STAKEHOLDER_MEETING choice based on selected stakeholder priorities.
     * Uses mentor guidance stakeholderHints priorities (high/medium/low) or positional
     * ordering to score the selection deterministically, bypassing fragile AI-generated mappings.
     */
    @SuppressWarnings("unchecked")
    private String resolveStakeholderMeetingChoice(
            Map<String, Object> node, Map<String, Object> config, Map<String, Object> input) {

        List<Map<String, Object>> choices = (List<Map<String, Object>>) node.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new IllegalStateException("STAKEHOLDER_MEETING has no choices");
        }

        // Sort choices by quality descending
        List<Map<String, Object>> sortedChoices = new ArrayList<>(choices);
        sortedChoices.sort((a, b) -> {
            int qa = a.get("quality") != null ? ((Number) a.get("quality")).intValue() : 1;
            int qb = b.get("quality") != null ? ((Number) b.get("quality")).intValue() : 1;
            return qb - qa;
        });

        // Get selected stakeholders from input
        List<String> selectedIds;
        Object selectedObj = input.get("selectedStakeholders");
        if (selectedObj instanceof List<?>) {
            selectedIds = ((List<?>) selectedObj).stream()
                .map(Object::toString).toList();
        } else {
            // Fallback: no selection → worst choice
            logger.warn("STAKEHOLDER_MEETING: no selectedStakeholders in input");
            return (String) sortedChoices.get(sortedChoices.size() - 1).get("id");
        }

        // Build priority map: stakeholder ID → priority score (high=3, medium=2, low=1)
        Map<String, Integer> priorityScores = new HashMap<>();
        List<Map<String, Object>> stakeholders = (List<Map<String, Object>>) config.get("stakeholders");

        // Try mentor guidance hints first
        Map<String, Object> mentorGuidance = (Map<String, Object>) node.get("mentorGuidance");
        Map<String, Object> stakeholderHints = null;
        if (mentorGuidance != null) {
            stakeholderHints = (Map<String, Object>) mentorGuidance.get("stakeholderHints");
        }

        if (stakeholderHints != null && !stakeholderHints.isEmpty()) {
            // Use hint priorities
            for (Map.Entry<String, Object> entry : stakeholderHints.entrySet()) {
                Map<String, Object> hint = (Map<String, Object>) entry.getValue();
                if (hint != null) {
                    String priority = (String) hint.get("priority");
                    int score = "high".equals(priority) ? 3 : "medium".equals(priority) ? 2 : 1;
                    priorityScores.put(entry.getKey(), score);
                }
            }
        } else if (stakeholders != null) {
            // Positional fallback: first stakeholders = best (same as autoGenerateMappings)
            for (int i = 0; i < stakeholders.size(); i++) {
                String sid = (String) stakeholders.get(i).get("id");
                priorityScores.put(sid, stakeholders.size() - i); // higher index = lower priority
            }
        }

        // Score the selection: sum of priority scores for selected stakeholders
        int totalScore = 0;
        int maxPossible = 0;
        List<Integer> allScores = new ArrayList<>(priorityScores.values());
        allScores.sort(Collections.reverseOrder());
        int maxSelections = config.get("maxSelections") != null
                ? ((Number) config.get("maxSelections")).intValue() : 2;
        for (int i = 0; i < Math.min(maxSelections, allScores.size()); i++) {
            maxPossible += allScores.get(i);
        }
        for (String sid : selectedIds) {
            totalScore += priorityScores.getOrDefault(sid, 1);
        }

        // Map score to quality:
        // >= 80% of max possible → quality 3 (best)
        // >= 50% of max possible → quality 2 (mid)
        // < 50% → quality 1 (worst)
        double scorePct = maxPossible > 0 ? (double) totalScore / maxPossible : 0;
        String choiceId;
        if (scorePct >= 0.80) {
            choiceId = (String) sortedChoices.get(0).get("id"); // quality 3
        } else if (scorePct >= 0.50) {
            choiceId = sortedChoices.size() >= 2
                ? (String) sortedChoices.get(1).get("id")   // quality 2
                : (String) sortedChoices.get(0).get("id");
        } else {
            choiceId = (String) sortedChoices.get(sortedChoices.size() - 1).get("id"); // quality 1
        }

        logger.info("STAKEHOLDER_MEETING scoring: selected={}, totalScore={}, maxPossible={}, scorePct={}%, choiceId={}",
                selectedIds, totalScore, maxPossible, String.format("%.1f", scorePct * 100), choiceId);
        return choiceId;
    }

    /**
     * Resolve HIRE_FIRE choice based on candidate fit from mentor guidance.
     * Uses candidateHints fit ratings (strong/moderate/weak) to score deterministically,
     * bypassing fragile AI-generated mapping conditions.
     */
    @SuppressWarnings("unchecked")
    private String resolveHireFireChoice(
            Map<String, Object> node, Map<String, Object> config, Map<String, Object> input) {

        List<Map<String, Object>> choices = (List<Map<String, Object>>) node.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new IllegalStateException("HIRE_FIRE has no choices");
        }

        // Sort choices by quality descending
        List<Map<String, Object>> sortedChoices = new ArrayList<>(choices);
        sortedChoices.sort((a, b) -> {
            int qa = a.get("quality") != null ? ((Number) a.get("quality")).intValue() : 1;
            int qb = b.get("quality") != null ? ((Number) b.get("quality")).intValue() : 1;
            return qb - qa;
        });

        // Get selected candidate from input
        Object selectedObj = input.get("selected");
        if (selectedObj == null) {
            logger.warn("HIRE_FIRE: no selected candidate in input");
            return (String) sortedChoices.get(sortedChoices.size() - 1).get("id");
        }
        String selectedId = selectedObj.toString();

        // Build fit map: candidate ID → fit score (strong=3, moderate=2, weak=1)
        Map<String, Integer> fitScores = new HashMap<>();
        List<Map<String, Object>> candidates = (List<Map<String, Object>>) config.get("candidates");

        // Try mentor guidance hints first
        Map<String, Object> mentorGuidance = (Map<String, Object>) node.get("mentorGuidance");
        Map<String, Object> candidateHints = null;
        if (mentorGuidance != null) {
            candidateHints = (Map<String, Object>) mentorGuidance.get("candidateHints");
        }

        if (candidateHints != null && !candidateHints.isEmpty()) {
            for (Map.Entry<String, Object> entry : candidateHints.entrySet()) {
                Map<String, Object> hint = (Map<String, Object>) entry.getValue();
                if (hint != null) {
                    String fit = (String) hint.get("fit");
                    int score = "strong".equals(fit) ? 3 : "moderate".equals(fit) ? 2 : 1;
                    fitScores.put(entry.getKey(), score);
                }
            }
        } else if (candidates != null) {
            // Positional fallback: first candidate = best
            for (int i = 0; i < candidates.size(); i++) {
                String cid = (String) candidates.get(i).get("id");
                fitScores.put(cid, candidates.size() - i);
            }
        }

        // Score: selected candidate's fit as percentage of max → maps to quality
        int fitScore = fitScores.getOrDefault(selectedId, 1);
        int maxFit = fitScores.values().stream().mapToInt(Integer::intValue).max().orElse(3);
        double fitPct = maxFit > 0 ? (double) fitScore / maxFit : 0;

        String choiceId;
        if (fitPct >= 0.80) {
            choiceId = (String) sortedChoices.get(0).get("id"); // quality 3 (best)
        } else if (fitPct >= 0.50) {
            choiceId = sortedChoices.size() >= 2
                ? (String) sortedChoices.get(1).get("id")   // quality 2
                : (String) sortedChoices.get(0).get("id");
        } else {
            choiceId = (String) sortedChoices.get(sortedChoices.size() - 1).get("id"); // quality 1
        }

        logger.info("HIRE_FIRE scoring: selected={}, fitScore={}, maxFit={}, fitPct={}%, choiceId={}",
                selectedId, fitScore, maxFit, String.format("%.1f", fitPct * 100), choiceId);
        return choiceId;
    }
}
