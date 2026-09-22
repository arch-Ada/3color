package io.threecolor.generation;

import java.util.*;

public record GenerationFailure(
    String code,
    int attempts,
    Map<String, Integer> rejectionCounts,
    GenerationDiagnostics diagnostics)
    implements GenerationOutcome {
  public GenerationFailure(String code, int attempts, Map<String, Integer> rejectionCounts) {
    this(code, attempts, rejectionCounts, null);
  }

  public GenerationFailure {
    rejectionCounts = Collections.unmodifiableMap(new TreeMap<>(rejectionCounts));
  }
}
