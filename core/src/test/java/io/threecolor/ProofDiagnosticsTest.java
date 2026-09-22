package io.threecolor;

import static org.junit.jupiter.api.Assertions.*;

import io.threecolor.deduction.*;
import io.threecolor.difficulty.DifficultyBand;
import io.threecolor.generation.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class ProofDiagnosticsTest {
  @Test
  void diagnosticsPreserveCertificationAndReplayActualRoundDependencies() {
    var entry =
        PuzzleBank.bundled().entries().stream()
            .filter(e -> e.band() == DifficultyBand.HARD)
            .findFirst()
            .orElseThrow();
    var puzzle = entry.puzzle();
    var levels = new ProofLevels();
    var diagnostic = levels.diagnose(puzzle.topology(), puzzle.givens(), 20000);
    assertEquals(entry.proof().p2(), diagnostic.result());
    assertEquals(2, diagnostic.rounds().size());
    var first = diagnostic.rounds().getFirst();
    var target = diagnostic.rounds().get(1).eliminations().getFirst();
    assertEquals(
        ProbeHardDependencies.Outcome.UNSUPPORTED,
        ProbeHardDependencies.test(first.before(), List.of(), target, 20000));
    assertEquals(
        ProbeHardDependencies.Outcome.REFUTED,
        ProbeHardDependencies.test(first.before(), first.eliminations(), target, 20000));
    var minimal =
        ProbeHardDependencies.minimize(first.before(), first.eliminations(), target, 20000);
    assertTrue(minimal.minimal());
    assertFalse(minimal.retained().isEmpty());
    for (var e : minimal.retained()) {
      var subset = new ArrayList<>(minimal.retained());
      subset.remove(e);
      assertEquals(
          ProbeHardDependencies.Outcome.UNSUPPORTED,
          ProbeHardDependencies.test(first.before(), subset, target, 20000));
    }
    assertEquals(
        ProofLevels.Status.UNKNOWN,
        levels.diagnose(puzzle.topology(), puzzle.givens(), 1).result().status());
    assertEquals(
        ProbeHardDependencies.Outcome.UNKNOWN,
        ProbeHardDependencies.test(first.before(), List.of(), target, 1));
  }
}
