package io.threecolor.solve;

import io.threecolor.model.*;
import java.util.*;

/** Framework-free exact search. All mutable state belongs to one invocation. */
public final class ExactSolver {
  public SolveResult solve(GraphTopology graph, PartialColoring givens, long budget) {
    return search(graph, givens, budget, 1, SolutionEquivalence.LABELED);
  }

  public SolveResult uniqueness(
      GraphTopology graph, PartialColoring givens, long budget, SolutionEquivalence equivalence) {
    return search(graph, givens, budget, 2, equivalence);
  }

  private SolveResult search(
      GraphTopology g, PartialColoring givens, long budget, int limit, SolutionEquivalence eq) {
    if (budget < 1) throw new IllegalArgumentException("Positive search budget required");
    givens.validateNodes(g);
    var s = new Search(g, budget, limit, eq);
    boolean valid = true;
    for (var e : givens.colors().entrySet())
      if (!s.assign(e.getKey().value(), e.getValue().mask())) {
        valid = false;
        break;
      }
    if (valid) s.visit(0);
    var status =
        s.solutions.size() >= 2
            ? UniquenessResult.MULTIPLE
            : s.exhausted || (limit == 1 && !s.solutions.isEmpty())
                ? UniquenessResult.UNKNOWN
                : s.solutions.isEmpty() ? UniquenessResult.UNSATISFIABLE : UniquenessResult.UNIQUE;
    return new SolveResult(
        status,
        s.solutions,
        new SolveStats(s.nodes, s.propagations, s.backtracks, s.depth, s.encountered),
        s.exhausted);
  }

  private static final class Search {
    final GraphTopology g;
    final long budget;
    final int limit;
    final SolutionEquivalence eq;
    final int[] domains, trailNodes, trailMasks, queue;
    int top, depth, encountered;
    long nodes, propagations, backtracks;
    boolean exhausted;
    final List<Coloring> solutions = new ArrayList<>();
    final Set<String> keys = new HashSet<>();

    Search(GraphTopology g, long budget, int limit, SolutionEquivalence eq) {
      this.g = g;
      this.budget = budget;
      this.limit = limit;
      this.eq = eq;
      domains = new int[g.nodeCount()];
      Arrays.fill(domains, 7);
      trailNodes = new int[g.nodeCount() * 3 + 1];
      trailMasks = new int[trailNodes.length];
      queue = new int[g.nodeCount() + 1];
    }

    void change(int v, int mask) {
      trailNodes[top] = v;
      trailMasks[top++] = domains[v];
      domains[v] = mask;
    }

    void rollback(int mark) {
      while (top > mark) {
        --top;
        domains[trailNodes[top]] = trailMasks[top];
      }
    }

    boolean assign(int v, int mask) {
      if ((domains[v] & mask) == 0) return false;
      if (domains[v] != mask) change(v, mask);
      int head = 0, tail = 0;
      queue[tail++] = v;
      while (head < tail) {
        int u = queue[head++], bit = domains[u];
        for (int i = 0; i < g.degree(u); i++) {
          int w = g.neighbor(u, i), old = domains[w], next = old & ~bit;
          if (next == old) continue;
          propagations++;
          change(w, next);
          if (next == 0) return false;
          if (Integer.bitCount(next) == 1) queue[tail++] = w;
        }
      }
      return true;
    }

    void visit(int level) {
      if (solutions.size() >= limit || exhausted) return;
      if (nodes >= budget || Thread.currentThread().isInterrupted()) {
        exhausted = true;
        return;
      }
      nodes++;
      depth = Math.max(depth, level);
      int v = -1, bestCount = 4, bestSat = -1, bestDegree = -1;
      for (int u = 0; u < domains.length; u++) {
        int count = Integer.bitCount(domains[u]);
        if (count == 0) return;
        if (count == 1) continue;
        int seen = 0;
        for (int i = 0; i < g.degree(u); i++) {
          int d = domains[g.neighbor(u, i)];
          if (Integer.bitCount(d) == 1) seen |= d;
        }
        int sat = Integer.bitCount(seen), degree = g.degree(u);
        if (count < bestCount
            || count == bestCount && (sat > bestSat || sat == bestSat && degree > bestDegree)) {
          v = u;
          bestCount = count;
          bestSat = sat;
          bestDegree = degree;
        }
      }
      if (v < 0) {
        encountered++;
        var colors = new ArrayList<Color>();
        for (int d : domains) colors.add(Color.fromMask(d));
        var c = new Coloring(colors);
        if (keys.add(eq.canonicalize(c))) solutions.add(c);
        return;
      }
      int candidates = domains[v];
      for (int bit = 1; bit <= 4; bit <<= 1)
        if ((candidates & bit) != 0) {
          int mark = top, before = solutions.size();
          if (assign(v, bit)) visit(level + 1);
          if (solutions.size() == before) backtracks++;
          rollback(mark);
          if (solutions.size() >= limit || exhausted) return;
        }
    }
  }
}
