package io.threecolor;

import static org.junit.jupiter.api.Assertions.*;

import io.threecolor.generation.*;
import io.threecolor.model.*;
import io.threecolor.solve.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class RegionCompositionTest {
  @Test
  void tableMatchesIndependentExhaustiveEnumeration() {
    var graph =
        new GraphTopology(
            5, List.of(new Edge(0, 1), new Edge(1, 2), new Edge(2, 0), new Edge(2, 3)));
    var table = RegionTable.build(graph, List.of(0, 3), PartialColoring.empty(), 10000, 1000);
    int[] counts = new int[9];
    for (int code = 0; code < 243; code++) {
      int rest = code;
      int[] colors = new int[5];
      for (int v = 0; v < 5; v++) {
        colors[v] = rest % 3;
        rest /= 3;
      }
      if (graph.edges().stream().allMatch(e -> colors[e.a().value()] != colors[e.b().value()]))
        counts[colors[0] + 3 * colors[3]]++;
    }
    for (int row = 0; row < 9; row++)
      assertEquals(Math.min(2, counts[row]), table.rows().get(row).completions());
  }

  @Test
  void compositionPreservesMultiplicityAndConflictingClues() {
    var edge = new GraphTopology(2, List.of(new Edge(0, 1)));
    var table = RegionTable.build(edge, List.of(0, 1), PartialColoring.empty(), 100, 100);
    var tables = List.of(table, table);
    var ids = List.of(List.of(0, 1), List.of(1, 2));
    assertEquals(2, RegionTable.count(tables, ids, 3, Map.of(0, 0, 2, 0)));
    assertEquals(1, RegionTable.count(tables, ids, 3, Map.of(0, 0, 2, 1)));
    assertEquals(0, RegionTable.count(tables, ids, 3, Map.of(0, 0, 1, 0)));
    var fixed =
        RegionTable.build(
            edge, List.of(0, 1), new PartialColoring(Map.of(new NodeId(0), Color.RED)), 100, 100);
    assertEquals(0, fixed.rows().get(1).completions());
  }

  @Test
  void unknownCannotMasqueradeAsExactTable() {
    assertThrows(
        IllegalStateException.class,
        () ->
            RegionTable.build(
                new GraphTopology(6, List.of()), List.of(0), PartialColoring.empty(), 1, 100));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            RegionTable.build(
                new GraphTopology(2, List.of()), List.of(0, 0), PartialColoring.empty(), 100, 100));
  }

  @Test
  void assembledCountsMatchWholeGraphSolverForAllOuterClues() {
    var a = RegionPatch.create(3);
    var b = RegionPatch.create(7);
    var assembly = RegionPatch.assemble(List.of(a, b), 2, 1);
    for (int code = 0; code < 81; code++) {
      int rest = code;
      var fixed = new TreeMap<Integer, Integer>();
      var clues = new TreeMap<NodeId, Color>();
      for (int v : new int[] {0, 2, 3, 5}) {
        int c = rest % 3;
        rest /= 3;
        fixed.put(v, c);
        clues.put(new NodeId(v), Color.values()[c]);
      }
      int count = RegionTable.count(assembly.tables(), assembly.junctions(), 6, fixed);
      var exact =
          new ExactSolver()
              .uniqueness(
                  assembly.graph(),
                  new PartialColoring(clues),
                  100000,
                  SolutionEquivalence.LABELED);
      assertEquals(
          switch (exact.status()) {
            case UNIQUE -> 1;
            case MULTIPLE -> 2;
            case UNSATISFIABLE -> 0;
            case UNKNOWN -> -1;
          },
          count);
    }
    assertEquals(a.graph().edges(), RegionPatch.create(3).graph().edges());
    assertEquals(a.table(), RegionPatch.create(3).table());
  }

  @Test
  void internalCluesAndGridGeometrySurviveAssembly() {
    var a = RegionPatch.create(12).withClues(new PartialColoring(Map.of(new NodeId(4), Color.RED)));
    var assembly = RegionPatch.assemble(List.of(a, a, a, a), 2, 2);
    assertEquals(33, assembly.graph().nodeCount());
    assertEquals(4, assembly.clues().colors().size());
    var puzzle =
        new Puzzle(
            assembly.graph(),
            assembly.clues(),
            RuleSet.CLASSIC_V1,
            new PuzzleProvenance("test", 0, 0, "test"),
            assembly.layout());
    assertTrue(
        new io.threecolor.validation.PuzzleValidator()
            .validate(puzzle, true, false, false, 1000)
            .valid());
    int count = RegionTable.count(assembly.tables(), assembly.junctions(), 9, Map.of());
    var exact =
        new ExactSolver()
            .uniqueness(assembly.graph(), assembly.clues(), 100000, SolutionEquivalence.LABELED);
    assertEquals(
        switch (exact.status()) {
          case UNIQUE -> 1;
          case MULTIPLE -> 2;
          case UNSATISFIABLE -> 0;
          case UNKNOWN -> -1;
        },
        count);
  }

  @Test
  void ambiguousInteriorIsNotLostAndProofUnknownIsRetained() {
    var table =
        RegionTable.build(
            new GraphTopology(3, List.of(new Edge(0, 1))),
            List.of(0),
            PartialColoring.empty(),
            100,
            1);
    assertEquals(2, RegionTable.count(List.of(table), List.of(List.of(0)), 1, Map.of(0, 0)));
    assertEquals(io.threecolor.deduction.ProofLevels.Status.UNKNOWN, table.rows().getFirst().p1());
  }

  @Test
  void compositionCanRequireMoreReasoningThanFullySpecifiedPieces() {
    var patches = List.of(190L, 73L, 12L, 87L).stream().map(RegionPatch::create).toList();
    var assembly = RegionPatch.assemble(patches, 2, 2);
    var clues = new PartialColoring(Map.of(new NodeId(2), Color.RED, new NodeId(5), Color.GREEN));
    assertEquals(
        1, RegionTable.count(assembly.tables(), assembly.junctions(), 9, Map.of(2, 0, 5, 1)));
    int[] rows = {25, 29, 29, 66};
    for (int i = 0; i < 4; i++) {
      assertEquals(1, patches.get(i).table().rows().get(rows[i]).completions());
      assertEquals(
          io.threecolor.deduction.ProofLevels.Status.SOLVED,
          patches.get(i).table().rows().get(rows[i]).p1());
    }
    var puzzle =
        new Puzzle(
            assembly.graph(),
            clues,
            RuleSet.CLASSIC_V1,
            new PuzzleProvenance("region-composition-v1", 91, 2, "regression"),
            assembly.layout());
    var certified = PuzzleBank.certify(io.threecolor.difficulty.DifficultyBand.MEDIUM, puzzle);
    assertEquals(1, certified.proof().p2().refutationRounds());
    assertEquals(
        io.threecolor.deduction.ProofLevels.Status.STALLED, certified.proof().p1().status());
  }
}
