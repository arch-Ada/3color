package io.threecolor;

import static org.junit.jupiter.api.Assertions.*;

import io.threecolor.deduction.*;
import io.threecolor.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class ExplanationModelTest {
  @Test
  void modelSelectsRulesIndependentlyOfProvenance() {
    var graph = new GraphTopology(3, List.of(new Edge(0, 1), new Edge(0, 2), new Edge(1, 2)));
    var clues = new PartialColoring(Map.of(new NodeId(0), Color.RED, new NodeId(1), Color.GREEN));
    for (String generator : List.of("unrelated-new-generator", "triangulation-deletion-v99")) {
      var provenance = new PuzzleProvenance(generator, 0, 0, "");
      var player =
          new Puzzle(
              graph,
              clues,
              RuleSet.CLASSIC_V1,
              provenance,
              new PuzzleLayout(List.of()),
              ExplanationModel.PLAYER_V1);
      var basic =
          new Puzzle(
              graph,
              clues,
              RuleSet.CLASSIC_V1,
              provenance,
              player.layout(),
              ExplanationModel.BASIC_V1);
      assertTrue(ProofLevels.usesPlayerRules(player));
      assertFalse(ProofLevels.usesPlayerRules(basic));
      assertEquals(
          DeductionTrace.Status.SOLVED,
          ProofLevels.explanationEngineFor(player).solve(graph, clues).status());
      assertEquals(
          DeductionTrace.Status.SOLVED,
          ProofLevels.explanationEngineFor(basic).solve(graph, clues).status());
    }
  }
}
