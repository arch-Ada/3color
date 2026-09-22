package io.threecolor.difficulty;

import java.util.*;

/**
 * Domain information is measured in bits. Relations are counted separately, never assigned fake
 * bits.
 */
public record DifficultyProfile(
    double totalInformation,
    double initialInformation,
    double completionFraction,
    int eventCount,
    List<Double> eventEfforts,
    double medianEffort,
    double p75Effort,
    double p90Effort,
    double maximumEffort,
    int substantialEvents,
    int hardEvents,
    int advancedEvents,
    int proofDepth,
    Map<String, Integer> ruleUsage,
    int ruleDiversity,
    double averageFrontierSize,
    int minimumFrontierSize,
    List<Double> easiestAvailableEfforts,
    double nontrivialProgressFraction,
    double longestTrivialProgressFraction,
    List<Integer> substantialEventsByRegion,
    int activeProgressRegions,
    double largestEventEffortShare,
    int hypothesisEvents,
    int hypothesesTried,
    int relationFacts,
    List<EventProgress> timeline,
    double initialCheapClosureProgressFraction,
    double remainingCheapCoreFraction,
    int nonCheapUnlocks,
    double substantialRootFraction,
    double largestCheapCascadeShare,
    List<Integer> cheapFrontierBreadths,
    double meanCheapFrontier,
    double p75CheapFrontier,
    double crowdedCheapFrontierFraction,
    int substantialCausalDepth,
    int maximumRuleTier) {
  public record EventProgress(
      int rootFactId,
      String ruleId,
      double progressStart,
      double progressEnd,
      double effort,
      double informationGain,
      int cascadeFacts,
      int relationFacts,
      double rootInformation,
      double cascadeInformation) {}

  public DifficultyProfile {
    cheapFrontierBreadths = List.copyOf(cheapFrontierBreadths);
    eventEfforts = List.copyOf(eventEfforts);
    ruleUsage = Collections.unmodifiableMap(new TreeMap<>(ruleUsage));
    easiestAvailableEfforts = List.copyOf(easiestAvailableEfforts);
    substantialEventsByRegion = List.copyOf(substantialEventsByRegion);
    timeline = List.copyOf(timeline);
  }
}
