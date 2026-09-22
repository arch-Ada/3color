package io.threecolor.deduction;

import java.util.*;

/**
 * A connected two-color component alternates colors. Opposite parity witnesses forbid both colors
 * at any common external neighbor, closing an odd cycle through that vertex.
 */
public final class ParityRule implements DeductionRule {
  public String id() {
    return "two_color_parity";
  }

  public int tier() {
    return 3;
  }

  public List<RuleApplication> find(DeductionState s) {
    var g = s.graph();
    var result = new ArrayList<RuleApplication>();
    for (int pair : new int[] {3, 5, 6}) {
      int[] component = new int[g.nodeCount()],
          parity = new int[g.nodeCount()],
          parent = new int[g.nodeCount()];
      Arrays.fill(component, -1);
      Arrays.fill(parent, -1);
      for (int start = 0; start < g.nodeCount(); start++)
        if (component[start] < 0 && (s.mask(start) & ~pair) == 0) {
          var q = new ArrayDeque<Integer>();
          q.add(start);
          component[start] = start;
          while (!q.isEmpty()) {
            int u = q.remove();
            for (int v : g.neighbors(u))
              if ((s.mask(v) & ~pair) == 0) {
                if (component[v] < 0) {
                  component[v] = start;
                  parity[v] = 1 - parity[u];
                  parent[v] = u;
                  q.add(v);
                } else if (parity[v] == parity[u]) {
                  var witnesses = path(u, v, parent);
                  return List.of(
                      new RuleApplication(
                          v, 0, witnesses, Map.of("pairMask", pair, "oddCycle", 1)));
                }
              }
          }
        }
      for (int v = 0; v < g.nodeCount(); v++)
        if ((s.mask(v) & pair) != 0) {
          var neighbors = g.neighbors(v);
          for (int i = 0; i < neighbors.length; i++)
            for (int j = i + 1; j < neighbors.length; j++) {
              int a = neighbors[i], b = neighbors[j];
              if (component[a] >= 0 && component[a] == component[b] && parity[a] != parity[b])
                result.add(
                    new RuleApplication(
                        v,
                        s.mask(v) & ~pair,
                        path(a, b, parent),
                        Map.of("pairMask", pair, "oddCycle", 1)));
            }
        }
    }
    return result;
  }

  private static List<Integer> path(int a, int b, int[] parent) {
    var left = new ArrayList<Integer>();
    for (int v = a; v >= 0; v = parent[v]) left.add(v);
    var right = new ArrayList<Integer>();
    int v = b;
    while (!left.contains(v)) {
      right.add(v);
      v = parent[v];
    }
    var result = new ArrayList<>(left.subList(0, left.indexOf(v) + 1));
    Collections.reverse(right);
    result.addAll(right);
    return result;
  }
}
