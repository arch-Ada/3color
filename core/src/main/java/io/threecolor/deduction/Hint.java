package io.threecolor.deduction;

import java.util.*;

public record Hint(
    Level level,
    Status status,
    Optional<Deduction> deduction,
    List<Deduction> supportingSteps,
    HintExplanation explanation) {
  public Hint {
    supportingSteps = List.copyOf(supportingSteps);
  }

  public Hint(Level level, Status status, Optional<Deduction> deduction, List<Deduction> support) {
    this(level, status, deduction, support, null);
  }

  public Hint(Level level, Status status, Optional<Deduction> deduction) {
    this(level, status, deduction, List.of(), null);
  }

  public enum Level {
    NUDGE,
    REASON,
    ANSWER
  }

  public enum Status {
    AVAILABLE,
    CORRECTION,
    SOLVED,
    NO_DEDUCTION,
    CONFLICT,
    BUDGET_EXHAUSTED
  }
}
