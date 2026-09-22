package io.threecolor.difficulty;

public enum ProfileMetric {
  SUBSTANTIAL_CAUSAL_DEPTH,
  MAXIMUM_RULE_TIER,
  MEAN_CHEAP_FRONTIER,
  P75_CHEAP_FRONTIER,
  CROWDED_CHEAP_FRONTIER_FRACTION,
  CHEAP_CORE,
  UNLOCKS,
  SUBSTANTIAL_ROOT_FRACTION,
  CASCADE_SHARE,
  INFORMATION,
  EVENTS,
  SUBSTANTIAL_EVENTS,
  HARD_EVENTS,
  ADVANCED_EVENTS,
  ACTIVE_REGIONS,
  NONTRIVIAL_PROGRESS,
  TRIVIAL_STRETCH,
  LARGEST_EVENT_SHARE,
  PROOF_DEPTH,
  P75_EFFORT,
  FRONTIER_MEAN,
  RULE_DIVERSITY;

  public double value(DifficultyProfile p) {
    return switch (this) {
      case SUBSTANTIAL_CAUSAL_DEPTH -> p.substantialCausalDepth();
      case MAXIMUM_RULE_TIER -> p.maximumRuleTier();
      case MEAN_CHEAP_FRONTIER -> p.meanCheapFrontier();
      case P75_CHEAP_FRONTIER -> p.p75CheapFrontier();
      case CROWDED_CHEAP_FRONTIER_FRACTION -> p.crowdedCheapFrontierFraction();
      case CHEAP_CORE -> p.remainingCheapCoreFraction();
      case UNLOCKS -> p.nonCheapUnlocks();
      case SUBSTANTIAL_ROOT_FRACTION -> p.substantialRootFraction();
      case CASCADE_SHARE -> p.largestCheapCascadeShare();
      case INFORMATION -> p.totalInformation();
      case EVENTS -> p.eventCount();
      case SUBSTANTIAL_EVENTS -> p.substantialEvents();
      case HARD_EVENTS -> p.hardEvents();
      case ADVANCED_EVENTS -> p.advancedEvents();
      case ACTIVE_REGIONS -> p.activeProgressRegions();
      case NONTRIVIAL_PROGRESS -> p.nontrivialProgressFraction();
      case TRIVIAL_STRETCH -> p.longestTrivialProgressFraction();
      case LARGEST_EVENT_SHARE -> p.largestEventEffortShare();
      case PROOF_DEPTH -> p.proofDepth();
      case P75_EFFORT -> p.p75Effort();
      case FRONTIER_MEAN -> p.averageFrontierSize();
      case RULE_DIVERSITY -> p.ruleDiversity();
    };
  }
}
