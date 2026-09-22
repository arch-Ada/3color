package io.threecolor.layout;

import io.threecolor.model.*;
import java.util.Arrays;

/** Presentation measurements only: normalized Euclidean distances, never difficulty inputs. */
public record LayoutQuality(
    double minimumVertexDistance,
    double minimumEdgeClearance,
    double boundingBoxArea,
    double occupiedGridFraction,
    double meanEdgeLength,
    double medianEdgeLength,
    double p90EdgeLength,
    double maximumEdgeLength,
    double maxMedianEdgeRatio,
    double edgeLengthCoefficientOfVariation,
    double gridEntropy,
    double minimumIncidentAngleDegrees) {
  public static LayoutQuality measure(GraphTopology graph, PuzzleLayout layout) {
    double vertex = Double.POSITIVE_INFINITY, edge = Double.POSITIVE_INFINITY;
    double minX = Double.POSITIVE_INFINITY, minY = minX, maxX = -minX, maxY = -minX;
    int[] occupied = new int[25];
    var p = layout.points();
    for (int i = 0; i < p.size(); i++) {
      var a = p.get(i);
      minX = Math.min(minX, a.x());
      maxX = Math.max(maxX, a.x());
      minY = Math.min(minY, a.y());
      maxY = Math.max(maxY, a.y());
      occupied[Math.clamp((int) (a.x() * 5), 0, 4) + 5 * Math.clamp((int) (a.y() * 5), 0, 4)]++;
      for (int j = 0; j < i; j++)
        vertex = Math.min(vertex, Math.hypot(a.x() - p.get(j).x(), a.y() - p.get(j).y()));
      for (var e : graph.edges())
        if (i != e.a().value() && i != e.b().value())
          edge = Math.min(edge, distance(a, p.get(e.a().value()), p.get(e.b().value())));
    }
    int cells = 0;
    double entropy = 0;
    for (int count : occupied)
      if (count > 0) {
        cells++;
        double probability = count / (double) p.size();
        entropy -= probability * Math.log(probability) / Math.log(25);
      }
    double[] lengths =
        graph.edges().stream()
            .mapToDouble(
                e ->
                    Math.hypot(
                        p.get(e.a().value()).x() - p.get(e.b().value()).x(),
                        p.get(e.a().value()).y() - p.get(e.b().value()).y()))
            .sorted()
            .toArray();
    int m = lengths.length;
    double mean = Arrays.stream(lengths).average().orElse(0);
    double median = m == 0 ? 0 : (lengths[(m - 1) / 2] + lengths[m / 2]) / 2;
    double maximum = m == 0 ? 0 : lengths[m - 1];
    double variance = Arrays.stream(lengths).map(x -> (x - mean) * (x - mean)).average().orElse(0);
    double angle = 180;
    for (int v = 0; v < graph.nodeCount(); v++) {
      var origin = p.get(v);
      var neighbors = graph.neighbors(v);
      for (int i = 0; i < neighbors.length; i++)
        for (int j = 0; j < i; j++) {
          var a = p.get(neighbors[i]);
          var b = p.get(neighbors[j]);
          double ax = a.x() - origin.x(), ay = a.y() - origin.y();
          double bx = b.x() - origin.x(), by = b.y() - origin.y();
          double norm = Math.hypot(ax, ay) * Math.hypot(bx, by);
          angle =
              Math.min(
                  angle,
                  norm == 0
                      ? 0
                      : Math.toDegrees(Math.acos(Math.clamp((ax * bx + ay * by) / norm, -1, 1))));
        }
    }
    return new LayoutQuality(
        vertex,
        edge,
        (maxX - minX) * (maxY - minY),
        cells / 25.,
        mean,
        median,
        m == 0 ? 0 : lengths[(int) Math.ceil(.9 * m) - 1],
        maximum,
        median == 0 ? 0 : maximum / median,
        mean == 0 ? 0 : Math.sqrt(variance) / mean,
        entropy,
        angle);
  }

  public static double distance(PuzzleLayout.Point p, PuzzleLayout.Point a, PuzzleLayout.Point b) {
    double dx = b.x() - a.x(), dy = b.y() - a.y();
    if (dx == 0 && dy == 0) return Math.hypot(p.x() - a.x(), p.y() - a.y());
    double t =
        Math.clamp(((p.x() - a.x()) * dx + (p.y() - a.y()) * dy) / (dx * dx + dy * dy), 0, 1);
    return Math.hypot(p.x() - a.x() - t * dx, p.y() - a.y() - t * dy);
  }
}
