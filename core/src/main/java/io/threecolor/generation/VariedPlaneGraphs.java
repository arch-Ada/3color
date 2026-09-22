package io.threecolor.generation;

import io.threecolor.layout.LayoutQuality;
import io.threecolor.model.*;
import io.threecolor.validation.PuzzleValidator;
import java.util.*;

/**
 * Aligned rectangular grids with varied aspect ratios and locally biased planar edge construction.
 */
public final class VariedPlaneGraphs {
  public static String family(long seed) {
    return switch (Math.floorMod(seed, 3)) {
      case 0 -> "grid-balanced";
      case 1 -> "grid-wide";
      default -> "grid-tall";
    };
  }

  public static PlaneTriangulation create(int n, long seed) {
    if (n < 4 || n > 53) throw new IllegalArgumentException("Sizes 4..53");
    var random = new Random(seed ^ 0x631baeL);
    var points = new ArrayList<PuzzleLayout.Point>();
    int offset = Math.floorMod(seed, 3) - 1;
    int rows = Math.min(n / 2, Math.max(2, (int) Math.round(Math.sqrt(n)) + offset));
    int columns = (n + rows - 1) / rows;
    double maximumEdgeLength = Math.max(.65, .88 / Math.min(rows - 1, columns - 1));
    for (int r = 0; r < rows; r++) {
      int inRow = n / rows + (r < n % rows ? 1 : 0);
      int startColumn = (columns - inRow) / 2;
      for (int c = 0; c < inRow; c++) {
        points.add(
            new PuzzleLayout.Point(
                .06 + .88 * (c + startColumn) / (columns - 1), .06 + .88 * r / (rows - 1)));
      }
    }
    var pool = new ArrayList<Edge>();
    var priorities = new HashMap<Edge, Double>();
    for (int a = 0; a < n; a++)
      for (int b = a + 1; b < n; b++) {
        var edge = new Edge(a, b);
        pool.add(edge);
        priorities.put(
            edge, distance(points.get(a), points.get(b)) * (.8 + .4 * random.nextDouble()));
      }
    pool.sort(Comparator.comparingDouble(priorities::get));
    var edges = new ArrayList<Edge>();
    int[] degrees = new int[n];
    int maximumDegree = 5 + random.nextInt(3);
    for (var edge : pool) {
      int av = edge.a().value(), bv = edge.b().value();
      var a = points.get(av);
      var b = points.get(bv);
      if (degrees[av] >= maximumDegree
          || degrees[bv] >= maximumDegree
          || distance(a, b) > maximumEdgeLength) continue;
      boolean clear = true;
      for (int v = 0; v < n && clear; v++)
        if (v != av && v != bv && LayoutQuality.distance(points.get(v), a, b) < .04 / Math.sqrt(n))
          clear = false;
      for (var other : edges) {
        if (edge.a().equals(other.a())
            || edge.a().equals(other.b())
            || edge.b().equals(other.a())
            || edge.b().equals(other.b())) continue;
        if (PuzzleValidator.intersects(
            a, b, points.get(other.a().value()), points.get(other.b().value()))) {
          clear = false;
          break;
        }
      }
      if (clear) {
        edges.add(edge);
        degrees[av]++;
        degrees[bv]++;
      }
    }
    return new PlaneTriangulation(new GraphTopology(n, edges), new PuzzleLayout(points));
  }

  private static double distance(PuzzleLayout.Point a, PuzzleLayout.Point b) {
    return Math.hypot(a.x() - b.x(), a.y() - b.y());
  }
}
