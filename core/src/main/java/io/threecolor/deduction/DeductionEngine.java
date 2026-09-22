package io.threecolor.deduction;

import io.threecolor.model.*;
import java.util.*;

/** Deterministic human proof search. The exact solver is deliberately not a dependency. */
public final class DeductionEngine {
  private final List<DeductionRule> rules;

  public DeductionEngine() {
    this(
        List.of(
            new LockedEdgeRule(),
            new DiamondRule(),
            new NeighborhoodParityRule(),
            new ParityRule(),
            new ImplicationChainRule()));
  }

  public DeductionEngine(List<DeductionRule> rules) {
    var all = new ArrayList<DeductionRule>();
    all.add(new AdjacencyRule());
    all.add(new RelationPropagationRule());
    all.addAll(rules);
    this.rules =
        all.stream()
            .sorted(Comparator.comparingInt(DeductionRule::tier).thenComparing(DeductionRule::id))
            .toList();
  }

  public DeductionTrace solve(GraphTopology graph, PartialColoring givens) {
    return solve(graph, givens, DeductionConfiguration.defaults());
  }

  public DeductionTrace solve(
      GraphTopology graph, PartialColoring givens, int tier, int hypotheses) {
    return solve(graph, givens, DeductionConfiguration.defaults().withLevel(tier, hypotheses));
  }

  public DeductionTrace solve(
      GraphTopology graph, PartialColoring givens, DeductionConfiguration config) {
    givens.validateNodes(graph);
    var masks = new ArrayList<Integer>();
    for (int i = 0; i < graph.nodeCount(); i++) masks.add(7);
    var run = new Run(new DeductionState(graph, masks), config, new Work());
    givens.colors().forEach((n, c) -> run.record(initial("given", n.value(), c.mask())));
    return run.finish();
  }

  /** Work includes unsuccessful hypothesis branches, not only the returned proof slice. */
  public record Measured(DeductionTrace trace, int states, int facts) {}

  public Measured solveMeasured(GraphTopology graph, PartialColoring givens) {
    givens.validateNodes(graph);
    var work = new Work();
    var run =
        new Run(
            new DeductionState(graph, Collections.nCopies(graph.nodeCount(), 7)),
            DeductionConfiguration.defaults(),
            work);
    givens.colors().forEach((n, c) -> run.record(initial("given", n.value(), c.mask())));
    var trace = run.finish();
    return new Measured(trace, work.states, work.facts);
  }

  /** Same engine, stopped after one current-frontier decision (including bounded hypotheses). */
  public DeductionTrace next(GraphTopology graph, PartialColoring givens) {
    givens.validateNodes(graph);
    var run =
        new Run(
            new DeductionState(graph, Collections.nCopies(graph.nodeCount(), 7)),
            DeductionConfiguration.defaults(),
            new Work());
    givens.colors().forEach((n, c) -> run.record(initial("given", n.value(), c.mask())));
    return run.finish(true);
  }

  /** Follow deductions until the first new determined colour, retaining its proof. */
  public DeductionTrace nextColor(GraphTopology graph, PartialColoring givens) {
    givens.validateNodes(graph);
    var run =
        new Run(
            new DeductionState(graph, Collections.nCopies(graph.nodeCount(), 7)),
            DeductionConfiguration.defaults(),
            new Work());
    givens.colors().forEach((n, c) -> run.record(initial("given", n.value(), c.mask())));
    return run.finish(false, true);
  }

  /** Explicit cheap closure for inspection/testing; no independent propagation implementation. */
  public DeductionTrace cheapClosure(GraphTopology graph, PartialColoring givens) {
    return solve(graph, givens, 1, 0);
  }

  /** Research entry point: initial domains/relations are explicit assumptions of this trace. */
  public DeductionTrace solve(DeductionState initial, DeductionConfiguration config) {
    return new Run(initial, config, new Work()).finish();
  }

  /**
   * Inspect every configured polynomial rule; arbitrary hypotheses are deferred, never miscounted.
   */
  public DeductionFrontier frontier(DeductionState state, int maxTier) {
    return polynomial(state, DeductionConfiguration.defaults().withLevel(maxTier, 0), null);
  }

