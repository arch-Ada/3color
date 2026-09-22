package io.threecolor.generation;

import io.threecolor.deduction.ProofLevels;
import io.threecolor.model.*;
import io.threecolor.solve.*;
import java.util.*;

/** Deterministic tile-edit fixtures shared by refinement tests. */
public final class ProbeTileRefinement {
  public static final String VERSION = "three-port-refinement-v1";

  public record State(
      ThreePortTile.Assembly assembly,
      Puzzle puzzle,
      Coloring target,
      ProofLevels.Classification proof,
      int score) {}

  public record Proposal(
      ThreePortTile.Assembly assembly, PartialColoring clues, Coloring target, String operation) {}

  public static Proposal propose(State state, List<ThreePortTile> library, Random random) {
    var assembly = state.assembly();
    var target = state.target();
    var clues = new TreeMap<>(state.puzzle().givens().colors());
    if (random.nextInt(5) < 2) {
      int slot = random.nextInt(assembly.placements().size());
      for (int trial = 0; trial < 20; trial++) {
        var placements = new ArrayList<>(assembly.placements());
        placements.set(
            slot,
            new ThreePortTile.Placement(
                library.get(random.nextInt(library.size())), random.nextInt(4)));
        var candidate = ThreePortTile.assemble(placements, assembly.columns(), assembly.rows());
        int code = 0, power = 1;
        for (int v : candidate.ports().get(slot)) {
          code += target.colors().get(v).ordinal() * power;
          power *= 3;
        }
        ThreePortTile.Row matched = null;
        for (var row : placements.get(slot).tile().rows())
          if (row.code() == code && row.count() == 1) {
            matched = row;
            break;
          }
        if (matched == null) continue;
        int interior = Long.numberOfTrailingZeros(matched.extensions());
        var colors = new ArrayList<>(target.colors());
        for (int k = 0; k < 3; k++) {
          colors.set(candidate.junctionCount() + slot * 3 + k, Color.values()[interior % 3]);
          interior /= 3;
        }
        var next = new Coloring(colors);
        for (var v : List.copyOf(clues.keySet())) clues.put(v, next.colors().get(v.value()));
        return new Proposal(candidate, new PartialColoring(clues), next, "TILE");
      }
      return null;
    }
    int mode = random.nextInt(4);
    String operation = mode < 2 ? "EXCHANGE_CLUE" : mode == 2 ? "DELETE_CLUE" : "ADD_CLUE";
    if (mode != 3) {
      if (clues.isEmpty()) return null;
      clues.remove(new ArrayList<>(clues.keySet()).get(random.nextInt(clues.size())));
    }
    if (mode != 2) {
      var available = new ArrayList<Integer>();
      for (int v = 0; v < target.colors().size(); v++)
        if (!clues.containsKey(new NodeId(v))) available.add(v);
      if (available.isEmpty()) return null;
      int v = available.get(random.nextInt(available.size()));
      clues.put(new NodeId(v), target.colors().get(v));
    }
    return new Proposal(assembly, new PartialColoring(clues), target, operation);
  }

  public static String spec(ThreePortTile.Assembly assembly) {
    return "shape="
        + assembly.columns()
        + "x"
        + assembly.rows()
        + ";tiles="
        + String.join(
            ",",
            assembly.placements().stream().map(p -> p.tile().seed() + ":" + p.rotation()).toList());
  }
}
