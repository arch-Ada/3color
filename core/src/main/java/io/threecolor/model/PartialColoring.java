package io.threecolor.model;

public record PartialColoring(java.util.Map<NodeId, Color> colors) {
  public PartialColoring {
    colors = java.util.Collections.unmodifiableMap(new java.util.TreeMap<>(colors));
    colors.forEach(
        (n, c) -> {
          java.util.Objects.requireNonNull(n);
          java.util.Objects.requireNonNull(c);
        });
  }

  public static PartialColoring empty() {
    return new PartialColoring(java.util.Map.of());
  }

  public void validateNodes(GraphTopology graph) {
    if (colors.keySet().stream().anyMatch(n -> n.value() >= graph.nodeCount()))
      throw new IllegalArgumentException("Invalid colored node");
  }
}
