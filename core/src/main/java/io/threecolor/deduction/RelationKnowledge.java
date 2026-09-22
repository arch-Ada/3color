package io.threecolor.deduction;

import io.threecolor.model.GraphTopology;
import java.util.*;

/** Immutable union-find snapshot plus a proof forest and explicit inequality evidence. */
public final class RelationKnowledge {
  public record Fact(int a, int b, boolean equal, int factId) {}

  private final GraphTopology graph;
  private final List<Fact> facts;
  private final int[] roots;
  private final Set<Long> different;

  public RelationKnowledge(GraphTopology graph) {
    this(graph, List.of());
  }

  private RelationKnowledge(GraphTopology graph, List<Fact> facts) {
    this.graph = graph;
    this.facts = List.copyOf(facts);
    roots = new int[graph.nodeCount()];
    for (int i = 0; i < roots.length; i++) roots[i] = i;
    for (var f : facts)
      if (f.equal()) {
        int a = root(f.a()), b = root(f.b());
        roots[Math.max(a, b)] = Math.min(a, b);
      }
    for (int i = 0; i < roots.length; i++) roots[i] = root(i);
    different = new TreeSet<>();
    for (var e : graph.edges()) mark(e.a().value(), e.b().value());
    for (var f : facts) if (!f.equal()) mark(f.a(), f.b());
  }

  private int root(int v) {
    while (roots[v] != v) v = roots[v];
    return v;
  }

  private static long pair(int a, int b) {
    return ((long) Math.min(a, b) << 32) | Math.max(a, b);
  }

  private void mark(int a, int b) {
    different.add(pair(roots[a], roots[b]));
  }

  public int representative(int v) {
    return roots[v];
  }

  public boolean equal(int a, int b) {
    return roots[a] == roots[b];
  }

  public boolean notEqual(int a, int b) {
    return different.contains(pair(roots[a], roots[b]));
  }

  public List<Fact> facts() {
    return facts;
  }

  public RelationKnowledge with(int a, int b, boolean equal, int factId) {
    if (a < 0 || b < 0 || a >= roots.length || b >= roots.length)
      throw new IllegalArgumentException("Invalid relation vertex");
    var next = new ArrayList<>(facts);
    next.add(new Fact(Math.min(a, b), Math.max(a, b), equal, factId));
    return new RelationKnowledge(graph, next);
  }

  public List<Integer> equalityProof(int a, int b) {
    if (a == b) return List.of();
    if (!equal(a, b)) throw new IllegalArgumentException("Equality not known");
    int[] parent = new int[roots.length], proof = new int[roots.length];
    Arrays.fill(parent, -1);
    var q = new ArrayDeque<Integer>();
    q.add(a);
    parent[a] = a;
    while (!q.isEmpty() && parent[b] < 0) {
      int u = q.remove();
      for (var f : facts)
        if (f.equal()) {
          int v = f.a() == u ? f.b() : f.b() == u ? f.a() : -1;
          if (v >= 0 && parent[v] < 0) {
            parent[v] = u;
            proof[v] = f.factId();
            q.add(v);
          }
        }
    }
    var ids = new TreeSet<Integer>();
    for (int v = b; v != a; v = parent[v]) ids.add(proof[v]);
    return List.copyOf(ids);
  }

  public List<Integer> inequalityProof(int a, int b) {
    if (!notEqual(a, b)) throw new IllegalArgumentException("Inequality not known");
    for (var e : graph.edges()) {
      var proof = bridge(a, b, e.a().value(), e.b().value(), -1);
      if (proof != null) return proof;
    }
    for (var f : facts)
      if (!f.equal()) {
        var proof = bridge(a, b, f.a(), f.b(), f.factId());
        if (proof != null) return proof;
      }
    throw new IllegalStateException("Missing inequality evidence");
  }

  private List<Integer> bridge(int a, int b, int u, int v, int fact) {
    if (!equal(a, u) || !equal(b, v)) {
      int t = u;
      u = v;
      v = t;
    }
    if (!equal(a, u) || !equal(b, v)) return null;
    var ids = new TreeSet<Integer>();
    ids.addAll(equalityProof(a, u));
    ids.addAll(equalityProof(b, v));
    if (fact >= 0) ids.add(fact);
    return List.copyOf(ids);
  }
}
