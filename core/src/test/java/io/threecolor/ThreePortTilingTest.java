package io.threecolor;

import static org.junit.jupiter.api.Assertions.*;

import io.threecolor.deduction.ProofLevels;
import io.threecolor.generation.*;
import io.threecolor.model.*;
import io.threecolor.solve.*;
import io.threecolor.validation.PuzzleValidator;
import java.util.*;
import org.junit.jupiter.api.Test;

class ThreePortTilingTest {
  @Test
  void boundaryTableMatchesIndependentFullColourEnumeration() {
    var tile = ThreePortTile.create(8);
    int[] counts = new int[6561];
    for (int code = 0; code < 177147; code++) {
      int rest = code;
      int[] colors = new int[11];
      for (int v = 0; v < 11; v++) {
        colors[v] = rest % 3;
        rest /= 3;
      }
      if (tile.graph().edges().stream()
          .allMatch(e -> colors[e.a().value()] != colors[e.b().value()])) counts[code % 6561]++;
    }
    var actual = tile.counts();
    for (int code = 0; code < 6561; code++)
      assertEquals(Math.min(2, counts[code]), actual.charAt(code) - '0');
    assertEquals(tile.graph().edges(), ThreePortTile.create(8).graph().edges());
    assertEquals(tile.rows(), ThreePortTile.create(8).rows());
  }

  @Test
  void interfacesHaveThreeVerticesAndAllRotationsPreserveGeometry() {
    var tile = ThreePortTile.create(8);
    for (int[] shape : List.of(new int[] {2, 2}, new int[] {2, 3}, new int[] {3, 3})) {
      var placements = new ArrayList<ThreePortTile.Placement>();
      for (int i = 0; i < shape[0] * shape[1]; i++)
        placements.add(new ThreePortTile.Placement(tile, i % 4));
      var assembly = ThreePortTile.assemble(placements, shape[0], shape[1]);
      assertEquals(
          shape[0] * shape[1] == 4 ? 33 : shape[0] * shape[1] == 6 ? 47 : 67,
          assembly.graph().nodeCount());
      var overlap = new HashSet<>(assembly.regions().get(0));
      overlap.retainAll(assembly.regions().get(1));
      assertEquals(3, overlap.size());
      var puzzle =
          new Puzzle(
              assembly.graph(),
              PartialColoring.empty(),
              RuleSet.CLASSIC_V1,
              PuzzleProvenance.supplied(),
              assembly.layout());
      assertTrue(new PuzzleValidator().validate(puzzle, true, false, false, 1).valid());
    }
  }

  @Test
  void joinedTableCountsAgreeWithWholeGraphExactSearch() {
    var eligible = new ArrayList<ThreePortTile>();
    for (int seed = 0; eligible.size() < 3 && seed < 30; seed++) {
      var tile = ThreePortTile.create(seed);
      if (tile.eligible()) eligible.add(tile);
    }
    assertEquals(3, eligible.size());
    for (int rotation = 0; rotation < 4; rotation++) {
      var assembly =
          ThreePortTile.assemble(
              List.of(
                  new ThreePortTile.Placement(eligible.get(0), rotation),
                  new ThreePortTile.Placement(eligible.get(1), 0)),
              2,
              1);
      var solver = new TileConstraintSolver(assembly, false);
      for (int code = 0; code < 9; code++) {
        var clues = Map.of(0, code % 3, assembly.junctionCount() - 1, code / 3);
        var table = solver.count(clues, 20000);
        assertFalse(table.exhausted());
        var givens = new TreeMap<NodeId, Color>();
        clues.forEach((v, c) -> givens.put(new NodeId(v), Color.values()[c]));
        var exact =
            new ExactSolver()
                .uniqueness(
                    assembly.graph(),
                    new PartialColoring(givens),
                    200000,
                    SolutionEquivalence.LABELED);
        assertEquals(
            switch (exact.status()) {
              case UNIQUE -> 1;
              case MULTIPLE -> 2;
              case UNSATISFIABLE -> 0;
              case UNKNOWN -> -1;
            },
            table.count());
      }
    }
  }

