package io.threecolor.generation;

import io.threecolor.model.*;
import io.threecolor.validation.PuzzleValidator;
import java.util.*;

/** Rectangular disc patch; only its four corners can be identified during assembly. */
public record RegionPatch(
    GraphTopology graph, PuzzleLayout layout, RegionTable table, PartialColoring clues) {
  public static RegionPatch create(long seed) {
    return create(seed, 2, 3);
  }

  public static RegionPatch create(long seed, int interiorColumns, int interiorRows) {
    if (interiorColumns < 1 || interiorRows < 1 || interiorColumns * interiorRows > 12)
      throw new IllegalArgumentException("Patch interior: 1..12 vertices");
    int n = 4 + interiorColumns * interiorRows;
    var random = new Random(seed);
    var points =
        new ArrayList<PuzzleLayout.Point>(
            List.of(
                new PuzzleLayout.Point(0, 0), new PuzzleLayout.Point(0, 1),
                new PuzzleLayout.Point(1, 0), new PuzzleLayout.Point(1, 1)));
    for (int x = 1; x <= interiorColumns; x++)
      for (int y = 1; y <= interiorRows; y++)
        points.add(
            new PuzzleLayout.Point(
                x / (interiorColumns + 1.0) + (random.nextDouble() - .5) * .12,
                y / (interiorRows + 1.0) + (random.nextDouble() - .5) * .12));
    var edges = new ArrayList<Edge>();
    var pool = new ArrayList<Edge>();
    for (int a = 0; a < n; a++) for (int b = a + 1; b < n; b++) pool.add(new Edge(a, b));
    pool.sort(Comparator.comparingDouble(e -> distance(points, e)));
    for (var e : pool) {
      boolean crosses = false;
      for (var f : edges) {
        if (e.a().equals(f.a())
            || e.a().equals(f.b())
            || e.b().equals(f.a())
            || e.b().equals(f.b())) continue;
        if (PuzzleValidator.intersects(
            points.get(e.a().value()),
            points.get(e.b().value()),
            points.get(f.a().value()),
            points.get(f.b().value()))) {
          crosses = true;
          break;
        }
      }
      if (!crosses) edges.add(e);
    }
    if (edges.size() != 3 * n - 7)
      throw new IllegalStateException("Patch triangulation incomplete");
    Collections.shuffle(edges, random);
    int removals = 2 + random.nextInt(n);
    for (var edge : List.copyOf(edges)) {
      if (removals == 0) break;
      // Keep the rectangular boundary; only merge internal faces.
      if (edge.a().value() < 4 && edge.b().value() < 4) continue;
      edges.remove(edge);
      if (!connected(new GraphTopology(n, edges))) edges.add(edge);
      else removals--;
    }
    var graph = new GraphTopology(n, edges);
    return new RegionPatch(
        graph,
        new PuzzleLayout(points),
        RegionTable.build(graph, List.of(0, 1, 2, 3), PartialColoring.empty(), 100000, 2000),
        PartialColoring.empty());
  }

  public RegionPatch withClues(PartialColoring givens) {
    return new RegionPatch(
        graph, layout, RegionTable.build(graph, List.of(0, 1, 2, 3), givens, 100000, 2000), givens);
  }

  private static double distance(List<PuzzleLayout.Point> p, Edge e) {
    var a = p.get(e.a().value());
    var b = p.get(e.b().value());
    return Math.hypot(a.x() - b.x(), a.y() - b.y());
  }

  private static boolean connected(GraphTopology graph) {
    var seen = new HashSet<Integer>();
    var queue = new ArrayDeque<Integer>();
    queue.add(0);
    while (!queue.isEmpty()) {
      int v = queue.remove();
      if (seen.add(v)) for (int u : graph.neighbors(v)) queue.add(u);
    }
    return seen.size() == graph.nodeCount();
  }

  public record Assembly(
      GraphTopology graph,
      PuzzleLayout layout,
      List<RegionTable> tables,
      List<List<Integer>> junctions,
      int junctionCount,
      PartialColoring clues) {}

  /** Non-overlapping unit rectangles in a grid. Shared side edges are deduplicated. */
  public static Assembly assemble(List<RegionPatch> patches, int columns, int rows) {
    if (columns < 1 || rows < 1 || columns * rows != patches.size())
      throw new IllegalArgumentException("Invalid patch grid");
    int junctionCount = (columns + 1) * (rows + 1);
    var points = new ArrayList<PuzzleLayout.Point>();
    for (int y = 0; y <= rows; y++)
      for (int x = 0; x <= columns; x++)
        points.add(new PuzzleLayout.Point(.06 + .88 * x / columns, .06 + .88 * y / rows));
    var edges = new TreeSet<Edge>();
    var givens = new TreeMap<NodeId, Color>();
    var tables = new ArrayList<RegionTable>();
    var junctions = new ArrayList<List<Integer>>();
    for (int i = 0; i < patches.size(); i++) {
      int x = i % columns, y = i / columns;
      var patch = patches.get(i);
      var ids =
          List.of(
              y * (columns + 1) + x,
              (y + 1) * (columns + 1) + x,
              y * (columns + 1) + x + 1,
              (y + 1) * (columns + 1) + x + 1);
      junctions.add(ids);
      tables.add(patch.table());
      int[] map = new int[patch.graph().nodeCount()];
      for (int j = 0; j < 4; j++) map[j] = ids.get(j);
      for (int j = 4; j < map.length; j++) {
        map[j] = points.size();
        var p = patch.layout().points().get(j);
        points.add(
            new PuzzleLayout.Point(
                .06 + .88 * (x + p.x()) / columns, .06 + .88 * (y + p.y()) / rows));
      }
      for (var clue : patch.clues().colors().entrySet()) {
        var old = givens.put(new NodeId(map[clue.getKey().value()]), clue.getValue());
        if (old != null && old != clue.getValue())
          throw new IllegalArgumentException("Conflicting patch clues");
      }
      for (var e : patch.graph().edges())
        edges.add(new Edge(map[e.a().value()], map[e.b().value()]));
    }
    return new Assembly(
        new GraphTopology(points.size(), edges),
        new PuzzleLayout(points),
        List.copyOf(tables),
        List.copyOf(junctions),
        junctionCount,
        new PartialColoring(givens));
  }
}
