package io.threecolor;

import static org.junit.jupiter.api.Assertions.*;

import io.threecolor.generation.*;
import io.threecolor.model.*;
import io.threecolor.solve.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class BoundaryTilingTest {
  @Test
  void criticalTilesHaveAnExactExtensionWitnessForEveryInternalEdge() {
    for (long seed : List.of(23L, 94L, 95L, 103L)) {
      var tile = BoundaryCriticalTile.create(seed).orElseThrow();
      assertEquals(
          tile.graph().edges(), BoundaryCriticalTile.create(seed).orElseThrow().graph().edges());
      var g = tile.graph();
      for (int u = 0; u < 11; u++)
        for (int v : g.neighbors(u)) for (int w : g.neighbors(v)) assertFalse(g.adjacent(u, w));
      var allowed = BoundaryCriticalTile.feasible(g);
      assertTrue(allowed.cardinality() < 258);
      for (var e : g.edges()) {
        if (BoundaryCriticalTile.boundaryEdge(e)) continue;
        var edges = new ArrayList<>(g.edges());
        edges.remove(e);
        var smaller = new GraphTopology(11, edges);
        var newlyAllowed = BoundaryCriticalTile.feasible(smaller);
        newlyAllowed.andNot(allowed);
        assertFalse(newlyAllowed.isEmpty());
        int[] values = ThreePortTile.decode(newlyAllowed.nextSetBit(0));
        var clues = new TreeMap<NodeId, Color>();
        for (int v = 0; v < 8; v++) clues.put(new NodeId(v), Color.values()[values[v]]);
        assertEquals(
            UniquenessResult.UNSATISFIABLE,
            new ExactSolver()
                .uniqueness(g, new PartialColoring(clues), 100000, SolutionEquivalence.LABELED)
                .status());
        assertFalse(
            new ExactSolver()
                .solve(smaller, new PartialColoring(clues), 100000)
                .solutions()
                .isEmpty());
      }
    }
  }

  @Test
  void ambiguousInteriorsProduceValidTargetsWithoutPretendingTheyAreUnique() {
    var tile = BoundaryCriticalTile.create(23).orElseThrow();
    var row = tile.rows().stream().filter(r -> r.count() == 2).findFirst().orElseThrow();
    for (int rotation = 0; rotation < 4; rotation++) {
      var assembly =
          ThreePortTile.assemble(List.of(new ThreePortTile.Placement(tile, rotation)), 1, 1);
      var boundary = new ArrayList<Integer>(Collections.nCopies(8, 0));
      var local = ThreePortTile.decode(row.code());
      for (int v = 0; v < 8; v++) boundary.set(assembly.ports().getFirst().get(v), local[v]);
      var boundaryClues = new TreeMap<Integer, Integer>();
      for (int v = 0; v < 8; v++) boundaryClues.put(v, boundary.get(v));
      var solver = new TileConstraintSolver(assembly, false);
      assertEquals(2, solver.count(boundaryClues, 1000).count());
      for (int seed = 0; seed < 10; seed++) {
        var target = ProbeBoundaryTiling.complete(assembly, boundary, seed);
        assertTrue(target.satisfies(assembly.graph(), PartialColoring.empty()));
        assertEquals(target, ProbeBoundaryTiling.complete(assembly, boundary, seed));
        var all = new TreeMap<>(boundaryClues);
        for (int v = 8; v < 11; v++) all.put(v, target.colors().get(v).ordinal());
        assertTrue(solver.count(all, 1000).unique());
      }
    }
  }
}
