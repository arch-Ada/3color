package io.threecolor.generation;

import io.threecolor.difficulty.*;
import java.util.*;

/** Research helper: independent work/caches, identical candidate object and RNG handoff. */
public final class ClueSearchComparison {
  public record Result(
      GenerationStrategy strategy,
      String topologyIdentity,
      boolean success,
      Integer clueCount,
      String clueKey,
      DifficultyReport difficulty,
      GenerationQuality quality) {}

  public List<Result> compare(
      GenerationSpec spec, int attempt, GenerationBudget budget, DifficultyTarget target) {
    return compare(spec, attempt, budget, target, TopologyStrategy.SPARSE_GRID);
  }

  public List<Result> compare(
      GenerationSpec spec,
      int attempt,
      GenerationBudget budget,
      DifficultyTarget target,
      TopologyStrategy generator) {
    var first = generator.prepare(spec, attempt);
    if (first.candidate() == null) throw new IllegalArgumentException("Topology attempt rejected");
    var results = new ArrayList<Result>();
    for (var strategy : GenerationStrategy.values()) {
      // Replay topology RNG consumption only; both verifiers receive the SAME candidate instance.
      var random =
          strategy == GenerationStrategy.RANDOM_BEAM
              ? first.clueRandom()
              : generator.prepare(spec, attempt).clueRandom();
      var verifier = new CandidateVerifier(first.candidate(), spec, budget, target, strategy);
      var accepted = strategy.search().search(verifier, random);
      var selected = accepted == null ? verifier.closest() : accepted;
      results.add(
          new Result(
              strategy,
              first.candidate().identity(),
              accepted != null,
              selected == null ? null : selected.clues().size(),
              selected == null ? null : selected.key(),
              selected == null ? null : selected.report(),
              verifier.quality(selected)));
    }
    return List.copyOf(results);
  }
}
