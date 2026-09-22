package io.threecolor.deduction;

import java.util.*;

/** Deterministic backward dependency slice. Nested branches retain separate fact-ID scopes. */
public final class ProofCore {
  private ProofCore() {}

  public static boolean contradiction(Deduction d) {
    return d.conclusion() instanceof LogicalConclusion.Contradiction
        || d.conclusion() instanceof LogicalConclusion.NarrowDomain n && n.mask() == 0;
  }

  public static List<Deduction> contradictionSlice(List<Deduction> steps) {
    return steps.stream()
        .filter(ProofCore::contradiction)
        .reduce((a, b) -> b)
        .map(d -> slice(steps, d.id()))
        .orElse(List.of());
  }

  public static List<Deduction> slice(List<Deduction> steps, int root) {
    var byId = new TreeMap<Integer, Deduction>();
    steps.forEach(d -> byId.put(d.id(), d));
    var needed = new TreeSet<Integer>();
    var pending = new ArrayDeque<Integer>();
    pending.push(root);
    while (!pending.isEmpty()) {
      int id = pending.pop();
      if (needed.add(id)) {
        var d = Objects.requireNonNull(byId.get(id), "Missing proof premise");
        d.premises().forEach(pending::push);
      }
    }
    return needed.stream()
        .map(byId::get)
        .map(
            d ->
                new Deduction(
                    d.id(),
                    d.ruleId(),
                    d.tier(),
                    d.node(),
                    d.beforeMask(),
                    d.afterMask(),
                    d.premises(),
                    d.witnesses(),
                    d.explanationKey(),
                    d.arguments(),
                    contradictionSlice(d.hypothesisEvidence()),
                    d.conclusion(),
                    d.effort(),
                    d.implicationChain()))
        .toList();
  }

  public static List<Integer> vertices(List<Deduction> proof) {
    var out = new TreeSet<Integer>();
    for (var d : proof) {
      switch (d.conclusion()) {
        case LogicalConclusion.NarrowDomain n -> out.add(n.node());
        case LogicalConclusion.Equal e -> {
          out.add(e.a());
          out.add(e.b());
        }
        case LogicalConclusion.NotEqual e -> {
          out.add(e.a());
          out.add(e.b());
        }
        case LogicalConclusion.Contradiction c -> {}
      }
      out.addAll(d.witnesses());
      for (var link : d.implicationChain()) {
        out.add(link.source());
        out.add(link.target());
      }
      out.addAll(vertices(d.hypothesisEvidence()));
    }
    return List.copyOf(out);
  }
}
