package io.threecolor.generation;

import io.threecolor.model.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.HexFormat;

/** Immutable topology input, shared by strategies. Hidden colors only supply clues. */
public record TopologyCandidate(
    GraphTopology graph,
    PuzzleLayout layout,
    Coloring solution,
    long masterSeed,
    int attemptIndex,
    TopologyStrategy topologyStrategy) {
  public TopologyCandidate(
      GraphTopology graph,
      PuzzleLayout layout,
      Coloring solution,
      long masterSeed,
      int attemptIndex) {
    this(graph, layout, solution, masterSeed, attemptIndex, TopologyStrategy.SPARSE_GRID);
  }

  public TopologyCandidate {
    java.util.Objects.requireNonNull(topologyStrategy);
    if (!solution.satisfies(graph, PartialColoring.empty()))
      throw new IllegalArgumentException("Invalid hidden coloring");
  }

  public String structuralHash() {
    return StructuralIdentity.of(graph);
  }

  public String identity() {
    String canonical =
        graph.nodeCount() + ";" + graph.edges() + ";" + layout.points() + ";" + solution.colors();
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(canonical.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
