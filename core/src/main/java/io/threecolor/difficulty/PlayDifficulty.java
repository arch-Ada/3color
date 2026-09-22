package io.threecolor.difficulty;

import io.threecolor.deduction.*;

/** Provisional player-facing filter, separate from the mathematical proof level. */
public final class PlayDifficulty {
  public static final String VERSION = "play-difficulty-v1";

  public enum Category {
    VERY_EASY,
    EASY,
    MEDIUM,
    CHALLENGING;

    public DifficultyBand sourceBand() {
      return this == CHALLENGING ? DifficultyBand.MEDIUM : DifficultyBand.valueOf(name());
    }
  }

  public record Rating(
      String modelVersion,
      Category category,
      String rejection,
      int contradictionCount,
      int maximumContradictionSteps,
      int maximumContradictionVertices,
      int maximumPathLength) {
    public boolean accepted() {
      return category != null;
    }
  }

  public static Rating rate(DifficultyBand source, DeductionTrace trace) {
    int count = 0, steps = 0, vertices = 0, path = 0;
    boolean invalid = false;
    for (var d : trace.steps()) {
      path = Math.max(path, d.effort().pathLength());
      if (!d.ruleId().equals("bounded_contradiction")) continue;
      count++;
      var evidence = d.hypothesisEvidence();
      steps = Math.max(steps, (int) evidence.stream().filter(s -> s.tier() > 0).count());
      vertices = Math.max(vertices, ProofCore.vertices(evidence).size());
      for (var fact : evidence) path = Math.max(path, fact.effort().pathLength());
      invalid |=
          evidence.stream().filter(s -> s.ruleId().equals("hypothesis")).count() != 1
              || evidence.stream().anyMatch(s -> !s.hypothesisEvidence().isEmpty())
              || evidence.stream().noneMatch(ProofCore::contradiction);
    }
    Category category = null;
    String rejection = null;
    if (trace.status() != DeductionTrace.Status.SOLVED) rejection = "INCOMPLETE_TRACE";
    else if (source == DifficultyBand.VERY_EASY || source == DifficultyBand.EASY)
      category = Category.valueOf(source.name());
    else if (source != DifficultyBand.MEDIUM) rejection = "SOURCE_TIER_UNAVAILABLE";
    else if (invalid || count == 0) rejection = "INVALID_CONTRADICTION_CERTIFICATE";
    else if (count > 6 || steps > 12 || vertices > 14 || path > 10)
      rejection = "EXPLANATION_TOO_LARGE";
    else
      category =
          count <= 2 && steps <= 6 && vertices <= 8 && path <= 6
              ? Category.MEDIUM
              : Category.CHALLENGING;
    return new Rating(VERSION, category, rejection, count, steps, vertices, path);
  }
}
