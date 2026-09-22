package io.threecolor;

import static org.junit.jupiter.api.Assertions.*;

import io.threecolor.deduction.ProofLevels;
import io.threecolor.difficulty.DifficultyBand;
import io.threecolor.generation.*;
import io.threecolor.model.*;
import io.threecolor.solve.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class HardMutationTest {
  @Test
  void mutationsPreserveTheTargetAndReplayDeterministically() {
    var entry =
        PuzzleBank.bundled().entries().stream()
            .filter(e -> e.band() == DifficultyBand.MEDIUM)
            .findFirst()
            .orElseThrow();
    var p = entry.puzzle();
    var exact =
        new ExactSolver().uniqueness(p.topology(), p.givens(), 200000, SolutionEquivalence.LABELED);
    var state =
        new ProbeHardMutation.State(
            p, exact.solutions().getFirst(), entry.proof(), ProbeHardMutation.score(entry.proof()));
    var a = new Random(42);
    var b = new Random(42);
    var operations = new HashSet<String>();
    for (int i = 0; i < 200; i++) {
      var first = ProbeHardMutation.mutate(state, a, 42, i);
      var second = ProbeHardMutation.mutate(state, b, 42, i);
      assertNotNull(first);
      assertNotNull(second);
      operations.add(first.operation());
      assertEquals(first.puzzle().logicalHash(), second.puzzle().logicalHash());
      assertEquals(p.layout(), first.puzzle().layout());
      assertTrue(state.target().satisfies(first.puzzle().topology(), first.puzzle().givens()));
    }
    assertEquals(
        Set.of(
            "DELETE_EDGE", "ADD_EDGE", "EXCHANGE_EDGE", "DELETE_CLUE", "ADD_CLUE", "EXCHANGE_CLUE"),
        operations);
    assertEquals(entry.puzzle(), p);
  }

  @Test
  void hardBankCertificatesSurviveVertexAndPaletteRenaming() {
    var hard =
        PuzzleBank.bundled().entries().stream()
            .filter(e -> e.band() == DifficultyBand.HARD)
            .toList();
    assertEquals(5, hard.size());
    for (var entry : hard) {
      var original = entry.puzzle();
      assertEquals(33, original.topology().nodeCount());
      assertEquals(3, original.givens().colors().size());
      assertEquals(2, entry.proof().p2().refutationRounds());
      for (int seed : new int[] {0, 1, 2}) {
        int n = original.topology().nodeCount();
        var permutation = new ArrayList<Integer>();
        for (int v = 0; v < n; v++) permutation.add(v);
        Collections.shuffle(permutation, new Random(seed));
        var edges =
            original.topology().edges().stream()
                .map(e -> new Edge(permutation.get(e.a().value()), permutation.get(e.b().value())))
                .toList();
        var clues = new TreeMap<NodeId, Color>();
        original
            .givens()
            .colors()
            .forEach(
                (v, c) ->
                    clues.put(
                        new NodeId(permutation.get(v.value())),
                        Color.values()[(c.ordinal() + seed) % 3]));
        var proof =
            new ProofLevels()
                .classify(new GraphTopology(n, edges), new PartialColoring(clues), 20000);
        assertEquals(DifficultyBand.HARD, proof.band());
        assertEquals(2, proof.p2().refutationRounds());
      }
    }
  }
}
