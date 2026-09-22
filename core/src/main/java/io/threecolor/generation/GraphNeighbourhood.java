package io.threecolor.generation;

import io.threecolor.layout.LayoutQuality;
import io.threecolor.model.*;
import io.threecolor.validation.PuzzleValidator;
import java.util.*;

/** Small geometric edits. Each accepted edit preserves connected, clear planar drawings. */
public final class GraphNeighbourhood {
  public record Variant(PlaneTriangulation candidate, String edit) {}

  public static List<Variant> variants(
      PlaneTriangulation source, int minimum, int maximum, long seed) {
    return variants(source, minimum, maximum, seed, new GenerationWork());
  }

  static List<Variant> variants(
      PlaneTriangulation source, int minimum, int maximum, long seed, GenerationWork work) {
    if (minimum < 4
        || maximum > 53
        || minimum > maximum
        || source.graph().nodeCount() < 4
        || source.graph().nodeCount() > 53)
      throw new IllegalArgumentException("Supported sizes 4..53");
    var random = new Random(seed);
    var result = new ArrayList<Variant>();
    if (source.graph().nodeCount() >= minimum && source.graph().nodeCount() <= maximum)
      result.add(new Variant(source, "original"));
    for (boolean insert : new boolean[] {false, true}) {
      var current = source;
      for (int count = 1; count <= 3 && !Thread.currentThread().isInterrupted(); count++) {
        if (insert
            ? current.graph().nodeCount() >= maximum
            : current.graph().nodeCount() <= minimum) break;
        work.count("vertexEditAttempts");
        var next = insert ? insert(current, random, work) : remove(current, random, work);
        if (next.isEmpty()) break;
        current = next.get();
        work.count("validVertexEdits");
        if (current.graph().nodeCount() >= minimum && current.graph().nodeCount() <= maximum)
          result.add(new Variant(current, (insert ? "insert-" : "remove-") + count));
      }
    }
    return List.copyOf(result);
  }

  private static Optional<PlaneTriangulation> remove(
      PlaneTriangulation source, Random random, GenerationWork work) {
    var order = new ArrayList<Integer>();
    for (int v = 0; v < source.graph().nodeCount(); v++) order.add(v);
    Collections.shuffle(order, random);
    for (int removed : order) {
      if (Thread.currentThread().isInterrupted()) return Optional.empty();
      var points = new ArrayList<>(source.layout().points());
      points.remove(removed);
      var edges = new ArrayList<Edge>();
      for (var e : source.graph().edges()) {
        int a = e.a().value(), b = e.b().value();
        if (a != removed && b != removed)
          edges.add(new Edge(a > removed ? a - 1 : a, b > removed ? b - 1 : b));
      }
      var candidate =
          new PlaneTriangulation(new GraphTopology(points.size(), edges), new PuzzleLayout(points));
      if (valid(candidate, work)) return Optional.of(candidate);
    }
    return Optional.empty();
  }

  private static Optional<PlaneTriangulation> insert(
      PlaneTriangulation source, Random random, GenerationWork work) {
    // Subdivide an edge, then try visible neighbours. Coordinates stay on a refinement of the grid.
    var order = new ArrayList<>(source.graph().edges());
    Collections.shuffle(order, random);
    for (var edge : order) {
      if (Thread.currentThread().isInterrupted()) return Optional.empty();
      int n = source.graph().nodeCount();
      var points = new ArrayList<>(source.layout().points());
      var a = points.get(edge.a().value());
      var b = points.get(edge.b().value());
      var midpoint = new PuzzleLayout.Point((a.x() + b.x()) / 2, (a.y() + b.y()) / 2);
      points.add(midpoint);
      var edges = new ArrayList<>(source.graph().edges());
      edges.remove(edge);
      edges.add(new Edge(edge.a().value(), n));
      edges.add(new Edge(edge.b().value(), n));
      var candidate =
          new PlaneTriangulation(new GraphTopology(n + 1, edges), new PuzzleLayout(points));
      if (!valid(candidate, work)) continue;
      var neighbours = new ArrayList<Integer>();
      for (int v = 0; v < n; v++)
        if (v != edge.a().value() && v != edge.b().value()) neighbours.add(v);
      Collections.shuffle(neighbours, random);
      int added = 0;
      for (int v : neighbours) {
        if (added >= 2) break;
        var p = points.get(v);
        if (Math.hypot(p.x() - midpoint.x(), p.y() - midpoint.y()) > .45) continue;
        var connection = new Edge(v, n);
        edges.add(connection);
        var trial =
            new PlaneTriangulation(new GraphTopology(n + 1, edges), new PuzzleLayout(points));
        if (valid(trial, work)) {
          candidate = trial;
          added++;
        } else edges.remove(connection);
      }
      return Optional.of(candidate);
    }
    return Optional.empty();
  }

  static boolean valid(PlaneTriangulation source, GenerationWork work) {
    work.count("sourceGeometryChecks");
    return valid(source);
  }

  public static boolean valid(PlaneTriangulation source) {
    var puzzle =
        new Puzzle(
            source.graph(),
            PartialColoring.empty(),
            RuleSet.CLASSIC_V1,
            PuzzleProvenance.supplied(),
            source.layout());
    if (!new PuzzleValidator().validate(puzzle, true, false, false, 1).valid()) return false;
    var q = LayoutQuality.measure(source.graph(), source.layout());
    double scale = Math.sqrt(source.graph().nodeCount());
    return q.minimumVertexDistance() >= .35 / scale && q.minimumEdgeClearance() >= .035 / scale;
  }
}
