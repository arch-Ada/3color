package io.threecolor.deduction;

import io.threecolor.difficulty.DifficultyBand;
import io.threecolor.model.*;
import java.util.*;

/** Bounded proof-level closure. All deductions in a round use the same snapshot. */
public final class ProofLevels {
  public static final String VERSION = "proof-level-v2";

  /** Same rule vocabulary for player explanations; its chosen trace is not the tier certificate. */
  public static DeductionEngine explanationEngine() {
    return new DeductionEngine(List.of(new DiamondRule(), new LockedEdgeRule(), new ParityRule()));
  }

  /** Explicit explanation vocabulary; provenance never selects reasoning rules. */
  public static boolean usesPlayerRules(Puzzle puzzle) {
    return puzzle.explanationModel() == ExplanationModel.PLAYER_V1;
  }

  public static DeductionEngine explanationEngineFor(Puzzle puzzle) {
    return usesPlayerRules(puzzle) ? explanationEngine() : new DeductionEngine();
  }

  public enum Status {
    SOLVED,
    STALLED,
    CONTRADICTION,
    UNKNOWN
  }

  public record Result(
      Status status,
      List<Integer> domains,
      int states,
      int hypotheses,
      int refutations,
      int refutationRounds) {
    public Result {
      domains = List.copyOf(domains);
    }
  }

  public record Classification(DifficultyBand band, Result p0, Result p1, Result p2) {
    public boolean sustainedHard() {
      return band == DifficultyBand.HARD && p2.refutationRounds() >= 2;
    }

    public int states() {
      return p0.states() + (p1 == null ? 0 : p1.states()) + (p2 == null ? 0 : p2.states());
    }
  }

  private static final List<DeductionRule> LOCAL = List.of(new AdjacencyRule());
  private static final List<DeductionRule> RELATIONAL =
      List.of(
          new AdjacencyRule(),
          new RelationPropagationRule(),
          new DiamondRule(),
          new LockedEdgeRule(),
          new ParityRule());

  private static final class Work {
    int states, hypotheses, refutations, layers;
    final int limit;

    Work(int limit) {
      this.limit = limit;
    }

    boolean charge() {
      return !Thread.currentThread().isInterrupted() && states++ < limit;
    }
  }

  public record Closure(Status status, DeductionState state) {}

  public record Elimination(int node, int bit) {}

  public record Round(DeductionState before, List<Elimination> eliminations) {
    public Round {
      eliminations = List.copyOf(eliminations);
    }
  }

  public record Diagnostic(Result result, List<Round> rounds) {
    public Diagnostic {
      rounds = List.copyOf(rounds);
    }
  }

  /** Diagnostic access to exactly the same P1 closure used by tier certification. */
  public Closure closeRelational(DeductionState state, int budget) {
    if (budget < 1) throw new IllegalArgumentException("Positive budget required");
    return close(state, RELATIONAL, new Work(budget));
  }

  public Diagnostic diagnose(GraphTopology graph, PartialColoring clues, int budget) {
    var rounds = new ArrayList<Round>();
    return new Diagnostic(solve(graph, clues, 2, budget, rounds), rounds);
  }

  public Classification classify(GraphTopology graph, PartialColoring clues, int budgetPerLevel) {
    var p0 = solve(graph, clues, 0, budgetPerLevel);
    if (p0.status() == Status.SOLVED)
      return new Classification(DifficultyBand.VERY_EASY, p0, null, null);
    if (p0.status() != Status.STALLED) return new Classification(null, p0, null, null);
    var p1 = solve(graph, clues, 1, budgetPerLevel);
    if (p1.status() == Status.SOLVED) return new Classification(DifficultyBand.EASY, p0, p1, null);
    if (p1.status() != Status.STALLED) return new Classification(null, p0, p1, null);
    var p2 = solve(graph, clues, 2, budgetPerLevel);
    return new Classification(
        p2.status() == Status.SOLVED
            ? (p2.refutationRounds() >= 2 ? DifficultyBand.HARD : DifficultyBand.MEDIUM)
            : null,
        p0,
        p1,
        p2);
  }

