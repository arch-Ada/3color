package io.threecolor;

import static org.junit.jupiter.api.Assertions.*;

import io.threecolor.difficulty.*;
import io.threecolor.generation.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class GenerationProfileTest {
  @Test
  void localSearchProposesRestorationsAndSwapsAndRetainsCertifiedPacing() {
    var spec = GenerationSpec.defaults(42, 24, DifficultyBand.EASY);
    var generator = new SparseGridGenerator();
    var a = assertInstanceOf(GeneratedPuzzle.class, generator.generate(spec));
    var b = assertInstanceOf(GeneratedPuzzle.class, generator.generate(spec));
    // Captured before extraction: legacy seed, graph, clue mutations and exact work stay identical.
    assertEquals(
        "bfd8b5ad9d74be9c4e675d7e32902a919b5ab698b5dfc79a96fbc456bb97e08c", a.logicalHash());
    assertEquals(71, a.quality().candidateEvaluations());
    assertEquals(40, a.quality().removals());
    assertEquals(16, a.quality().restorations());
    assertEquals(16, a.quality().swaps());
    assertEquals(209, a.quality().exactSearchNodes());
    assertEquals(GenerationStrategy.RANDOM_BEAM, a.quality().context().strategy());
    assertTrue(a.quality().restorations() > 0);
    assertTrue(a.quality().swaps() > 0);
    assertTrue(a.quality().targetMatch().accepted());
    assertEquals(a.quality(), b.quality());
    assertEquals(a.logicalHash(), b.logicalHash());
    assertEquals(a.trace(), b.trace());
    assertEquals(a.difficulty(), b.difficulty());
    var evaluation =
        new ClueEvaluator().evaluate(a.puzzle().topology(), a.puzzle().givens(), 20000);
    assertTrue(evaluation.certified());
    assertTrue(evaluation.difficulty().humanSolved());
    assertEquals(a.difficulty(), evaluation.difficulty());
  }

  @Test
  void candidateAndExactWorkBudgetsAreExplicitAndDeterministic() {
    var spec = GenerationSpec.defaults(0, 24, DifficultyBand.EASY);
    var tiny = new GenerationBudget(1, 1, 1, 0, 1);
    var first = new SparseGridGenerator().generate(spec, tiny);
    assertInstanceOf(GenerationFailure.class, first);
    assertEquals(first, new SparseGridGenerator().generate(spec, tiny));
    assertEquals("SEARCH_BUDGET_EXHAUSTED", ((GenerationFailure) first).code());
  }

  @Test
  void configuredTargetRejectsUnattainableShapeInsteadOfRelabeling() {
    var target =
        new DifficultyTarget(
            DifficultyBand.VERY_EASY,
            Map.of(ProfileMetric.SUBSTANTIAL_EVENTS, DifficultyTarget.Range.atLeast(500)));
    var result =
        new SparseGridGenerator()
            .generate(
                GenerationSpec.defaults(42, 12, DifficultyBand.VERY_EASY),
                new GenerationBudget(20000, 2000000, 50, 6, 2),
                target);
    assertInstanceOf(GenerationFailure.class, result);
  }
}
