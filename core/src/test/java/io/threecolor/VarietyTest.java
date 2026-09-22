package io.threecolor;

import static org.junit.jupiter.api.Assertions.*;

import io.threecolor.difficulty.DifficultyBand;
import io.threecolor.generation.*;
import io.threecolor.model.*;
import io.threecolor.validation.PuzzleValidator;
import java.util.*;
import org.junit.jupiter.api.Test;

class VarietyTest {
  @Test
  void fingerprintsIgnoreVertexNamesButDistinguishDifferentLocalStructure() {
    var graph =
        new GraphTopology(
            6,
            List.of(
                new Edge(0, 1),
                new Edge(1, 2),
                new Edge(2, 0),
                new Edge(2, 3),
                new Edge(3, 4),
                new Edge(4, 5)));
    int[] perm = {4, 2, 0, 5, 3, 1};
    var renamed =
        new GraphTopology(
            6,
            graph.edges().stream()
                .map(e -> new Edge(perm[e.a().value()], perm[e.b().value()]))
                .toList());
    assertEquals(TopologyFingerprint.of(graph), TopologyFingerprint.of(renamed));
    var cycle =
        new GraphTopology(
            6,
            List.of(
                new Edge(0, 1),
                new Edge(1, 2),
                new Edge(2, 3),
                new Edge(3, 4),
                new Edge(4, 5),
                new Edge(5, 0)));
    assertNotEquals(TopologyFingerprint.of(graph), TopologyFingerprint.of(cycle));
  }

  @Test
  void variedSourcesAreReproduciblePlanarDrawingsWithDifferentStructures() {
    var keys = new HashSet<String>();
    for (int seed = 0; seed < 30; seed++) {
      var source = VariedPlaneGraphs.create(24, seed);
      assertEquals(source.graph().edges(), VariedPlaneGraphs.create(24, seed).graph().edges());
      var puzzle =
          new Puzzle(
              source.graph(),
              PartialColoring.empty(),
              RuleSet.CLASSIC_V1,
              PuzzleProvenance.supplied(),
              source.layout());
      assertTrue(new PuzzleValidator().validate(puzzle, true, false, false, 1).valid());
      keys.add(TopologyFingerprint.of(source.graph()));
    }
    assertTrue(keys.size() >= 25);
  }

  @Test
  void allSupportedSizesUseAlignedGridPositionsWithValidGeometry() {
    for (int n = 4; n <= 53; n++)
      for (int seed = 0; seed < 3; seed++) {
        var source = VariedPlaneGraphs.create(n, seed);
        assertAligned(source.layout());
        var puzzle =
            new Puzzle(
                source.graph(),
                PartialColoring.empty(),
                RuleSet.CLASSIC_V1,
                PuzzleProvenance.supplied(),
                source.layout());
        assertTrue(
            new PuzzleValidator().validate(puzzle, true, false, false, 1).valid(), n + ":" + seed);
      }
    for (var entry : PuzzleBank.bundled().entries())
      if (entry.band() != DifficultyBand.HARD) assertAligned(entry.puzzle().layout());
  }

  private static void assertAligned(PuzzleLayout layout) {
    for (boolean horizontal : new boolean[] {true, false}) {
      boolean aligned = false;
      for (int divisions = 1; divisions <= 64; divisions++) {
        final int steps = divisions;
        if (layout.points().stream()
            .allMatch(
                p -> {
                  double value = ((horizontal ? p.x() : p.y()) - .06) / .88 * steps;
                  return Math.abs(value - Math.rint(value)) < 1e-8;
                })) aligned = true;
      }
      assertTrue(aligned, "Positions must lie on aligned, evenly spaced grid lines");
    }
  }

  @Test
  void publishedLowerTiersHaveAtLeast64DifferentFingerprintsAtEachStandardSize() {
    var bank = PuzzleBank.bundled();
    for (var band : List.of(DifficultyBand.VERY_EASY, DifficultyBand.EASY, DifficultyBand.MEDIUM))
      for (int n : new int[] {16, 24, 32, 40}) {
        var keys =
            bank.entries().stream()
                .filter(e -> e.band() == band && e.puzzle().topology().nodeCount() == n)
                .map(e -> TopologyFingerprint.of(e.puzzle().topology()))
                .toList();
        assertTrue(keys.size() >= 64, band + ":" + n);
        assertEquals(keys.size(), new HashSet<>(keys).size(), "Duplicate topology fingerprint");
      }
  }

  @Test
  void bankExcludesSeenStructuresAndDoesNotResetHistoryWhenExhausted() {
    var bank = PuzzleBank.bundled();
    var excluded = new HashSet<String>();
    int count = 0;
    while (true) {
      var result = bank.select(DifficultyBand.MEDIUM, 24, 0, excluded);
      if (result.isEmpty()) break;
      assertTrue(excluded.add(TopologyFingerprint.of(result.get().puzzle().topology())));
      count++;
    }
    assertTrue(count >= 64);
    assertTrue(bank.select(DifficultyBand.MEDIUM, 24, 1, excluded).isEmpty());
    assertEquals(
        bank.select(DifficultyBand.MEDIUM, 24, 42), bank.select(DifficultyBand.MEDIUM, 24, 42));
  }
}
