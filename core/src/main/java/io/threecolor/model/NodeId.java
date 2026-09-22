package io.threecolor.model;

public record NodeId(int value) implements Comparable<NodeId> {
  public NodeId {
    if (value < 0) throw new IllegalArgumentException("Negative node ID");
  }

  public int compareTo(NodeId other) {
    return Integer.compare(value, other.value);
  }
}
