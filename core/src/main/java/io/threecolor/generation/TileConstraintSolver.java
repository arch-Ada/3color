package io.threecolor.generation;

import java.util.*;

/**
 * Bounded table-constraint search over shared boundary vertices; interior counts saturate at two.
 */
public final class TileConstraintSolver {
  public record Result(
      int count, boolean exhausted, boolean complete, List<Integer> witness, int nodes) {
    public Result {
      witness = List.copyOf(witness);
    }

    public boolean unique() {
      return count == 1 && complete;
    }
  }

  private record Relation(List<Integer> ports, int[][] values, long[] extensions) {}

  private final int size;
  private final List<Relation> relations;

  public TileConstraintSolver(ThreePortTile.Assembly assembly, boolean uniqueInteriorsOnly) {
    size = assembly.junctionCount();
    var tables = new ArrayList<Relation>();
    for (int i = 0; i < assembly.placements().size(); i++) {
      var rows =
          assembly.placements().get(i).tile().rows().stream()
              .filter(r -> !uniqueInteriorsOnly || r.count() == 1)
              .toList();
      tables.add(
          new Relation(
              assembly.ports().get(i),
              rows.stream().map(r -> ThreePortTile.decode(r.code())).toArray(int[][]::new),
              rows.stream().mapToLong(ThreePortTile.Row::extensions).toArray()));
    }
    relations = List.copyOf(tables);
  }

  public Result count(Map<Integer, Integer> clues, int budget) {
    return search(clues, budget, 2, 0);
  }

  /** A witness search does not certify uniqueness; callers must use count() for that. */
  public Result witness(int budget, long seed) {
    return search(Map.of(), budget, 1, seed);
  }

  /** Vary value preferences per junction to explore more than global colour permutations. */
  public Result diverseWitness(int budget, long seed) {
    return search(Map.of(), budget, 1, seed, true);
  }

  private Result search(Map<Integer, Integer> clues, int budget, int cap, long seed) {
    return search(clues, budget, cap, seed, false);
  }

  private Result search(
      Map<Integer, Integer> clues, int budget, int cap, long seed, boolean diverse) {
    if (budget < 1) throw new IllegalArgumentException("Positive budget required");
    var domains = new int[size];
    Arrays.fill(domains, 7);
    for (var e : clues.entrySet()) {
      if (e.getKey() < 0
          || e.getKey() >= size + 3 * relations.size()
          || e.getValue() < 0
          || e.getValue() > 2) throw new IllegalArgumentException("Invalid junction clue");
      if (e.getKey() < size) domains[e.getKey()] = 1 << e.getValue();
    }
    var work = new Search(budget, cap, seed, clues, diverse);
    work.visit(domains);
    return new Result(
        work.total, work.exhausted, cap == 2 && !work.exhausted, work.witness, work.nodes);
  }

  private final class Search {
    final int budget, cap;
    final List<int[]> weights = new ArrayList<>();
    final int[] colorOrder = {0, 1, 2};
    final int[][] preferences;
    int nodes, total;
    boolean exhausted;
    List<Integer> witness = List.of();

    Search(int budget, int cap, long seed, Map<Integer, Integer> clues, boolean diverse) {
      for (int i = 0; i < relations.size(); i++) {
        long allowed = 0;
        for (int code = 0; code < 27; code++) {
          int rest = code;
          boolean matches = true;
          for (int v = 0; v < 3; v++) {
            if (clues.containsKey(size + 3 * i + v) && clues.get(size + 3 * i + v) != rest % 3)
              matches = false;
            rest /= 3;
          }
          if (matches) allowed |= 1L << code;
        }
        int[] counts = new int[relations.get(i).extensions().length];
        for (int r = 0; r < counts.length; r++)
          counts[r] = Math.min(2, Long.bitCount(relations.get(i).extensions()[r] & allowed));
        weights.add(counts);
      }
      this.budget = budget;
      this.cap = cap;
      var random = new Random(seed);
      for (int i = 2; i > 0; i--) {
        int j = random.nextInt(i + 1), t = colorOrder[i];
        colorOrder[i] = colorOrder[j];
        colorOrder[j] = t;
      }
      preferences = diverse ? new int[size][3] : null;
      if (diverse)
        for (int v = 0; v < size; v++) {
          preferences[v] = new int[] {0, 1, 2};
          for (int i = 2; i > 0; i--) {
            int j = random.nextInt(i + 1), t = preferences[v][i];
            preferences[v][i] = preferences[v][j];
            preferences[v][j] = t;
          }
        }
    }

    void visit(int[] domains) {
      if (total >= cap || exhausted) return;
      if (nodes++ >= budget || Thread.currentThread().isInterrupted()) {
        exhausted = true;
        return;
      }
      boolean changed;
      do {
        changed = false;
        for (int i = 0; i < relations.size(); i++) {
          var relation = relations.get(i);
          int[] support = new int[8];
          boolean any = false;
          for (int r = 0; r < relation.values().length; r++) {
            if (weights.get(i)[r] == 0) continue;
            var row = relation.values()[r];
            boolean valid = true;
            for (int p = 0; p < 8; p++)
              if ((domains[relation.ports().get(p)] & (1 << row[p])) == 0) {
                valid = false;
                break;
              }
            if (!valid) continue;
            any = true;
            for (int p = 0; p < 8; p++) support[p] |= 1 << row[p];
          }
          if (!any) return;
          for (int p = 0; p < 8; p++) {
            int v = relation.ports().get(p);
            int next = domains[v] & support[p];
            if (next != domains[v]) {
              domains[v] = next;
              changed = true;
            }
          }
        }
      } while (changed);
      int branch = -1, best = 4;
      for (int v = 0; v < size; v++) {
        int bits = Integer.bitCount(domains[v]);
        if (bits > 1 && bits < best) {
          branch = v;
          best = bits;
        }
      }
      if (branch < 0) {
        int product = 1;
        for (int i = 0; i < relations.size(); i++) {
          var relation = relations.get(i);
          for (int r = 0; r < relation.values().length; r++) {
            boolean matches = true;
            for (int p = 0; p < 8; p++)
              if (domains[relation.ports().get(p)] != (1 << relation.values()[r][p])) {
                matches = false;
                break;
              }
            if (matches) {
              product = Math.min(2, product * weights.get(i)[r]);
              break;
            }
          }
        }
        if (witness.isEmpty()) {
          var values = new ArrayList<Integer>();
          for (int mask : domains) values.add(Integer.numberOfTrailingZeros(mask));
          witness = values;
        }
        total = Math.min(cap, total + product);
        return;
      }
      for (int color : preferences == null ? colorOrder : preferences[branch])
        if ((domains[branch] & (1 << color)) != 0) {
          var next = domains.clone();
          next[branch] = 1 << color;
          visit(next);
          if (total >= cap || exhausted) return;
        }
    }
  }
}
