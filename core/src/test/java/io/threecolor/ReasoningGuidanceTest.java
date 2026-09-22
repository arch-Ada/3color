package io.threecolor;

import static io.threecolor.difficulty.ProfileMetric.*;
import static org.junit.jupiter.api.Assertions.*;

import io.threecolor.deduction.*;
import io.threecolor.difficulty.*;
import io.threecolor.generation.*;
import io.threecolor.model.*;
import io.threecolor.solve.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class ReasoningGuidanceTest {
  private static DeductionTrace trace() {
    var original = DifficultyProfileTest.synthetic(1, 1, 1, 1, 3, 1, 1, 1);
    var steps = new ArrayList<>(original.steps());
    for (int i = 5; i < 8; i++) {
      var d = steps.get(8 + i);
      steps.set(
          8 + i,
          new Deduction(
              d.id(),
              d.ruleId(),
              d.tier(),
              d.node(),
              d.beforeMask(),
              d.afterMask(),
              List.of(i, 7 + i),
              d.witnesses(),
              d.explanationKey(),
              d.arguments(),
              d.hypothesisEvidence(),
              d.conclusion(),
              d.effort(),
              d.implicationChain()));
    }
    return new DeductionTrace(
        steps,
        original.finalDomains(),
        original.status(),
        List.of(),
        0,
        List.of(
            new FrontierSummary(9, 8, List.of(1., 4., 5., 6., 7., 8., 9., 10.), false, false),
            new FrontierSummary(10, 8, Collections.nCopies(8, 1.), false, false)),
        ReasoningEvents.from(steps));
  }

  private static EvaluatedCandidate candidate(DifficultyTarget target) {
    var report = new DifficultyAnalyzer().analyze(trace());
    var clues =
        new TreeMap<NodeId, Color>(
            Map.of(
                new NodeId(0), Color.RED, new NodeId(2), Color.GREEN, new NodeId(4), Color.BLUE));
    return new EvaluatedCandidate(
        clues,
        trace(),
        report,
        target.compare(report.profile()),
        clues.toString(),
        UniquenessResult.UNIQUE);
  }

  private static final GraphTopology GRAPH = new GraphTopology(8, List.of());

  @Test
  void constraintsTakePriorityEvenOverMuchLargerPreferencePenalties() {
    var target =
        new DifficultyTarget(
            DifficultyBand.MEDIUM,
            Map.of(UNLOCKS, DifficultyTarget.Range.atLeast(2)),
            Map.of(MEAN_CHEAP_FRONTIER, new DifficultyTarget.Preference(0, 1, 100)));
    var guidance = new ReasoningGuidance();
    var c = candidate(target);
    var deficit = guidance.dominant(c, target);
    assertEquals(UNLOCKS, deficit.metric());
    assertTrue(deficit.constraint());
    assertTrue(deficit.increase());
    assertEquals(c.match().penalties().get(UNLOCKS), deficit.penalty());
    assertEquals(guidance.inspect(c, target, GRAPH), guidance.inspect(c, target, GRAPH));
  }

  @Test
  void cascadeEvidenceAndCheapPressureChooseDifferentClueLocations() {
    var cascade =
        new DifficultyTarget(
            DifficultyBand.MEDIUM, Map.of(CASCADE_SHARE, DifficultyTarget.Range.atMost(.1)));
    var pressure =
        new DifficultyTarget(
            DifficultyBand.MEDIUM,
            Map.of(),
            Map.of(MEAN_CHEAP_FRONTIER, new DifficultyTarget.Preference(0, 1, 1)));
    var guidance = new ReasoningGuidance();
    var a = guidance.inspect(candidate(cascade), cascade, GRAPH);
    var b = guidance.inspect(candidate(pressure), pressure, GRAPH);
    assertEquals(CASCADE_SHARE, a.deficit().metric());
    assertEquals(new NodeId(4), a.removals().getFirst());
    assertEquals(new NodeId(2), b.removals().getFirst());
    assertTrue(b.evidenceFacts().contains(10)); // eight cheap alternatives
    assertFalse(b.evidenceFacts().contains(9)); // same size, but seven hard alternatives
    assertFalse(a.evidenceFacts().contains(10));
    var topology =
        new TopologyCandidate(
            GRAPH,
            new PuzzleLayout(List.of()),
            new Coloring(Collections.nCopies(8, Color.RED)),
            0,
            0);
    var strategy = new ReasoningGuidedClueSearch();
    var mutations = strategy.neighborhood(candidate(cascade), topology, a);
    assertEquals(6, mutations.size());
    assertEquals(6, mutations.stream().map(m -> m.clues().toString()).distinct().count());
    assertNotEquals(mutations, strategy.neighborhood(candidate(pressure), topology, b));
    assertEquals(
        Set.of(ReasoningGuidedClueSearch.Kind.values()),
        new HashSet<>(mutations.stream().map(ReasoningGuidedClueSearch.Mutation::kind).toList()));
  }

  @Test
  void missingReasoningProtectsExistingRootAndSeeksUncoveredRegions() {
    var target =
        new DifficultyTarget(
            DifficultyBand.HARD, Map.of(ACTIVE_REGIONS, DifficultyTarget.Range.atLeast(3)));
    var advice = new ReasoningGuidance().inspect(candidate(target), target, GRAPH);
    assertEquals(ACTIVE_REGIONS, advice.deficit().metric());
    assertEquals(Set.of(4), advice.protectedNodes());
    assertEquals(new NodeId(0), advice.removals().getFirst());
    assertEquals(new NodeId(4), advice.removals().getLast());
    assertTrue(advice.evidenceFacts().size() <= ReasoningGuidance.MAX_EVIDENCE_FACTS);
  }

  @Test
  void directionReadsActualTargetAndEasyDoesNotRequireNewSubstantialRoots() {
    var low =
        new DifficultyTarget(
            DifficultyBand.EASY,
            Map.of(),
            Map.of(MEAN_CHEAP_FRONTIER, new DifficultyTarget.Preference(0, 1, 1)));
    var high =
        new DifficultyTarget(
            DifficultyBand.EASY,
            Map.of(),
            Map.of(MEAN_CHEAP_FRONTIER, new DifficultyTarget.Preference(12, 1, 1)));
    var guidance = new ReasoningGuidance();
    assertFalse(guidance.inspect(candidate(low), low, GRAPH).restoreFirst());
    assertTrue(guidance.inspect(candidate(high), high, GRAPH).restoreFirst());
    var easy = DifficultyTargets.defaults().get(DifficultyBand.EASY);
    var simple =
        new DifficultyAnalyzer()
            .analyze(DifficultyProfileTest.synthetic(1, 1, 1, 1, 1, 1, 1, 1, 1, 1));
    assertEquals(0, simple.profile().substantialEvents());
    assertTrue(easy.compare(simple.profile()).accepted());
    var medium = DifficultyTargets.defaults().get(DifficultyBand.MEDIUM);
    assertEquals(.5, medium.criteria().get(CASCADE_SHARE).maximum());
    assertEquals(2, medium.criteria().get(UNLOCKS).minimum());
  }
}
