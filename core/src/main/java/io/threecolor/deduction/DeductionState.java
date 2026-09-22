package io.threecolor.deduction;

import io.threecolor.model.GraphTopology;
import java.util.*;

/** Immutable rule input. Relation merges do not silently change domains: propagation is traced. */
public record DeductionState(
    GraphTopology graph, List<Integer> domains, RelationKnowledge relations) {
  public DeductionState(GraphTopology graph, List<Integer> domains) {
    this(graph, domains, new RelationKnowledge(graph));
  }

  public DeductionState {
    domains = List.copyOf(domains);
    if (domains.size() != graph.nodeCount() || domains.stream().anyMatch(m -> m < 0 || m > 7))
      throw new IllegalArgumentException("Invalid domains");
  }

  public int mask(int node) {
    return domains.get(node);
  }
}
