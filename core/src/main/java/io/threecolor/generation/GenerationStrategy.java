package io.threecolor.generation;

public enum GenerationStrategy {
  RANDOM_BEAM,
  REASONING_GUIDED;

  public ClueSearchStrategy search() {
    return this == RANDOM_BEAM ? new RandomBeamClueSearch() : new ReasoningGuidedClueSearch();
  }
}
