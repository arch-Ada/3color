package io.threecolor.deduction;

import java.util.*;

public record FrontierSummary(
    int selectedFactId,
    int size,
    List<Double> efforts,
    boolean hypothesesDeferred,
    boolean budgetExhausted) {
  public FrontierSummary {
    efforts = List.copyOf(efforts);
  }

  public double easiestEffort() {
    return efforts.isEmpty() ? 0 : efforts.getFirst();
  }
}
