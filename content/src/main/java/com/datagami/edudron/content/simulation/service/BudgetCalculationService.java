package com.datagami.edudron.content.simulation.service;

import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
public class BudgetCalculationService {

    @SuppressWarnings("unchecked")
    public Map<String, Object> calculateYearEndReturns(
            Map<String, BigDecimal> allocations,
            Map<String, Object> financialModel,
            String performanceBand,
            List<Map<String, Object>> budgetHistory,
            String playId,
            int currentYear) {

        List<Map<String, Object>> departments =
            (List<Map<String, Object>>) financialModel.get("departments");
        Map<String, Object> multipliers =
            (Map<String, Object>) financialModel.get("performanceMultipliers");

        double perfMultiplier = ((Number) multipliers.getOrDefault(
            performanceBand, 1.0)).doubleValue();

        Random random = new Random((playId + "_" + currentYear).hashCode());

        // Fallback: if allocation keys don't match financialModel department IDs,
        // map by position (handles legacy simulations with mismatched dept IDs)
        Map<String, BigDecimal> resolvedAllocations = allocations;
        boolean hasDirectMatch = departments.stream()
            .anyMatch(d -> allocations.containsKey(d.get("id")));
        if (!hasDirectMatch && !allocations.isEmpty()) {
            resolvedAllocations = new LinkedHashMap<>();
            List<BigDecimal> allocationValues = new ArrayList<>(allocations.values());
            for (int i = 0; i < departments.size() && i < allocationValues.size(); i++) {
                String deptId = (String) departments.get(i).get("id");
                resolvedAllocations.put(deptId, allocationValues.get(i));
            }
        }

        Map<String, Object> returns = new LinkedHashMap<>();
        BigDecimal totalReturns = BigDecimal.ZERO;
        BigDecimal totalInvested = BigDecimal.ZERO;

        for (Map<String, Object> dept : departments) {
            String deptId = (String) dept.get("id");
            double baseRoi = ((Number) dept.get("baseRoi")).doubleValue();
            double volatility = ((Number) dept.get("volatility")).doubleValue();
            int lagYears = ((Number) dept.get("lagYears")).intValue();

            BigDecimal allocation = BigDecimal.ZERO;
            int sourceYear = currentYear - lagYears;

            if (lagYears == 0) {
                allocation = resolvedAllocations.getOrDefault(deptId, BigDecimal.ZERO);
            } else if (sourceYear >= 1 && budgetHistory != null) {
                for (Map<String, Object> hist : budgetHistory) {
                    if (((Number) hist.get("year")).intValue() == sourceYear) {
                        Map<String, Object> pastAllocs =
                            (Map<String, Object>) hist.get("allocations");
                        if (pastAllocs != null) {
                            if (pastAllocs.containsKey(deptId)) {
                                allocation = new BigDecimal(pastAllocs.get(deptId).toString());
                            } else {
                                // Positional fallback for past allocations too
                                List<Object> pastValues = new ArrayList<>(pastAllocs.values());
                                int deptIdx = departments.indexOf(dept);
                                if (deptIdx >= 0 && deptIdx < pastValues.size()) {
                                    allocation = new BigDecimal(pastValues.get(deptIdx).toString());
                                }
                            }
                        }
                        break;
                    }
                }
            }

            if (allocation.compareTo(BigDecimal.ZERO) <= 0) {
                Map<String, Object> deptResult = new LinkedHashMap<>();
                deptResult.put("invested", 0);
                deptResult.put("return", null);
                deptResult.put("roi", null);
                deptResult.put("note", lagYears > 0 && sourceYear < 1
                    ? "Returns in Year " + (1 + lagYears)
                    : "No allocation");
                returns.put(deptId, deptResult);
                continue;
            }

            double volatilityFactor = 1.0 + (random.nextDouble() * 2 - 1) * volatility;
            double actualRoi = baseRoi * perfMultiplier * volatilityFactor;
            BigDecimal returnAmount = allocation.multiply(
                BigDecimal.ONE.add(BigDecimal.valueOf(actualRoi)));

            totalReturns = totalReturns.add(returnAmount);
            totalInvested = totalInvested.add(allocation);

            Map<String, Object> deptResult = new LinkedHashMap<>();
            deptResult.put("invested", allocation);
            deptResult.put("return", returnAmount.setScale(0, RoundingMode.HALF_UP));
            deptResult.put("roi", String.format("%+.1f%%", actualRoi * 100));
            deptResult.put("note", null);
            returns.put(deptId, deptResult);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("departments", returns);
        result.put("totalInvested", totalInvested);
        result.put("totalReturns", totalReturns.setScale(0, RoundingMode.HALF_UP));
        result.put("endingBudget", totalReturns.setScale(0, RoundingMode.HALF_UP));
        return result;
    }

    public BigDecimal getYearStartBudget(
            Map<String, Object> financialModel,
            List<Map<String, Object>> budgetHistory) {
        if (budgetHistory == null || budgetHistory.isEmpty()) {
            return new BigDecimal(financialModel.get("startingBudget").toString());
        }
        Map<String, Object> lastYear = budgetHistory.get(budgetHistory.size() - 1);
        return new BigDecimal(lastYear.get("endingBudget").toString());
    }
}
