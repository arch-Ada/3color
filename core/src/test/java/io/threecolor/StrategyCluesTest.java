package io.threecolor;

import static org.junit.jupiter.api.Assertions.*;

import io.threecolor.deduction.ProofLevels;
import io.threecolor.difficulty.DifficultyBand;
import io.threecolor.generation.StrategyClues;
import io.threecolor.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class StrategyCluesTest {
  @Test
  void removalsPreserveUniqueCompletionAndRequestedStrategyOnSmallGraphs() {
    for (int seed = 0; seed < 12; seed++) {
      var graph = SolverProperties.randomGraph(seed, 6);
      var solutions = SolverProperties.brute(graph, PartialColoring.empty());
      if (solutions.isEmpty()) continue;
      var target = solutions.getFirst();
      for (var band :
          List.of(
              DifficultyBand.VERY_EASY,
              DifficultyBand.EASY,
              DifficultyBand.MEDIUM,
              DifficultyBand.HARD)) {
        var result = StrategyClues.remove(graph, target, seed, band, 20000);
        assertEquals(
            List.of(target),
            solutions.stream().filter(c -> c.satisfies(graph, result.clues())).toList());
        int level = band == DifficultyBand.VERY_EASY ? 0 : band == DifficultyBand.EASY ? 1 : 2;
        var proof = new ProofLevels().solve(graph, result.clues(), level, 20000);
        assertEquals(ProofLevels.Status.SOLVED, proof.status());
        if (band == DifficultyBand.MEDIUM) assertTrue(proof.refutationRounds() <= 1);
        assertEquals(result, StrategyClues.remove(graph, target, seed, band, 20000));
      }
    }
  }

  @Test
  void invalidTargetsAreRejectedAndUnknownNeverAuthorizesRemoval() {
    var graph = new GraphTopology(3, List.of(new Edge(0, 1), new Edge(1, 2), new Edge(0, 2)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            StrategyClues.remove(
                graph,
                new Coloring(List.of(Color.RED, Color.RED, Color.BLUE)),
                0,
                DifficultyBand.EASY,
                10));
    var result =
        StrategyClues.remove(
            graph,
            new Coloring(List.of(Color.RED, Color.GREEN, Color.BLUE)),
            0,
            DifficultyBand.VERY_EASY,
            1);
    assertTrue(result.unknowns() > 0);
    assertEquals(3, result.clues().colors().size());
  }
}