  private DeductionFrontier polynomial(
      DeductionState state, DeductionConfiguration config, Run run) {
    var out = new ArrayList<AvailableDeduction>();
    for (var rule : rules)
      if (rule.tier() <= config.maxTier()) {
        DeductionRule actual =
            rule instanceof ImplicationChainRule
                ? new ImplicationChainRule(config.maximumImplicationLength())
                : rule;
        for (var raw : actual.find(state)) {
          var a = normalize(raw, state);
          if (a == null) continue;
          int depth =
              run == null
                  ? 0
                  : run.premises(rule.id(), a).stream()
                      .mapToInt(id -> run.depths.get(id))
                      .max()
                      .orElse(0);
          out.add(
              new AvailableDeduction(
                  rule.id(),
                  rule.tier(),
                  a,
                  config.effortModel().measure(rule.id(), rule.tier(), a, depth, 0),
                  List.of()));
        }
      }
    return DeductionFrontier.canonical(out, config.maxTier() >= 4, false);
  }

  private RuleApplication normalize(RuleApplication a, DeductionState s) {
    var refs = new TreeSet<>(a.relationPremises());
    LogicalConclusion c = a.conclusion();
    if (c instanceof LogicalConclusion.NarrowDomain d) {
      if (d.mask() == s.mask(d.node())) return null;
      if ((d.mask() & ~s.mask(d.node())) != 0)
        throw new IllegalArgumentException("Rule expands a domain");
    }
    if (c instanceof LogicalConclusion.Equal e) {
      if (s.relations().equal(e.a(), e.b())
          || s.mask(e.a()) == s.mask(e.b()) && Integer.bitCount(s.mask(e.a())) == 1) return null;
      int x = s.relations().representative(e.a()), y = s.relations().representative(e.b());
      refs.addAll(s.relations().equalityProof(e.a(), x));
      refs.addAll(s.relations().equalityProof(e.b(), y));
      c = new LogicalConclusion.Equal(x, y);
    }
    if (c instanceof LogicalConclusion.NotEqual e) {
      if (s.relations().notEqual(e.a(), e.b()) || (s.mask(e.a()) & s.mask(e.b())) == 0) return null;
      int x = s.relations().representative(e.a()), y = s.relations().representative(e.b());
      refs.addAll(s.relations().equalityProof(e.a(), x));
      refs.addAll(s.relations().equalityProof(e.b(), y));
      c = new LogicalConclusion.NotEqual(x, y);
    }
    return new RuleApplication(
        c, a.witnesses(), a.arguments(), List.copyOf(refs), a.implicationChain());
  }

  private static AvailableDeduction initial(String rule, int node, int mask) {
    var a = new RuleApplication(node, mask, List.of(), Map.of("colorMask", mask));
    return new AvailableDeduction(
        rule, 0, a, new ApplicationEffort(0, 0, 0, 0, 0, 0, 0), List.of());
  }

  private static final class Work {
    int facts, states;
  }

  private final class Run {
    final GraphTopology graph;
    final int[] masks, last;
    final DeductionConfiguration config;
    final Work work;
    RelationKnowledge relations;
    final List<Deduction> steps = new ArrayList<>();
    final List<Integer> depths = new ArrayList<>();
    final List<FrontierSummary> frontiers = new ArrayList<>();
    int tried;
    boolean contradiction, exhausted;

    Run(DeductionState initial, DeductionConfiguration config, Work work) {
      this.graph = initial.graph();
      this.config = config;
      this.work = work;
      masks = initial.domains().stream().mapToInt(Integer::intValue).toArray();
      last = new int[masks.length];
      Arrays.fill(last, -1);
      relations = new RelationKnowledge(graph);
      for (int v = 0; v < masks.length; v++) record(initial("domain_initialization", v, masks[v]));
      // Rebase branch snapshot relation IDs into the branch-local proof DAG.
      for (var f : initial.relations().facts()) {
        var a =
            new RuleApplication(
                f.equal()
                    ? new LogicalConclusion.Equal(f.a(), f.b())
                    : new LogicalConclusion.NotEqual(f.a(), f.b()),
                List.of(f.a(), f.b()),
                Map.of());
        record(
            new AvailableDeduction(
                "relation_snapshot", 0, a, new ApplicationEffort(0, 2, 0, 0, 0, 2, 0), List.of()));
      }
    }

