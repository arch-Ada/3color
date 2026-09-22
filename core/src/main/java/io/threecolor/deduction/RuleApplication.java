package io.threecolor.deduction;

import java.util.*;

public record RuleApplication(
    LogicalConclusion conclusion,
    List<Integer> witnesses,
    Map<String, Integer> arguments,
    List<Integer> relationPremises,
    List<ImplicationLink> implicationChain) {
  public RuleApplication {
    witnesses = List.copyOf(witnesses);
    arguments = Collections.unmodifiableMap(new TreeMap<>(arguments));
    relationPremises = List.copyOf(relationPremises);
    implicationChain = List.copyOf(implicationChain);
  }

  public RuleApplication(
      int node, int mask, List<Integer> witnesses, Map<String, Integer> arguments) {
    this(
        new LogicalConclusion.NarrowDomain(node, mask), witnesses, arguments, List.of(), List.of());
  }

  public RuleApplication(
      LogicalConclusion conclusion, List<Integer> witnesses, Map<String, Integer> arguments) {
    this(conclusion, witnesses, arguments, List.of(), List.of());
  }

  public int node() {
    return switch (conclusion) {
      case LogicalConclusion.NarrowDomain d -> d.node();
      case LogicalConclusion.Equal e -> e.a();
      case LogicalConclusion.NotEqual e -> e.a();
      case LogicalConclusion.Contradiction c -> witnesses.isEmpty() ? 0 : witnesses.getFirst();
    };
  }

  public int afterMask() {
    return conclusion instanceof LogicalConclusion.NarrowDomain d ? d.mask() : 7;
  }
}