  @Test
  void ambiguityAndWitnessSearchCannotCertifyUniqueness() {
    var original = ThreePortTile.create(8);
    int code = 0;
    for (int v = 0, power = 1; v < 8; v++, power *= 3) code += (v % 2) * power;
    var tile =
        new ThreePortTile(
            0,
            original.graph(),
            original.layout(),
            List.of(new ThreePortTile.Row(code, 2, ProofLevels.Status.STALLED, 3L)));
    var assembly = ThreePortTile.assemble(List.of(new ThreePortTile.Placement(tile, 0)), 1, 1);
    var solver = new TileConstraintSolver(assembly, false);
    assertEquals(2, solver.count(Map.of(), 100).count());
    assertFalse(solver.witness(100, 0).unique());
    assertTrue(solver.count(Map.of(8, 0), 100).unique());
    assertEquals(0, solver.count(Map.of(8, 2), 100).count());
    assertEquals(0, new TileConstraintSolver(assembly, true).count(Map.of(), 100).count());
    var empty =
        new TileConstraintSolver(
                ThreePortTile.assemble(List.of(new ThreePortTile.Placement(original, 0)), 1, 1),
                false)
            .count(Map.of(), 1);
    assertFalse(empty.unique());
  }

  @Test
  void sharedBoundaryAloneDoesNotFalselyCountAsCrossTileSupport() {
    var regions = List.of(Set.of(0, 1, 2, 3), Set.of(1, 2, 3, 4));
    assertFalse(TileProofSupport.spans(Set.of(1, 2, 3), regions));
    assertTrue(TileProofSupport.spans(Set.of(0, 4), regions));
  }

  @Test
  void wholeTileReplacementPreservesItsTargetAndTheTableCount() {
    var library = new ArrayList<ThreePortTile>();
    for (int seed = 0; library.size() < 5; seed++) {
      var tile = ThreePortTile.create(seed);
      if (tile.eligible()) library.add(tile);
    }
    var assembly =
        ThreePortTile.assemble(List.of(new ThreePortTile.Placement(library.getFirst(), 0)), 1, 1);
    var witness = new TileConstraintSolver(assembly, true).witness(1000, 0);
    assertFalse(witness.witness().isEmpty());
    var givens = new TreeMap<NodeId, Color>();
    for (int v = 0; v < assembly.junctionCount(); v++)
      givens.put(new NodeId(v), Color.values()[witness.witness().get(v)]);
    var clues = new PartialColoring(givens);
    var exact =
        new ExactSolver().uniqueness(assembly.graph(), clues, 10000, SolutionEquivalence.LABELED);
    assertEquals(UniquenessResult.UNIQUE, exact.status());
    var puzzle =
        new Puzzle(
            assembly.graph(),
            clues,
            RuleSet.CLASSIC_V1,
            PuzzleProvenance.supplied(),
            assembly.layout());
    var proof = new ProofLevels().classify(assembly.graph(), clues, 10000);
    var state =
        new ProbeTileRefinement.State(assembly, puzzle, exact.solutions().getFirst(), proof, 0);
    var random = new Random(42);
    var replay = new Random(42);
    int replacements = 0;
    for (int i = 0; i < 100; i++) {
      var proposal = ProbeTileRefinement.propose(state, library, random);
      var repeated = ProbeTileRefinement.propose(state, library, replay);
      if (proposal == null) {
        assertNull(repeated);
        continue;
      }
      assertNotNull(repeated);
      assertEquals(proposal.clues(), repeated.clues());
      assertEquals(proposal.assembly().graph().edges(), repeated.assembly().graph().edges());
      assertTrue(proposal.target().satisfies(proposal.assembly().graph(), proposal.clues()));
      if (proposal.operation().equals("TILE")) replacements++;
      var map = new TreeMap<Integer, Integer>();
      proposal.clues().colors().forEach((v, c) -> map.put(v.value(), c.ordinal()));
      var table = new TileConstraintSolver(proposal.assembly(), false).count(map, 10000);
      var whole =
          new ExactSolver()
              .uniqueness(
                  proposal.assembly().graph(),
                  proposal.clues(),
                  10000,
                  SolutionEquivalence.LABELED);
      assertEquals(
          switch (whole.status()) {
            case UNIQUE -> 1;
            case MULTIPLE -> 2;
            case UNSATISFIABLE -> 0;
            case UNKNOWN -> -1;
          },
          table.count());
    }
    assertTrue(replacements > 0);
  }
}
