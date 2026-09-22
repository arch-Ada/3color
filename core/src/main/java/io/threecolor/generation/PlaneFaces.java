package io.threecolor.generation;

import io.threecolor.model.*;
import java.util.*;

/** Face walks induced by a crossing-free straight-line embedding. Outer walk is excluded. */
public final class PlaneFaces {
  private PlaneFaces() {}

  public record Face(List<Integer> vertices, Set<Edge> edges) {
    public Face {
      vertices = List.copyOf(vertices);
      edges = Set.copyOf(edges);
    }
  }

  public static List<Face> bounded(GraphTopology graph, PuzzleLayout layout) {
    var rotation = new ArrayList<List<Integer>>();
    for (int v = 0; v < graph.nodeCount(); v++) {
      var origin = layout.points().get(v);
      var neighbors = new ArrayList<Integer>();
      for (int w : graph.neighbors(v)) neighbors.add(w);
      neighbors.sort(
          Comparator.comparingDouble(
              w -> {
                var p = layout.points().get(w);
                return Math.atan2(p.y() - origin.y(), p.x() - origin.x());
              }));
      rotation.add(neighbors);
    }
    var visited = new HashSet<Long>();
    var faces = new ArrayList<Face>();
    for (var edge : graph.edges())
      for (int direction = 0; direction < 2; direction++) {
        int start = direction == 0 ? edge.a().value() : edge.b().value();
        int end = direction == 0 ? edge.b().value() : edge.a().value();
        if (visited.contains(key(start, end))) continue;
        var vertices = new ArrayList<Integer>();
        var edges = new HashSet<Edge>();
        int u = start, v = end;
        double area = 0;
        do {
          if (!visited.add(key(u, v))) throw new IllegalArgumentException("Invalid plane rotation");
          vertices.add(u);
          edges.add(new Edge(u, v));
          var a = layout.points().get(u);
          var b = layout.points().get(v);
          area += a.x() * b.y() - a.y() * b.x();
          var around = rotation.get(v);
          int next = around.get(Math.floorMod(around.indexOf(u) - 1, around.size()));
          u = v;
          v = next;
        } while (u != start || v != end);
        if (area > 1e-10) faces.add(new Face(vertices, edges));
      }
    return List.copyOf(faces);
  }

  private static long key(int a, int b) {
    return ((long) a << 32) | b;
  }
}
