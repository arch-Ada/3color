package io.threecolor;

import static org.junit.jupiter.api.Assertions.*;

import io.threecolor.difficulty.DifficultyBand;
import io.threecolor.generation.*;
import io.threecolor.model.*;
import io.threecolor.solve.*;
import io.threecolor.validation.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;

class PuzzleBankTest {
  @Test
  void publishedConstructionCatalogueIsPlanarAndNotThreeColourable() {
    assertFalse(CriticalCatalog.entries().isEmpty());
    for (var entry : CriticalCatalog.entries()) {
      var source = entry.candidate();
      var puzzle =
          new Puzzle(
              source.graph(),
              PartialColoring.empty(),
              RuleSet.CLASSIC_V1,
              PuzzleProvenance.supplied(),
              source.layout());
      assertTrue(new PuzzleValidator().validate(puzzle, true, false, false, 1).valid(), entry.id());
      assertEquals(
          UniquenessResult.UNSATISFIABLE,
          new ExactSolver().solve(source.graph(), PartialColoring.empty(), 200000).status(),
          entry.id());
      // Verify edge criticality independently for the smaller catalogue members.
      if (source.graph().nodeCount() <= 10)
        for (var edge : source.graph().edges()) {
          var edges = new ArrayList<>(source.graph().edges());
          edges.remove(edge);
          var result =
              new ExactSolver()
                  .solve(
                      new GraphTopology(source.graph().nodeCount(), edges),
                      PartialColoring.empty(),
                      200000);
          assertFalse(result.solutions().isEmpty(), entry.id() + " " + edge);
        }
    }
  }

  @Test
  void bankRejectsStaleOrMislabeledCertificatesAndSelectsOnlyExactSizeAndBand() {
    var result = new DeletionGenerator().generate(DifficultyBand.EASY, 16, 0);
    assertTrue(result.success());
    var entry = PuzzleBank.certify(DifficultyBand.EASY, result.puzzle());
    String serialized = PuzzleBank.HEADER + "\n" + PuzzleBank.encode(entry) + "\n";
    var bank = read(serialized);
    var selected = bank.select(DifficultyBand.EASY, 16, 123).orElseThrow();
    assertEquals(result.puzzle().logicalHash(), selected.puzzle().logicalHash());
    assertEquals(123, selected.puzzle().provenance().masterSeed());
    assertEquals(
        selected.puzzle(), bank.select(DifficultyBand.EASY, 16, 123).orElseThrow().puzzle());
    assertTrue(bank.select(DifficultyBand.HARD, 16, 123).isEmpty());
    assertTrue(bank.select(DifficultyBand.EASY, 17, 123).isEmpty());
    assertThrows(
        IllegalArgumentException.class, () -> read(serialized.replace(PuzzleBank.HEADER, "# old")));
    assertThrows(
        IllegalArgumentException.class, () -> read(serialized.replace("\nEASY|", "\nHARD|")));
    assertThrows(
        IllegalArgumentException.class,
        () -> read(serialized.replace(PuzzleRecords.clues(entry.puzzle().givens()), "")));
  }

  @Test
  void bundledEntriesAreCertifiedAndCoverTheThreeAvailableBands() {
    var bank = PuzzleBank.bundled();
    assertTrue(
        bank.entries().stream()
            .anyMatch(
                e -> e.puzzle().provenance().generationSpec().contains("source=mixed-faces-v1")));
    for (int n : new int[] {16, 24, 32, 40}) {
      for (var band :
          List.of(DifficultyBand.VERY_EASY, DifficultyBand.EASY, DifficultyBand.MEDIUM)) {
        var result = bank.select(band, n, 0).orElseThrow();
        assertEquals(band, result.proof().band());
        assertEquals(n, result.puzzle().topology().nodeCount());
        if (band == DifficultyBand.MEDIUM) {
          assertEquals(
              io.threecolor.deduction.ProofLevels.Status.STALLED, result.proof().p1().status());
          assertEquals(1, result.proof().p2().refutationRounds());
        }
      }
    }
  }

  private static PuzzleBank read(String text) {
    return PuzzleBank.read(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
  }
}
