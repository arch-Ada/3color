package io.threecolor.generation;

import io.threecolor.difficulty.*;

/** Closest exactly certified, eligible candidate seen during a failed bounded search. */
public record GenerationDiagnostics(
    DifficultyReport closestDifficulty,
    DifficultyTarget.Match closestMatch,
    int candidateEvaluations,
    long exactSearchNodes,
    int removals,
    int restorations,
    int swaps,
    int localProposals,
    GenerationContext context) {
  public GenerationDiagnostics(
      DifficultyReport report,
      DifficultyTarget.Match match,
      int evaluations,
      long nodes,
      int removals,
      int restorations,
      int swaps) {
    this(report, match, evaluations, nodes, removals, restorations, swaps, 0, null);
  }
}
