package io.threecolor.deduction;

import io.threecolor.model.GraphTopology;
import java.util.*;

/** Language-neutral projection of a proof, never a second solver or a topology mutation. */
public record HintExplanation(
    String reasoningType,
    List<Integer> primaryTargets,
    LogicalConclusion conclusion,
    List<Integer> focusVertices,
    List<FocusEdge> focusEdges,
    List<Integer> orderedPath,
    Assignment assumption,
    Integer contradictionPoint,
    List<WalkthroughStep> walkthrough) {
  public record FocusEdge(int a, int b) implements Comparable<FocusEdge> {
    public FocusEdge {
      if (a > b) {
        int t = a;
        a = b;
        b = t;
      }
    }

    public int compareTo(FocusEdge other) {
      int c = Integer.compare(a, other.a);
      return c == 0 ? Integer.compare(b, other.b) : c;
    }
  }

  public record Assignment(int node, int mask) {}

  public record WalkthroughStep(
      String kind,
      Integer node,
      Integer mask,
      Deduction deduction,
      List<Integer> vertices,
      List<FocusEdge> edges) {
    public WalkthroughStep {
      vertices = List.copyOf(vertices);
      edges = List.copyOf(edges);
    }
  }

  public HintExplanation {
    primaryTargets = List.copyOf(primaryTargets);
    focusVertices = List.copyOf(focusVertices);
    focusEdges = List.copyOf(focusEdges);
    orderedPath = List.copyOf(orderedPath);
    walkthrough = List.copyOf(walkthrough);
  }

  public static HintExplanation from(GraphTopology graph, Deduction root, List<Deduction> proof) {
    var targets =
        switch (root.conclusion()) {
          case LogicalConclusion.NarrowDomain n -> List.of(n.node());
          case LogicalConclusion.Equal e -> List.of(e.a(), e.b());
          case LogicalConclusion.NotEqual e -> List.of(e.a(), e.b());
          case LogicalConclusion.Contradiction c -> root.witnesses();
        };
    var walkthrough = new ArrayList<WalkthroughStep>();
    // Include every prerequisite in the dependency slice, not only the final rule.
    for (var step : proof) if (step.tier() > 0) append(graph, step, proof, walkthrough);
    var edges = new TreeSet<FocusEdge>();
    collectEdges(graph, proof, edges);
    Assignment assumption =
        root.arguments().containsKey("assumedMask")
            ? new Assignment(root.node(), root.arguments().get("assumedMask"))
            : null;
    Integer conflict =
        walkthrough.stream()
            .filter(s -> s.kind().equals("CONTRADICTION"))
            .map(WalkthroughStep::node)
            .filter(Objects::nonNull)
            .reduce((a, b) -> b)
            .orElse(null);
    List<Integer> path =
        root.ruleId().equals("two_color_parity")
            ? root.witnesses()
            : root.ruleId().equals("neighborhood_parity")
                ? root.witnesses().stream()
                    .filter(v -> !v.equals(root.arguments().get("center")))
                    .toList()
                : List.of();
    return new HintExplanation(
        root.ruleId(),
        targets,
        root.conclusion(),
        ProofCore.vertices(proof),
        List.copyOf(edges),
        path,
        assumption,
        conflict,
        compact(graph, walkthrough));
  }

  /** Atomic, scope-preserving segment for offline dependency slicing. */
  public static List<WalkthroughStep> segment(
      GraphTopology graph, Deduction step, List<Deduction> proof) {
    var result = new ArrayList<WalkthroughStep>();
    if (step.tier() > 0) append(graph, step, proof, result);
    return List.copyOf(result);
  }

  /** Presentation only: the supporting proof remains unchanged. */
  private static List<WalkthroughStep> compact(GraphTopology graph, List<WalkthroughStep> input) {
    var result = new ArrayList<WalkthroughStep>();
    for (var current : input) {
      var previous = result.isEmpty() ? null : result.getLast();
      var a = previous == null ? null : previous.deduction();
      var b = current.deduction();
      if (a != null
          && b != null
          && previous.kind().equals("DEDUCTION")
          && current.kind().equals("DEDUCTION")
          && a.ruleId().equals("adjacent_color_elimination")
          && b.ruleId().equals(a.ruleId())
          && a.node() == b.node()
          && a.beforeMask() == 7
          && a.afterMask() == b.beforeMask()
          && Integer.bitCount(b.afterMask()) == 1) {
        int left = a.arguments().get("sourceNode"), right = b.arguments().get("sourceNode");
        boolean triangle = graph.adjacent(left, right);
        var step =
            new Deduction(
                b.id(),
                b.ruleId(),
                b.tier(),
                b.node(),
                7,
                b.afterMask(),
                b.premises(),
                List.of(left, right),
                triangle ? "triangle_completion" : "two_colored_neighbors",
                Map.of(
                    "neighbourA",
                    left,
                    "neighbourB",
                    right,
                    "maskA",
                    a.arguments().get("colorMask"),
                    "maskB",
                    b.arguments().get("colorMask")),
                List.of(),
                b.conclusion(),
                b.effort(),
                List.of());
        var edges = new TreeSet<FocusEdge>(previous.edges());
        edges.addAll(current.edges());
        if (triangle) edges.add(new FocusEdge(left, right));
        result.set(
            result.size() - 1,
            new WalkthroughStep(
                "DEDUCTION",
                b.node(),
                b.afterMask(),
                step,
                List.of(b.node(), left, right),
                List.copyOf(edges)));
      } else result.add(current);
    }
    return List.copyOf(result);
  }

  private static void append(
      GraphTopology graph, Deduction d, List<Deduction> scope, List<WalkthroughStep> out) {
    if (d.ruleId().equals("bounded_contradiction")) {
      for (var branch : d.hypothesisEvidence())
        if (branch.tier() > 0 || branch.ruleId().equals("hypothesis"))
          append(graph, branch, d.hypothesisEvidence(), out);
    } else if (!d.implicationChain().isEmpty()) {
      var first = d.implicationChain().getFirst();
      out.add(
          new WalkthroughStep(
              "ASSUMPTION",
              first.source(),
              first.sourceMask(),
              null,
              List.of(first.source()),
              List.of()));
      for (var link : d.implicationChain()) {
        var linkEdges = new TreeSet<FocusEdge>();
        var known = relationsBefore(graph, scope, d.id());
        if (!known.equal(link.source(), link.target()))
          inequalityEdge(graph, known, link.source(), link.target(), linkEdges);
        var edge = List.copyOf(linkEdges);
        out.add(
            new WalkthroughStep(
                link.targetMask() == 0 ? "CONTRADICTION" : "IMPLICATION",
                link.target(),
                link.targetMask(),
                null,
                withEndpoints(List.of(link.source(), link.target()), edge),
                edge));
      }
      if (d.implicationChain().getLast().targetMask() != 0)
        out.add(
            new WalkthroughStep(
                "CONTRADICTION", first.source(), 0, null, List.of(first.source()), List.of()));
    }
    var edges = new TreeSet<FocusEdge>();
    atomicEdges(graph, d, relationsBefore(graph, scope, d.id()), edges);
    out.add(
        new WalkthroughStep(
            d.ruleId().equals("hypothesis")
                ? "ASSUMPTION"
                : ProofCore.contradiction(d)
                    ? "CONTRADICTION"
                    : d.hypothesisEvidence().isEmpty() && d.implicationChain().isEmpty()
                        ? "DEDUCTION"
                        : "CONCLUSION",
            d.conclusion() instanceof LogicalConclusion.Contradiction
                ? d.witnesses().stream().findFirst().orElse(null)
                : d.node(),
            d.afterMask(),
            d,
            withEndpoints(ProofCore.vertices(List.of(d)), edges),
            List.copyOf(edges)));
  }

  private static List<Integer> withEndpoints(
      Collection<Integer> vertices, Collection<FocusEdge> edges) {
    var result = new TreeSet<>(vertices);
    for (var edge : edges) {
      result.add(edge.a());
      result.add(edge.b());
    }
    return List.copyOf(result);
  }

  private static void collectEdges(GraphTopology graph, List<Deduction> proof, Set<FocusEdge> out) {
    for (var d : proof) {
      atomicEdges(graph, d, relationsBefore(graph, proof, d.id()), out);
      collectEdges(graph, d.hypothesisEvidence(), out);
    }
  }

  private static RelationKnowledge relationsBefore(
      GraphTopology graph, List<Deduction> proof, int id) {
    var relations = new RelationKnowledge(graph);
    for (var d : proof)
      if (d.id() < id) {
        if (d.conclusion() instanceof LogicalConclusion.Equal e)
          relations = relations.with(e.a(), e.b(), true, d.id());
        if (d.conclusion() instanceof LogicalConclusion.NotEqual e)
          relations = relations.with(e.a(), e.b(), false, d.id());
      }
    return relations;
  }

  private static void edge(GraphTopology graph, int a, int b, Set<FocusEdge> out) {
    if (graph.adjacent(a, b)) out.add(new FocusEdge(a, b));
  }

  private static void inequalityEdge(
      GraphTopology graph, RelationKnowledge relations, int a, int b, Set<FocusEdge> out) {
    // Same physical-edge-first order as RelationKnowledge. Inferred inequality proofs
    // without a physical bridge contribute their own structural edges through premises.
    for (var e : graph.edges()) {
      int u = e.a().value(), v = e.b().value();
      if (relations.equal(a, u) && relations.equal(b, v)
          || relations.equal(a, v) && relations.equal(b, u)) {
        out.add(new FocusEdge(u, v));
        return;
      }
    }
  }

  private static void atomicEdges(
      GraphTopology graph, Deduction d, RelationKnowledge relations, Set<FocusEdge> out) {
    if (d.tier() == 0 || d.ruleId().equals("bounded_contradiction")) return;
    if (!d.implicationChain().isEmpty()) {
      for (var link : d.implicationChain())
        if (!relations.equal(link.source(), link.target()))
          inequalityEdge(graph, relations, link.source(), link.target(), out);
      return;
    }
    var w = d.witnesses();
    switch (d.ruleId()) {
      case "adjacent_color_elimination" ->
          edge(graph, d.node(), d.arguments().get("sourceNode"), out);
      case "relation_propagation" -> {
        if (d.conclusion() instanceof LogicalConclusion.Contradiction && !w.isEmpty())
          inequalityEdge(graph, relations, w.getFirst(), w.getFirst(), out);
        else if (d.arguments().getOrDefault("equality", 0) == 0
            && d.arguments().containsKey("sourceNode"))
          inequalityEdge(graph, relations, d.node(), d.arguments().get("sourceNode"), out);
      }
      case "diamond_equality" -> {
        int a = d.arguments().get("edgeA"), b = d.arguments().get("edgeB");
        edge(graph, a, b, out);
        for (int v : w)
          if (v != a && v != b) {
            edge(graph, a, v, out);
            edge(graph, b, v, out);
          }
      }
      case "locked_two_color_edge" -> {
        edge(graph, w.get(0), w.get(1), out);
        for (int v : w) edge(graph, d.node(), v, out);
      }
      case "two_color_parity", "neighborhood_parity" -> {
        boolean neighborhood = d.ruleId().equals("neighborhood_parity");
        int center = d.arguments().getOrDefault("center", -1);
        var path = neighborhood ? w.stream().filter(v -> v != center).toList() : w;
        for (int i = 1; i < path.size(); i++) edge(graph, path.get(i - 1), path.get(i), out);
        if (neighborhood) for (int v : path) edge(graph, center, v, out);
        if (!path.isEmpty()) {
          if (neighborhood && ProofCore.contradiction(d))
            edge(graph, path.getFirst(), path.getLast(), out);
          else if (!neighborhood) {
            edge(graph, d.node(), path.getFirst(), out);
            edge(graph, d.node(), path.getLast(), out);
          }
        }
      }
      default -> {}
    }
  }
}
