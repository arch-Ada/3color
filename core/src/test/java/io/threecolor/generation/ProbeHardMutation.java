package io.threecolor.generation;

import io.threecolor.deduction.ProofLevels;
import io.threecolor.difficulty.DifficultyBand;
import io.threecolor.model.*;
import io.threecolor.solve.*;
import java.util.*;

/** Graph-edit fixtures for deterministic mutation and validity tests. */
public final class ProbeHardMutation {
  public static final String VERSION = "hard-mutation-v1";

  public record State(
      Puzzle puzzle, Coloring target, ProofLevels.Classification proof, int score) {}

  public record Mutation(Puzzle puzzle, String operation) {}

  /** Heuristic only: reward wrong values not directly refuted, then three-choice residuals. */
  public static int score(ProofLevels.Classification proof) {
    if (proof.band() == DifficultyBand.HARD) return 1000000;
    if (proof.band() == DifficultyBand.MEDIUM) {
      int wrong = proof.p1().domains().stream().mapToInt(m -> Integer.bitCount(m) - 1).sum();
      int triples = (int) proof.p1().domains().stream().filter(m -> m == 7).count();
      // For a one-round Medium, these candidates disappear only in subsequent P1 propagation.
      int indirect = wrong - proof.p2().refutations();
      return 10000 + 100 * indirect + 10 * triples + wrong;
    }
    return proof.band() == DifficultyBand.EASY ? 100 : 0;
  }

  public static Mutation mutate(State parent, Random random, long seed, int step) {
    var p = parent.puzzle();
    var edges = new TreeSet<>(p.topology().edges());
    var clues = new TreeMap<>(p.givens().colors());
    int choice = random.nextInt(10), n = p.topology().nodeCount();
    String operation;
    if (choice < 3) {
      operation = "DELETE_EDGE";
      edges.remove(new ArrayList<>(edges).get(random.nextInt(edges.size())));
    } else if (choice < 6) {
      operation = choice == 3 ? "ADD_EDGE" : "EXCHANGE_EDGE";
      if (choice != 3) edges.remove(new ArrayList<>(edges).get(random.nextInt(edges.size())));
      var available = new ArrayList<Edge>();
      for (int a = 0; a < n; a++)
        for (int b = a + 1; b < n; b++)
          if (parent.target().colors().get(a) != parent.target().colors().get(b)
              && !edges.contains(new Edge(a, b))) available.add(new Edge(a, b));
      if (available.isEmpty()) return null;
      edges.add(available.get(random.nextInt(available.size())));
    } else {
      operation = choice == 6 ? "DELETE_CLUE" : choice == 7 ? "ADD_CLUE" : "EXCHANGE_CLUE";
      if (choice != 7) {
        if (clues.isEmpty()) return null;
        clues.remove(new ArrayList<>(clues.keySet()).get(random.nextInt(clues.size())));
      }
      if (choice != 6) {
        var available = new ArrayList<Integer>();
        for (int v = 0; v < n; v++) if (!clues.containsKey(new NodeId(v))) available.add(v);
        if (available.isEmpty()) return null;
        int v = available.get(random.nextInt(available.size()));
        clues.put(new NodeId(v), parent.target().colors().get(v));
      }
    }
    var puzzle =
        new Puzzle(
            new GraphTopology(n, edges),
            new PartialColoring(clues),
            p.rules(),
            new PuzzleProvenance(
                VERSION, seed, step, "parent=" + p.logicalHash() + ";operation=" + operation),
            p.layout());
    return new Mutation(puzzle, operation);
  }
}
