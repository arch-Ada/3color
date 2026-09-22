package io.threecolor.generation;

/** Deterministic attempt-seed derivation shared by bounded searches. */
public final class GenerationSeeds {
  private GenerationSeeds() {}

  public static long attemptSeed(long master, int attempt) {
    long z = master + 0x9e3779b97f4a7c15L * (attempt + 1L);
    z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
    z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
    return z ^ (z >>> 31);
  }
}
