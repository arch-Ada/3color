package io.threecolor.deduction;

/** Deterministic work bounds; no wall-clock cutoffs enter traces. */
public record DeductionConfiguration(
    int maxTier,
    int hypothesisBudget,
    int maximumAtomicFacts,
    int maximumDecisionStates,
    int maximumImplicationLength,
    EffortModel effortModel) {
  public DeductionConfiguration {
    if (maxTier < 1
        || maxTier > 4
        || hypothesisBudget < 0
        || maximumAtomicFacts < 1
        || maximumDecisionStates < 1
        || maximumImplicationLength < 1)
      throw new IllegalArgumentException("Invalid deduction budget");
    java.util.Objects.requireNonNull(effortModel);
  }

  public static DeductionConfiguration defaults() {
    return new DeductionConfiguration(4, 192, 12000, 4000, 24, EffortModel.defaults());
  }

  public DeductionConfiguration withLevel(int tier, int hypotheses) {
    return new DeductionConfiguration(
        tier,
        hypotheses,
        maximumAtomicFacts,
        maximumDecisionStates,
        maximumImplicationLength,
        effortModel);
  }
}
