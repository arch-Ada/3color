package io.threecolor.generation;

import io.threecolor.deduction.ProofLevels;
import io.threecolor.model.*;
import io.threecolor.solve.*;
import java.util.*;

/** Exact labelled boundary semantics. Counts saturate at two; UNKNOWN never becomes a count. */
public record RegionTable(List<Integer> ports, List<Row> rows) {
  public record Row(int completions, ProofLevels.Status p1, List<Integer> domains) {
    public Row {
      domains = List.copyOf(domains);
    }
  }

  public RegionTable {
    ports = List.copyOf(ports);
    rows = List.copyOf(rows);
    if (ports.size() > 6
        || new HashSet<>(ports).size() != ports.size()
        || rows.size() != power(3, ports.size()))
      throw new IllegalArgumentException("Invalid table");
    for (var row : rows)
      if (row.completions() < 0 || row.completions() > 2)
        throw new IllegalArgumentException("Invalid count");
  }

  public static RegionTable build(
      GraphTopology graph,
      List<Integer> ports,
      PartialColoring clues,
      long exactBudget,
      int proofBudget) {
    if (ports.size() > 6
        || new HashSet<>(ports).size() != ports.size()
        || ports.stream().anyMatch(v -> v < 0 || v >= graph.nodeCount()))
      throw new IllegalArgumentException("Invalid ports");
    clues.validateNodes(graph);
    var rows = new ArrayList<Row>();
    for (int code = 0; code < power(3, ports.size()); code++) {
      var givens = new TreeMap<>(clues.colors());
      int rest = code;
      boolean conflict = false;
      for (int port : ports) {
        var color = Color.values()[rest % 3];
        rest /= 3;
        var old = givens.put(new NodeId(port), color);
        conflict |= old != null && old != color;
      }
      if (conflict) {
        rows.add(new Row(0, ProofLevels.Status.CONTRADICTION, List.of()));
        continue;
      }
      var partial = new PartialColoring(givens);
      var exact =
          new ExactSolver().uniqueness(graph, partial, exactBudget, SolutionEquivalence.LABELED);
      int count =
          switch (exact.status()) {
            case UNSATISFIABLE -> 0;
            case UNIQUE -> 1;
            case MULTIPLE -> 2;
            case UNKNOWN ->
                throw new IllegalStateException("Incomplete region table: exact budget exhausted");
          };
      var proof = new ProofLevels().solve(graph, partial, 1, proofBudget);
      rows.add(new Row(count, proof.status(), proof.domains()));
    }
    return new RegionTable(ports, rows);
  }

  /** Sum of products for arbitrary shared port IDs; unmentioned junctions are summed out. */
  public static int count(
      List<RegionTable> tables,
      List<List<Integer>> junctions,
      int junctionCount,
      Map<Integer, Integer> fixed) {
    if (tables.size() != junctions.size() || junctionCount < 0 || junctionCount > 12)
      throw new IllegalArgumentException("Invalid composition");
    for (int i = 0; i < tables.size(); i++) {
      var ids = junctions.get(i);
      if (ids.size() != tables.get(i).ports.size()
          || new HashSet<>(ids).size() != ids.size()
          || ids.stream().anyMatch(v -> v < 0 || v >= junctionCount))
        throw new IllegalArgumentException("Invalid interface");
    }
    for (var e : fixed.entrySet())
      if (e.getKey() < 0 || e.getKey() >= junctionCount || e.getValue() < 0 || e.getValue() > 2)
        throw new IllegalArgumentException("Invalid clue");
    int sum = 0;
    int[] colors = new int[junctionCount];
    for (int code = 0; code < power(3, junctionCount); code++) {
      int rest = code;
      for (int v = 0; v < junctionCount; v++) {
        colors[v] = rest % 3;
        rest /= 3;
      }
      boolean matches = true;
      for (var e : fixed.entrySet()) matches &= colors[e.getKey()] == e.getValue();
      if (!matches) continue;
      int product = 1;
      for (int i = 0; i < tables.size(); i++) {
        int row = 0, multiplier = 1;
        for (int v : junctions.get(i)) {
          row += multiplier * colors[v];
          multiplier *= 3;
        }
        product = Math.min(2, product * tables.get(i).rows.get(row).completions());
        if (product == 0) break;
      }
      sum = Math.min(2, sum + product);
      if (sum == 2) return 2;
    }
    return sum;
  }

  static int power(int base, int exponent) {
    int value = 1;
    for (int i = 0; i < exponent; i++) value *= base;
    return value;
  }
}
