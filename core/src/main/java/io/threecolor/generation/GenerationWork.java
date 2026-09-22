package io.threecolor.generation;

import java.util.*;

/** Small request-owned ledger. Search limits remain per source; final work has a separate cap. */
public final class GenerationWork {
  public static final int FINAL_CANDIDATES = 10;
  private final int finalLimit;
  private final Map<String, Long> counters = new TreeMap<>();
  private final List<String> sources = new ArrayList<>();

  public void source(String id) {
    sources.add(id);
  }

  public List<String> sources() {
    return List.copyOf(sources);
  }

  final Set<String> candidates = new HashSet<>();

  public GenerationWork() {
    this(FINAL_CANDIDATES);
  }

  public GenerationWork(int finalLimit) {
    if (finalLimit < 0) throw new IllegalArgumentException("Negative final allowance");
    this.finalLimit = finalLimit;
  }

  public void add(String key, long amount) {
    counters.merge(key, amount, Long::sum);
  }

  public void count(String key) {
    add(key, 1);
  }

  public long get(String key) {
    return counters.getOrDefault(key, 0L);
  }

  public Map<String, Long> snapshot() {
    return Collections.unmodifiableMap(new TreeMap<>(counters));
  }

  public boolean beginFinal() {
    if (Thread.currentThread().isInterrupted() || get("finalCandidates") >= finalLimit)
      return false;
    count("finalCandidates");
    return true;
  }
}