    DeductionState state() {
      return new DeductionState(graph, Arrays.stream(masks).boxed().toList(), relations);
    }

    List<Integer> premises(String ruleId, RuleApplication a) {
      var refs = new TreeSet<>(a.relationPremises());
      // These rules use topology, not the witnesses' current color domains.
      // Retain canonical equality bridges, but not unrelated domain histories.
      if (ruleId.equals("diamond_equality")
          || ruleId.equals("neighborhood_parity")
          || ruleId.equals("relation_snapshot")) return List.copyOf(refs);
      int node = a.node();
      if (last[node] >= 0) refs.add(last[node]);
      if (!ruleId.equals("bounded_contradiction"))
        for (int v : a.witnesses()) if (last[v] >= 0) refs.add(last[v]);
      if (a.conclusion() instanceof LogicalConclusion.Equal e) {
        if (last[e.b()] >= 0) refs.add(last[e.b()]);
      }
      if (a.conclusion() instanceof LogicalConclusion.NotEqual e) {
        if (last[e.b()] >= 0) refs.add(last[e.b()]);
      }
      return List.copyOf(refs);
    }

    void record(AvailableDeduction candidate) {
      if (work.facts >= config.maximumAtomicFacts()) {
        exhausted = true;
        return;
      }
      var a = candidate.application();
      int node = a.node(), before = masks[node], after = before, id = steps.size();
      var refs = premises(candidate.ruleId(), a);
      int depth =
          refs.stream().mapToInt(i -> depths.get(i)).max().orElse(0)
              + (candidate.tier() > 0 ? 1 : 0);
      switch (a.conclusion()) {
        case LogicalConclusion.NarrowDomain d -> {
          after = d.mask();
          masks[node] = after;
          last[node] = id;
        }
        case LogicalConclusion.Equal e -> relations = relations.with(e.a(), e.b(), true, id);
        case LogicalConclusion.NotEqual e -> relations = relations.with(e.a(), e.b(), false, id);
        case LogicalConclusion.Contradiction c -> {
          contradiction = true;
          after = 0;
        }
      }
      steps.add(
          new Deduction(
              id,
              candidate.ruleId(),
              candidate.tier(),
              node,
              before,
              after,
              refs,
              a.witnesses(),
              candidate.ruleId(),
              a.arguments(),
              candidate.evidence(),
              a.conclusion(),
              candidate.effort(),
              a.implicationChain()));
      depths.add(depth);
      work.facts++;
    }

    DeductionTrace finish() {
      return finish(false);
    }

    DeductionTrace finish(boolean singleDecision) {
      return finish(singleDecision, false);
    }

