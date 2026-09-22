package io.threecolor.deduction;

import java.util.*;

/**
 * Restricted binary-domain implication paths, with explicit assignment-to-assignment evidence. No
 * branching, no recursion, and no participation by a three-candidate vertex.
 */
public final class ImplicationChainRule implements DeductionRule {
  private final int maximumPathLength;

  public ImplicationChainRule() {
    this(24);
  }

  public ImplicationChainRule(int maximumPathLength) {
    if (maximumPathLength < 1) throw new IllegalArgumentException();
    this.maximumPathLength = maximumPathLength;
  }

  public String id() {
    return "implication_chain";
  }

  public int tier() {
    return 3;
  }

  private record Arc(int target, int mask, List<Integer> premises) {}

  public List<RuleApplication> find(DeductionState s) {
    int n = s.graph().nodeCount();
    var arcs = new ArrayList<List<Arc>>(3 * n);
    for (int i = 0; i < 3 * n; i++) arcs.add(new ArrayList<>());
    for (int a = 0; a < n; a++)
      if (Integer.bitCount(s.mask(a)) <= 2 && s.mask(a) != 0)
        for (int b = 0; b < n; b++)
          if (a != b && Integer.bitCount(s.mask(b)) <= 2 && s.mask(b) != 0) {
            boolean equal = s.relations().equal(a, b), different = s.relations().notEqual(a, b);
            if (!equal && !different) continue;
            var refs =
                equal ? s.relations().equalityProof(a, b) : s.relations().inequalityProof(a, b);
            for (int bit = 1; bit <= 4; bit <<= 1)
              if ((s.mask(a) & bit) != 0) {
                int next = equal ? s.mask(b) & bit : s.mask(b) & ~bit;
                if (next == 0 || Integer.bitCount(next) == 1 && next != s.mask(b))
                  arcs.get(literal(a, bit)).add(new Arc(b, next, refs));
              }
          }
    var out = new ArrayList<RuleApplication>();
    for (int a = 0; a < n; a++)
      if (Integer.bitCount(s.mask(a)) == 2)
        for (int bit = 1; bit <= 4; bit <<= 1)
          if ((s.mask(a) & bit) != 0) {
            int start = literal(a, bit), opposite = literal(a, s.mask(a) & ~bit);
            int[] parent = new int[3 * n], depth = new int[3 * n];
            Arrays.fill(parent, -1);
            parent[start] = start;
            Arc[] edge = new Arc[3 * n];
            var q = new ArrayDeque<Integer>();
            q.add(start);
            RuleApplication found = null;
            while (!q.isEmpty() && found == null) {
              int u = q.remove();
              if (depth[u] >= maximumPathLength) continue;
              for (var arc : arcs.get(u)) {
                if (arc.mask() == 0) {
                  found = proof(s, a, bit, u, parent, edge, arc);
                  break;
                }
                int v = literal(arc.target(), arc.mask());
                if (parent[v] >= 0) continue;
                parent[v] = u;
                edge[v] = arc;
                depth[v] = depth[u] + 1;
                if (v == opposite) {
                  found = proof(s, a, bit, v, parent, edge, null);
                  break;
                }
                q.add(v);
              }
            }
            if (found != null) out.add(found);
          }
    return out;
  }

  private static int literal(int node, int bit) {
    return node * 3 + Integer.numberOfTrailingZeros(bit);
  }

  private static RuleApplication proof(
      DeductionState s, int node, int bit, int end, int[] parent, Arc[] edges, Arc terminal) {
    var vertices = new ArrayList<Integer>();
    for (int u = end; ; u = parent[u]) {
      vertices.add(u);
      if (parent[u] == u) break;
    }
    Collections.reverse(vertices);
    var links = new ArrayList<ImplicationLink>();
    var refs = new TreeSet<Integer>();
    var witnesses = new TreeSet<Integer>();
    witnesses.add(node);
    for (int i = 1; i < vertices.size(); i++) {
      int u = vertices.get(i - 1), v = vertices.get(i);
      var arc = edges[v];
      links.add(new ImplicationLink(u / 3, 1 << (u % 3), v / 3, 1 << (v % 3)));
      refs.addAll(arc.premises());
      witnesses.add(u / 3);
      witnesses.add(v / 3);
    }
    if (terminal != null) {
      links.add(new ImplicationLink(end / 3, 1 << (end % 3), terminal.target(), 0));
      refs.addAll(terminal.premises());
      witnesses.add(terminal.target());
    }
    return new RuleApplication(
        new LogicalConclusion.NarrowDomain(node, s.mask(node) & ~bit),
        List.copyOf(witnesses),
        Map.of("assumedMask", bit, "chainLength", links.size()),
        List.copyOf(refs),
        links);
  }
}
