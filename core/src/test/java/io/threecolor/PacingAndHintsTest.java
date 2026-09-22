package io.threecolor;

import static org.junit.jupiter.api.Assertions.*;

import io.threecolor.deduction.*;
import io.threecolor.difficulty.*;
import io.threecolor.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class PacingAndHintsTest {
  @Test
  void oneInsightDoesNotTurnItsCascadeIntoSustainedReasoning() {
    double[] efforts = new double[30];
    Arrays.fill(efforts, 1);
    efforts[0] = 12;
    var original = DifficultyProfileTest.synthetic(efforts);
    var steps = new ArrayList<>(original.steps());
    for (int i = 31; i < steps.size(); i++) {
      var d = steps.get(i);
      steps.set(
          i,
          new Deduction(
              d.id(),
              d.ruleId(),
              d.tier(),
              d.node(),
              d.beforeMask(),
              d.afterMask(),
              List.of(d.node(), i - 1),
              d.witnesses(),
              d.explanationKey(),
              d.arguments(),
              List.of(),
              d.conclusion(),
              d.effort(),
              d.implicationChain()));
    }
    var trace = new DeductionTrace(steps, original.finalDomains(), original.status(), List.of(), 0);
    var report = new DifficultyAnalyzer().analyze(trace);
    var p = report.profile();
    assertEquals(1, p.eventCount());
    assertEquals(1, p.nonCheapUnlocks());
    assertEquals(12, p.p75Effort());
    assertEquals(12, p.p90Effort());
    assertEquals(1.0 / 30, p.nontrivialProgressFraction(), 1e-9);
    assertEquals(29.0 / 30, p.largestCheapCascadeShare(), 1e-9);
    assertEquals(29.0 / 30, p.longestTrivialProgressFraction(), 1e-9);
    assertEquals(
        trace.events().getFirst().informationGain(),
        trace.events().getFirst().rootInformation()
            + trace.events().getFirst().cascadeInformation(),
        1e-9);
    var sustained =
        new DifficultyAnalyzer()
            .analyze(DifficultyProfileTest.synthetic(1, 5, 3, 5, 3, 5, 3, 5, 3, 1));
    assertTrue(sustained.profile().nonCheapUnlocks() > p.nonCheapUnlocks());
    assertTrue(sustained.profile().activeProgressRegions() > p.activeProgressRegions());
    var target = DifficultyTargets.defaults().get(DifficultyBand.HARD);
    assertFalse(target.compare(p).accepted());
    assertTrue(target.compare(p).penalties().get(ProfileMetric.CASCADE_SHARE) > 0);
    assertTrue(target.compare(sustained.profile()).accepted());
    assertTrue(target.compare(p).distance() > target.compare(sustained.profile()).distance());
  }

  @Test
  void cheapClosureIsDeterministicAndMatchesTheMeasuredOpening() {
    var graph =
        new GraphTopology(
            5, List.of(new Edge(0, 1), new Edge(1, 2), new Edge(0, 2), new Edge(2, 3)));
    var givens = new PartialColoring(Map.of(new NodeId(0), Color.RED, new NodeId(1), Color.GREEN));
    var engine = new DeductionEngine();
    var closure = engine.cheapClosure(graph, givens);
    assertEquals(closure, engine.cheapClosure(graph, givens));
    assertTrue(
        engine
            .frontier(new DeductionState(graph, closure.finalDomains()), 1)
            .deductions()
            .isEmpty());
    var profile = new DifficultyAnalyzer().analyze(engine.solve(graph, givens)).profile();
    double opening = closure.events().stream().mapToDouble(DeductionEvent::informationGain).sum();
    assertEquals(
        opening / profile.initialInformation(),
        profile.initialCheapClosureProgressFraction(),
        1e-9);
    assertEquals(
        1 - profile.initialCheapClosureProgressFraction(),
        profile.remainingCheapCoreFraction(),
        1e-9);
  }

  @Test
  void boundedContradictionKeepsOnlyTheLocalProofEvenWithManyIrrelevantVertices() {
    var graph =
        new GraphTopology(
            40,
            List.of(
                new Edge(0, 1),
                new Edge(0, 2),
                new Edge(0, 3),
                new Edge(1, 2),
                new Edge(1, 3),
                new Edge(2, 3),
                new Edge(20, 21)));
    var trace =
        new DeductionEngine(List.of(new LockedEdgeRule(), new ParityRule()))
            .next(graph, PartialColoring.empty());
    var d =
        trace.steps().stream()
            .filter(s -> s.ruleId().equals("bounded_contradiction"))
            .findFirst()
            .orElseThrow();
    assertTrue(d.witnesses().stream().allMatch(v -> v < 4));
    assertTrue(ProofCore.vertices(d.hypothesisEvidence()).stream().allMatch(v -> v < 4));
    assertTrue(d.hypothesisEvidence().stream().anyMatch(ProofCore::contradiction));
    assertTrue(d.hypothesisEvidence().stream().anyMatch(s -> s.ruleId().equals("hypothesis")));
    var proof = ProofCore.slice(trace.steps(), d.id());
    var explanation = HintExplanation.from(graph, d, proof);
    assertTrue(explanation.focusVertices().stream().allMatch(v -> v < 4));
    assertTrue(explanation.focusEdges().stream().allMatch(e -> e.a() < 4 && e.b() < 4));
    assertNotNull(explanation.assumption());
    assertNotNull(explanation.contradictionPoint());
    assertEquals("ASSUMPTION", explanation.walkthrough().getFirst().kind());
    assertEquals("CONCLUSION", explanation.walkthrough().getLast().kind());
    assertClosed(proof);
  }

  @Test
  void implicationWalkthroughPreservesLinkOrderAndParityDoesNotHighlightUnusedChords() {
    var graph =
        new GraphTopology(
            4, List.of(new Edge(0, 1), new Edge(1, 2), new Edge(2, 3), new Edge(0, 3)));
    var trace =
        new DeductionEngine(List.of(new ImplicationChainRule()))
            .solve(
                new DeductionState(graph, List.of(3, 5, 6, 3)),
                DeductionConfiguration.defaults().withLevel(3, 0));
    var root =
        trace.steps().stream()
            .filter(d -> d.ruleId().equals("implication_chain"))
            .findFirst()
            .orElseThrow();
    var explanation = HintExplanation.from(graph, root, ProofCore.slice(trace.steps(), root.id()));
    assertEquals("ASSUMPTION", explanation.walkthrough().getFirst().kind());
    for (int i = 0; i < root.implicationChain().size(); i++) {
      var link = root.implicationChain().get(i);
      var step = explanation.walkthrough().get(i + 1);
      assertEquals(link.target(), step.node());
      assertEquals(link.targetMask(), step.mask());
    }
    assertEquals("CONCLUSION", explanation.walkthrough().getLast().kind());
    var chordGraph =
        new GraphTopology(
            5,
            List.of(
                new Edge(0, 1),
                new Edge(1, 2),
                new Edge(2, 3),
                new Edge(3, 4),
                new Edge(0, 4),
                new Edge(1, 3)));
    var parity =
        new Deduction(
            0,
            "two_color_parity",
            3,
            0,
            7,
            4,
            List.of(),
            List.of(1, 2, 3, 4),
            "two_color_parity",
            Map.of("pairMask", 3),
            List.of());
    var projected = HintExplanation.from(chordGraph, parity, List.of(parity));
    assertEquals(List.of(1, 2, 3, 4), projected.orderedPath());
    assertFalse(projected.focusEdges().contains(new HintExplanation.FocusEdge(1, 3)));
    assertEquals(5, projected.focusEdges().size());
    var impossiblePair =
        new Deduction(
            0,
            "two_color_parity",
            3,
            0,
            3,
            0,
            List.of(),
            List.of(1, 2, 3, 4),
            "two_color_parity",
            Map.of("pairMask", 3),
            List.of());
    assertEquals(
        projected.focusEdges(),
        HintExplanation.from(chordGraph, impossiblePair, List.of(impossiblePair)).focusEdges());
  }

  @Test
  void inheritedInequalityHighlightsItsActualPhysicalBridge() {
    var graph = new GraphTopology(3, List.of(new Edge(1, 2)));
    var relations = new RelationKnowledge(graph).with(0, 2, true, 0);
    var trace =
        new DeductionEngine(List.of())
            .solve(
                new DeductionState(graph, List.of(7, 1, 7), relations),
                DeductionConfiguration.defaults().withLevel(1, 0));
    var root =
        trace.steps().stream()
            .filter(d -> d.ruleId().equals("relation_propagation") && d.node() == 0)
            .findFirst()
            .orElseThrow();
    var explanation = HintExplanation.from(graph, root, ProofCore.slice(trace.steps(), root.id()));
    assertEquals(0, root.arguments().get("equality"));
    assertEquals(List.of(new HintExplanation.FocusEdge(1, 2)), explanation.focusEdges());
    assertEquals(List.of(0, 1, 2), explanation.walkthrough().getLast().vertices());
  }

  @Test
  void backwardSliceTraversesNestedEvidenceWithIndependentFactIds() {
    var inner =
        List.of(
            new Deduction(
                0,
                "hypothesis",
                0,
                2,
                7,
                1,
                List.of(),
                List.of(),
                "hypothesis",
                Map.of(),
                List.of()),
            new Deduction(
                1, "irrelevant", 0, 20, 7, 7, List.of(), List.of(), "initial", Map.of(), List.of()),
            new Deduction(
                2,
                "adjacent_color_elimination",
                1,
                3,
                1,
                0,
                List.of(0),
                List.of(2),
                "adjacent_color_elimination",
                Map.of("sourceNode", 2),
                List.of()));
    var outer =
        List.of(
            new Deduction(
                0,
                "domain_initialization",
                0,
                2,
                7,
                7,
                List.of(),
                List.of(),
                "initial",
                Map.of(),
                List.of()),
            new Deduction(
                1,
                "bounded_contradiction",
                4,
                2,
                7,
                6,
                List.of(0),
                List.of(2, 3),
                "bounded_contradiction",
                Map.of("assumedMask", 1),
                inner),
            new Deduction(
                2,
                "contradiction",
                1,
                2,
                6,
                0,
                List.of(1),
                List.of(),
                "contradiction",
                Map.of(),
                List.of()));
    var core = ProofCore.contradictionSlice(outer);
    assertEquals(
        List.of(0, 2), core.get(1).hypothesisEvidence().stream().map(Deduction::id).toList());
    assertEquals(List.of(2, 3), ProofCore.vertices(core));
    assertClosed(core);
  }

  @Test
  void structuralProofDoesNotImportUnneededWitnessDomainHistory() {
    var graph =
        new GraphTopology(
            5,
            List.of(
                new Edge(0, 1),
                new Edge(0, 2),
                new Edge(1, 2),
                new Edge(0, 3),
                new Edge(1, 3),
                new Edge(0, 4)));
    var trace =
        new DeductionEngine(List.of(new DiamondRule()))
            .solve(graph, new PartialColoring(Map.of(new NodeId(4), Color.RED)), 2, 0);
    var root =
        trace.steps().stream()
            .filter(d -> d.ruleId().equals("diamond_equality"))
            .findFirst()
            .orElseThrow();
    assertTrue(root.premises().isEmpty());
    var core = ProofCore.slice(trace.steps(), root.id());
    assertEquals(List.of(0, 1, 2, 3), HintExplanation.from(graph, root, core).focusVertices());
  }

  @Test
  void separateRelationalInsightsCountWithoutInventingDirectDomainInformation() {
    var steps = new ArrayList<Deduction>();
    for (int i = 0; i < 4; i++)
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
              "initial",
              Map.of(),
              List.of()));
    for (int a : new int[] {0, 2}) {
      int root = steps.size();
      steps.add(
          new Deduction(
              root,
              "diamond_equality",
              2,
              a,
              7,
              7,
              List.of(),
              List.of(a, a + 1),
              "diamond_equality",
              Map.of(),
              List.of(),
              new LogicalConclusion.Equal(a, a + 1),
              new ApplicationEffort(3, 4, 0, 1, 0, 4, 0),
              List.of()));
      for (int v : new int[] {a, a + 1})
        steps.add(
            new Deduction(
                steps.size(),
                "relation_propagation",
                1,
                v,
                7,
                1,
                List.of(root),
                List.of(),
                "relation_propagation",
                Map.of(),
                List.of()));
    }
    var report =
        new DifficultyAnalyzer()
            .analyze(
                new DeductionTrace(
                    steps, List.of(1, 1, 1, 1), DeductionTrace.Status.SOLVED, List.of(), 0));
    assertEquals(2, report.profile().nonCheapUnlocks());
    assertEquals(0, report.profile().nontrivialProgressFraction());
    assertEquals(1, report.profile().substantialRootFraction());
    assertEquals(1, report.profile().substantialCausalDepth());
    assertEquals(DifficultyBand.EASY, report.band());
    assertFalse(
        DifficultyTargets.defaults()
            .get(DifficultyBand.MEDIUM)
            .compare(report.profile())
            .accepted());
    assertFalse(
        DifficultyTargets.defaults().get(DifficultyBand.HARD).compare(report.profile()).accepted());
  }

  private static void assertClosed(List<Deduction> proof) {
    var ids = new HashSet<Integer>();
    for (var d : proof) {
      assertTrue(ids.containsAll(d.premises()));
      ids.add(d.id());
      assertClosed(d.hypothesisEvidence());
    }
  }

  @Test
  void hintsContinueThroughCandidateEliminationsToAColour() {
    var graph = new GraphTopology(3, List.of(new Edge(0, 1), new Edge(1, 2), new Edge(0, 2)));
    var puzzle =
        new Puzzle(
            graph,
            new PartialColoring(Map.of(new NodeId(0), Color.RED, new NodeId(1), Color.GREEN)),
            RuleSet.CLASSIC_V1,
            PuzzleProvenance.supplied(),
            new PuzzleLayout(List.of()));
    var service = new HintService();
    var hint = service.hint(puzzle, PartialColoring.empty(), Hint.Level.NUDGE);
    assertEquals(Hint.Status.AVAILABLE, hint.status());
    assertEquals(4, hint.deduction().orElseThrow().afterMask());
    assertEquals(List.of(2), hint.explanation().primaryTargets());
    var steps = hint.explanation().walkthrough();
    assertEquals(1, steps.size());
    assertEquals("triangle_completion", steps.getFirst().deduction().explanationKey());
    assertEquals(2, hint.supportingSteps().stream().filter(d -> d.tier() > 0).count());
    assertEquals(4, steps.getLast().deduction().afterMask());
    assertEquals(
        hint.explanation(),
        service.hint(puzzle, PartialColoring.empty(), Hint.Level.ANSWER).explanation());
    assertEquals(
        Hint.Status.SOLVED,
        service
            .hint(puzzle, new PartialColoring(Map.of(new NodeId(2), Color.BLUE)), Hint.Level.NUDGE)
            .status());
  }

  @Test
  void ambiguousCandidateReductionIsNotPresentedAsACompleteHint() {
    var puzzle =
        new Puzzle(
            new GraphTopology(2, List.of(new Edge(0, 1))),
            new PartialColoring(Map.of(new NodeId(0), Color.RED)),
            RuleSet.CLASSIC_V1,
            PuzzleProvenance.supplied(),
            new PuzzleLayout(List.of()));
    var hint = new HintService().hint(puzzle, PartialColoring.empty(), Hint.Level.ANSWER);
    assertEquals(Hint.Status.NO_DEDUCTION, hint.status());
    assertTrue(hint.deduction().isEmpty());
  }

  @Test
  void successiveHintsCanFinishAChallengingPuzzleWithoutRepeatingAColour() {
    var puzzle =
        io.threecolor.generation.PuzzleBank.bundled().entries().stream()
            .filter(e -> e.playRating().category() == PlayDifficulty.Category.CHALLENGING)
            .filter(e -> e.puzzle().topology().nodeCount() == 24)
            .findFirst()
            .orElseThrow()
            .puzzle();
    var colors = new TreeMap<>(puzzle.givens().colors());
    var service = new HintService();
    while (colors.size() < puzzle.topology().nodeCount()) {
      var hint = service.hint(puzzle, new PartialColoring(colors), Hint.Level.ANSWER);
      assertEquals(Hint.Status.AVAILABLE, hint.status());
      var d = hint.deduction().orElseThrow();
      assertFalse(colors.containsKey(new NodeId(d.node())));
      assertEquals(d.id(), hint.explanation().walkthrough().getLast().deduction().id());
      assertClosed(hint.supportingSteps());
      colors.put(new NodeId(d.node()), Color.fromMask(d.afterMask()));
    }
    assertEquals(
        Hint.Status.SOLVED,
        service.hint(puzzle, new PartialColoring(colors), Hint.Level.ANSWER).status());
    for (var edge : puzzle.topology().edges())
      assertNotEquals(colors.get(edge.a()), colors.get(edge.b()));
  }
}
