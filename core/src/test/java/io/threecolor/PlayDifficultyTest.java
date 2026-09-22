package io.threecolor;

import static org.junit.jupiter.api.Assertions.*;

import io.threecolor.deduction.*;
import io.threecolor.difficulty.*;
import io.threecolor.generation.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class PlayDifficultyTest {
  @Test
  void mediumSupplySplitsAndRejectsOversizedExplanations() {
    var counts = new EnumMap<PlayDifficulty.Category, Integer>(PlayDifficulty.Category.class);
    int rejected = 0;
    for (var entry : PuzzleBank.bundled().entries()) {
      if (entry.band() != DifficultyBand.MEDIUM) continue;
      var rating = entry.playRating();
      if (!rating.accepted()) {
        assertNotNull(rating.rejection());
        rejected++;
        continue;
      }
      counts.merge(rating.category(), 1, Integer::sum);
      assertTrue(rating.contradictionCount() >= 1 && rating.contradictionCount() <= 6);
      assertTrue(rating.maximumContradictionSteps() <= 12);
      assertTrue(rating.maximumContradictionVertices() <= 14);
      if (rating.category() == PlayDifficulty.Category.MEDIUM) {
        assertTrue(rating.contradictionCount() <= 2);
        assertTrue(rating.maximumContradictionSteps() <= 6);
        assertTrue(rating.maximumContradictionVertices() <= 8);
      }
    }
    assertTrue(counts.get(PlayDifficulty.Category.MEDIUM) > 20);
    assertTrue(counts.get(PlayDifficulty.Category.CHALLENGING) > 20);
    assertTrue(rejected > 0, "An overlong proof is rejected, never promoted to Challenging");
  }

  @Test
  void incompleteAndOldHardProofsAreNotPlayerCategories() {
    var entry =
        PuzzleBank.bundled().entries().stream()
            .filter(e -> e.band() == DifficultyBand.HARD)
            .findFirst()
            .orElseThrow();
    assertFalse(entry.playRating().accepted());
    var trace =
        ProofLevels.explanationEngine()
            .solve(entry.puzzle().topology(), entry.puzzle().givens(), 1, 0);
    assertEquals("INCOMPLETE_TRACE", PlayDifficulty.rate(DifficultyBand.MEDIUM, trace).rejection());
  }

  @Test
  void rangeSelectionFiltersBeforeChoosingAndRespectsExclusions() {
    var bank = PuzzleBank.bundled();
    for (var category : PlayDifficulty.Category.values()) {
      var selected = bank.select(category, 24, 33, 42, Set.of()).orElseThrow();
      assertEquals(category.sourceBand(), selected.proof().band());
      assertTrue(PuzzleSize.MEDIUM.contains(selected.puzzle().topology().nodeCount()));
      String key = TopologyFingerprint.of(selected.puzzle().topology());
      var next = bank.select(category, 24, 33, 42, Set.of(key)).orElseThrow();
      assertNotEquals(key, TopologyFingerprint.of(next.puzzle().topology()));
      assertEquals(
          selected.puzzle().logicalHash(),
          bank.select(category, 24, 33, 42, Set.of()).orElseThrow().puzzle().logicalHash());
    }
  }
}
