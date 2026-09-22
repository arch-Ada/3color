package io.threecolor;

import static org.junit.jupiter.api.Assertions.*;

import io.threecolor.difficulty.PlayDifficulty;
import io.threecolor.generation.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class RangeGeneratorTest {
  @Test
  void freshExactCountReplayExclusionAndRequestLimits() {
    var bank =
        PuzzleBank.read(
            new java.io.ByteArrayInputStream(
                (PuzzleBank.HEADER + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    var generator = new RangeGenerator();
    var category = PlayDifficulty.Category.VERY_EASY;
    var first = generator.search(category, 16, 16, 0, bank, Set.of());
    assertTrue(first.success(), first.failure());
    assertEquals(16, first.puzzle().topology().nodeCount());
    assertNotNull(first.certificate());
    var replay = generator.search(category, 16, 16, 0, bank, Set.of());
    assertEquals(first.puzzle().logicalHash(), replay.puzzle().logicalHash());
    assertEquals(first.stats(), replay.stats());
    var key = TopologyFingerprint.of(first.puzzle().topology());
    var continued = generator.search(category, 16, 16, 0, bank, Set.of(key));
    if (continued.success()) {
      assertEquals(16, continued.puzzle().topology().nodeCount());
      assertNotEquals(key, TopologyFingerprint.of(continued.puzzle().topology()));
    }
    assertTrue(continued.stats().attempts() <= 5);
    assertTrue(continued.stats().graphs() <= 90);
    assertTrue(continued.stats().work().getOrDefault("finalCandidates", 0L) <= 10);
    Thread.currentThread().interrupt();
    try {
      var interrupted = generator.search(category, 16, 16, 0, bank, Set.of());
      assertEquals("INTERRUPTED", interrupted.failure());
      assertEquals(0, interrupted.stats().attempts());
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void freshLargestRangePuzzleIsCertifiedAndSeedReplayIsDeterministic() {
    var generator = new RangeGenerator();
    var bank = PuzzleBank.bundled();
    var category = PlayDifficulty.Category.CHALLENGING;
    var a = generator.search(category, 44, 53, 3006, bank, Set.of());
    var b = generator.search(category, 44, 53, 3006, bank, Set.of());
    assertTrue(a.success(), a.failure());
    assertEquals(a.puzzle().logicalHash(), b.puzzle().logicalHash());
    assertEquals(a.puzzle().layout(), b.puzzle().layout());
    assertTrue(PuzzleSize.VERY_LARGE.contains(a.puzzle().topology().nodeCount()));
    var certified = PuzzleBank.certify(category.sourceBand(), a.puzzle());
    assertEquals(category, certified.playRating().category());
    assertEquals(1, a.proof().p2().refutationRounds());
    assertTrue(a.stats().graphs() <= 90);
  }
}
