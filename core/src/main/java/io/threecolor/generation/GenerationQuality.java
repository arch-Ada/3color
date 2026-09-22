package io.threecolor.generation;

import io.threecolor.difficulty.*;

/**
 * Counts include all proposed mutations; evaluations count cache misses with valid color anchors.
 */
public record GenerationQuality(
    DifficultyTarget.Match targetMatch,
    int candidateEvaluations,
    int removals,
    int restorations,
    int swaps,
    long exactSearchNodes,
    int localProposals,
    GenerationContext context) {
  public GenerationQuality(
      DifficultyTarget.Match match,
      int evaluations,
      int removals,
      int restorations,
      int swaps,
      long nodes) {
    this(match, evaluations, removals, restorations, swaps, nodes, 0, null);
  }
}
