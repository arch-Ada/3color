package io.threecolor.deduction;

import java.util.*;

public record DeductionTrace(
    List<Deduction> steps,
    List<Integer> finalDomains,
    Status status,
    List<Integer> availableDeductions,
    int hypothesesTried,
    List<FrontierSummary> frontiers,
    List<DeductionEvent> events) {
  public enum Status {
    SOLVED,
    STALLED,
    CONTRADICTION,
    BUDGET_EXHAUSTED
  }

  public DeductionTrace(
      List<Deduction> steps,
      List<Integer> finalDomains,
      Status status,
      List<Integer> availableDeductions,
      int hypothesesTried) {
    this(
        steps,
        finalDomains,
        status,
        availableDeductions,
        hypothesesTried,
        List.of(),
        ReasoningEvents.from(steps));
  }

  public DeductionTrace {
    frontiers = List.copyOf(frontiers);
    events = List.copyOf(events);
    steps = List.copyOf(steps);
    finalDomains = List.copyOf(finalDomains);
    availableDeductions = List.copyOf(availableDeductions);
  }
}
