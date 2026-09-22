package io.threecolor;

import static org.junit.jupiter.api.Assertions.*;

import io.threecolor.deduction.*;
import io.threecolor.difficulty.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class DifficultyProfileTest {
  static DeductionTrace synthetic(double... effort) {
    var steps = new ArrayList<Deduction>();
    var frontier = new ArrayList<FrontierSummary>();
    int n = effort.length;
    for (int i = 0; i < n; i++)
      steps.add(
          new Deduction(
              i,
              "domain_initialization",
              0,
              i,
              7,
              7,
              List.of(),
              List.of(),
              "domain_initialization",
              Map.of(),
              List.of()));
    for (int i = 0; i < n; i++) {
      int tier = effort[i] < 3 ? 1 : effort[i] < 5 ? 2 : 3;
      String rule =
          tier == 1
              ? "adjacent_color_elimination"
              : tier == 2 ? "diamond_equality" : "two_color_parity";
      steps.add(
          new Deduction(
              n + i,
              rule,
              tier,
              i,
              7,
              1,
              List.of(i),
              List.of(),
              rule,
              Map.of(),
              List.of(),
              new LogicalConclusion.NarrowDomain(i, 1),
              new ApplicationEffort(effort[i], 2, 2, 1, 0, 2, 0),
              List.of()));
      frontier.add(new FrontierSummary(n + i, 2, List.of(effort[i], effort[i] + 2), true, false));
    }
    return new DeductionTrace(
        steps,
        Collections.nCopies(n, 1),
        DeductionTrace.Status.SOLVED,
        Collections.nCopies(n, 2),
        0,
        frontier,
        ReasoningEvents.from(steps));
  }

  @Test
  void separatesASingleSpikeFromSustainedReasoning() {
    var analyzer = new DifficultyAnalyzer();
    var spike = analyzer.analyze(synthetic(1, 1, 1, 30, 1, 1, 1, 1, 1, 1));
    var sustained = analyzer.analyze(synthetic(1, 5, 3, 5, 3, 5, 3, 5, 3, 1));
    assertEquals(1, spike.profile().substantialEvents());
    assertEquals(1, spike.profile().activeProgressRegions());
    assertTrue(spike.profile().largestEventEffortShare() > .7);
    assertTrue(sustained.profile().activeProgressRegions() >= 3);
    assertTrue(sustained.profile().nontrivialProgressFraction() > .7);
    assertTrue(
        spike.profile().longestTrivialProgressFraction()
            > sustained.profile().longestTrivialProgressFraction());
    assertEquals(DifficultyBand.EASY, spike.band());
    assertEquals(DifficultyBand.HARD, sustained.band());
    var target = DifficultyTargets.defaults().get(DifficultyBand.HARD);
    assertFalse(target.compare(spike.profile()).accepted());
    assertTrue(target.compare(sustained.profile()).accepted());
    assertTrue(
        target.compare(spike.profile()).distance()
            > target.compare(sustained.profile()).distance());
  }

  @Test
  void informationCountsDomainReductionNotVertexAssignments() {
    var threeToTwo =
        new Deduction(0, "test", 2, 0, 7, 3, List.of(), List.of(), "test", Map.of(), List.of());
    var twoToOne =
        new Deduction(1, "test", 1, 0, 3, 1, List.of(0), List.of(), "test", Map.of(), List.of());
    assertEquals(Math.log(1.5) / Math.log(2), ReasoningEvents.information(threeToTwo), 1e-9);
    assertEquals(1, ReasoningEvents.information(twoToOne), 1e-9);
    var events = ReasoningEvents.from(List.of(threeToTwo, twoToOne));
    assertEquals(1, events.size());
    assertEquals(List.of(0), events.getFirst().directFacts());
    assertEquals(List.of(1), events.getFirst().propagationFacts());
    assertEquals(Math.log(3) / Math.log(2), events.getFirst().informationGain(), 1e-9);
  }

  @Test
  void unrelatedMechanicalMovesDoNotBecomePartOfAHardCascade() {
    var trace = synthetic(5, 1, 1);
    assertEquals(3, trace.events().size());
    var p = new DifficultyAnalyzer().analyze(trace).profile();
    assertEquals(1.0 / 3, p.nontrivialProgressFraction(), 1e-9);
    assertEquals(1, p.completionFraction(), 1e-9);
    assertEquals(3, p.timeline().size());
    assertEquals(List.of(5.0, 1.0, 1.0), p.easiestAvailableEfforts());
  }

  @Test
  void eventEffortExposesCalibratableApplicationFeatures() {
    var model = EffortModel.defaults();
    var shortPath = new RuleApplication(0, 4, List.of(1, 2), Map.of("pathLength", 1));
    var longPath =
        new RuleApplication(0, 4, List.of(1, 2, 3, 4, 5, 6, 7, 8), Map.of("pathLength", 7));
    assertTrue(
        model.measure("two_color_parity", 3, longPath, 4, 0).score()
            > model.measure("two_color_parity", 3, shortPath, 1, 0).score());
    assertEquals(7, model.measure("two_color_parity", 3, longPath, 4, 0).pathLength());
  }

  @Test
  void qualityCanRankTwoCandidatesInTheSameBand() {
    var a = new DifficultyAnalyzer().analyze(synthetic(5, 1, 5, 3, 5, 3, 5, 3, 1, 1));
    var b = new DifficultyAnalyzer().analyze(synthetic(5, 3, 5, 3, 5, 3, 5, 3, 3, 1));
    assertEquals(DifficultyBand.HARD, a.band());
    assertEquals(DifficultyBand.HARD, b.band());
    var target = DifficultyTargets.defaults().get(DifficultyBand.HARD);
    assertNotEquals(target.compare(a.profile()).distance(), target.compare(b.profile()).distance());
    assertFalse(target.compare(a.profile()).preferencePenalties().isEmpty());
  }
}
