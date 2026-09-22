package io.threecolor;

import static org.junit.jupiter.api.Assertions.*;

import io.threecolor.difficulty.*;
import io.threecolor.generation.*;
import io.threecolor.model.*;
import io.threecolor.solve.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class ClueSearchStrategyTest {
  private static final GenerationBudget SMALL = new GenerationBudget(2000, 20000, 50, 12, 3);

  @Test
  void topologyIsStableAcrossSearchStrategiesAndLaterAttempts() {
    var spec = GenerationSpec.defaults(42, 12, DifficultyBand.VERY_EASY);
    var generator = new SparseGridTopologyGenerator();
    var topology = generator.generate(spec, 0);
    assertNotNull(topology);
    var next = generator.generate(spec, 1);
    for (var strategy : GenerationStrategy.values()) {
      var verifier =
          new CandidateVerifier(
              topology,
              spec,
              SMALL,
              DifficultyTargets.defaults().get(spec.targetDifficulty()),
              strategy);
      assertSame(topology, verifier.topology());
      strategy.search().search(verifier, new Random(12));
      var replay = generator.generate(spec, 0);
      assertEquals(topology.identity(), replay.identity());
      assertEquals(topology.graph().edges(), replay.graph().edges());
      assertEquals(topology.layout(), replay.layout());
      assertEquals(topology.solution(), replay.solution());
      assertEquals(next.identity(), generator.generate(spec, 1).identity());
    }
  }

  @Test
  void sharedVerifierCertifiesCachesAndUsesV4() {
    var graph =
        new GraphTopology(
            4, List.of(new Edge(0, 1), new Edge(0, 2), new Edge(1, 2), new Edge(2, 3)));
    var topology =
        new TopologyCandidate(
            graph,
            new PuzzleLayout(List.of()),
            new Coloring(List.of(Color.RED, Color.GREEN, Color.BLUE, Color.RED)),
            0,
            0);
    var spec = GenerationSpec.defaults(0, 4, DifficultyBand.VERY_EASY);
    var verifier =
        new CandidateVerifier(
            topology,
            spec,
            SMALL,
            DifficultyTargets.defaults().get(spec.targetDifficulty()),
            GenerationStrategy.RANDOM_BEAM);
    var clues =
        new TreeMap<NodeId, Color>(Map.of(new NodeId(0), Color.RED, new NodeId(1), Color.GREEN));
    assertNull(verifier.evaluate(clues)); // vertex 3 has two colors: labeled MULTIPLE
    assertEquals(
        UniquenessResult.UNKNOWN,
        new ClueEvaluator().evaluate(graph, new PartialColoring(clues), 1).exact().status());
    var limited =
        new CandidateVerifier(
            topology,
            spec,
            new GenerationBudget(1, 1, 1, 0, 1),
            DifficultyTargets.defaults().get(spec.targetDifficulty()),
            GenerationStrategy.REASONING_GUIDED);
    assertNull(limited.evaluate(clues)); // UNKNOWN is never certification
    assertNull(limited.closest());
    clues.put(new NodeId(3), Color.RED);
    var unique = verifier.evaluate(clues);
    assertNotNull(unique);
    assertTrue(unique.certified());
    assertTrue(unique.report().humanSolved());
    assertEquals("human-profile-v5", unique.report().modelVersion());
    assertEquals(
        new ClueEvaluator().evaluate(graph, new PartialColoring(clues), 2000).difficulty(),
        unique.report());
    int evaluations = verifier.quality(unique).candidateEvaluations();
    var reversed = new TreeMap<NodeId, Color>(Comparator.reverseOrder());
    reversed.putAll(clues);
    assertSame(unique, verifier.evaluate(reversed));
    assertEquals(evaluations, verifier.quality(unique).candidateEvaluations());
    var harderSpec = GenerationSpec.defaults(0, 4, DifficultyBand.HARD);
    var permissiveHard =
        new CandidateVerifier(
            topology,
            harderSpec,
            SMALL,
            new DifficultyTarget(DifficultyBand.HARD, Map.of()),
            GenerationStrategy.REASONING_GUIDED);
    var wrongBand = permissiveHard.evaluate(clues);
    assertTrue(wrongBand.match().accepted());
    assertNotEquals(DifficultyBand.HARD, wrongBand.report().band());
    assertFalse(permissiveHard.acceptable(wrongBand));
    clues.put(new NodeId(2), Color.RED); // conflicts with edge 0--2
    assertNull(verifier.evaluate(clues));
  }

  @Test
  void guidedOutputIsDeterministicCertifiedAndMatchesActualTarget() {
    var spec = GenerationSpec.defaults(42, 12, DifficultyBand.VERY_EASY);
    var target = DifficultyTargets.defaults().get(spec.targetDifficulty());
    var generator = new SparseGridGenerator();
    var first =
        assertInstanceOf(
            GeneratedPuzzle.class,
            generator.generate(spec, SMALL, target, GenerationStrategy.REASONING_GUIDED));
    var replay =
        assertInstanceOf(
            GeneratedPuzzle.class,
            generator.generate(spec, SMALL, target, GenerationStrategy.REASONING_GUIDED));
    assertEquals(first.logicalHash(), replay.logicalHash());
    assertEquals(first.trace(), replay.trace());
    assertEquals(first.quality(), replay.quality());
    assertEquals(spec.targetDifficulty(), first.difficulty().band());
    assertTrue(target.compare(first.difficulty().profile()).accepted());
    assertTrue(
        new ClueEvaluator()
            .evaluate(first.puzzle().topology(), first.puzzle().givens(), 2000)
            .certified());
    assertEquals(GenerationStrategy.REASONING_GUIDED, first.quality().context().strategy());
    assertTrue(first.puzzle().provenance().generationSpec().contains("strategy=REASONING_GUIDED"));
    assertTrue(first.quality().localProposals() <= SMALL.localMutationAttempts());
    assertTrue(first.quality().candidateEvaluations() <= SMALL.maximumCandidateEvaluations());
    assertTrue(first.quality().exactSearchNodes() <= SMALL.totalSearchNodes());
  }

  @Test
  void sameTopologyComparisonIncludesBothFullProfilesAndIndependentWork() {
    var spec = GenerationSpec.defaults(42, 12, DifficultyBand.VERY_EASY);
    var helper = new ClueSearchComparison();
    var results =
        helper.compare(spec, 0, SMALL, DifficultyTargets.defaults().get(spec.targetDifficulty()));
    assertEquals(
        results,
        helper.compare(spec, 0, SMALL, DifficultyTargets.defaults().get(spec.targetDifficulty())));
    assertEquals(2, results.size());
    assertEquals(results.get(0).topologyIdentity(), results.get(1).topologyIdentity());
    for (var result : results) {
      assertTrue(result.success());
      assertNotNull(result.difficulty().profile());
      assertNotNull(result.quality().targetMatch().penalties());
      assertEquals(result.strategy(), result.quality().context().strategy());
      assertEquals(SMALL, result.quality().context().budget());
      assertTrue(result.quality().candidateEvaluations() <= 50);
      assertTrue(result.quality().localProposals() <= 12);
    }
  }

  @Test
  void guidedCannotPublishWrongTargetAndWorkExhaustionHasContext() {
    var spec = GenerationSpec.defaults(42, 12, DifficultyBand.VERY_EASY);
    var impossible =
        new DifficultyTarget(
            spec.targetDifficulty(),
            Map.of(ProfileMetric.SUBSTANTIAL_EVENTS, DifficultyTarget.Range.atLeast(500)));
    var tiny = new GenerationBudget(1, 1, 1, 12, 2);
    var failure =
        assertInstanceOf(
            GenerationFailure.class,
            new SparseGridGenerator()
                .generate(spec, tiny, impossible, GenerationStrategy.REASONING_GUIDED));
    assertEquals("SEARCH_BUDGET_EXHAUSTED", failure.code());
    assertEquals(1, failure.diagnostics().candidateEvaluations());
    assertEquals(GenerationStrategy.REASONING_GUIDED, failure.diagnostics().context().strategy());
    assertEquals(impossible, failure.diagnostics().context().target());
    assertEquals(tiny, failure.diagnostics().context().budget());
    assertEquals(
        failure,
        new SparseGridGenerator()
            .generate(spec, tiny, impossible, GenerationStrategy.REASONING_GUIDED));
  }
}
