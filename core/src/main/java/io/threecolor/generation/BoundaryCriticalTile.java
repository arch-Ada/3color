package io.threecolor.generation;

import io.threecolor.layout.LayoutQuality;
import io.threecolor.model.*;
import io.threecolor.validation.PuzzleValidator;
import java.util.*;

/** Small triangle-free C8 regions, screened by the exact definition of C-criticality. */
public final class BoundaryCriticalTile {
  private BoundaryCriticalTile() {}

  public static Optional<ThreePortTile> create(long seed) {
    var random = new Random(seed);
    var points =
        new ArrayList<PuzzleLayout.Point>(
            List.of(
                new PuzzleLayout.Point(0, 0), new PuzzleLayout.Point(.5, 0),
                new PuzzleLayout.Point(1, 0), new PuzzleLayout.Point(1, .5),
                new PuzzleLayout.Point(1, 1), new PuzzleLayout.Point(.5, 1),
                new PuzzleLayout.Point(0, 1), new PuzzleLayout.Point(0, .5)));
    for (var p :
        List.of(
            new PuzzleLayout.Point(.3, .3),
            new PuzzleLayout.Point(.7, .3),
            new PuzzleLayout.Point(.5, .7)))
      points.add(
          new PuzzleLayout.Point(
              p.x() + .24 * (random.nextDouble() - .5), p.y() + .24 * (random.nextDouble() - .5)));
    var edges = new ArrayList<Edge>();
    var pool = new ArrayList<Edge>();
    for (int v = 0; v < 8; v++) edges.add(new Edge(v, (v + 1) % 8));
    for (int a = 0; a < 11; a++)
      for (int b = a + 1; b < 11; b++)
        if (!edges.contains(new Edge(a, b))) pool.add(new Edge(a, b));
    Collections.shuffle(pool, random);
    for (var edge : pool) {
      int u = edge.a().value(), v = edge.b().value();
      var a = points.get(u);
      var b = points.get(v);
      boolean invalid = false;
      for (int w = 0; w < 11; w++) {
        if (w != u && w != v && edges.contains(new Edge(u, w)) && edges.contains(new Edge(v, w)))
          invalid = true;
        if (w != u && w != v && LayoutQuality.distance(points.get(w), a, b) < 1e-10) invalid = true;
      }
      for (var other : edges) {
        if (other.a().value() == u
            || other.a().value() == v
            || other.b().value() == u
            || other.b().value() == v) continue;
        if (PuzzleValidator.intersects(
            a, b, points.get(other.a().value()), points.get(other.b().value()))) invalid = true;
      }
      if (!invalid) edges.add(edge);
    }
    var graph = new GraphTopology(11, edges);
    // An interior vertex of degree <=2 cannot constrain boundary extension in 3-colouring.
    for (int v = 8; v < 11; v++) if (graph.degree(v) < 3) return Optional.empty();
    if (!critical(graph)) return Optional.empty();
    return Optional.of(ThreePortTile.tabulate(seed, graph, new PuzzleLayout(points)));
  }

  public static boolean boundaryEdge(Edge e) {
    int a = e.a().value(), b = e.b().value();
    return a < 8 && b < 8 && (Math.abs(a - b) == 1 || Math.abs(a - b) == 7);
  }

  /** Feasibility only: multiplicities are deliberately irrelevant to C-criticality. */
  public static BitSet feasible(GraphTopology graph) {
    var allowed = new BitSet(6561);
    int[] colors = new int[11];
    for (int boundary = 0; boundary < 6561; boundary++) {
      int code = boundary;
      for (int v = 0; v < 8; v++) {
        colors[v] = code % 3;
        code /= 3;
      }
      boolean valid = true;
      for (int v = 0; v < 8; v++) if (colors[v] == colors[(v + 1) % 8]) valid = false;
      if (!valid) continue;
      for (int interior = 0; interior < 27; interior++) {
        code = interior;
        for (int v = 8; v < 11; v++) {
          colors[v] = code % 3;
          code /= 3;
        }
        valid = true;
        for (var e : graph.edges())
          if (colors[e.a().value()] == colors[e.b().value()]) {
            valid = false;
            break;
          }
        if (valid) {
          allowed.set(boundary);
          break;
        }
      }
    }
    return allowed;
  }

  public static boolean critical(GraphTopology graph) {
    for (int v = 8; v < 11; v++) if (graph.degree(v) == 0) return false;
    var original = feasible(graph);
    if (original.cardinality() == 258) return false;
    for (var e : graph.edges()) {
      if (boundaryEdge(e)) continue;
      var removed = new ArrayList<>(graph.edges());
      removed.remove(e);
      var added = feasible(new GraphTopology(11, removed));
      added.andNot(original);
      if (added.isEmpty()) return false;
    }
    return true;
  }

  public static void main(String[] args) {
    int found = 0;
    for (int seed = 0; seed < Integer.parseInt(args[0]); seed++) {
      var tile = create(seed);
      if (tile.isPresent()) {
        found++;
        System.out.println(seed + " " + tile.get().rows().size());
      }
    }
    System.out.println("critical=" + found);
  }
}
