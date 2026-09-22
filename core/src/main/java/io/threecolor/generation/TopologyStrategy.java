package io.threecolor.generation;

import java.util.Random;

/** The only remaining physical graph generator. */
public enum TopologyStrategy {
  SPARSE_GRID;

  record Prepared(TopologyCandidate candidate, Random clueRandom) {}

  Prepared prepare(GenerationSpec spec, int attempt) {
    return new SparseGridTopologyGenerator().prepare(spec, attempt);
  }

  public TopologyCandidate generate(GenerationSpec spec, int attempt) {
    return prepare(spec, attempt).candidate();
  }
}
