package io.threecolor.generation;

import io.threecolor.difficulty.DifficultyTarget;

/** Reproducibility context for the remaining sparse-grid generator. */
public record GenerationContext(
    String generatorVersion,
    GenerationStrategy strategy,
    GenerationSpec spec,
    GenerationBudget budget,
    DifficultyTarget target,
    int topologyAttempt,
    String topologyIdentity,
    TopologyStrategy topologyStrategy,
    String structuralHash) {
  public GenerationContext(
      String version,
      GenerationStrategy strategy,
      GenerationSpec spec,
      GenerationBudget budget,
      DifficultyTarget target,
      int attempt,
      String identity) {
    this(
        version,
        strategy,
        spec,
        budget,
        target,
        attempt,
        identity,
        TopologyStrategy.SPARSE_GRID,
        null);
  }
}