    DeductionTrace finish(boolean singleDecision, boolean untilColor) {
      while (true) {
        if (contradiction || Arrays.stream(masks).anyMatch(m -> m == 0))
          return trace(DeductionTrace.Status.CONTRADICTION);
        if (Thread.currentThread().isInterrupted()
            || work.facts >= config.maximumAtomicFacts()
            || work.states >= config.maximumDecisionStates())
          return trace(DeductionTrace.Status.BUDGET_EXHAUSTED);
        work.states++;
        // Check relational/physical consistency before accepting a completely colored graph.
        var mechanical = new ArrayList<RuleApplication>();
        mechanical.addAll(new AdjacencyRule().find(state()));
        mechanical.addAll(new RelationPropagationRule().find(state()));
        if (mechanical.isEmpty() && Arrays.stream(masks).allMatch(m -> Integer.bitCount(m) == 1))
          return trace(DeductionTrace.Status.SOLVED);
        var frontier = polynomial(state(), config, this);
        if (frontier.deductions().isEmpty() && config.maxTier() >= 4) frontier = hypotheses();
        if (frontier.deductions().isEmpty())
          return trace(
              exhausted || frontier.budgetExhausted()
                  ? DeductionTrace.Status.BUDGET_EXHAUSTED
                  : DeductionTrace.Status.STALLED);
        if (work.facts >= config.maximumAtomicFacts())
          return trace(DeductionTrace.Status.BUDGET_EXHAUSTED);
        frontiers.add(frontier.summary(steps.size()));
        var chosen =
            singleDecision
                ? frontier.deductions().stream()
                    .min(
                        Comparator.comparingDouble((AvailableDeduction d) -> d.effort().score())
                            .thenComparingInt(
                                d -> new TreeSet<>(d.application().witnesses()).size())
                            .thenComparingInt(
                                d -> d.effort().pathLength() + d.effort().implicationLength())
                            .thenComparingInt(
                                d ->
                                    d.application().conclusion()
                                            instanceof LogicalConclusion.NarrowDomain
                                        ? 0
                                        : 1)
                            .thenComparing(AvailableDeduction.order()))
                    .orElseThrow()
                : frontier.easiest().orElseThrow();
        record(chosen);
        if (singleDecision
            || untilColor
                && chosen.application().conclusion() instanceof LogicalConclusion.NarrowDomain d
                && Integer.bitCount(d.mask()) == 1)
          return trace(
              contradiction || Arrays.stream(masks).anyMatch(m -> m == 0)
                  ? DeductionTrace.Status.CONTRADICTION
                  : DeductionTrace.Status.STALLED);
      }
    }

    DeductionFrontier hypotheses() {
      var found = new ArrayList<AvailableDeduction>();
      outer:
      for (int v = 0; v < masks.length; v++)
        if (Integer.bitCount(masks[v]) > 1)
          for (int bit = 1; bit <= 4; bit <<= 1)
            if ((masks[v] & bit) != 0) {
              if (tried >= config.hypothesisBudget()
                  || work.facts >= config.maximumAtomicFacts()
                  || work.states >= config.maximumDecisionStates()) {
                exhausted = true;
                break outer;
              }
              tried++;
              var branch = new Run(state(), config.withLevel(3, 0), work);
              branch.record(initial("hypothesis", v, bit));
              var proof = branch.finish();
              if (proof.status() == DeductionTrace.Status.CONTRADICTION) {
                var core = ProofCore.contradictionSlice(proof.steps());
                var witnesses = ProofCore.vertices(core);
                var refs = new TreeSet<Integer>();
                for (var fact : core) {
                  if (fact.ruleId().equals("domain_initialization") && last[fact.node()] >= 0)
                    refs.add(last[fact.node()]);
                  if (fact.ruleId().equals("relation_snapshot"))
                    for (var relation : relations.facts()) {
                      LogicalConclusion conclusion =
                          relation.equal()
                              ? new LogicalConclusion.Equal(relation.a(), relation.b())
                              : new LogicalConclusion.NotEqual(relation.a(), relation.b());
                      if (fact.conclusion().equals(conclusion)) refs.add(relation.factId());
                    }
                }
                var a =
                    new RuleApplication(
                        new LogicalConclusion.NarrowDomain(v, masks[v] & ~bit),
                        witnesses,
                        Map.of("assumedMask", bit),
                        List.copyOf(refs),
                        List.of());
                int depth =
                    premises("bounded_contradiction", a).stream()
                        .mapToInt(i -> depths.get(i))
                        .max()
                        .orElse(0);
                found.add(
                    new AvailableDeduction(
                        "bounded_contradiction",
                        4,
                        a,
                        config
                            .effortModel()
                            .measure("bounded_contradiction", 4, a, depth, core.size()),
                        core));
              }
            }
      return DeductionFrontier.canonical(found, false, exhausted);
    }

    DeductionTrace trace(DeductionTrace.Status status) {
      return new DeductionTrace(
          steps,
          Arrays.stream(masks).boxed().toList(),
          status,
          frontiers.stream().map(FrontierSummary::size).toList(),
          tried,
          frontiers,
          ReasoningEvents.from(steps));
    }
  }
}
