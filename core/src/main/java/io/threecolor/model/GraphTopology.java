package io.threecolor.model;

public final class GraphTopology {
  private final int nodeCount;
  private final java.util.List<Edge> edges;
  private final int[][] adjacency;

  public GraphTopology(int nodeCount, java.util.Collection<Edge> input) {
    if (nodeCount < 1 || nodeCount > 10000)
      throw new IllegalArgumentException("Invalid node count");
    this.nodeCount = nodeCount;
    var sorted = new java.util.TreeSet<Edge>();
    for (var e : input) {
      if (e.b().value() >= nodeCount) throw new IllegalArgumentException("Invalid edge node");
      if (!sorted.add(e)) throw new IllegalArgumentException("Duplicate edge");
    }
    edges = java.util.List.copyOf(sorted);
    int[] counts = new int[nodeCount];
    for (var e : edges) {
      counts[e.a().value()]++;
      counts[e.b().value()]++;
    }
    adjacency = new int[nodeCount][];
    for (int i = 0; i < nodeCount; i++) adjacency[i] = new int[counts[i]];
    java.util.Arrays.fill(counts, 0);
    for (var e : edges) {
      int a = e.a().value(), b = e.b().value();
      adjacency[a][counts[a]++] = b;
      adjacency[b][counts[b]++] = a;
    }
    for (var row : adjacency) java.util.Arrays.sort(row);
  }

  public int nodeCount() {
    return nodeCount;
  }

  public java.util.List<Edge> edges() {
    return edges;
  }

  public int degree(int node) {
    return adjacency[node].length;
  }

  public int neighbor(int node, int index) {
    return adjacency[node][index];
  }

  public int[] neighbors(int node) {
    return adjacency[node].clone();
  }

  public boolean adjacent(int a, int b) {
    return java.util.Arrays.binarySearch(adjacency[a], b) >= 0;
  }
}
