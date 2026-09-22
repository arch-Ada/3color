package io.threecolor.generation;

import io.threecolor.deduction.DeductionTrace;
import io.threecolor.difficulty.*;
import io.threecolor.model.*;
import io.threecolor.solve.UniquenessResult;
import java.util.*;

public record EvaluatedCandidate(
    SortedMap<NodeId, Color> clues,
    DeductionTrace trace,
    DifficultyReport report,
    DifficultyTarget.Match match,
    String key,
    UniquenessResult exactStatus) {
  public EvaluatedCandidate {
    clues = Collections.unmodifiableSortedMap(new TreeMap<>(clues));
  }

  public boolean certified() {
    return exactStatus == UniquenessResult.UNIQUE;
  }

  public static final Comparator<EvaluatedCandidate> RANDOM_BEAM_ORDER =
      Comparator.comparingDouble((EvaluatedCandidate e) -> e.match().distance())
          .thenComparingInt(e -> e.clues().size())
          .thenComparing(EvaluatedCandidate::key);
}
