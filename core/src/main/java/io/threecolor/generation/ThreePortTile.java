package io.threecolor.generation;

import io.threecolor.deduction.ProofLevels;
import io.threecolor.layout.LayoutQuality;
import io.threecolor.model.*;
import io.threecolor.solve.*;
import io.threecolor.validation.PuzzleValidator;
import java.util.*;

/** Rectangular tile with an eight-vertex boundary cycle, three vertices on each side. */
public record ThreePortTile(long seed, GraphTopology graph, PuzzleLayout layout, List<Row> rows) {
  public static final int TABLE_SIZE = 6561;

  public record Row(int code, int count, ProofLevels.Status p1, long extensions) {}

  public ThreePortTile {
    rows = List.copyOf(rows);
  }

  public static ThreePortTile create(long seed) {
    var random = new Random(seed);
    var points =
        new ArrayList<PuzzleLayout.Point>(
            List.of(
                new PuzzleLayout.Point(0, 0),
                new PuzzleLayout.Point(.5, 0),
                new PuzzleLayout.Point(1, 0),
                new PuzzleLayout.Point(1, .5),
                new PuzzleLayout.Point(1, 1),
                new PuzzleLayout.Point(.5, 1),
                new PuzzleLayout.Point(0, 1),
                new PuzzleLayout.Point(0, .5)));
    for (var p :
        List.of(
            new PuzzleLayout.Point(.3, .3),
            new PuzzleLayout.Point(.7, .3),
            new PuzzleLayout.Point(.5, .7)))
      points.add(
          new PuzzleLayout.Point(
              p.x() + (random.nextDouble() - .5) * .16, p.y() + (random.nextDouble() - .5) * .16));
    var edges = new ArrayList<Edge>();
    var pool = new ArrayList<Edge>();
    for (int v = 0; v < 8; v++) edges.add(new Edge(v, (v + 1) % 8));
    for (int a = 0; a < 11; a++)
      for (int b = a + 1; b < 11; b++)
        if (!edges.contains(new Edge(a, b))) pool.add(new Edge(a, b));
    Collections.shuffle(pool, random);
    pool.sort(
        Comparator.comparingDouble(
            e -> {
              var a = points.get(e.a().value());
              var b = points.get(e.b().value());
              return Math.hypot(a.x() - b.x(), a.y() - b.y());
            }));
    for (var edge : pool) {
      var a = points.get(edge.a().value());
      var b = points.get(edge.b().value());
      boolean invalid = false;
      for (int v = 0; v < 11; v++)
        if (v != edge.a().value()
            && v != edge.b().value()
            && LayoutQuality.distance(points.get(v), a, b) < 1e-10) invalid = true;
      for (var other : edges) {
        if (edge.a().equals(other.a())
            || edge.a().equals(other.b())
            || edge.b().equals(other.a())
            || edge.b().equals(other.b())) continue;
        if (PuzzleValidator.intersects(
            a, b, points.get(other.a().value()), points.get(other.b().value()))) invalid = true;
      }
      if (!invalid) edges.add(edge);
    }
    if (edges.size() != 22) throw new IllegalStateException("Incomplete tile triangulation");
    var removable = new ArrayList<>(edges.subList(8, edges.size()));
    Collections.shuffle(removable, random);
    int remove = 1 + random.nextInt(7);
    for (var edge : removable) {
      if (remove == 0) break;
      var g = new GraphTopology(11, edges);
      if ((edge.a().value() >= 8 && g.degree(edge.a().value()) <= 2)
          || (edge.b().value() >= 8 && g.degree(edge.b().value()) <= 2)) continue;
      edges.remove(edge);
      remove--;
    }
    var graph = new GraphTopology(11, edges);
    return tabulate(seed, graph, new PuzzleLayout(points));
  }

