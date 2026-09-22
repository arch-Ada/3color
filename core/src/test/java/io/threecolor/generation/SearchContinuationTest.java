package io.threecolor.generation;

import static org.junit.jupiter.api.Assertions.*;

import io.threecolor.difficulty.*;
import io.threecolor.model.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SearchContinuationTest {
  static CriticalCatalog.Entry source() {
    return new CriticalCatalog.Entry(
        "fixed-diamond",
        new PlaneTriangulation(
            new GraphTopology(
                4,
                List.of(
                    new Edge(0, 1),
                    new Edge(1, 2),
                    new Edge(2, 3),
                    new Edge(0, 3),
                    new Edge(0, 2))),
            new PuzzleLayout(
                List.of(
                    new PuzzleLayout.Point(.1, .1),
                    new PuzzleLayout.Point(.9, .1),
                    new PuzzleLayout.Point(.9, .9),
                    new PuzzleLayout.Point(.1, .9)))));
  }

  static final DeletionGenerator.Budget BUDGET =
      new DeletionGenerator.Budget(1, 18, 200, 50000, 12000, 3000);

  static CertifiedPuzzle.Check certify(Puzzle p, GenerationWork w) {
    return CertifiedPuzzle.verify(
        p, DifficultyBand.VERY_EASY, PlayDifficulty.Category.VERY_EASY, w);
  }

  @Test
  void rejectionContinuesWithDistinctCluesOnTheSameGraph() {
    var work = new GenerationWork();
    var offered = new ArrayList<Puzzle>();
    var result =
        new DeletionGenerator()
            .generateFrom(
                DifficultyBand.VERY_EASY,
                0,
                BUDGET,
                source(),
                p -> {
                  offered.add(p);
                  return offered.size() == 1
                      ? CertifiedPuzzle.Check.rejected("PLAY_FILTER_REJECTED")
                      : certify(p, work);
                },
                work);
    assertTrue(result.success(), result.toString());
    assertEquals(2, offered.size());
    assertEquals(offered.get(0).topology().edges(), offered.get(1).topology().edges());
    assertNotEquals(offered.get(0).givens(), offered.get(1).givens());
    assertEquals(1, result.stats().attempts());
    assertEquals(1, result.stats().graphs());
    assertEquals(1, work.get("finalExactChecks"));
    assertEquals(1, work.get("finalProofChecks"));
  }

  @Test
  void rejectionContinuesToLaterStatesWithoutResettingSourceOrBudget() {
    var work = new GenerationWork();
    var offered = new HashSet<String>();
    var result =
        new DeletionGenerator()
            .generateFrom(
                DifficultyBand.VERY_EASY,
                0,
                BUDGET,
                source(),
                p -> {
                  assertTrue(offered.add(p.logicalHash()), "Duplicate clue candidate evaluated");
                  return p.topology().edges().size() == 5
                      ? CertifiedPuzzle.Check.rejected("INCOMPLETE_TRACE")
                      : certify(p, work);
                },
                work);
    assertTrue(result.success(), result.toString());
    assertTrue(result.puzzle().topology().edges().size() < 5);
    assertEquals(1, result.stats().attempts());
    assertTrue(result.stats().graphs() > 1);
    assertTrue(result.stats().exactChecks() > result.puzzle().givens().colors().size());
  }

  @Test
  void seenGraphSkipsAllClueWorkButNotLaterDeletions() {
    var work = new GenerationWork();
    String seen = TopologyFingerprint.of(source().candidate().graph());
    var result =
        new DeletionGenerator()
            .generateFrom(
                DifficultyBand.VERY_EASY,
                0,
                BUDGET,
                source(),
                new DeletionGenerator.Acceptance() {
                  public boolean skipGraph(GraphTopology g) {
                    return seen.equals(TopologyFingerprint.of(g));
                  }

                  public CertifiedPuzzle.Check evaluate(Puzzle p) {
                    assertNotEquals(seen, TopologyFingerprint.of(p.topology()));
                    return certify(p, work);
                  }
                },
                work);
    assertEquals(1, result.stats().outcomes().get("SKIPPED_TOPOLOGY"));
    assertTrue(result.stats().graphs() > 1);
    assertEquals(result.stats().graphs() - 1, work.get("colourableStates"));
    assertEquals(2 * work.get("colourableStates"), work.get("clueSearches"));
    assertEquals(1, work.get("colourabilityChecks"));
  }

  @Test
  void rejectionExhaustionAndInterruptionAreNotImpossibilityClaims() {
    var calls = new AtomicInteger();
    var limited = new DeletionGenerator.Budget(1, 1, 200, 50000, 12000, 3000);
    var result =
        new DeletionGenerator()
            .generateFrom(
                DifficultyBand.VERY_EASY,
                0,
                limited,
                source(),
                p -> {
                  calls.incrementAndGet();
                  return CertifiedPuzzle.Check.rejected("PLAY_FILTER_REJECTED");
                },
                new GenerationWork());
    assertFalse(result.success());
    assertEquals("SEARCH_BUDGET_EXHAUSTED", result.failure());
    assertEquals(2, calls.get());
    var zero = new GenerationWork(0);
    result =
        new DeletionGenerator()
            .generateFrom(
                DifficultyBand.VERY_EASY, 0, BUDGET, source(), p -> certify(p, zero), zero);
    assertEquals("FINAL_BUDGET_EXHAUSTED", result.failure());
    assertEquals(0, zero.get("finalExactChecks"));
    Thread.currentThread().interrupt();
    try {
      result =
          new DeletionGenerator()
              .generateFrom(
                  DifficultyBand.VERY_EASY,
                  0,
                  BUDGET,
                  source(),
                  p -> {
                    fail("Interrupted callback");
                    return null;
                  },
                  new GenerationWork());
      assertEquals("INTERRUPTED", result.failure());
      assertEquals(0, result.stats().graphs());
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void unexpectedAcceptanceExceptionsPropagate() {
    assertThrows(
        IllegalStateException.class,
        () ->
            new DeletionGenerator()
                .generateFrom(
                    DifficultyBand.VERY_EASY,
                    0,
                    BUDGET,
                    source(),
                    p -> {
                      throw new IllegalStateException("bug");
                    },
                    new GenerationWork()));
  }
}
