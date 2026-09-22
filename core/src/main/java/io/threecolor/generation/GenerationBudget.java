package io.threecolor.generation;

/** All limits count work, not elapsed time; local proposals include cache hits. */
public record GenerationBudget(
    long searchNodesPerCheck,
    long totalSearchNodes,
    int maximumCandidateEvaluations,
    int localMutationAttempts,
    int beamWidth) {
  public GenerationBudget(long perCheck, long total) {
    this(perCheck, total, 1200, 48, 4);
  }

  public GenerationBudget {
    if (searchNodesPerCheck < 1
        || totalSearchNodes < 1
        || maximumCandidateEvaluations < 1
        || localMutationAttempts < 0
        || beamWidth < 1)
      throw new IllegalArgumentException("Positive generation budgets required");
  }

  public static GenerationBudget defaults() {
    return new GenerationBudget(20000, 2000000, 1200, 48, 4);
  }
}
