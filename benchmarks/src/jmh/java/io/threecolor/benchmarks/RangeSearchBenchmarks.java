package io.threecolor.benchmarks;

import io.threecolor.difficulty.PlayDifficulty;
import io.threecolor.generation.*;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;

/**
 * Current bounded fresh search. Bank loading is setup; bank selection is bypassed. A failed attempt
 * is returned and measured too. This one fixture is not a yield study.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 1)
@Measurement(iterations = 3)
@Fork(1)
public class RangeSearchBenchmarks {
  private PuzzleBank bank;

  @Setup(Level.Trial)
  public void setup() {
    bank = PuzzleBank.bundled();
  }

  @Benchmark
  public DeletionGenerator.Outcome currentSmallEasyFreshSearch() {
    return new RangeGenerator().search(PlayDifficulty.Category.EASY, 14, 23, 4000, bank, Set.of());
  }
}
