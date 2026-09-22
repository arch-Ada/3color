package io.threecolor.model;

public record Puzzle(
    GraphTopology topology,
    PartialColoring givens,
    RuleSet rules,
    PuzzleProvenance provenance,
    PuzzleLayout layout,
    ExplanationModel explanationModel) {
  public Puzzle(
      GraphTopology topology,
      PartialColoring givens,
      RuleSet rules,
      PuzzleProvenance provenance,
      PuzzleLayout layout) {
    this(topology, givens, rules, provenance, layout, ExplanationModel.PLAYER_V1);
  }

  public Puzzle {
    java.util.Objects.requireNonNull(topology);
    java.util.Objects.requireNonNull(givens);
    java.util.Objects.requireNonNull(rules);
    java.util.Objects.requireNonNull(provenance);
    java.util.Objects.requireNonNull(layout);
    java.util.Objects.requireNonNull(explanationModel);
    givens.validateNodes(topology);
  }

  public String logicalHash() {
    var s =
        new StringBuilder("3color-logical-v1\n")
            .append(rules.name())
            .append('\n')
            .append(topology.nodeCount())
            .append('\n');
    for (var e : topology.edges())
      s.append(e.a().value()).append(',').append(e.b().value()).append('\n');
    s.append("givens\n");
    givens
        .colors()
        .forEach((n, c) -> s.append(n.value()).append(':').append(c.name()).append('\n'));
    try {
      return java.util.HexFormat.of()
          .formatHex(
              java.security.MessageDigest.getInstance("SHA-256")
                  .digest(s.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
