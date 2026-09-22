package io.threecolor.deduction;

import static org.junit.jupiter.api.Assertions.*;

import io.threecolor.model.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class ClueProofCacheTest {
  private Puzzle puzzle(int variant) {
    var edges = new ArrayList<>(List.of(new Edge(0, 1), new Edge(0, 2), new Edge(1, 2)));
    if (variant == 1) edges.add(new Edge(2, 3));
    return new Puzzle(
        new GraphTopology(4, edges),
        new PartialColoring(Map.of(new NodeId(0), variant == 2 ? Color.GREEN : Color.RED)),
        RuleSet.CLASSIC_V1,
        PuzzleProvenance.supplied(),
        new PuzzleLayout(List.of()));
  }

  private DeductionTrace trace(DeductionTrace.Status status, int facts) {
    var steps = new ArrayList<Deduction>();
    for (int i = 0; i < facts; i++)
      steps.add(
          new Deduction(
              i, "given", 0, 0, 7, 1, List.of(), List.of(), "given", Map.of(), List.of()));
    return new DeductionTrace(steps, List.of(1, 2, 4, 1), status, List.of(), 0);
  }

  @Test
  void bindsTopologyCluesFamilyAndConfigurationButNotLayoutOrProvenance() {
    var cache = new ClueProofCache(8, 20);
    var calls = new AtomicInteger();
    Supplier<DeductionTrace> loader =
        () -> {
          calls.incrementAndGet();
          return trace(DeductionTrace.Status.SOLVED, 1);
        };
    var config = DeductionConfiguration.defaults();
    var p = puzzle(0);
    var first = cache.get(p, "player", config, loader);
    var relayout =
        new Puzzle(
            p.topology(),
            p.givens(),
            p.rules(),
            new PuzzleProvenance("changed", 42, 1, "different"),
            new PuzzleLayout(List.of(new PuzzleLayout.Point(.5, .5))));
    assertSame(first, cache.get(relayout, "player", config, loader));
    assertEquals(1, calls.get());
    cache.get(puzzle(1), "player", config, loader);
    cache.get(puzzle(2), "player", config, loader);
    cache.get(p, "legacy", config, loader);
    cache.get(p, "player", config.withLevel(3, 0), loader);
    cache.get(
        new Puzzle(
            p.topology(),
            p.givens(),
            p.rules(),
            p.provenance(),
            p.layout(),
            ExplanationModel.BASIC_V1),
        "player",
        config,
        loader);
    assertEquals(6, calls.get());
  }

  @Test
  void evictsLeastRecentlyUsedEntriesAndHonoursTotalFactLimit() {
    var cache = new ClueProofCache(2, 2);
    var config = DeductionConfiguration.defaults();
    var calls = new AtomicInteger();
    Supplier<DeductionTrace> load =
        () -> {
          calls.incrementAndGet();
          return trace(DeductionTrace.Status.SOLVED, 1);
        };
    cache.get(puzzle(0), "p", config, load);
    cache.get(puzzle(1), "p", config, load);
    cache.get(puzzle(0), "p", config, load);
    cache.get(puzzle(2), "p", config, load);
    cache.get(puzzle(1), "p", config, load);
    assertEquals(4, calls.get());
    var weighted = new ClueProofCache(8, 2);
    Supplier<DeductionTrace> two =
        () -> {
          calls.incrementAndGet();
          return trace(DeductionTrace.Status.SOLVED, 2);
        };
    weighted.get(puzzle(0), "p", config, two);
    weighted.get(puzzle(1), "p", config, two);
    weighted.get(puzzle(0), "p", config, two);
    assertEquals(7, calls.get());
  }

  @Test
  void doesNotCacheIncompleteOversizedOrInterruptedResults() {
    var cache = new ClueProofCache(2, 2);
    var calls = new AtomicInteger();
    var config = DeductionConfiguration.defaults();
    for (var status :
        List.of(
            DeductionTrace.Status.STALLED,
            DeductionTrace.Status.BUDGET_EXHAUSTED,
            DeductionTrace.Status.CONTRADICTION))
      for (int i = 0; i < 2; i++)
        cache.get(
            puzzle(0),
            "p",
            config,
            () -> {
              calls.incrementAndGet();
              return trace(status, 1);
            });
    for (int i = 0; i < 2; i++)
      cache.get(
          puzzle(0),
          "p",
          config,
          () -> {
            calls.incrementAndGet();
            return trace(DeductionTrace.Status.SOLVED, 3);
          });
    try {
      Thread.currentThread().interrupt();
      cache.get(
          puzzle(0),
          "p",
          config,
          () -> {
            calls.incrementAndGet();
            return trace(DeductionTrace.Status.SOLVED, 1);
          });
    } finally {
      Thread.interrupted();
    }
    cache.get(
        puzzle(0),
        "p",
        config,
        () -> {
          calls.incrementAndGet();
          return trace(DeductionTrace.Status.SOLVED, 1);
        });
    assertEquals(10, calls.get());
  }
}
