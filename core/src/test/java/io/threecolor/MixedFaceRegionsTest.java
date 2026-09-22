package io.threecolor;

import static org.junit.jupiter.api.Assertions.*;

import io.threecolor.deduction.*;
import io.threecolor.generation.*;
import io.threecolor.model.*;
import io.threecolor.validation.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class MixedFaceRegionsTest {
  @Test
  void regionsKeepTheExactLayoutAndPlaneTopologyWithActualQuadrilateralFaces() {
    var fingerprints = new HashSet<String>();
    boolean largerFace = false;
    for (int n = 6; n <= 40; n++)
      for (long seed = 0; seed < 5; seed++) {
        var base = PlaneTriangulation.create(n, seed);
        var mixed = MixedFaceRegions.create(n, seed);
        var again = MixedFaceRegions.create(n, seed);
        assertEquals(n, mixed.graph().nodeCount());
        assertEquals(base.layout(), mixed.layout());
        assertEquals(mixed.graph().edges(), again.graph().edges());
        assertTrue(base.graph().edges().containsAll(mixed.graph().edges()));
        var puzzle =
            new Puzzle(
                mixed.graph(),
                PartialColoring.empty(),
                RuleSet.CLASSIC_V1,
                PuzzleProvenance.supplied(),
                mixed.layout());
        assertTrue(new PuzzleValidator().validate(puzzle, true, false, false, 1).valid());
        var faces = PlaneFaces.bounded(mixed.graph(), mixed.layout());
        assertEquals(mixed.graph().edges().size() - n + 1, faces.size());
        assertTrue(faces.stream().anyMatch(f -> f.vertices().size() == 4), n + "/" + seed);
        assertTrue(
            faces.stream()
                .allMatch(f -> new HashSet<>(f.vertices()).size() == f.vertices().size()));
        largerFace |= faces.stream().anyMatch(f -> f.vertices().size() > 4);
        if (n == 24) fingerprints.add(PuzzleRecords.edges(mixed.graph()));
      }
    assertTrue(largerFace);
    assertEquals(
        5, fingerprints.size()); // Not an isomorphism claim; independent pilot checks that.
  }

  @Test
  void faceWalksExcludeTheOuterFaceAndHandleBridges() {
    var square =
        new GraphTopology(
            5,
            List.of(
                new Edge(0, 1), new Edge(1, 2), new Edge(2, 3), new Edge(3, 0), new Edge(0, 4)));
    var layout =
        new PuzzleLayout(
            List.of(
                new PuzzleLayout.Point(0, 0),
                new PuzzleLayout.Point(1, 0),
                new PuzzleLayout.Point(1, 1),
                new PuzzleLayout.Point(0, 1),
                new PuzzleLayout.Point(-1, 0)));
    var faces = PlaneFaces.bounded(square, layout);
    assertEquals(1, faces.size());
    assertEquals(Set.of(0, 1, 2, 3), new HashSet<>(faces.getFirst().vertices()));
  }

  @Test
  void uniqueTwoChoiceInstancesFinishWithinOneRefutationRound() {
    var random = new Random(271828);
    int uniqueCount = 0;
    for (int sample = 0; sample < 300; sample++) {
      // Three fixed palette vertices encode arbitrary two-element lists on six vertices.
      var edges = new ArrayList<>(List.of(new Edge(0, 1), new Edge(1, 2), new Edge(0, 2)));
      int[] forbidden = new int[6];
      for (int v = 0; v < 6; v++) {
        forbidden[v] = random.nextInt(3);
        edges.add(new Edge(forbidden[v], v + 3));
      }
      for (int a = 3; a < 9; a++)
        for (int b = a + 1; b < 9; b++) if (random.nextDouble() < .45) edges.add(new Edge(a, b));
      var graph = new GraphTopology(9, edges);
      var givens =
          new PartialColoring(
              Map.of(
                  new NodeId(0), Color.RED, new NodeId(1), Color.GREEN, new NodeId(2), Color.BLUE));
      int completions = 0;
      // Exhaust all 64 list assignments independently of either production solver.
      for (int bits = 0; bits < 64; bits++) {
        var colors = new ArrayList<>(List.of(Color.RED, Color.GREEN, Color.BLUE));
        for (int v = 0; v < 6; v++)
          colors.add(Color.values()[(forbidden[v] + 1 + ((bits >> v) & 1)) % 3]);
        if (new Coloring(colors).satisfies(graph, givens)) completions++;
      }
      if (completions != 1) continue;
      uniqueCount++;
      var p1 = new ProofLevels().solve(graph, givens, 1, 20000);
      assertEquals(0, HardResidual.of(p1).triples());
      var p2 = new ProofLevels().solve(graph, givens, 2, 20000);
      assertEquals(ProofLevels.Status.SOLVED, p2.status());
      assertTrue(p2.refutationRounds() <= 1);
      if (p1.status() == ProofLevels.Status.STALLED) assertTrue(HardResidual.of(p1).binaryOnly());
    }
    assertTrue(
        uniqueCount > 10, "Insufficient independently unique test instances: " + uniqueCount);
  }

  @Test
  void unknownAndThreeColourStatesAreNeverExcludedByTheBinaryTheorem() {
    var unknown = new ProofLevels.Result(ProofLevels.Status.UNKNOWN, List.of(1, 3), 1, 0, 0, 0);
    assertFalse(HardResidual.of(unknown).binaryOnly());
    var ternary = new ProofLevels.Result(ProofLevels.Status.STALLED, List.of(1, 3, 7), 1, 0, 0, 0);
    assertEquals(1, HardResidual.of(ternary).triples());
    assertFalse(HardResidual.of(ternary).binaryOnly());
  }
}
