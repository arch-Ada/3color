package io.threecolor;

import static org.junit.jupiter.api.Assertions.*;

import io.threecolor.deduction.*;
import io.threecolor.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class RelationalDeductionTest {
  @Test
  void equalityIsTransitiveAndCarriesItsProof() {
    var g = new GraphTopology(4, List.of(new Edge(2, 3)));
    var r = new RelationKnowledge(g).with(0, 1, true, 4).with(1, 2, true, 5);
    assertTrue(r.equal(0, 2));
    assertEquals(List.of(4, 5), r.equalityProof(0, 2));
    assertTrue(r.notEqual(0, 3));
    assertEquals(List.of(4, 5), r.inequalityProof(0, 3));
    assertFalse(g.adjacent(0, 3));
    var conflict = r.with(0, 3, true, 6);
    var deductions =
        new RelationPropagationRule().find(new DeductionState(g, List.of(7, 7, 7, 7), conflict));
    assertInstanceOf(LogicalConclusion.Contradiction.class, deductions.getFirst().conclusion());
    assertTrue(deductions.getFirst().relationPremises().contains(6));
  }

  @Test
  void explicitInequalityConflictsWithEquality() {
    var g = new GraphTopology(2, List.of());
    var r = new RelationKnowledge(g).with(0, 1, false, 2).with(0, 1, true, 3);
    assertTrue(r.notEqual(0, 0));
    assertInstanceOf(
        LogicalConclusion.Contradiction.class,
        new RelationPropagationRule()
            .find(new DeductionState(g, List.of(7, 7), r))
            .getFirst()
            .conclusion());
  }

  @Test
  void equalityPropagatesAnyDomainIntersectionAndInequalityEliminatesSingletons() {
    var g = new GraphTopology(3, List.of());
    var r = new RelationKnowledge(g).with(0, 1, true, 3).with(1, 2, false, 4);
    var rule = new RelationPropagationRule();
    assertTrue(
        rule.find(new DeductionState(g, List.of(1, 7, 3), r)).stream()
            .anyMatch(
                a ->
                    a.conclusion().equals(new LogicalConclusion.NarrowDomain(1, 1))
                        && a.relationPremises().contains(3)));
    assertTrue(
        rule.find(new DeductionState(g, List.of(3, 7, 4), r)).stream()
            .anyMatch(a -> a.conclusion().equals(new LogicalConclusion.NarrowDomain(1, 3))));
    assertTrue(
        rule.find(new DeductionState(g, List.of(1, 1, 3), r)).stream()
            .anyMatch(a -> a.conclusion().equals(new LogicalConclusion.NarrowDomain(2, 2))));
    assertTrue(
        rule.find(new DeductionState(g, List.of(1, 2, 7), r)).stream()
            .anyMatch(a -> a.afterMask() == 0));
  }

  @Test
  void diamondFindsEqualityWithoutAnyGivenColors() {
    var g =
        new GraphTopology(
            4,
            List.of(
                new Edge(0, 1), new Edge(0, 2), new Edge(1, 2), new Edge(0, 3), new Edge(1, 3)));
    var s = new DeductionState(g, List.of(7, 7, 7, 7));
    assertTrue(
        new DiamondRule()
            .find(s).stream()
                .anyMatch(a -> a.conclusion().equals(new LogicalConclusion.Equal(2, 3))));
    var trace = new DeductionEngine().solve(g, PartialColoring.empty(), 3, 0);
    assertTrue(
        trace.steps().stream()
            .anyMatch(d -> d.conclusion().equals(new LogicalConclusion.Equal(2, 3))));
    assertEquals(trace, new DeductionEngine().solve(g, PartialColoring.empty(), 3, 0));
  }

  @Test
  void neighborhoodPathsProduceEvenEqualityAndOddInequalityEconomically() {
    var g =
        new GraphTopology(
            5,
            List.of(
                new Edge(0, 1),
                new Edge(0, 2),
                new Edge(0, 3),
                new Edge(0, 4),
                new Edge(1, 2),
                new Edge(2, 3),
                new Edge(3, 4)));
    var deductions =
        new NeighborhoodParityRule().find(new DeductionState(g, List.of(7, 7, 7, 7, 7)));
    assertTrue(
        deductions.stream()
            .anyMatch(a -> a.conclusion().equals(new LogicalConclusion.Equal(1, 3))));
    // Spanning equalities plus the physical edge 1!=2 imply every odd-distance inequality.
    var relations = new RelationKnowledge(g);
    int id = 0;
    for (var a : deductions)
      if (a.conclusion() instanceof LogicalConclusion.Equal e)
        relations = relations.with(e.a(), e.b(), true, id++);
    assertTrue(relations.notEqual(1, 4));
    assertTrue(relations.equal(2, 4));
    assertTrue(deductions.size() < 10);
    var k4 =
        new GraphTopology(
            4,
            List.of(
                new Edge(0, 1),
                new Edge(0, 2),
                new Edge(0, 3),
                new Edge(1, 2),
                new Edge(1, 3),
                new Edge(2, 3)));
    assertInstanceOf(
        LogicalConclusion.Contradiction.class,
        new NeighborhoodParityRule()
            .find(new DeductionState(k4, List.of(7, 7, 7, 7)))
            .getFirst()
            .conclusion());
  }

  @Test
  void mixedPairChainHasReadableEvidence() {
    // A=R -> B=B -> C=G -> D=R -> A!=R around a four-cycle.
    var g =
        new GraphTopology(
            4, List.of(new Edge(0, 1), new Edge(1, 2), new Edge(2, 3), new Edge(0, 3)));
    var s = new DeductionState(g, List.of(3, 5, 6, 3));
    var found = new ImplicationChainRule().find(s);
    assertFalse(found.isEmpty());
    assertTrue(found.stream().anyMatch(a -> a.implicationChain().size() >= 4));
    assertTrue(new ImplicationChainRule(2).find(s).isEmpty());
    for (var a : found) {
      assertFalse(a.implicationChain().isEmpty());
      assertTrue(a.arguments().containsKey("assumedMask"));
    }
    for (var c : SolverProperties.brute(g, PartialColoring.empty()))
      if (java.util.stream.IntStream.range(0, 4)
          .allMatch(v -> (c.colors().get(v).mask() & List.of(3, 5, 6, 3).get(v)) != 0))
        for (var a : found) assertNotEquals(0, c.colors().get(a.node()).mask() & a.afterMask());
  }

  @Test
  void frontierIsGlobalCanonicalAndChoosesTheSimplestProof() {
    var g =
        new GraphTopology(
            4,
            List.of(
                new Edge(0, 1), new Edge(0, 2), new Edge(1, 2), new Edge(0, 3), new Edge(1, 3)));
    var engine = new DeductionEngine();
    var frontier = engine.frontier(new DeductionState(g, List.of(1, 2, 7, 7)), 3);
    assertTrue(frontier.deductions().size() >= 4);
    assertEquals(1, frontier.easiest().orElseThrow().tier());
    assertEquals(frontier, engine.frontier(new DeductionState(g, List.of(1, 2, 7, 7)), 3));
    var a = new RuleApplication(2, 4, List.of(0, 1), Map.of());
    var easy =
        new AvailableDeduction(
            "short", 2, a, new ApplicationEffort(3, 2, 1, 1, 0, 2, 0), List.of());
    var hard =
        new AvailableDeduction("long", 3, a, new ApplicationEffort(8, 9, 8, 6, 0, 9, 0), List.of());
    assertEquals(
        List.of(easy), DeductionFrontier.canonical(List.of(hard, easy), false, false).deductions());
  }

  @Test
  void engineRecordsRelationPropagationAndItsActualProofDependency() {
    var g =
        new GraphTopology(
            4,
            List.of(
                new Edge(0, 1), new Edge(0, 2), new Edge(1, 2), new Edge(0, 3), new Edge(1, 3)));
    var trace =
        new DeductionEngine(List.of(new DiamondRule()))
            .solve(g, new PartialColoring(Map.of(new NodeId(2), Color.RED)), 2, 0);
    var relation =
        trace.steps().stream()
            .filter(d -> d.conclusion() instanceof LogicalConclusion.Equal)
            .findFirst()
            .orElseThrow();
    var propagated =
        trace.steps().stream()
            .filter(
                d ->
                    d.ruleId().equals("relation_propagation")
                        && d.node() == 3
                        && d.afterMask() == 1)
            .findFirst()
            .orElseThrow();
    assertTrue(propagated.premises().contains(relation.id()));
    var event =
        trace.events().stream()
            .filter(e -> e.rootFactId() == relation.id())
            .findFirst()
            .orElseThrow();
    assertTrue(event.propagationFacts().contains(propagated.id()));
    assertEquals(1, event.relationFacts());
    assertTrue(event.informationGain() > 0);
  }

  @Test
  void workLimitsAndHypothesisProofScopesAreRespected() {
    var graph = new GraphTopology(3, List.of(new Edge(0, 1), new Edge(1, 2), new Edge(0, 2)));
    var config = new DeductionConfiguration(4, 192, 2, 1, 24, EffortModel.defaults());
    var trace = new DeductionEngine().solve(graph, PartialColoring.empty(), config);
    assertEquals(DeductionTrace.Status.BUDGET_EXHAUSTED, trace.status());
    assertTrue(trace.steps().size() <= 2);
    var k4 =
        new GraphTopology(
            4,
            List.of(
                new Edge(0, 1),
                new Edge(0, 2),
                new Edge(0, 3),
                new Edge(1, 2),
                new Edge(1, 3),
                new Edge(2, 3)));
    checkProof(
        new DeductionEngine(List.of(new LockedEdgeRule(), new ParityRule()))
            .solve(k4, PartialColoring.empty())
            .steps());
  }

  private static void checkProof(List<Deduction> steps) {
    for (var d : steps) {
      for (int id : d.premises()) assertTrue(id >= 0 && id < d.id());
      checkProof(d.hypothesisEvidence());
    }
  }
}
