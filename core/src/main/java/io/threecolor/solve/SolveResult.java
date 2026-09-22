package io.threecolor.solve;

import io.threecolor.model.Coloring;
import java.util.List;

public record SolveResult(
    UniquenessResult status, List<Coloring> solutions, SolveStats stats, boolean budgetExhausted) {
  public SolveResult {
    solutions = List.copyOf(solutions);
  }
}
