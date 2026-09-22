package io.threecolor.deduction;

import java.util.*;

public record AvailableDeduction(
    String ruleId,
    int tier,
    RuleApplication application,
    ApplicationEffort effort,
    List<Deduction> evidence) {
  public AvailableDeduction {
    evidence = List.copyOf(evidence);
  }

  public String key() {
    return application.conclusion().toString();
  }

  public static Comparator<AvailableDeduction> order() {
    return Comparator.comparingDouble((AvailableDeduction d) -> d.effort().score())
        .thenComparing(AvailableDeduction::ruleId)
        .thenComparingInt(d -> d.application().node())
        .thenComparing(AvailableDeduction::key)
        .thenComparing(d -> d.application().witnesses().toString());
  }
}
