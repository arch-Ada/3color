package io.threecolor;

import static org.junit.jupiter.api.Assertions.*;

import io.threecolor.deduction.*;
import io.threecolor.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class HintCorrectionTest {
  private Puzzle puzzle() {
    return new Puzzle(
        new GraphTopology(
            4,
            List.of(
                new Edge(0, 1), new Edge(0, 2), new Edge(1, 2), new Edge(1, 3), new Edge(2, 3))),
        new PartialColoring(Map.of(new NodeId(0), Color.RED, new NodeId(1), Color.GREEN)),
        RuleSet.CLASSIC_V1,
        new PuzzleProvenance("triangulation-deletion-v6", 0, 0, "test"),
        new PuzzleLayout(
            List.of(
                new PuzzleLayout.Point(0, 0),
                new PuzzleLayout.Point(1, 0),
                new PuzzleLayout.Point(.5, .5),
                new PuzzleLayout.Point(1, 1))));
  }

  @Test
  void correctsWrongEntryBeforeAnObviousForwardMoveWithoutUsingPlayerPremises() {
    var p = puzzle();
    var current = new PartialColoring(Map.of(new NodeId(3), Color.BLUE));
    for (var level : Hint.Level.values()) {
      var h = new HintService().hint(p, current, level);
      assertEquals(Hint.Status.CORRECTION, h.status());
      assertEquals(3, h.deduction().orElseThrow().node());
      assertEquals(Color.RED.mask(), h.deduction().orElseThrow().afterMask());
      assertFalse(h.explanation().walkthrough().isEmpty());
      for (var d : h.supportingSteps())
        if (d.tier() == 0 && Integer.bitCount(d.afterMask()) == 1)
          assertEquals(p.givens().colors().get(new NodeId(d.node())).mask(), d.afterMask());
    }
    assertEquals(Color.BLUE, current.colors().get(new NodeId(3)));
  }

  @Test
  void highlightsEdgeConflictWithoutClaimingWhichEntryIsWrong() {
    var h =
        new HintService()
            .hint(
                puzzle(),
                new PartialColoring(Map.of(new NodeId(2), Color.GREEN)),
                Hint.Level.NUDGE);
    assertEquals(Hint.Status.CONFLICT, h.status());
    assertTrue(h.deduction().isEmpty());
    assertEquals(List.of(1, 2), h.explanation().primaryTargets());
    assertEquals(List.of(new HintExplanation.FocusEdge(1, 2)), h.explanation().focusEdges());
  }

  @Test
  void correctEntriesStillAllowCurrentBoardHints() {
    var h =
        new HintService()
            .hint(
                puzzle(), new PartialColoring(Map.of(new NodeId(3), Color.RED)), Hint.Level.REASON);
    assertEquals(Hint.Status.AVAILABLE, h.status());
    assertEquals(2, h.deduction().orElseThrow().node());
  }
}
