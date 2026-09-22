package io.threecolor.benchmarks;

import io.threecolor.deduction.*;
import io.threecolor.difficulty.*;
import io.threecolor.generation.*;
import io.threecolor.model.*;
import io.threecolor.solve.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
/** Solver microbenchmarks and the legacy Easy generator; current range search is separate. */
public class PuzzleBenchmarks {
  private Puzzle puzzle;
  private GraphTopology impossible;
  private GenerationSpec spec;
  private DeductionState frontierState;
  private DeductionState implicationState;
  private DeductionTrace trace;

  @Setup
  public void setup() {
    spec = GenerationSpec.defaults(42, 24, DifficultyBand.EASY);
    var result = new SparseGridGenerator().generate(spec);
    if (!(result instanceof GeneratedPuzzle g)) throw new IllegalStateException(result.toString());
    puzzle = g.puzzle();
    trace = new DeductionEngine().solve(puzzle.topology(), puzzle.givens());
    var domains = new ArrayList<Integer>();
    for (int v = 0; v < puzzle.topology().nodeCount(); v++)
      domains.add(puzzle.givens().colors().getOrDefault(new NodeId(v), Color.RED).mask());
    for (int v = 0; v < domains.size(); v++)
      if (!puzzle.givens().colors().containsKey(new NodeId(v))) domains.set(v, 7);
    frontierState = new DeductionState(puzzle.topology(), domains);
    implicationState =
        new DeductionState(
            new GraphTopology(
                4, List.of(new Edge(0, 1), new Edge(1, 2), new Edge(2, 3), new Edge(0, 3))),
            List.of(3, 5, 6, 3));
    impossible =
        new GraphTopology(
            4,
            List.of(
                new Edge(0, 1),
                new Edge(0, 2),
                new Edge(0, 3),
                new Edge(1, 2),
                new Edge(1, 3),
                new Edge(2, 3)));
  }

  @Benchmark
  public DeductionFrontier frontierCalculation() {
    return new DeductionEngine().frontier(frontierState, 3);
  }

  @Benchmark
  public List<RuleApplication> neighborhoodParity() {
    return new NeighborhoodParityRule().find(frontierState);
  }

  @Benchmark
  public List<RuleApplication> implicationReasoning() {
    return new ImplicationChainRule().find(implicationState);
  }

  @Benchmark
  public DifficultyReport profileAnalysis() {
    return new DifficultyAnalyzer().analyze(trace);
  }

  @Benchmark
  public ClueEvaluator.Evaluation candidateEvaluation() {
    return new ClueEvaluator().evaluate(puzzle.topology(), puzzle.givens(), 20000);
  }

  @Benchmark
  public SolveResult firstSolution() {
    return new ExactSolver().solve(puzzle.topology(), puzzle.givens(), 100000);
  }

  @Benchmark
  public SolveResult uniqueness() {
    return new ExactSolver()
        .uniqueness(puzzle.topology(), puzzle.givens(), 100000, SolutionEquivalence.LABELED);
  }

  @Benchmark
  public SolveResult unsatisfiable() {
    return new ExactSolver()
        .uniqueness(impossible, PartialColoring.empty(), 100000, SolutionEquivalence.LABELED);
  }

  @Benchmark
  public DifficultyReport deductionAnalysis() {
    return new DifficultyAnalyzer()
        .analyze(new DeductionEngine().solve(puzzle.topology(), puzzle.givens()));
  }

  @Benchmark
  public GenerationOutcome legacyEasyGenerationAttempt() {
    return new SparseGridGenerator()
        .generate(new GenerationSpec(42, 24, DifficultyBand.EASY, 2.8, 1, 6, .65, true, true, 1));
  }

  @Benchmark
  public GenerationOutcome legacyEasyCompleteGeneration() {
    return new SparseGridGenerator().generate(spec);
  }
}
