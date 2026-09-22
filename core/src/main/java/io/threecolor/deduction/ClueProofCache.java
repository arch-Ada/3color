package io.threecolor.deduction;

import io.threecolor.model.ExplanationModel;
import io.threecolor.model.Puzzle;
import java.util.*;
import java.util.function.Supplier;

/** Small process-local cache of completed logical proofs; never stores player assignments. */
final class ClueProofCache {
  private record Key(
      String logicalHash,
      ExplanationModel model,
      String family,
      String version,
      DeductionConfiguration config) {}

  private record Entry(DeductionTrace trace, int facts) {}

  private final LinkedHashMap<Key, Entry> entries = new LinkedHashMap<>(16, .75f, true);
  private final int maximumEntries, maximumFacts;
  private int facts;

  ClueProofCache(int maximumEntries, int maximumFacts) {
    if (maximumEntries < 1 || maximumFacts < 1)
      throw new IllegalArgumentException("Invalid cache limits");
    this.maximumEntries = maximumEntries;
    this.maximumFacts = maximumFacts;
  }

  DeductionTrace get(
      Puzzle puzzle,
      String family,
      DeductionConfiguration config,
      Supplier<DeductionTrace> loader) {
    // Layout/provenance are deliberately absent: this is logical evidence, not geometry validation.
    var key =
        new Key(
            puzzle.logicalHash(), puzzle.explanationModel(), family, ProofLevels.VERSION, config);
    if (!Thread.currentThread().isInterrupted()) {
      synchronized (this) {
        var entry = entries.get(key);
        if (entry != null) return entry.trace();
      }
    }
    // Compute outside the lock; unrelated requests do not queue behind a slow proof.
    var trace = loader.get();
    if (trace.status() != DeductionTrace.Status.SOLVED || Thread.currentThread().isInterrupted())
      return trace;
    int weight = weight(trace.steps());
    if (weight > maximumFacts) return trace;
    synchronized (this) {
      var previous = entries.put(key, new Entry(trace, weight));
      facts += weight - (previous == null ? 0 : previous.facts());
      while (entries.size() > maximumEntries || facts > maximumFacts) {
        var first = entries.entrySet().iterator();
        facts -= first.next().getValue().facts();
        first.remove();
      }
    }
    return trace;
  }

  private int weight(List<Deduction> steps) {
    int count = 0;
    for (var step : steps) {
      count += 1 + weight(step.hypothesisEvidence());
      if (count > maximumFacts) return maximumFacts + 1;
    }
    return count;
  }
}
