package io.threecolor.generation;

import io.threecolor.deduction.*;
import io.threecolor.difficulty.*;
import io.threecolor.model.*;
import io.threecolor.solve.*;

/** One cacheable clue-set evaluation. Exact certification and human analysis remain independent. */
public final class ClueEvaluator {
  public record Evaluation(SolveResult exact, DeductionTrace trace, DifficultyReport difficulty) {
    public boolean certified() {
      return exact.status() == UniquenessResult.UNIQUE;
    }
  }

  public Evaluation evaluate(GraphTopology graph, PartialColoring givens, long exactBudget) {
    var exact =
        new ExactSolver().uniqueness(graph, givens, exactBudget, SolutionEquivalence.LABELED);
    if (exact.status() != UniquenessResult.UNIQUE) return new Evaluation(exact, null, null);
    var trace = new DeductionEngine().solve(graph, givens);
    return new Evaluation(exact, trace, new DifficultyAnalyzer().analyze(trace));
  }
}
