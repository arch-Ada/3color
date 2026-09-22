package io.threecolor.validation;

import io.threecolor.model.*;
import java.util.*;

public record GraphMetrics(
    int n,
    int m,
    double averageDegree,
    int minDegree,
    int maxDegree,
    Map<Integer, Integer> degreeDistribution,
    int triangleCount,
    int connectedComponents,
    int cycleRank) {
  public GraphMetrics {
    degreeDistribution = Collections.unmodifiableMap(new TreeMap<>(degreeDistribution));
  }

  public static GraphMetrics of(GraphTopology g) {
    var histogram = new TreeMap<Integer, Integer>();
    int min = Integer.MAX_VALUE, max = 0, triangles = 0, components = 0;
    boolean[] seen = new boolean[g.nodeCount()];
    for (int v = 0; v < g.nodeCount(); v++) {
      int d = g.degree(v);
      min = Math.min(min, d);
      max = Math.max(max, d);
      histogram.merge(d, 1, Integer::sum);
      for (int a : g.neighbors(v))
        if (a > v) for (int b : g.neighbors(a)) if (b > a && g.adjacent(v, b)) triangles++;
      if (!seen[v]) {
        components++;
        var q = new ArrayDeque<Integer>();
        q.add(v);
        seen[v] = true;
        while (!q.isEmpty())
          for (int w : g.neighbors(q.remove()))
            if (!seen[w]) {
              seen[w] = true;
              q.add(w);
            }
      }
    }
    return new GraphMetrics(
        g.nodeCount(),
        g.edges().size(),
        2.0 * g.edges().size() / g.nodeCount(),
        min,
        max,
        histogram,
        triangles,
        components,
        g.edges().size() - g.nodeCount() + components);
  }
}
