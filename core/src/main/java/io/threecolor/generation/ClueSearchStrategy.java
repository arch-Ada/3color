package io.threecolor.generation;

import java.util.*;

public interface ClueSearchStrategy {
  GenerationStrategy id();

  EvaluatedCandidate search(CandidateVerifier verifier, Random random);

  static void retain(
      List<EvaluatedCandidate> beam,
      EvaluatedCandidate candidate,
      int width,
      Comparator<EvaluatedCandidate> order) {
    if (beam.stream().noneMatch(e -> e.key().equals(candidate.key()))) beam.add(candidate);
    beam.sort(order);
    while (beam.size() > width) beam.removeLast();
  }
}
