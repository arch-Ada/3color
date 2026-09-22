package io.threecolor;

import static org.junit.jupiter.api.Assertions.*;

import io.threecolor.generation.DefiningSets;
import io.threecolor.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class DefiningSetsTest {
  @Test
  void kempeComponentsAreActualAlternativesAndEveryDefiningSetHitsThem() {
    for (int seed = 0; seed < 30; seed++) {
      var graph = SolverProperties.randomGraph(seed, 6);
      var solutions = SolverProperties.brute(graph, PartialColoring.empty());
      if (solutions.isEmpty()) continue;
      var target = solutions.getFirst();
      var components = DefiningSets.kempeComponents(graph, target);
      for (long component : components)
        assertTrue(
            solutions.stream().anyMatch(c -> DefiningSets.disagreement(target, c) == component));
      for (long clues = 0; clues < 64; clues++) {
        final long selected = clues;
        boolean unique =
            solutions.stream()
                    .filter(c -> (DefiningSets.disagreement(target, c) & selected) == 0)
                    .count()
                == 1;
        if (unique) for (long component : components) assertNotEquals(0, component & clues);
      }
    }
  }

  @Test
  void boundedMinimumCoverAgreesWithExhaustiveSubsets() {
    var random = new Random(318);
    var order = new ArrayList<>(List.of(0, 1, 2, 3, 4, 5));
    for (int trial = 0; trial < 150; trial++) {
      var constraints = new ArrayList<Long>();
      for (int i = 0; i < 10; i++) constraints.add((long) random.nextInt(64));
      int best = 7;
      for (long mask = 0; mask < 64; mask++) {
        final long selected = mask;
        if (constraints.stream().allMatch(c -> (c & selected) != 0))
          best = Math.min(best, Long.bitCount(mask));
      }
      Collections.shuffle(order, random);
      var result = DefiningSets.minimum(constraints, order, 6, 10000);
      if (best == 7) assertEquals(DefiningSets.Status.INFEASIBLE, result.status());
      else {
        assertEquals(DefiningSets.Status.FOUND, result.status());
        assertEquals(best, Long.bitCount(result.vertices()));
        assertTrue(constraints.stream().allMatch(c -> (c & result.vertices()) != 0));
        if (best > 0)
          assertEquals(
              DefiningSets.Status.INFEASIBLE,
              DefiningSets.minimum(constraints, order, best - 1, 10000).status());
      }
    }
    var unknown = DefiningSets.minimum(List.of(3L, 6L), order, 6, 1);
    assertEquals(DefiningSets.Status.UNKNOWN, unknown.status());
    assertEquals(1, unknown.nodes());
  }

  @Test
  void counterexamplesConvergeToAMinimumDefiningSetIncludingPaletteSymmetry() {
    for (int seed = 0; seed < 20; seed++) {
      var graph = SolverProperties.randomGraph(seed, 6);
      var solutions = SolverProperties.brute(graph, PartialColoring.empty());
      if (solutions.isEmpty()) continue;
      var target = solutions.getFirst();
      var constraints = new ArrayList<>(DefiningSets.kempeComponents(graph, target));
      long selected;
      while (true) {
        var cover = DefiningSets.minimum(constraints, List.of(0, 1, 2, 3, 4, 5), 6, 10000);
        assertEquals(DefiningSets.Status.FOUND, cover.status());
        selected = cover.vertices();
        final long clues = selected;
        var alternative =
            solutions.stream()
                .filter(
                    c -> !c.equals(target) && (DefiningSets.disagreement(target, c) & clues) == 0)
                .findFirst();
        if (alternative.isEmpty()) break;
        assertTrue(constraints.add(DefiningSets.disagreement(target, alternative.get())));
      }
      for (long mask = 0; mask < 64; mask++) {
        if (Long.bitCount(mask) >= Long.bitCount(selected)) continue;
        final long clues = mask;
        assertTrue(
            solutions.stream()
                .anyMatch(
                    c -> !c.equals(target) && (DefiningSets.disagreement(target, c) & clues) == 0));
      }
    }
  }

  @Test
  void crossingTheColourabilityBoundaryForcesTheDeletedEndpointsEqual() {
    var edges = new ArrayList<Edge>();
    for (int a = 0; a < 4; a++) for (int b = a + 1; b < 4; b++) edges.add(new Edge(a, b));
    assertTrue(
        SolverProperties.brute(new GraphTopology(4, edges), PartialColoring.empty()).isEmpty());
    edges.remove(new Edge(0, 1));
    var solutions = SolverProperties.brute(new GraphTopology(4, edges), PartialColoring.empty());
    assertFalse(solutions.isEmpty());
    for (var coloring : solutions) assertEquals(coloring.colors().get(0), coloring.colors().get(1));
  }
}
