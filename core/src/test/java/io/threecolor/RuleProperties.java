package io.threecolor;

import static org.junit.jupiter.api.Assertions.*;

import io.threecolor.deduction.*;
import io.threecolor.model.*;
import java.util.*;
import net.jqwik.api.*;

class RuleProperties {
  @Property(tries = 200, seed = "314000")
  void structuralRulesPreserveEveryDomainCompatibleCompletion(@ForAll long seed) {
    var graph = SolverProperties.randomGraph(seed, 7);
    var random = new Random(seed ^ 0x12345678);
    var masks = new ArrayList<Integer>();
    for (int i = 0; i < 7; i++) masks.add(1 + random.nextInt(7));
    var solutions =
        SolverProperties.brute(graph, PartialColoring.empty()).stream()
            .filter(
                c ->
                    java.util.stream.IntStream.range(0, 7)
                        .allMatch(v -> (c.colors().get(v).mask() & masks.get(v)) != 0))
            .toList();
    for (var rule :
        List.of(
            new LockedEdgeRule(),
            new ParityRule(),
            new DiamondRule(),
            new NeighborhoodParityRule(),
            new ImplicationChainRule(),
            new RelationPropagationRule()))
      for (var application : rule.find(new DeductionState(graph, masks)))
        for (var c : solutions)
          assertTrue(holds(c, application.conclusion()), rule.id() + " " + application);
  }

  static boolean holds(Coloring c, LogicalConclusion conclusion) {
    return switch (conclusion) {
      case LogicalConclusion.NarrowDomain d -> (c.colors().get(d.node()).mask() & d.mask()) != 0;
      case LogicalConclusion.Equal e -> c.colors().get(e.a()) == c.colors().get(e.b());
      case LogicalConclusion.NotEqual e -> c.colors().get(e.a()) != c.colors().get(e.b());
      case LogicalConclusion.Contradiction x -> false;
    };
  }

  @Property(tries = 120, seed = "719315")
  void rulesRemainSoundWithAssumedRelations(@ForAll long seed) {
    var g = SolverProperties.randomGraph(seed, 6);
    var all = SolverProperties.brute(g, PartialColoring.empty());
    if (all.isEmpty()) return;
    var random = new Random(seed);
    var witness = all.getFirst();
    var relations = new RelationKnowledge(g);
    for (int i = 0; i < 4; i++) {
      int a = random.nextInt(6), b = random.nextInt(6);
      if (a != b)
        relations = relations.with(a, b, witness.colors().get(a) == witness.colors().get(b), i);
    }
    var r = relations;
    var compatible =
        all.stream()
            .filter(
                c ->
                    r.facts().stream()
                        .allMatch(
                            f -> (c.colors().get(f.a()) == c.colors().get(f.b())) == f.equal()))
            .toList();
    var domains = new ArrayList<Integer>();
    for (int i = 0; i < 6; i++) domains.add(witness.colors().get(i).mask() | random.nextInt(8));
    compatible =
        compatible.stream()
            .filter(
                c ->
                    java.util.stream.IntStream.range(0, 6)
                        .allMatch(v -> (c.colors().get(v).mask() & domains.get(v)) != 0))
            .toList();
    var state = new DeductionState(g, domains, r);
    for (var rule : List.of(new RelationPropagationRule(), new ImplicationChainRule()))
      for (var a : rule.find(state))
        for (var c : compatible) assertTrue(holds(c, a.conclusion()), rule.id() + " " + a);
  }
}
