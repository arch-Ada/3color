package io.threecolor;

import static org.junit.jupiter.api.Assertions.*;

import io.threecolor.deduction.*;
import io.threecolor.difficulty.*;
import io.threecolor.model.*;
import io.threecolor.solve.*;
import io.threecolor.validation.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class CoreTest {
  @Test
  void topologyEnforcesInvariantsAndOwnsItsData() {
    assertThrows(IllegalArgumentException.class, () -> new Edge(1, 1));
    assertThrows(
        IllegalArgumentException.class, () -> new GraphTopology(2, List.of(new Edge(0, 2))));
    assertThrows(
        IllegalArgumentException.class,
        () -> new GraphTopology(2, List.of(new Edge(0, 1), new Edge(1, 0))));
    var g = new GraphTopology(2, List.of(new Edge(1, 0)));
    g.neighbors(0)[0] = 99;
    assertEquals(1, g.neighbor(0, 0));
  }

  @Test
  void symmetryAndSearchBudgetAreExplicit() {
    var g = new GraphTopology(3, List.of(new Edge(0, 1), new Edge(1, 2), new Edge(0, 2)));
    var s = new ExactSolver();
    assertEquals(
        UniquenessResult.MULTIPLE,
        s.uniqueness(g, PartialColoring.empty(), 1000, SolutionEquivalence.LABELED).status());
    assertEquals(
        UniquenessResult.UNIQUE,
        s.uniqueness(g, PartialColoring.empty(), 1000, SolutionEquivalence.GLOBAL_COLOR_PERMUTATION)
            .status());
    assertEquals(
        UniquenessResult.UNKNOWN,
        s.uniqueness(g, PartialColoring.empty(), 1, SolutionEquivalence.LABELED).status());
    assertEquals(
        UniquenessResult.UNIQUE,
        s.uniqueness(
                g,
                new PartialColoring(Map.of(new NodeId(0), Color.RED, new NodeId(1), Color.GREEN)),
                1000,
                SolutionEquivalence.LABELED)
            .status());
  }

  @Test
  void oddCycleRuleHasStructuralWitnesses() {
    var g =
        new GraphTopology(
            5,
            List.of(
                new Edge(0, 1), new Edge(1, 2), new Edge(2, 3), new Edge(3, 4), new Edge(0, 4)));
    var result = new ParityRule().find(new DeductionState(g, List.of(7, 3, 3, 3, 3)));
    assertTrue(
        result.stream()
            .anyMatch(d -> d.node() == 0 && d.afterMask() == 4 && d.witnesses().size() == 4));
    // Exhaustively check the rule under the supplied domain assumptions.
    for (var c : SolverProperties.brute(g, PartialColoring.empty()))
      if (java.util.stream.IntStream.range(1, 5).allMatch(v -> c.colors().get(v) != Color.BLUE))
        assertEquals(Color.BLUE, c.colors().get(0));
  }

  @Test
  void lockedEdgeAndContradictionEvidence() {
    var triangle = new GraphTopology(3, List.of(new Edge(0, 1), new Edge(1, 2), new Edge(0, 2)));
    assertEquals(
        4,
        new LockedEdgeRule()
            .find(new DeductionState(triangle, List.of(3, 3, 7)))
            .getFirst()
            .afterMask());
    var k4 =
        new GraphTopology(
            4,
            List.of(
                new Edge(0, 1),
                new Edge(0, 2),
                new Edge(0, 3),
                new Edge(1, 2),
                new Edge(1, 3),
                new Edge(2, 3)));
    var trace = new DeductionEngine().solve(k4, PartialColoring.empty());
    assertEquals(DeductionTrace.Status.CONTRADICTION, trace.status());
    assertTrue(
        trace.steps().stream()
            .anyMatch(d -> d.conclusion() instanceof LogicalConclusion.Contradiction));
    // K4 now has a direct structural proof, so it must not be rated Expert by one spike.
    assertNotEquals(DifficultyBand.EXPERT, new DifficultyAnalyzer().analyze(trace).band());
    var bounded =
        new DeductionEngine(List.of(new LockedEdgeRule(), new ParityRule()))
            .solve(k4, PartialColoring.empty());
    assertTrue(
        bounded.steps().stream()
            .anyMatch(
                d ->
                    d.ruleId().equals("bounded_contradiction")
                        && !d.hypothesisEvidence().isEmpty()));
  }

  @Test
  void hashExcludesLayoutAndValidatorFindsCrossings() {
    var g = new GraphTopology(4, List.of(new Edge(0, 1), new Edge(2, 3)));
    var p =
        new Puzzle(
            g,
            PartialColoring.empty(),
            RuleSet.CLASSIC_V1,
            PuzzleProvenance.supplied(),
            new PuzzleLayout(
                List.of(
                    new PuzzleLayout.Point(0, 0),
                    new PuzzleLayout.Point(1, 1),
                    new PuzzleLayout.Point(0, 1),
                    new PuzzleLayout.Point(1, 0))));
    var other = new Puzzle(g, p.givens(), p.rules(), p.provenance(), new PuzzleLayout(List.of()));
    assertEquals(p.logicalHash(), other.logicalHash());
    assertEquals(64, p.logicalHash().length());
    var failures = new PuzzleValidator().validate(p, true, false, false, 100).failures();
    assertTrue(failures.stream().anyMatch(f -> f.code().equals("CROSSING_EDGES")));
    assertTrue(failures.stream().anyMatch(f -> f.code().equals("DISCONNECTED")));
  }
}
