package io.threecolor.generation;

import io.threecolor.model.*;
import java.util.*;

/** Experimental six-neighbor deterministic beam search using named deficits and trace locality. */
public final class ReasoningGuidedClueSearch implements ClueSearchStrategy {
  public static final int NEIGHBORHOOD_SIZE = 6;

  public GenerationStrategy id() {
    return GenerationStrategy.REASONING_GUIDED;
  }

  public EvaluatedCandidate search(CandidateVerifier verifier, Random random) {
    var work = verifier.work();
    var budget = verifier.budget();
    Comparator<EvaluatedCandidate> rank =
        Comparator.comparing((EvaluatedCandidate e) -> !verifier.acceptable(e))
            .thenComparing(e -> !e.report().humanSolved())
            .thenComparingDouble(e -> e.match().constraintDistance())
            .thenComparing(EvaluatedCandidate.RANDOM_BEAM_ORDER);
    var clues = new TreeMap<NodeId, Color>();
    for (int v = 0; v < verifier.spec().nodeCount(); v++)
      clues.put(new NodeId(v), verifier.topology().solution().colors().get(v));
    var current = verifier.evaluate(clues);
    if (current == null) return null;
    EvaluatedCandidate best = verifier.acceptable(current) ? current : null;
    var beam = new ArrayList<EvaluatedCandidate>();
    beam.add(current);
    var order = bootstrapOrder(verifier.topology(), random);
    // Keep the sparse-grid shuffle deterministic.
    for (var node : order) {
      if (work.exhausted()) break;
      var proposed = new TreeMap<>(current.clues());
      proposed.remove(node);
      work.removals++;
      var next = verifier.evaluate(proposed);
      if (next == null) continue;
      current = next;
      ClueSearchStrategy.retain(beam, next, budget.beamWidth(), rank);
      if (verifier.acceptable(next) && (best == null || rank.compare(next, best) < 0)) best = next;
    }
    var expanded = new HashSet<String>();
    int attempts = 0;
    while (attempts < budget.localMutationAttempts() && !work.exhausted()) {
      var base = beam.stream().filter(e -> !expanded.contains(e.key())).findFirst().orElse(null);
      if (base == null) break;
      expanded.add(base.key());
      var advice =
          new ReasoningGuidance().inspect(base, verifier.target(), verifier.topology().graph());
      for (var mutation : neighborhood(base, verifier.topology(), advice)) {
        if (attempts >= budget.localMutationAttempts() || work.exhausted()) break;
        attempts++;
        work.localProposals++;
        switch (mutation.kind()) {
          case REMOVE -> work.removals++;
          case RESTORE -> work.restorations++;
          case SWAP -> work.swaps++;
        }
        var next = verifier.evaluate(mutation.clues());
        if (next == null) continue;
        ClueSearchStrategy.retain(beam, next, budget.beamWidth(), rank);
        if (verifier.acceptable(next) && (best == null || rank.compare(next, best) < 0))
          best = next;
      }
    }
    return best;
  }

  /** Deterministic sparse-grid clue-removal order. */
  public List<NodeId> bootstrapOrder(TopologyCandidate topology, Random random) {
    var order = new ArrayList<NodeId>();
    for (int v = 0; v < topology.graph().nodeCount(); v++) order.add(new NodeId(v));
    Collections.shuffle(order, random);
    return List.copyOf(order);
  }

  public enum Kind {
    REMOVE,
    RESTORE,
    SWAP
  }

  public record Mutation(Kind kind, SortedMap<NodeId, Color> clues) {
    public Mutation {
      clues = Collections.unmodifiableSortedMap(new TreeMap<>(clues));
    }
  }

  public List<Mutation> neighborhood(
      EvaluatedCandidate base, TopologyCandidate topology, ReasoningGuidance.Advice advice) {
    var result = new ArrayList<Mutation>();
    var seen = new HashSet<String>();
    var kinds =
        advice.restoreFirst()
            ? List.of(Kind.RESTORE, Kind.SWAP, Kind.REMOVE)
            : List.of(Kind.REMOVE, Kind.SWAP, Kind.RESTORE);
    // Two ranked locations per kind; no randomized proposals or unbounded cross product.
    for (int i = 0; i < NEIGHBORHOOD_SIZE / kinds.size(); i++)
      for (var kind : kinds) {
        if (kind != Kind.RESTORE && i >= advice.removals().size()
            || kind != Kind.REMOVE && i >= advice.restorations().size()) continue;
        var clues = new TreeMap<>(base.clues());
        if (kind != Kind.RESTORE) clues.remove(advice.removals().get(i));
        if (kind != Kind.REMOVE) {
          var node = advice.restorations().get(i);
          clues.put(node, topology.solution().colors().get(node.value()));
        }
        if (seen.add(clues.toString())) result.add(new Mutation(kind, clues));
      }
    return List.copyOf(result);
  }
}
