package io.threecolor;

import static org.junit.jupiter.api.Assertions.*;

import io.threecolor.deduction.*;
import io.threecolor.model.*;
import io.threecolor.solve.*;
import java.util.*;
import net.jqwik.api.*;
import org.sat4j.core.VecInt;
import org.sat4j.minisat.SolverFactory;
import org.sat4j.specs.*;

class SolverProperties {
  static GraphTopology randomGraph(long seed, int n) {
    var r = new Random(seed);
    var edges = new ArrayList<Edge>();
    for (int a = 0; a < n; a++)
      for (int b = a + 1; b < n; b++) if (r.nextDouble() < 0.4) edges.add(new Edge(a, b));
    return new GraphTopology(n, edges);
  }

  static List<Coloring> brute(GraphTopology g, PartialColoring givens) {
    var result = new ArrayList<Coloring>();
    int combinations = (int) Math.pow(3, g.nodeCount());
    for (int x = 0; x < combinations; x++) {
      int k = x;
      var colors = new ArrayList<Color>();
      for (int v = 0; v < g.nodeCount(); v++) {
        colors.add(Color.values()[k % 3]);
        k /= 3;
      }
      var c = new Coloring(colors);
      if (c.satisfies(g, givens)) result.add(c);
    }
    return result;
  }

  static int satCount(GraphTopology g, PartialColoring givens) throws Exception {
    var sat = SolverFactory.newDefault();
    sat.newVar(3 * g.nodeCount());
    sat.setTimeout(10);
    try {
      for (int v = 0; v < g.nodeCount(); v++) {
        sat.addClause(new VecInt(new int[] {3 * v + 1, 3 * v + 2, 3 * v + 3}));
        for (int a = 1; a <= 3; a++)
          for (int b = a + 1; b <= 3; b++)
            sat.addClause(new VecInt(new int[] {-(3 * v + a), -(3 * v + b)}));
      }
      for (var e : g.edges())
        for (int c = 1; c <= 3; c++)
          sat.addClause(new VecInt(new int[] {-(3 * e.a().value() + c), -(3 * e.b().value() + c)}));
      for (var e : givens.colors().entrySet())
        sat.addClause(new VecInt(new int[] {3 * e.getKey().value() + e.getValue().ordinal() + 1}));
      int count = 0;
      while (count < 2 && sat.isSatisfiable()) {
        count++;
        int[] block = new int[g.nodeCount()];
        for (int v = 0; v < g.nodeCount(); v++)
          for (int c = 1; c <= 3; c++) if (sat.model(3 * v + c)) block[v] = -(3 * v + c);
        try {
          sat.addClause(new VecInt(block));
        } catch (ContradictionException ex) {
          break;
        }
      }
      return count;
    } catch (ContradictionException ex) {
      return 0;
    }
  }

  @Property(tries = 140, seed = "314159")
  void exactAndEveryDeductionAgreeWithBruteForce(@ForAll long seed) {
    var g = randomGraph(seed, 6);
    var givens = new PartialColoring(Map.of(new NodeId(0), Color.RED));
    var all = brute(g, givens);
    var exact = new ExactSolver().uniqueness(g, givens, 100000, SolutionEquivalence.LABELED);
    assertEquals(
        all.isEmpty()
            ? UniquenessResult.UNSATISFIABLE
            : all.size() == 1 ? UniquenessResult.UNIQUE : UniquenessResult.MULTIPLE,
        exact.status());
    exact.solutions().forEach(c -> assertTrue(c.satisfies(g, givens)));
    var trace = new DeductionEngine().solve(g, givens);
    for (var d : trace.steps()) {
      for (int ref : d.premises()) assertTrue(ref < d.id());
      for (var c : all) assertTrue(RuleProperties.holds(c, d.conclusion()), d.toString());
    }
    if (trace.status() == DeductionTrace.Status.SOLVED) {
      var c = new Coloring(trace.finalDomains().stream().map(Color::fromMask).toList());
      assertTrue(c.satisfies(g, givens));
    }
  }

  @Property(tries = 100, seed = "271828")
  void sat4jDifferential(@ForAll long seed) throws Exception {
    var g = randomGraph(seed, 12);
    var givens = new PartialColoring(Map.of(new NodeId(0), Color.RED, new NodeId(1), Color.GREEN));
    var result = new ExactSolver().uniqueness(g, givens, 100000, SolutionEquivalence.LABELED);
    int count = satCount(g, givens);
    assertEquals(
        count == 0
            ? UniquenessResult.UNSATISFIABLE
            : count == 1 ? UniquenessResult.UNIQUE : UniquenessResult.MULTIPLE,
        result.status());
  }

  @Property(tries = 80, seed = "12345")
  void permutationsPreserveSatisfiability(@ForAll long seed) {
    var g = randomGraph(seed, 7);
    var edges =
        g.edges().stream().map(e -> new Edge(6 - e.a().value(), 6 - e.b().value())).toList();
    var solver = new ExactSolver();
    assertEquals(
        solver.uniqueness(g, PartialColoring.empty(), 10000, SolutionEquivalence.LABELED).status(),
        solver
            .uniqueness(
                new GraphTopology(7, edges),
                PartialColoring.empty(),
                10000,
                SolutionEquivalence.LABELED)
            .status());
    for (var c : solver.solve(g, PartialColoring.empty(), 10000).solutions())
      assertTrue(
          new Coloring(c.colors().stream().map(x -> Color.values()[(x.ordinal() + 1) % 3]).toList())
              .satisfies(g, PartialColoring.empty()));
  }
}
