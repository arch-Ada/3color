package io.threecolor;

import static org.junit.jupiter.api.Assertions.*;

import io.threecolor.difficulty.*;
import io.threecolor.generation.*;
import io.threecolor.model.*;
import io.threecolor.solve.*;
import io.threecolor.validation.*;
import net.jqwik.api.*;
import org.junit.jupiter.api.Test;

class GeneratorTest {
  @Property(tries = 20, seed = "424242")
  void generatedPuzzlesAreReproducibleAndCertified(@ForAll long seed) {
    var spec = GenerationSpec.defaults(seed, 12, DifficultyBand.VERY_EASY);
    var generator = new SparseGridGenerator();
    var outcome = generator.generate(spec);
    assertInstanceOf(GeneratedPuzzle.class, outcome, outcome.toString());
    var a = (GeneratedPuzzle) outcome;
    var b = (GeneratedPuzzle) generator.generate(spec);
    assertEquals(a.logicalHash(), b.logicalHash());
    assertEquals(a.puzzle().provenance(), b.puzzle().provenance());
    assertEquals(a.trace(), b.trace());
    assertTrue(a.hiddenSolution().satisfies(a.puzzle().topology(), a.puzzle().givens()));
    assertTrue(new PuzzleValidator().validate(a.puzzle(), true, true, true, 100000).valid());
    assertTrue(a.difficulty().humanSolved());
    assertEquals(spec.targetDifficulty(), a.difficulty().band());
    assertTrue(a.puzzle().givens().colors().values().stream().distinct().count() >= 2);
    assertTrue(a.metrics().m() < 3 * a.metrics().n() - 6);
  }

  @Test
  void impossibleStructuralTargetFailsExplicitly() {
    var spec = new GenerationSpec(1, 12, DifficultyBand.EASY, 5, 4, 4, 0, true, true, 2);
    assertInstanceOf(GenerationFailure.class, new SparseGridGenerator().generate(spec));
  }

  @Test
  void removedDifficultyGeneratorsFailBeforeSearch() {
    for (var band :
        java.util.List.of(DifficultyBand.MEDIUM, DifficultyBand.HARD, DifficultyBand.EXPERT)) {
      var spec = GenerationSpec.defaults(0, 24, band);
      for (var strategy : GenerationStrategy.values())
        assertThrows(
            IllegalArgumentException.class, () -> new SparseGridGenerator().generate(spec, strategy));
    }
  }
}
