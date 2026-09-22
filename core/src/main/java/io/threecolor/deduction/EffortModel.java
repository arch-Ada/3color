package io.threecolor.deduction;

import java.util.*;

/** Calibratable human-effort heuristic; never based on exact-search cost. */
public record EffortModel(
    double witnessWeight, double pathWeight, double depthWeight, double hypothesisFactWeight) {
  public static EffortModel defaults() {
    return new EffortModel(.12, .18, .04, .04);
  }

  public ApplicationEffort measure(
      String rule, int tier, RuleApplication a, int depth, int evidenceSize) {
    int witnesses = (int) a.witnesses().stream().distinct().count();
    int path =
        rule.contains("parity")
            ? a.arguments().getOrDefault("pathLength", Math.max(0, witnesses - 1))
            : 0;
    int chain = a.implicationChain().size();
    int hypothesis = rule.equals("bounded_contradiction") ? 1 : 0;
    double base = tier == 0 ? 0 : tier == 1 ? 1 : tier == 2 ? 3 : tier == 3 ? 5 : 10;
    double score =
        tier <= 1
            ? base
            : base
                + witnessWeight * Math.max(0, witnesses - 3)
                + pathWeight * (path + chain)
                + depthWeight * depth
                + hypothesisFactWeight * evidenceSize;
    return new ApplicationEffort(
        score, witnesses, path, depth + (tier > 0 ? 1 : 0), chain, witnesses, hypothesis);
  }
}
