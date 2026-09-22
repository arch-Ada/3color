package io.threecolor.generation;

import io.threecolor.model.*;
import java.util.*;

/**
 * Necessary defining-set constraints, with no machine-word vertex limit. Not a difficulty score.
 */
public record RecolouringProfile(
    List<Set<Integer>> components,
    Set<Integer> mandatory,
    List<Integer> pairCounts,
    int lowerBound) {
  public RecolouringProfile {
    components = components.stream().map(Set::copyOf).toList();
    mandatory = Set.copyOf(mandatory);
    pairCounts = List.copyOf(pairCounts);
  }

  public static RecolouringProfile of(GraphTopology graph, Coloring target) {
    if (!target.satisfies(graph, PartialColoring.empty()))
      throw new IllegalArgumentException("Proper target required");
    var components = new ArrayList<Set<Integer>>();
    var mandatory = new TreeSet<Integer>();
    var counts = new ArrayList<Integer>();
    for (int a = 0; a < 3; a++)
      for (int b = a + 1; b < 3; b++) {
        var remaining = new BitSet(graph.nodeCount());
        for (int v = 0; v < graph.nodeCount(); v++) {
          int c = target.colors().get(v).ordinal();
          if (c == a || c == b) remaining.set(v);
        }
        int count = 0;
        while (!remaining.isEmpty()) {
          var component = new TreeSet<Integer>();
          var queue = new ArrayDeque<Integer>();
          int root = remaining.nextSetBit(0);
          remaining.clear(root);
          queue.add(root);
          while (!queue.isEmpty()) {
            int v = queue.remove();
            component.add(v);
            for (int w : graph.neighbors(v))
              if (remaining.get(w)) {
                remaining.clear(w);
                queue.add(w);
              }
          }
          components.add(component);
          count++;
          if (component.size() == 1) mandatory.add(component.iterator().next());
        }
        counts.add(count);
      }
    int sum = counts.stream().mapToInt(Integer::intValue).sum();
    int bound = Math.max(mandatory.size(), Math.max(Collections.max(counts), (sum + 1) / 2));
    // Mandatory clues are fixed already. Each further clue can hit at most two uncovered
    // components.
    int unhit = 0;
    for (var component : components) if (Collections.disjoint(component, mandatory)) unhit++;
    bound = Math.max(bound, mandatory.size() + (unhit + 1) / 2);
    return new RecolouringProfile(components, mandatory, counts, bound);
  }

  public boolean coveredBy(Set<Integer> clues) {
    return components.stream().noneMatch(c -> Collections.disjoint(c, clues));
  }
}
