package io.threecolor;

import static org.junit.jupiter.api.Assertions.*;

import io.threecolor.generation.*;
import io.threecolor.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class RecolouringProfileTest {
  @Test
  void boundsAndMandatoryCluesHoldForEveryDefiningSetOnSmallGraphs() {
    for (var edges :
        List.of(
            List.of(new Edge(0, 1), new Edge(1, 2), new Edge(2, 3), new Edge(3, 4)),
            List.of(new Edge(0, 1), new Edge(1, 2), new Edge(2, 3), new Edge(3, 4), new Edge(4, 0)),
            List.of(
                new Edge(0, 1), new Edge(1, 2), new Edge(0, 2), new Edge(2, 3), new Edge(3, 4)))) {
      var graph = new GraphTopology(5, edges);
      var solutions = new ArrayList<Coloring>();
      for (int code = 0; code < 243; code++) {
        int rest = code;
        var colors = new ArrayList<Color>();
        for (int v = 0; v < 5; v++) {
          colors.add(Color.values()[rest % 3]);
          rest /= 3;
        }
        var c = new Coloring(colors);
        if (c.satisfies(graph, PartialColoring.empty())) solutions.add(c);
      }
      for (var target : solutions) {
        var profile = RecolouringProfile.of(graph, target);
        var encoded = new LinkedHashSet<Long>();
        for (var component : profile.components()) {
          long bits = 0;
          for (int v : component) bits |= 1L << v;
          encoded.add(bits);
        }
        assertEquals(List.copyOf(encoded), DefiningSets.kempeComponents(graph, target));
        for (int mask = 0; mask < 32; mask++) {
          var clues = new HashSet<Integer>();
          for (int v = 0; v < 5; v++) if ((mask & (1 << v)) != 0) clues.add(v);
          long count =
              solutions.stream()
                  .filter(
                      c ->
                          clues.stream().allMatch(v -> c.colors().get(v) == target.colors().get(v)))
                  .count();
          if (count == 1) {
            assertTrue(clues.containsAll(profile.mandatory()));
            assertTrue(profile.coveredBy(clues));
            assertTrue(clues.size() >= profile.lowerBound());
          }
        }
      }
    }
  }

  @Test
  void verticesAboveMachineWordBoundaryRemainDistinct() {
    var edges = new ArrayList<Edge>();
    var colors = new ArrayList<Color>();
    for (int v = 0; v < 67; v++) {
      colors.add(Color.values()[v % 2]);
      if (v > 0) edges.add(new Edge(v - 1, v));
    }
    var profile = RecolouringProfile.of(new GraphTopology(67, edges), new Coloring(colors));
    assertEquals(67, profile.mandatory().size());
    assertEquals(67, profile.lowerBound());
    assertTrue(profile.components().contains(Set.of(66)));
    var clues = new HashSet<>(profile.mandatory());
    clues.remove(66);
    assertFalse(profile.coveredBy(clues));
    assertThrows(UnsupportedOperationException.class, () -> profile.mandatory().clear());
  }

  @Test
  void frozenDoesNotMeanUniqueAndWitnessDiversityIsDeterministic() {
    var edges = new ArrayList<Edge>();
    var colors = new ArrayList<Color>();
    for (int v = 0; v < 6; v++) {
      edges.add(new Edge(v, (v + 1) % 6));
      colors.add(Color.values()[v % 3]);
    }
    var graph = new GraphTopology(6, edges);
    var target = new Coloring(colors);
    assertTrue(RecolouringProfile.of(graph, target).mandatory().isEmpty());
    assertTrue(
        new Coloring(
                List.of(Color.RED, Color.GREEN, Color.RED, Color.GREEN, Color.RED, Color.GREEN))
            .satisfies(graph, PartialColoring.empty()));
    var tile = ThreePortTile.create(8);
    var assembly = ThreePortTile.assemble(List.of(new ThreePortTile.Placement(tile, 0)), 1, 1);
    var solver = new TileConstraintSolver(assembly, false);
    var orbits = new HashSet<String>();
    for (int seed = 0; seed < 30; seed++) {
      var witness = solver.diverseWitness(20000, seed);
      assertEquals(witness, solver.diverseWitness(20000, seed));
      assertFalse(witness.unique());
      if (!witness.witness().isEmpty())
        orbits.add(
            ProbeTargetSelection.orbit(
                ProbeBoundaryTiling.complete(assembly, witness.witness(), seed)));
    }
    assertTrue(orbits.size() > 1);
  }
}
