package com.datagami.edudron.content.simulation.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
            // Fallback: treat as NARRATIVE_CHOICE if config is missing
            logger.warn("Decision config missing for type {}. Falling back to NARRATIVE_CHOICE.", decisionType);
            return validateChoiceId(node, choiceId);
        }

        List<Map<String, Object>> mappings = (List<Map<String, Object>>) config.get("mappings");
        if (mappings == null || mappings.isEmpty()) {
            // Fallback: treat as NARRATIVE_CHOICE if mappings are missing
            logger.warn("Mappings missing for type {}. Falling back to NARRATIVE_CHOICE.", decisionType);
            return validateChoiceId(node, choiceId);
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
        for (Map<String, Object> mapping : mappings) {
            String condition = (String) mapping.get("condition");
            String mappedChoiceId = (String) mapping.get("choiceId");

            if ("default".equals(condition)) {
                return validateChoiceId(node, mappedChoiceId);
            }

            if (evaluateCondition(condition, flatInput)) {
                return validateChoiceId(node, mappedChoiceId);
            }
        }

        // No match and no default — fallback to choiceId if provided (narrative-style)
        if (choiceId != null && !choiceId.isEmpty()) {
            logger.warn("No mapping matched for type {}. Falling back to direct choiceId.", decisionType);
            return validateChoiceId(node, choiceId);
        }

        // Last resort: pick the first choice
        List<Map<String, Object>> choices = (List<Map<String, Object>>) node.get("choices");
        if (choices != null && !choices.isEmpty()) {
            String firstChoiceId = (String) choices.get(0).get("id");
            logger.warn("No mapping matched and no choiceId provided for type {}. Defaulting to first choice.", decisionType);
            return firstChoiceId;
        }

        throw new IllegalStateException("No mapping matched and no choices available");
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
}
