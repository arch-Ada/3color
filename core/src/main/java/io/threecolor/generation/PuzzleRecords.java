package io.threecolor.generation;

import io.threecolor.model.*;
import java.util.*;

/** Small, versioned offline catalogue format; it contains givens, never stored solutions. */
public final class PuzzleRecords {
  private PuzzleRecords() {}

  public static GraphTopology graph(int n, String text) {
    var edges = new ArrayList<Edge>();
    if (!text.isEmpty())
      for (String item : text.split(";")) {
        var pair = item.split(",");
        edges.add(new Edge(Integer.parseInt(pair[0]), Integer.parseInt(pair[1])));
      }
    return new GraphTopology(n, edges);
  }

  public static PuzzleLayout layout(String text) {
    return new PuzzleLayout(
        Arrays.stream(text.split(";"))
            .map(
                item -> {
                  var xy = item.split(",");
                  return new PuzzleLayout.Point(
                      Double.parseDouble(xy[0]), Double.parseDouble(xy[1]));
                })
            .toList());
  }

  public static String edges(GraphTopology graph) {
    return String.join(
        ";", graph.edges().stream().map(e -> e.a().value() + "," + e.b().value()).toList());
  }

  public static String points(PuzzleLayout layout) {
    return String.join(";", layout.points().stream().map(p -> p.x() + "," + p.y()).toList());
  }

  public static String clues(PartialColoring clues) {
    return String.join(
        ";",
        new TreeMap<>(clues.colors())
            .entrySet().stream().map(e -> e.getKey().value() + "," + e.getValue().name()).toList());
  }

  public static PartialColoring clues(String text) {
    var clues = new TreeMap<NodeId, Color>();
    if (!text.isEmpty())
      for (String item : text.split(";")) {
        var pair = item.split(",");
        clues.put(new NodeId(Integer.parseInt(pair[0])), Color.valueOf(pair[1]));
      }
    return new PartialColoring(clues);
  }
}
