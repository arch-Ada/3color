package io.threecolor.generation;

import io.threecolor.model.*;
import java.util.*;

/**
 * Necessary defining-set constraints and bounded exact minimum hitting sets (up to 53 vertices).
 */
public final class DefiningSets {
  private DefiningSets() {}

  public static List<Long> kempeComponents(GraphTopology graph, Coloring target) {
    if (graph.nodeCount() > 53 || !target.satisfies(graph, PartialColoring.empty()))
      throw new IllegalArgumentException(
          "A proper target colouring of at most 53 vertices is required");
    var components = new LinkedHashSet<Long>();
    for (var component : RecolouringProfile.of(graph, target).components()) {
      long mask = 0;
      for (int v : component) mask |= 1L << v;
      components.add(mask);
    }
    return List.copyOf(components);
  }

  public static long disagreement(Coloring target, Coloring alternative) {
    long mask = 0;
    for (int v = 0; v < target.colors().size(); v++)
      if (target.colors().get(v) != alternative.colors().get(v)) mask |= 1L << v;
    return mask;
  }

  public enum Status {
    FOUND,
    INFEASIBLE,
    UNKNOWN
  }

  public record Cover(Status status, long vertices, int nodes) {}

  /** FOUND certifies minimum cardinality for these constraints; UNKNOWN certifies nothing. */
  public static Cover minimum(List<Long> constraints, List<Integer> order, int maxSize, int limit) {
    var search = new Search(constraints, order, limit);
    for (int size = 0; size <= maxSize; size++) {
      long result = search.visit(0, size);
      if (search.exhausted) return new Cover(Status.UNKNOWN, 0, search.nodes);
      if (result >= 0) return new Cover(Status.FOUND, result, search.nodes);
    }
    return new Cover(Status.INFEASIBLE, 0, search.nodes);
  }

  private static final class Search {
    final List<Long> constraints;
    final List<Integer> order;
    final int limit;
    int nodes;
    boolean exhausted;

    Search(List<Long> constraints, List<Integer> order, int limit) {
      this.constraints = constraints;
      this.order = order;
      this.limit = limit;
    }

    long visit(long selected, int left) {
      if (nodes >= limit || Thread.currentThread().isInterrupted()) {
        exhausted = true;
        return -1;
      }
      nodes++;
      long branch = 0;
      int smallest = Integer.MAX_VALUE;
      for (long constraint : constraints) {
        if ((constraint & selected) != 0) continue;
        if (constraint == 0) return -1;
        int size = Long.bitCount(constraint);
        if (size < smallest) {
          smallest = size;
          branch = constraint;
        }
      }
      if (branch == 0) return selected;
      if (left == 0) return -1;
      // Disjoint unhit constraints each need a distinct new clue.
      long union = 0;
      int bound = 0;
      for (long constraint : constraints) {
        if ((constraint & selected) == 0 && (constraint & union) == 0) {
          union |= constraint;
          if (++bound > left) return -1;
        }
      }
      for (int v : order) {
        if ((branch & (1L << v)) == 0) continue;
        long result = visit(selected | (1L << v), left - 1);
        if (result >= 0 || exhausted) return result;
      }
      return -1;
    }
  }
}
