package io.threecolor;

import static org.junit.jupiter.api.Assertions.*;

import io.threecolor.deduction.*;
import io.threecolor.difficulty.DifficultyBand;
import io.threecolor.generation.*;
import io.threecolor.model.*;
import io.threecolor.solve.*;
import io.threecolor.validation.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class DeletionGeneratorTest {
  @Test
  void refutationCanSolveWhatRelationalClosureCannotWithoutClaimingSustainedHard() {
    // A one-round near miss from the fixed pilot: retain it as a classifier regression,
    // not as a puzzle allowed through the sustained-Hard generation gate.
    int[][] pairs = {
      {0, 4}, {0, 10}, {0, 11}, {1, 6}, {1, 8}, {1, 15}, {1, 20}, {2, 5}, {2, 9}, {2, 10}, {3, 13},
      {3, 17}, {3, 18}, {4, 11}, {4, 19}, {5, 9}, {5, 10}, {5, 14}, {5, 17}, {6, 13}, {6, 15},
      {6, 16}, {6, 23}, {7, 10}, {7, 11}, {7, 18}, {7, 19}, {8, 16}, {8, 20}, {9, 14}, {9, 21},
      {10, 17}, {11, 19}, {12, 19}, {12, 23}, {13, 15}, {13, 23}, {15, 21}, {16, 23}, {17, 21},
      {17, 22}, {18, 19}, {18, 22}
    };
    var graph = new GraphTopology(24, Arrays.stream(pairs).map(e -> new Edge(e[0], e[1])).toList());
    var clues =
        new PartialColoring(
            Map.of(
                new NodeId(0),
                Color.BLUE,
                new NodeId(12),
                Color.RED,
                new NodeId(16),
                Color.GREEN,
                new NodeId(20),
                Color.BLUE));
    var proof = new ProofLevels().classify(graph, clues, 20000);
    assertEquals(DifficultyBand.MEDIUM, proof.band());
    assertEquals(ProofLevels.Status.STALLED, proof.p1().status());
    assertEquals(ProofLevels.Status.SOLVED, proof.p2().status());
    assertEquals(1, proof.p2().refutationRounds());
    assertFalse(proof.sustainedHard());
    var exact = new ExactSolver().uniqueness(graph, clues, 100000, SolutionEquivalence.LABELED);
    assertEquals(UniquenessResult.UNIQUE, exact.status());
    assertEquals(
        exact.solutions().getFirst().colors().stream().map(Color::mask).toList(),
        proof.p2().domains());
    var reversed =
        new GraphTopology(
            24,
            graph.edges().stream()
                .map(e -> new Edge(23 - e.a().value(), 23 - e.b().value()))
                .toList());
    var mapped = new TreeMap<NodeId, Color>();
    clues.colors().forEach((n, c) -> mapped.put(new NodeId(23 - n.value()), c));
    var replay = new ProofLevels().classify(reversed, new PartialColoring(mapped), 20000);
    assertEquals(proof.band(), replay.band());
    assertEquals(proof.p2().refutationRounds(), replay.p2().refutationRounds());
    for (int v = 0; v < 24; v++)
      assertEquals(proof.p2().domains().get(v), replay.p2().domains().get(23 - v));
  }

  @Test
  void seededTriangulationsFillARectangularRegionAtTheExactSize() {
    for (int n : new int[] {6, 16, 24, 40})
      for (long seed = 0; seed < 5; seed++) {
        var t = PlaneTriangulation.create(n, seed);
        assertEquals(n, t.graph().nodeCount());
        int rows = (int) Math.round(Math.sqrt(n));
        int hullSize = 2 * rows + 2 * (n / rows) + (n % rows > 0 ? 1 : 0) - 4;
        assertEquals(3 * n - 3 - hullSize, t.graph().edges().size());
        if (n >= 16) {
          for (var edge : t.graph().edges()) {
            var a = t.layout().points().get(edge.a().value());
            var b = t.layout().points().get(edge.b().value());
            assertTrue(Math.hypot(a.x() - b.x(), a.y() - b.y()) < .9);
          }
        }
        assertEquals(t.graph().edges(), PlaneTriangulation.create(n, seed).graph().edges());
        assertEquals(t.layout(), PlaneTriangulation.create(n, seed).layout());
        var p =
            new Puzzle(
                t.graph(),
                PartialColoring.empty(),
                RuleSet.CLASSIC_V1,
                PuzzleProvenance.supplied(),
                t.layout());
        assertTrue(new PuzzleValidator().validate(p, true, false, false, 1).valid());
      }
    assertNotEquals(
        PlaneTriangulation.create(24, 0).layout(), PlaneTriangulation.create(24, 1).layout());
  }

  @Test
  void easyAndMediumUseTheSameSizeAndHaveIndependentFinalCertificates() {
    for (var band : List.of(DifficultyBand.VERY_EASY, DifficultyBand.EASY)) {
      var result = new DeletionGenerator().generate(band, 16, 0);
      assertTrue(result.success(), result.toString());
      var replay = new DeletionGenerator().generate(band, 16, 0);
      assertEquals(result.puzzle().logicalHash(), replay.puzzle().logicalHash());
      assertEquals(result.puzzle().layout(), replay.puzzle().layout());
      assertEquals(result.puzzle().provenance(), replay.puzzle().provenance());
      assertEquals(result.proof(), replay.proof());
      assertEquals(result.stats(), replay.stats());
      var p = result.puzzle();
      assertEquals(16, p.topology().nodeCount());
      assertTrue(p.givens().colors().size() <= 16 / 3);
      assertTrue(new PuzzleValidator().validate(p, true, true, true, 100000).valid());
      assertEquals(band, new ProofLevels().classify(p.topology(), p.givens(), 20000).band());
      if (band == DifficultyBand.EASY) {
        assertEquals(ProofLevels.Status.STALLED, result.proof().p0().status());
        assertEquals(ProofLevels.Status.SOLVED, result.proof().p1().status());
      }
      assertEquals(
          DeductionTrace.Status.SOLVED,
          ProofLevels.explanationEngine().solve(p.topology(), p.givens()).status());
    }
  }

  @Test
  void exhaustionIsUnknownAndNeverEvidenceOfHigherDifficulty() {
    var graph = new GraphTopology(3, List.of(new Edge(0, 1), new Edge(1, 2), new Edge(0, 2)));
    var clues = new PartialColoring(Map.of(new NodeId(0), Color.RED, new NodeId(1), Color.GREEN));
    var result = new ProofLevels().classify(graph, clues, 1);
    assertNull(result.band());
    assertEquals(ProofLevels.Status.UNKNOWN, result.p0().status());
    var budget = new DeletionGenerator.Budget(1, 1, 1, 1, 1, 1);
    var failed = new DeletionGenerator().generate(DifficultyBand.HARD, 16, 0, budget);
    assertFalse(failed.success());
    assertEquals("SEARCH_BUDGET_EXHAUSTED", failed.failure());
    assertTrue(failed.stats().exactChecks() <= 1);
    assertTrue(failed.stats().exactNodes() <= 1);
    assertTrue(failed.stats().proofStates() <= 1);
  }

  @Test
  void closuresAreSoundAndInvariantUnderVertexAndColourRenaming() {
    var solver = new ProofLevels();
    for (long seed = 0; seed < 80; seed++) {
      var graph = SolverProperties.randomGraph(seed, 6);
      var clues = new PartialColoring(Map.of(new NodeId(0), Color.RED));
      var solutions = SolverProperties.brute(graph, clues);
      var renamed =
          new GraphTopology(
              6,
              graph.edges().stream()
                  .map(e -> new Edge(5 - e.a().value(), 5 - e.b().value()))
                  .toList());
      var renamedClues = new PartialColoring(Map.of(new NodeId(5), Color.GREEN));
      for (int level = 0; level <= 2; level++) {
        var a = solver.solve(graph, clues, level, 20000);
        var b = solver.solve(renamed, renamedClues, level, 20000);
        assertNotEquals(ProofLevels.Status.UNKNOWN, a.status());
        assertEquals(a.status(), b.status());
        for (var solution : solutions)
          for (int v = 0; v < 6; v++)
            assertNotEquals(0, a.domains().get(v) & solution.colors().get(v).mask());
        if (a.status() == ProofLevels.Status.CONTRADICTION) assertTrue(solutions.isEmpty());
        if (a.status() == ProofLevels.Status.SOLVED) assertEquals(1, solutions.size());
        if (a.status() != ProofLevels.Status.CONTRADICTION)
          for (int v = 0; v < 6; v++) {
            int mask = a.domains().get(v), rotated = ((mask << 1) & 7) | (mask >> 2);
            assertEquals(rotated, b.domains().get(5 - v));
          }
      }
    }
  }
}
