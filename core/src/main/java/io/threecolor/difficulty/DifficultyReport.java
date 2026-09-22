package io.threecolor.difficulty;

import java.util.*;

public record DifficultyReport(
    String modelVersion,
    DifficultyBand band,
    double score,
    int totalDeductionCount,
    int hardestRuleTier,
    Map<String, Integer> ruleUsageCounts,
    int weightedRuleCost,
    int maximumProofDepth,
    int hypothesisSteps,
    double averageAvailableDeductions,
    int minimumAvailableDeductions,
    boolean humanSolved,
    DifficultyProfile profile) {
  public DifficultyReport {
    ruleUsageCounts = Collections.unmodifiableMap(new TreeMap<>(ruleUsageCounts));
  }
}
