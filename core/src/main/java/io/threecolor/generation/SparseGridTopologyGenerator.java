package io.threecolor.generation;

import io.threecolor.model.*;
import io.threecolor.validation.*;
import java.util.*;

/** The original sparse-grid family. Each attempt owns a fresh RNG. */
public final class SparseGridTopologyGenerator {
  TopologyStrategy.Prepared prepare(GenerationSpec spec, int attempt) {
    if (attempt < 0) throw new IllegalArgumentException("Negative topology attempt");
    var random = new Random(GenerationSeeds.attemptSeed(spec.seed(), attempt));
    return new TopologyStrategy.Prepared(candidate(spec, attempt, random), random);
  }

  public TopologyCandidate generate(GenerationSpec spec, int attempt) {
    return prepare(spec, attempt).candidate();
  }

  private TopologyCandidate candidate(GenerationSpec spec, int attempt, Random r) {
    int n = spec.nodeCount(), cols = (int) Math.ceil(Math.sqrt(n)), rows = (n + cols - 1) / cols;
    var points = new ArrayList<PuzzleLayout.Point>();
    var pool = new ArrayList<Edge>();
    for (int v = 0; v < n; v++) {
      int x = v % cols, y = v / cols;
      points.add(
          new PuzzleLayout.Point(
              (x + 0.35 + (r.nextDouble() - 0.5) * 0.16) / (cols - 0.3),
              (y + 0.35 + (r.nextDouble() - 0.5) * 0.16) / (rows - 0.3)));
      if (x > 0) pool.add(new Edge(v - 1, v));
      if (y > 0) pool.add(new Edge(v - cols, v));
      if (x > 0 && y > 0) {
        if (r.nextBoolean()) pool.add(new Edge(v - cols - 1, v));
        else pool.add(new Edge(v - cols, v - 1));
      }
    }
    Collections.shuffle(pool, r);
    var selected = new ArrayList<Edge>();
    int[] parent = new int[n], degree = new int[n];
    for (int i = 0; i < n; i++) parent[i] = i;
    for (var e : pool) {
      int a = e.a().value(), b = e.b().value(), pa = find(parent, a), pb = find(parent, b);
      if (pa != pb && degree[a] < spec.maxDegree() && degree[b] < spec.maxDegree()) {
        parent[pa] = pb;
        selected.add(e);
        degree[a]++;
        degree[b]++;
      }
    }
    if (selected.size() != n - 1) return null;
    var tree = new GraphTopology(n, selected);
    Color[] hidden = new Color[n];
    hidden[0] = Color.values()[r.nextInt(3)];
    var queue = new ArrayDeque<Integer>();
    queue.add(0);
    while (!queue.isEmpty()) {
      int v = queue.remove();
      for (int w : tree.neighbors(v))
        if (hidden[w] == null) {
          hidden[w] = Color.values()[(hidden[v].ordinal() + 1 + r.nextInt(2)) % 3];
          queue.add(w);
        }
    }
    int target = Math.max(n - 1, (int) Math.round(spec.targetAverageDegree() * n / 2));
    var edgeSet = new HashSet<>(selected);
    for (var e : pool) {
      if (selected.size() >= target) break;
      int a = e.a().value(), b = e.b().value();
      if (edgeSet.contains(e)
          || hidden[a] == hidden[b]
          || degree[a] >= spec.maxDegree()
          || degree[b] >= spec.maxDegree()) continue;
      selected.add(e);
      var trial = new GraphTopology(n, selected);
      if (GraphMetrics.of(trial).triangleCount() > spec.maximumTriangleRatio() * n) {
        selected.removeLast();
        continue;
      }
      edgeSet.add(e);
      degree[a]++;
      degree[b]++;
    }
    if (selected.size() != target || Arrays.stream(degree).min().orElse(0) < spec.minDegree())
      return null;
    return new TopologyCandidate(
        new GraphTopology(n, selected),
        new PuzzleLayout(points),
        new Coloring(Arrays.asList(hidden)),
        spec.seed(),
        attempt);
  }

  private static int find(int[] parent, int v) {
    while (parent[v] != v) {
      parent[v] = parent[parent[v]];
      v = parent[v];
    }
    return v;
  }
}
