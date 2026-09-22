package io.threecolor.generation;

import io.threecolor.difficulty.*;
import io.threecolor.model.*;
import io.threecolor.validation.*;
import java.util.*;

/** Tested sparse-grid baseline for algorithm comparisons; not used by player supply. */
public final class SparseGridGenerator {
  public static final String VERSION = "sparse-grid-v6";

  public GenerationOutcome generate(GenerationSpec spec) {
    return generate(spec, GenerationBudget.defaults());
  }

  public GenerationOutcome generate(GenerationSpec spec, GenerationBudget budget) {
    return generate(spec, budget, DifficultyTargets.defaults().get(spec.targetDifficulty()));
  }

  public GenerationOutcome generate(
      GenerationSpec spec, GenerationBudget budget, DifficultyTarget target) {
    return generate(spec, budget, target, GenerationStrategy.RANDOM_BEAM);
  }

  public GenerationOutcome generate(GenerationSpec spec, GenerationStrategy strategy) {
    return generate(
        spec,
        GenerationBudget.defaults(),
        DifficultyTargets.defaults().get(spec.targetDifficulty()),
        strategy);
  }

  public GenerationOutcome generate(
      GenerationSpec spec,
      GenerationBudget budget,
      DifficultyTarget target,
      GenerationStrategy strategy) {
    return generate(spec, budget, target, strategy, TopologyStrategy.SPARSE_GRID);
  }

  public GenerationOutcome generate(
      GenerationSpec spec, GenerationStrategy strategy, TopologyStrategy topology) {
    return generate(
        spec,
        GenerationBudget.defaults(),
        DifficultyTargets.defaults().get(spec.targetDifficulty()),
        strategy,
        topology);
  }

  public GenerationOutcome generate(
      GenerationSpec spec,
      GenerationBudget budget,
      DifficultyTarget target,
      GenerationStrategy strategy,
      TopologyStrategy topology) {
    if (spec.targetDifficulty() != DifficultyBand.EASY
        && spec.targetDifficulty() != DifficultyBand.VERY_EASY)
      throw new IllegalArgumentException(
          "Generation supports EASY and VERY_EASY only; Medium/Hard generators were removed");
    Objects.requireNonNull(strategy);
    Objects.requireNonNull(topology);
    if (target.band() != spec.targetDifficulty())
      throw new IllegalArgumentException("Target band mismatch");
    var rejections = new TreeMap<String, Integer>();
    var work = new SearchWork(budget);
    work.context =
        new GenerationContext(VERSION, strategy, spec, budget, target, 0, null, topology, null);
    int attemptLimit = spec.maxAttempts();
    for (int attempt = 0; attempt < attemptLimit; attempt++) {
      if (Thread.currentThread().isInterrupted())
        return failure("INTERRUPTED", attempt, rejections, work);
      if (work.exhausted()) return failure("SEARCH_BUDGET_EXHAUSTED", attempt, rejections, work);
      work.context =
          new GenerationContext(
              VERSION, strategy, spec, budget, target, attempt, null, topology, null);
      var prepared = topology.prepare(spec, attempt);
      var candidate = prepared.candidate();
      if (candidate == null) {
        rejections.merge("TOPOLOGY_TARGET", 1, Integer::sum);
        continue;
      }
      work.context =
          new GenerationContext(
              VERSION,
              strategy,
              spec,
              budget,
              target,
              attempt,
              candidate.identity(),
              topology,
              candidate.structuralHash());
      var verifier = new CandidateVerifier(candidate, spec, target, work);
      var best = strategy.search().search(verifier, prepared.clueRandom());
      if (Thread.currentThread().isInterrupted())
        return failure("INTERRUPTED", attempt, rejections, work);
      if (best == null) {
        rejections.merge("DIFFICULTY_PROFILE_OR_HUMAN_SOLVABILITY", 1, Integer::sum);
        continue;
      }
      var puzzle =
          new Puzzle(
              candidate.graph(),
              new PartialColoring(best.clues()),
              RuleSet.CLASSIC_V1,
              new PuzzleProvenance(
                  VERSION,
                  spec.seed(),
                  attempt,
                  spec
                      + ";budget="
                      + budget
                      + ";target="
                      + target
                      + ";strategy="
                      + strategy
                      + ";topologyStrategy="
                      + topology
                      + ";topology="
                      + candidate.identity()),
              candidate.layout(),
              ExplanationModel.BASIC_V1);
      if (!new PuzzleValidator().validate(puzzle, true, false, false, 1).valid()) {
        rejections.merge("LAYOUT", 1, Integer::sum);
        continue;
      }
      var generated =
          new GeneratedPuzzle(
              puzzle,
              candidate.solution(),
              spec,
              puzzle.logicalHash(),
              best.report(),
              best.trace(),
              GraphMetrics.of(candidate.graph()),
              verifier.quality(best));
      return generated;
    }
    return failure(
        work.exhausted() ? "SEARCH_BUDGET_EXHAUSTED" : "NO_MATCH_WITHIN_BUDGET",
        attemptLimit,
        rejections,
        work);
  }

  private static GenerationFailure failure(
      String code, int attempts, Map<String, Integer> rejections, SearchWork work) {
    var best = work.best;
    var context = work.bestContext == null ? work.context : work.bestContext;
    return new GenerationFailure(
        code,
        attempts,
        rejections,
        new GenerationDiagnostics(
            best == null ? null : best.report(),
            best == null ? null : best.match(),
            work.evaluations,
            work.nodes,
            work.removals,
            work.restorations,
            work.swaps,
            work.localProposals,
            context));
  }
}
