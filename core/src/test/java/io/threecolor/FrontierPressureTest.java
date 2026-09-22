package io.threecolor;

import static io.threecolor.difficulty.ProfileMetric.*;
import static org.junit.jupiter.api.Assertions.*;

import io.threecolor.deduction.*;
import io.threecolor.difficulty.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class FrontierPressureTest {
  private static DeductionTrace frontiers(DeductionTrace trace, double[]... efforts) {
    var summaries = new ArrayList<FrontierSummary>();
    for (var values : efforts)
      summaries.add(
          new FrontierSummary(
              summaries.size(),
              values.length,
              Arrays.stream(values).boxed().toList(),
              true,
              false));
    return new DeductionTrace(
        trace.steps(),
        trace.finalDomains(),
        trace.status(),
        summaries.stream().map(FrontierSummary::size).toList(),
        trace.hypothesesTried(),
        summaries,
        trace.events());
  }

  private static DifficultyProfile profile(double[]... efforts) {
    return new DifficultyAnalyzer()
        .analyze(frontiers(DifficultyProfileTest.synthetic(), efforts))
        .profile();
  }

  @Test
  void equalTotalBreadthDoesNotMeanEqualCheapChoiceBreadth() {
    var cheap = profile(new double[] {1, 1, 1, 1, 1, 1, 1, 1});
    var mixed = profile(new double[] {1, 4, 5, 6, 7, 8, 9, 10});
    assertEquals(cheap.averageFrontierSize(), mixed.averageFrontierSize());
    assertEquals(8, cheap.meanCheapFrontier());
    assertEquals(1, mixed.meanCheapFrontier());
    assertEquals(1, cheap.crowdedCheapFrontierFraction());
    assertEquals(0, mixed.crowdedCheapFrontierFraction());
  }

  @Test
  void aggregatesRecordedStatesWithInterpolatedPercentilesDeterministically() {
    double[][] samples = {{}, {1}, {1, 2, 2.9, 3, 5}, {1, 1, 1, 1, 1, 1, 1, 1}};
    var p = profile(samples); // cheap breadth 0,1,3,8
    assertEquals(List.of(0, 1, 3, 8), p.cheapFrontierBreadths());
    assertEquals(3, p.meanCheapFrontier());
    assertEquals(4.25, p.p75CheapFrontier());
    assertEquals(.25, p.crowdedCheapFrontierFraction());
    assertEquals(p, profile(samples));
    assertThrows(UnsupportedOperationException.class, () -> p.cheapFrontierBreadths().add(9));
  }

  @Test
  void emptySingletonAndHardOnlyFrontiersStayFinite() {
    for (var p : List.of(profile(), profile(new double[] {}), profile(new double[] {3, 5, 8}))) {
      assertEquals(0, p.meanCheapFrontier());
      assertEquals(0, p.p75CheapFrontier());
      assertEquals(0, p.crowdedCheapFrontierFraction());
    }
    var one = profile(new double[] {1});
    assertEquals(1, one.meanCheapFrontier());
    assertEquals(1, one.p75CheapFrontier());
    assertEquals(0, one.crowdedCheapFrontierFraction());
  }

  @Test
  void calibrationOwnsBothCheapAndCrowdedBoundaries() {
    var trace = frontiers(DifficultyProfileTest.synthetic(), new double[] {1, 3, 4, 5});
    var p =
        new DifficultyAnalyzer(new DifficultyCalibration(4, 5, 5, 2), DifficultyTargets.defaults())
            .analyze(trace)
            .profile();
    assertEquals(List.of(2), p.cheapFrontierBreadths());
    assertEquals(1, p.crowdedCheapFrontierFraction());
    assertThrows(IllegalArgumentException.class, () -> new DifficultyCalibration(3, 5, 5, 0));
  }

  @Test
  void veryEasyAllowsBroadMechanicalChoiceAndEasyNeedsNoSubstantialRoots() {
    var targets = DifficultyTargets.defaults();
    var ve =
        new DifficultyAnalyzer()
            .analyze(
                frontiers(
                    DifficultyProfileTest.synthetic(1, 1, 1, 1, 1, 1),
                    new double[] {1, 1, 1, 1, 1, 1, 1, 1}));
    assertEquals(DifficultyBand.VERY_EASY, ve.band());
    assertTrue(targets.get(DifficultyBand.VERY_EASY).compare(ve.profile()).accepted());
    assertFalse(
        targets.get(DifficultyBand.VERY_EASY).preferences().containsKey(MEAN_CHEAP_FRONTIER));
    var base = DifficultyProfileTest.synthetic(1, 1, 1, 1, 1, 1, 1, 1, 1, 1);
    var a = new DifficultyAnalyzer().analyze(frontiers(base, new double[] {1, 1, 1}));
    var b =
        new DifficultyAnalyzer().analyze(frontiers(base, new double[] {1, 1, 1, 1, 1, 1, 1, 1}));
    assertEquals(DifficultyBand.EASY, a.band());
    assertEquals(0, a.profile().substantialEvents());
    var easy = targets.get(DifficultyBand.EASY);
    assertTrue(easy.compare(a.profile()).accepted());
    assertTrue(easy.compare(b.profile()).accepted());
    assertTrue(easy.compare(a.profile()).distance() < easy.compare(b.profile()).distance());
    assertTrue(
        easy.preferences().get(MEAN_CHEAP_FRONTIER).penalty(3)
            < easy.preferences().get(MEAN_CHEAP_FRONTIER).penalty(0));
    assertTrue(easy.preferences().containsKey(CASCADE_SHARE));
  }

  private static DeductionTrace cascades(int... roots) {
    double[] effort = new double[10];
    Arrays.fill(effort, 1);
    for (int root : roots) effort[root] = 3;
    var original = DifficultyProfileTest.synthetic(effort);
    var steps = new ArrayList<>(original.steps());
    for (int i = 0; i < 10; i++)
      if (i > 0) {
        var d = steps.get(10 + i);
        steps.set(
            10 + i,
            new Deduction(
                d.id(),
                d.ruleId(),
                d.tier(),
                d.node(),
                d.beforeMask(),
                d.afterMask(),
                List.of(i, 9 + i),
                d.witnesses(),
                d.explanationKey(),
                d.arguments(),
                d.hypothesisEvidence(),
                d.conclusion(),
                d.effort(),
                d.implicationChain()));
      }
    return new DeductionTrace(steps, original.finalDomains(), original.status(), List.of(), 0);
  }

  @Test
  void mediumRejectsMajorityAvalancheButAllowsDistributedModerateInsights() {
    var target = DifficultyTargets.defaults().get(DifficultyBand.MEDIUM);
    var bad = new DifficultyAnalyzer().analyze(cascades(0, 2)).profile();
    var good = new DifficultyAnalyzer().analyze(cascades(0, 4, 7)).profile();
    assertEquals(2, bad.nonCheapUnlocks());
    assertEquals(2, bad.activeProgressRegions());
    assertEquals(.7, bad.largestCheapCascadeShare(), 1e-9);
    assertFalse(target.compare(bad).accepted());
    assertTrue(target.compare(bad).penalties().get(CASCADE_SHARE) > 0);
    assertTrue(target.compare(good).accepted());
    assertTrue(target.compare(good).distance() < target.compare(bad).distance());
    assertEquals(0, good.hardEvents());
    assertEquals(2, target.criteria().get(UNLOCKS).minimum());
    assertFalse(target.compare(new DifficultyAnalyzer().analyze(cascades(0)).profile()).accepted());
  }

  @Test
  void namedPressureMetricsAreAvailableWithoutHiddenAcceptanceGates() {
    var p = profile(new double[] {1, 1, 1, 1, 1, 1, 1, 1});
    var custom =
        new DifficultyTarget(
            DifficultyBand.EASY, Map.of(P75_CHEAP_FRONTIER, DifficultyTarget.Range.atMost(4)));
    assertTrue(custom.compare(p).penalties().get(P75_CHEAP_FRONTIER) > 0);
    for (var band :
        List.of(
            DifficultyBand.EASY,
            DifficultyBand.MEDIUM,
            DifficultyBand.HARD,
            DifficultyBand.EXPERT)) {
      var target = DifficultyTargets.defaults().get(band);
      assertFalse(target.criteria().containsKey(MEAN_CHEAP_FRONTIER));
      assertFalse(target.criteria().containsKey(CROWDED_CHEAP_FRONTIER_FRACTION));
      assertTrue(target.compare(p).preferencePenalties().get(MEAN_CHEAP_FRONTIER) > 0);
      assertTrue(target.compare(p).preferencePenalties().get(CROWDED_CHEAP_FRONTIER_FRACTION) > 0);
    }
  }

  @Test
  void hardAndExpertRetainEveryV3SustainedGate() {
    for (var band : List.of(DifficultyBand.HARD, DifficultyBand.EXPERT)) {
      boolean expert = band == DifficultyBand.EXPERT;
      var expected = new EnumMap<ProfileMetric, DifficultyTarget.Range>(ProfileMetric.class);
      expected.put(SUBSTANTIAL_EVENTS, DifficultyTarget.Range.atLeast(expert ? 4 : 3));
      expected.put(HARD_EVENTS, DifficultyTarget.Range.atLeast(expert ? 4 : 3));
      expected.put(ACTIVE_REGIONS, DifficultyTarget.Range.atLeast(3));
      expected.put(TRIVIAL_STRETCH, DifficultyTarget.Range.atMost(expert ? .5 : .65));
      expected.put(LARGEST_EVENT_SHARE, DifficultyTarget.Range.atMost(expert ? .5 : .65));
      expected.put(CHEAP_CORE, DifficultyTarget.Range.atLeast(expert ? .65 : .5));
      expected.put(UNLOCKS, DifficultyTarget.Range.atLeast(expert ? 5 : 4));
      expected.put(SUBSTANTIAL_ROOT_FRACTION, DifficultyTarget.Range.atLeast(expert ? .6 : .45));
      expected.put(P75_EFFORT, DifficultyTarget.Range.atLeast(expert ? 5 : 3));
      expected.put(CASCADE_SHARE, DifficultyTarget.Range.atMost(expert ? .35 : .45));
      if (expert) expected.put(ADVANCED_EVENTS, DifficultyTarget.Range.atLeast(1));
      assertEquals(expected, DifficultyTargets.defaults().get(band).criteria());
    }
  }
}
