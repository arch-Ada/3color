package io.threecolor.generation;

import io.threecolor.deduction.ProofLevels;
import io.threecolor.difficulty.DifficultyBand;
import io.threecolor.model.*;
import io.threecolor.solve.*;
import java.util.*;

/** Greedy clue removal, preserving a bounded proof strategy rather than only uniqueness. */
public final class StrategyClues {
  public record Result(PartialColoring clues, int checks, int unknowns) {}

  public static Result remove(
      GraphTopology graph, Coloring target, long seed, DifficultyBand ceiling, int budget) {
    if (budget < 1 || ceiling == DifficultyBand.EXPERT) throw new IllegalArgumentException();
    if (target.colors().size() != graph.nodeCount())
      throw new IllegalArgumentException("Target size");
    for (var edge : graph.edges())
      if (target.colors().get(edge.a().value()) == target.colors().get(edge.b().value()))
        throw new IllegalArgumentException("Improper target");
    var clues = new TreeMap<NodeId, Color>();
    for (int v = 0; v < graph.nodeCount(); v++) clues.put(new NodeId(v), target.colors().get(v));
    var order = new ArrayList<>(clues.keySet());
    Collections.shuffle(order, new Random(seed));
    int checks = 0, unknowns = 0;
    for (var vertex : order) {
      if (Thread.currentThread().isInterrupted())
        throw new java.util.concurrent.CancellationException();
      var color = clues.remove(vertex);
      var candidate = new PartialColoring(clues);
      boolean accepted;
      checks++;
      if (ceiling == null) {
        var result =
            new ExactSolver().uniqueness(graph, candidate, budget, SolutionEquivalence.LABELED);
        accepted = result.status() == UniquenessResult.UNIQUE;
        if (result.status() == UniquenessResult.UNKNOWN) unknowns++;
      } else {
        int level =
            ceiling == DifficultyBand.VERY_EASY ? 0 : ceiling == DifficultyBand.EASY ? 1 : 2;
        var result = new ProofLevels().solve(graph, candidate, level, budget);
        accepted =
            result.status() == ProofLevels.Status.SOLVED
                && (ceiling != DifficultyBand.MEDIUM || result.refutationRounds() <= 1);
        if (result.status() == ProofLevels.Status.UNKNOWN) unknowns++;
      }
      if (!accepted) clues.put(vertex, color);
    }
    return new Result(new PartialColoring(clues), checks, unknowns);
  }
}
