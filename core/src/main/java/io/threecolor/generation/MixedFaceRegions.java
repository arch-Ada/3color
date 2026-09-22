package io.threecolor.generation;

import io.threecolor.model.*;
import java.util.*;

/**
 * Seeded face merging in the plane dual: quadrilateral patches with remaining triangles and larger
 * faces.
 */
public final class MixedFaceRegions {
  public static final String VERSION = "mixed-faces-v1";

  private MixedFaceRegions() {}

  public static PlaneTriangulation create(int n, long seed) {
    var base = PlaneTriangulation.create(n, seed);
    var random = new Random(seed ^ 0x4d49584544464143L);
    var edges = new ArrayList<>(base.graph().edges());
    var order = new ArrayList<>(edges);
    Collections.shuffle(order, random);
    var center = base.layout().points().get(random.nextInt(n));
    // Some seeds concentrate merges into a region; others distribute them over the board.
    if (random.nextBoolean())
      order.sort(
          Comparator.comparingDouble(
              e -> {
                var a = base.layout().points().get(e.a().value());
                var b = base.layout().points().get(e.b().value());
                return Math.hypot(
                    (a.x() + b.x()) / 2 - center.x(), (a.y() + b.y()) / 2 - center.y());
              }));
    int faceCount = PlaneFaces.bounded(base.graph(), base.layout()).size();
    int wanted = Math.max(1, (int) (faceCount * (.22 + random.nextDouble() * .22)));
    int merged = 0;
    for (var edge : order) {
      var faces = PlaneFaces.bounded(new GraphTopology(n, edges), base.layout());
      var touching = faces.stream().filter(f -> f.edges().contains(edge)).toList();
      if (touching.size() == 2
          && touching.stream().allMatch(f -> f.vertices().size() == 3)
          && simpleUnion(touching)) {
        edges.remove(edge);
        if (++merged >= wanted) break;
      }
    }
    Collections.shuffle(order, random);
    int larger = random.nextInt(Math.max(2, n / 8));
    for (var edge : order) {
      if (larger == 0 || !edges.contains(edge)) continue;
      var faces = PlaneFaces.bounded(new GraphTopology(n, edges), base.layout());
      if (faces.stream().filter(f -> f.vertices().size() == 4).count() <= 2) break;
      var touching = faces.stream().filter(f -> f.edges().contains(edge)).toList();
      if (touching.size() != 2) continue;
      int a = touching.get(0).vertices().size(), b = touching.get(1).vertices().size();
      if (a <= 4 && b <= 4 && a + b >= 7 && simpleUnion(touching)) {
        edges.remove(edge);
        larger--;
      }
    }
    return new PlaneTriangulation(new GraphTopology(n, edges), base.layout());
  }

  private static boolean simpleUnion(List<PlaneFaces.Face> faces) {
    var a = faces.get(0);
    var b = faces.get(1);
    var common = new HashSet<>(a.edges());
    common.retainAll(b.edges());
    var vertices = new HashSet<>(a.vertices());
    vertices.addAll(b.vertices());
    return common.size() == 1 && vertices.size() == a.vertices().size() + b.vertices().size() - 2;
  }
}
