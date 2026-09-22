package io.threecolor.generation;

import io.threecolor.model.*;
import io.threecolor.validation.PuzzleValidator;
import java.util.*;

/**
 * Seeded rounded rectangular hull and lightly jittered grid points, triangulated in length order.
 */
public record PlaneTriangulation(GraphTopology graph, PuzzleLayout layout) {
  public static PlaneTriangulation create(int n, long seed) {
    if (n < 6 || n > 40) throw new IllegalArgumentException("Prototype sizes: 6..40");
    var random = new Random(seed);
    var points = new ArrayList<PuzzleLayout.Point>();
    int rows = (int) Math.round(Math.sqrt(n));
    int baseColumns = n / rows, extra = n % rows;
    int hullSize = 2 * rows + baseColumns + (extra > 0 ? 1 : 0) + baseColumns - 4;
    for (int row = 0; row < rows; row++) {
      int columns = baseColumns + (row < extra ? 1 : 0);
      double v = (double) row / (rows - 1);
      for (int column = 0; column < columns; column++) {
        double u = (double) column / (columns - 1);
        double x = .06 + .88 * u, y = .06 + .88 * v;
        // Bow the boundary outward so its grid points are strictly convex.
        if (column == 0) x -= .025 * Math.sin(Math.PI * v);
        else if (column == columns - 1) x += .025 * Math.sin(Math.PI * v);
        if (row == 0) y -= .025 * Math.sin(Math.PI * u);
        else if (row == rows - 1) y += .025 * Math.sin(Math.PI * u);
        if (row > 0 && row < rows - 1 && column > 0 && column < columns - 1) {
          x += (random.nextDouble() - .5) * .24 * .88 / (columns - 1);
          y += (random.nextDouble() - .5) * .24 * .88 / (rows - 1);
        }
        points.add(new PuzzleLayout.Point(x, y));
      }
    }
    var pool = new ArrayList<Edge>();
    for (int a = 0; a < n; a++) for (int b = a + 1; b < n; b++) pool.add(new Edge(a, b));
    pool.sort(
        Comparator.comparingDouble(
            e -> distance(points.get(e.a().value()), points.get(e.b().value()))));
    var edges = new ArrayList<Edge>();
    for (var edge : pool) {
      var a = points.get(edge.a().value());
      var b = points.get(edge.b().value());
      boolean crossing = false;
      for (var other : edges) {
        if (edge.a().equals(other.a())
            || edge.a().equals(other.b())
            || edge.b().equals(other.a())
            || edge.b().equals(other.b())) continue;
        if (PuzzleValidator.intersects(
            a, b, points.get(other.a().value()), points.get(other.b().value()))) {
          crossing = true;
          break;
        }
      }
      if (!crossing) edges.add(edge);
    }
    if (edges.size() != 3 * n - 3 - hullSize)
      throw new IllegalStateException("Incomplete triangulation");
    return new PlaneTriangulation(new GraphTopology(n, edges), new PuzzleLayout(points));
  }

  private static double distance(PuzzleLayout.Point a, PuzzleLayout.Point b) {
    return Math.hypot(a.x() - b.x(), a.y() - b.y());
  }
}
