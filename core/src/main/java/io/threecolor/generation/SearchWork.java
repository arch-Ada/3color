package io.threecolor.generation;

final class SearchWork {
  final GenerationBudget budget;
  EvaluatedCandidate best;
  GenerationContext context, bestContext;
  long nodes;
  int evaluations, removals, restorations, swaps, localProposals;

  SearchWork(GenerationBudget budget) {
    this.budget = budget;
  }

  boolean exhausted() {
    return Thread.currentThread().isInterrupted()
        || nodes >= budget.totalSearchNodes()
        || evaluations >= budget.maximumCandidateEvaluations();
  }
}
