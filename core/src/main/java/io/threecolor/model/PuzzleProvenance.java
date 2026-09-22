package io.threecolor.model;

public record PuzzleProvenance(
    String generatorVersion, long masterSeed, int attemptIndex, String generationSpec) {
  public static PuzzleProvenance supplied() {
    return new PuzzleProvenance("supplied", 0, 0, "");
  }
}