  public static ThreePortTile tabulate(long seed, GraphTopology graph, PuzzleLayout layout) {
    if (graph.nodeCount() != 11 || layout.points().size() != 11)
      throw new IllegalArgumentException("Eleven vertices required");
    var rows = new ArrayList<Row>();
    // Boundary colour conflicts are impossible without inspecting the interior.
    for (int code = 0; code < TABLE_SIZE; code++) {
      var values = decode(code);
      boolean valid = true;
      for (int v = 0; v < 8; v++) if (values[v] == values[(v + 1) % 8]) valid = false;
      if (!valid) continue;
      var colors = new TreeMap<NodeId, Color>();
      for (int v = 0; v < 8; v++) colors.put(new NodeId(v), Color.values()[values[v]]);
      long extensions = 0;
      for (int interior = 0; interior < 27; interior++) {
        var full = new int[11];
        System.arraycopy(values, 0, full, 0, 8);
        int remaining = interior;
        for (int v = 8; v < 11; v++) {
          full[v] = remaining % 3;
          remaining /= 3;
        }
        if (graph.edges().stream().allMatch(e -> full[e.a().value()] != full[e.b().value()]))
          extensions |= 1L << interior;
      }
      var clues = new PartialColoring(colors);
      var exact = new ExactSolver().uniqueness(graph, clues, 100000, SolutionEquivalence.LABELED);
      if (exact.status() == UniquenessResult.UNKNOWN)
        throw new IllegalStateException("Tile table UNKNOWN");
      if (exact.status() != UniquenessResult.UNSATISFIABLE)
        rows.add(
            new Row(
                code,
                exact.status() == UniquenessResult.UNIQUE ? 1 : 2,
                new ProofLevels().solve(graph, clues, 1, 2000).status(),
                extensions));
    }
    return new ThreePortTile(seed, graph, layout, rows);
  }

  public static int[] decode(int code) {
    var values = new int[8];
    for (int v = 0; v < 8; v++) {
      values[v] = code % 3;
      code /= 3;
    }
    return values;
  }

  public String counts() {
    var counts = new char[TABLE_SIZE];
    Arrays.fill(counts, '0');
    for (var row : rows) counts[row.code()] = (char) ('0' + row.count());
    return new String(counts);
  }

  public boolean eligible() {
    return rows.stream().anyMatch(r -> r.count() == 1);
  }

  public record Placement(ThreePortTile tile, int rotation) {
    public Placement {
      if (rotation < 0 || rotation > 3) throw new IllegalArgumentException("Rotation 0..3");
    }
  }

  public record Assembly(
      GraphTopology graph,
      PuzzleLayout layout,
      List<Placement> placements,
      List<List<Integer>> ports,
      List<Set<Integer>> regions,
      int junctionCount,
      int columns,
      int rows) {}

  public static Assembly assemble(List<Placement> placements, int columns, int rows) {
    if (columns < 1 || rows < 1 || placements.size() != columns * rows)
      throw new IllegalArgumentException("Invalid tile grid");
    int width = 2 * columns + 1;
    var grid = new HashMap<Integer, Integer>();
    var points = new ArrayList<PuzzleLayout.Point>();
    for (int y = 0; y <= 2 * rows; y++)
      for (int x = 0; x <= 2 * columns; x++)
        if (x % 2 == 0 || y % 2 == 0) {
          grid.put(y * width + x, points.size());
          points.add(
              new PuzzleLayout.Point(.06 + .88 * x / (2 * columns), .06 + .88 * y / (2 * rows)));
        }
    int junctions = points.size();
    var edges = new TreeSet<Edge>();
    var ports = new ArrayList<List<Integer>>();
    var regions = new ArrayList<Set<Integer>>();
    int[] dx = {0, 1, 2, 2, 2, 1, 0, 0}, dy = {0, 0, 0, 1, 2, 2, 2, 1};
    for (int i = 0; i < placements.size(); i++) {
      int x = i % columns, y = i / columns;
      var placement = placements.get(i);
      var tile = placement.tile();
      int rotation = placement.rotation();
      var boundary = new ArrayList<Integer>();
      int[] map = new int[11];
      for (int v = 0; v < 8; v++) {
        int rotated = (v + 2 * rotation) % 8;
        map[v] = grid.get((2 * y + dy[rotated]) * width + 2 * x + dx[rotated]);
        boundary.add(map[v]);
      }
      for (int v = 8; v < 11; v++) {
        var p = tile.layout().points().get(v);
        double px = p.x(), py = p.y();
        for (int turn = 0; turn < rotation; turn++) {
          double next = 1 - py;
          py = px;
          px = next;
        }
        map[v] = points.size();
        points.add(
            new PuzzleLayout.Point(.06 + .88 * (x + px) / columns, .06 + .88 * (y + py) / rows));
      }
      var region = new TreeSet<Integer>();
      for (int v : map) region.add(v);
      regions.add(Set.copyOf(region));
      ports.add(List.copyOf(boundary));
      for (var e : tile.graph().edges())
        edges.add(new Edge(map[e.a().value()], map[e.b().value()]));
    }
    return new Assembly(
        new GraphTopology(points.size(), edges),
        new PuzzleLayout(points),
        List.copyOf(placements),
        List.copyOf(ports),
        List.copyOf(regions),
        junctions,
        columns,
        rows);
  }
}
