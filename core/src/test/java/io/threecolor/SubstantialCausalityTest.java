package io.threecolor;

import static org.junit.jupiter.api.Assertions.*;

import io.threecolor.deduction.*;
import io.threecolor.difficulty.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class SubstantialCausalityTest {
  static DeductionTrace trace(int[] tiers, int[][] parents) {
    var facts = new ArrayList<Deduction>();
    for (int i = 0; i < tiers.length; i++)
      facts.add(
          new Deduction(
              i,
              "test",
              tiers[i],
              i,
              7,
              1,
              Arrays.stream(parents[i]).boxed().toList(),
              List.of(),
              "test",
              Map.of(),
              List.of()));
    return new DeductionTrace(
        facts, Collections.nCopies(tiers.length, 1), DeductionTrace.Status.SOLVED, List.of(), 0);
  }

  static int depth(DeductionTrace t) {
    return new DifficultyAnalyzer().analyze(t).profile().substantialCausalDepth();
  }

  @Test
  void countsSubstantialRootsNotAtomicOrMechanicalDepth() {
    assertEquals(1, depth(trace(new int[] {2, 2}, new int[][] {{}, {}})));
    assertEquals(2, depth(trace(new int[] {2, 1, 2}, new int[][] {{}, {0}, {1}})));
    assertEquals(3, depth(trace(new int[] {2, 2, 2}, new int[][] {{}, {0}, {1}})));
    assertEquals(1, depth(trace(new int[] {2, 1, 1, 1, 1}, new int[][] {{}, {0}, {1}, {2}, {3}})));
    assertEquals(0, depth(trace(new int[] {1, 1}, new int[][] {{}, {0}})));
  }

  @Test
  void severalDirectFactsInOneEventCountOnce() {
    var t = trace(new int[] {2, 2, 1}, new int[][] {{}, {0}, {1}});
    var event =
        new DeductionEvent(
            0,
            "test",
            t.steps().getFirst().effort(),
            List.of(0, 1),
            List.of(2),
            List.of(),
            1,
            1,
            0,
            0);
    var grouped =
        new DeductionTrace(
            t.steps(), t.finalDomains(), t.status(), List.of(), 0, List.of(), List.of(event));
    assertEquals(1, depth(grouped));
    assertEquals(
        SubstantialCausality.inspect(grouped, 3), SubstantialCausality.inspect(grouped, 3));
  }

  @Test
  void easyRejectsChainsAndMediumRequiresThem() {
    var independent =
        new DifficultyAnalyzer().analyze(trace(new int[] {2, 2}, new int[][] {{}, {}})).profile();
    var chained =
        new DifficultyAnalyzer()
            .analyze(trace(new int[] {2, 1, 2}, new int[][] {{}, {0}, {1}}))
            .profile();
    assertEquals(
        DifficultyBand.MEDIUM, DifficultyTargets.classify(chained, DifficultyTargets.defaults()));
    var targets = DifficultyTargets.defaults();
    assertEquals(
        0,
        targets
            .get(DifficultyBand.EASY)
            .compare(independent)
            .penalties()
            .get(ProfileMetric.SUBSTANTIAL_CAUSAL_DEPTH));
    assertTrue(
        targets
                .get(DifficultyBand.EASY)
                .compare(chained)
                .penalties()
                .get(ProfileMetric.SUBSTANTIAL_CAUSAL_DEPTH)
            > 0);
    assertTrue(
        targets
                .get(DifficultyBand.MEDIUM)
                .compare(independent)
                .penalties()
                .get(ProfileMetric.SUBSTANTIAL_CAUSAL_DEPTH)
            > 0);
  }
}
