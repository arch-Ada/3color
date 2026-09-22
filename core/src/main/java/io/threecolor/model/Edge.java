package io.threecolor.model;

public record Edge(NodeId a, NodeId b) implements Comparable<Edge> {
  public Edge {
    java.util.Objects.requireNonNull(a);
    java.util.Objects.requireNonNull(b);
    if (a.equals(b)) throw new IllegalArgumentException("Self loop");
    if (a.compareTo(b) > 0) {
      var tmp = a;
      a = b;
      b = tmp;
    }
  }

  public Edge(int a, int b) {
    this(new NodeId(a), new NodeId(b));
  }

  public int compareTo(Edge e) {
    int c = a.compareTo(e.a);
    return c != 0 ? c : b.compareTo(e.b);
  }
}
