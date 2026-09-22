package io.threecolor.deduction;

import java.util.*;

/** The neighborhood of one vertex has an unknown but common two-color palette. */
public final class NeighborhoodParityRule implements DeductionRule {
  public String id() {
    return "neighborhood_parity";
  }

  public int tier() {
    return 3;
  }

  public List<RuleApplication> find(DeductionState s) {
    var out = new ArrayList<RuleApplication>();
    var g = s.graph();
    int n = g.nodeCount();
    for (int center = 0; center < n; center++) {
      boolean[] inside = new boolean[n];
      for (int v : g.neighbors(center)) inside[v] = true;
      int[] parity = new int[n], parent = new int[n];
      Arrays.fill(parity, -1);
      Arrays.fill(parent, -1);
      for (int anchor : g.neighbors(center))
        if (parity[anchor] < 0) {
          var q = new ArrayDeque<Integer>();
          var members = new ArrayList<Integer>();
          q.add(anchor);
          parity[anchor] = 0;
          while (!q.isEmpty()) {
            int u = q.remove();
            members.add(u);
            for (int v : g.neighbors(u))
              if (inside[v]) {
                if (parity[v] < 0) {
                  parity[v] = 1 - parity[u];
                  parent[v] = u;
                  q.add(v);
                } else if (parity[v] == parity[u]) {
                  var path = path(u, v, parent);
                  var witnesses = new ArrayList<>(path);
                  witnesses.add(center);
                  return List.of(
                      new RuleApplication(
                          new LogicalConclusion.Contradiction(),
                          witnesses,
                          Map.of("center", center, "pathLength", path.size(), "oddCycle", 1)));
                }
              }
          }
          // At most a spanning set of same-parity equalities plus one cross-part inequality.
          Collections.sort(members);
          int[] anchors = {-1, -1};
          for (int v : members) {
            int side = parity[v];
            if (anchors[side] < 0) {
              anchors[side] = v;
              continue;
            }
            int a = anchors[side];
            if (!s.relations().equal(a, v)) out.add(application(center, a, v, parent, true));
          }
          if (anchors[1] >= 0 && !s.relations().notEqual(anchors[0], anchors[1]))
            out.add(application(center, anchors[0], anchors[1], parent, false));
        }
    }
    return out;
  }

  private RuleApplication application(int center, int a, int b, int[] parent, boolean equal) {
    var path = path(a, b, parent);
    var witnesses = new ArrayList<>(path);
    witnesses.add(center);
    return new RuleApplication(
        equal ? new LogicalConclusion.Equal(a, b) : new LogicalConclusion.NotEqual(a, b),
        witnesses,
        Map.of("center", center, "pathLength", path.size() - 1));
  }

  static List<Integer> path(int a, int b, int[] parent) {
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