  public Result solve(GraphTopology graph, PartialColoring clues, int level, int budget) {
    return solve(graph, clues, level, budget, null);
  }

  private Result solve(
      GraphTopology graph, PartialColoring clues, int level, int budget, List<Round> rounds) {
    if (level < 0 || level > 2 || budget < 1)
      throw new IllegalArgumentException("Invalid proof level/budget");
    clues.validateNodes(graph);
    var domains = new ArrayList<>(Collections.nCopies(graph.nodeCount(), 7));
    clues.colors().forEach((n, c) -> domains.set(n.value(), c.mask()));
    var state = new DeductionState(graph, domains);
    var work = new Work(budget);
    while (true) {
      var closure = close(state, level == 0 ? LOCAL : RELATIONAL, work);
      state = closure.state();
      if (closure.status() != Status.STALLED || level < 2)
        return result(closure.status(), state, work);
      var next = new ArrayList<>(state.domains());
      int removed = 0;
      var eliminations = rounds == null ? null : new ArrayList<Elimination>();
      // Complete the entire assumption round before committing any eliminations.
      for (int v = 0; v < graph.nodeCount(); v++) {
        if (Integer.bitCount(state.mask(v)) < 2) continue;
        for (int bit : new int[] {1, 2, 4})
          if ((state.mask(v) & bit) != 0) {
            work.hypotheses++;
            var branch = new ArrayList<>(state.domains());
            branch.set(v, bit);
            var proof =
                close(new DeductionState(graph, branch, state.relations()), RELATIONAL, work);
            if (proof.status() == Status.UNKNOWN) return result(Status.UNKNOWN, state, work);
            if (proof.status() == Status.CONTRADICTION) {
              next.set(v, next.get(v) & ~bit);
              removed++;
              if (eliminations != null) eliminations.add(new Elimination(v, bit));
            }
          }
      }
      if (removed == 0) return result(Status.STALLED, state, work);
      if (rounds != null) rounds.add(new Round(state, eliminations));
      work.refutations += removed;
      work.layers++;
      state = new DeductionState(graph, next, state.relations());
    }
  }

  private static Result result(Status status, DeductionState state, Work work) {
    return new Result(
        status,
        state.domains(),
        Math.min(work.states, work.limit),
        work.hypotheses,
        work.refutations,
        work.layers);
  }

  private static Closure close(DeductionState state, List<DeductionRule> rules, Work work) {
    while (true) {
      if (!work.charge()) return new Closure(Status.UNKNOWN, state);
      if (state.domains().contains(0)) return new Closure(Status.CONTRADICTION, state);
      var next = new ArrayList<>(state.domains());
      var relations = state.relations();
      boolean changed = false;
      for (var rule : rules)
        for (var application : rule.find(state)) {
          switch (application.conclusion()) {
            case LogicalConclusion.Contradiction ignored -> {
              return new Closure(Status.CONTRADICTION, state);
            }
            case LogicalConclusion.NarrowDomain d -> {
              int mask = next.get(d.node()) & d.mask();
              changed |= mask != next.get(d.node());
              next.set(d.node(), mask);
            }
            case LogicalConclusion.Equal e -> {
              if (!relations.equal(e.a(), e.b())) {
                relations = relations.with(e.a(), e.b(), true, relations.facts().size());
                changed = true;
              }
            }
            case LogicalConclusion.NotEqual e -> {
              if (!relations.notEqual(e.a(), e.b())) {
                relations = relations.with(e.a(), e.b(), false, relations.facts().size());
                changed = true;
              }
            }
          }
        }
      if (!changed)
        return new Closure(
            state.domains().stream().allMatch(m -> Integer.bitCount(m) == 1)
                ? Status.SOLVED
                : Status.STALLED,
            state);
      state = new DeductionState(state.graph(), next, relations);
    }
  }
}
