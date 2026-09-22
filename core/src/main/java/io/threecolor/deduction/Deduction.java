package io.threecolor.deduction;

import java.util.*;

/** Fact IDs reference earlier steps in this trace. Hypothesis evidence has its own local IDs. */
public record Deduction(
    int id,
    String ruleId,
    int tier,
    int node,
    int beforeMask,
    int afterMask,
    List<Integer> premises,
    List<Integer> witnesses,
    String explanationKey,
    Map<String, Integer> arguments,
    List<Deduction> hypothesisEvidence,
    LogicalConclusion conclusion,
    ApplicationEffort effort,
    List<ImplicationLink> implicationChain) {
  public Deduction(
      int id,
      String ruleId,
      int tier,
      int node,
      int beforeMask,
      int afterMask,
      List<Integer> premises,
      List<Integer> witnesses,
      String explanationKey,
      Map<String, Integer> arguments,
      List<Deduction> evidence) {
    this(
        id,
        ruleId,
        tier,
        node,
        beforeMask,
        afterMask,
        premises,
        witnesses,
        explanationKey,
        arguments,
        evidence,
        new LogicalConclusion.NarrowDomain(node, afterMask),
        EffortModel.defaults()
            .measure(
                ruleId,
                tier,
                new RuleApplication(node, afterMask, witnesses, arguments),
                0,
                evidence.size()),
        List.of());
  }

  public Deduction {
    implicationChain = List.copyOf(implicationChain);
    premises = List.copyOf(premises);
    witnesses = List.copyOf(witnesses);
    arguments = Collections.unmodifiableMap(new TreeMap<>(arguments));
    hypothesisEvidence = List.copyOf(hypothesisEvidence);
  }
}
