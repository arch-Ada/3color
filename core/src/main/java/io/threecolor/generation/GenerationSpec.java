package io.threecolor.generation;

import io.threecolor.difficulty.DifficultyBand;

public record GenerationSpec(
    long seed,
    int nodeCount,
    DifficultyBand targetDifficulty,
    double targetAverageDegree,
    int minDegree,
    int maxDegree,
    double maximumTriangleRatio,
    boolean requireUniqueSolution,
    boolean requireHumanSolvable,
    int maxAttempts) {
  public GenerationSpec {
    if (nodeCount < 4 || nodeCount > 80)
      throw new IllegalArgumentException("nodeCount must be 4..80");
    if (targetDifficulty == null) throw new IllegalArgumentException("Difficulty required");
    if (!Double.isFinite(targetAverageDegree)
        || targetAverageDegree < 1.5
        || targetAverageDegree > 5)
      throw new IllegalArgumentException("Average degree must be 1.5..5");
    if (minDegree < 1 || maxDegree < minDegree || maxDegree > 12)
      throw new IllegalArgumentException("Invalid degree limits");
    if (!Double.isFinite(maximumTriangleRatio)
        || maximumTriangleRatio < 0
        || maximumTriangleRatio > 2)
      throw new IllegalArgumentException("Triangle ratio must be 0..2");
    if (maxAttempts < 1 || maxAttempts > 128)
      throw new IllegalArgumentException("maxAttempts must be 1..128");
  }

  public static GenerationSpec defaults(long seed, int nodes, DifficultyBand difficulty) {
    return new GenerationSpec(seed, nodes, difficulty, 2.8, 1, 6, 0.65, true, true, 48);
  }
}
