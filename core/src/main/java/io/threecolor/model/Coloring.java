package io.threecolor.model;

public record Coloring(java.util.List<Color> colors) {
  public Coloring {
    colors = java.util.List.copyOf(colors);
  }

  public boolean satisfies(GraphTopology graph, PartialColoring givens) {
    return colors.size() == graph.nodeCount()
        && graph.edges().stream()
            .allMatch(e -> colors.get(e.a().value()) != colors.get(e.b().value()))
        && givens.colors().entrySet().stream()
            .allMatch(e -> colors.get(e.getKey().value()) == e.getValue());
  }
}
