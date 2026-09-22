package io.threecolor.generation;

import io.threecolor.model.*;
import io.threecolor.solve.*;
import java.util.*;

/** Deterministic colouring fixtures shared by tiling and recolouring tests. */
public final class ProbeBoundaryTiling {
  public static final String VERSION = "boundary-tiling-v1";

  public static Coloring complete(
      ThreePortTile.Assembly assembly, List<Integer> boundary, long seed) {
    if (boundary.size() != assembly.junctionCount())
      throw new IllegalArgumentException("Boundary size");
    var colors = new ArrayList<Color>();
    for (int c : boundary) colors.add(Color.values()[c]);
    var random = new Random(seed);
    for (int i = 0; i < assembly.placements().size(); i++) {
      int code = 0, power = 1;
      for (int v : assembly.ports().get(i)) {
        code += boundary.get(v) * power;
        power *= 3;
      }
      ThreePortTile.Row match = null;
      for (var row : assembly.placements().get(i).tile().rows())
        if (row.code() == code) match = row;
      if (match == null || match.extensions() == 0)
        throw new IllegalArgumentException("Incompatible boundary");
      long extensions = match.extensions();
      int skip = random.nextInt(Long.bitCount(extensions));
      for (int k = 0; k < skip; k++) extensions &= extensions - 1;
      int interior = Long.numberOfTrailingZeros(extensions);
      for (int k = 0; k < 3; k++) {
        colors.add(Color.values()[interior % 3]);
        interior /= 3;
      }
    }
    var target = new Coloring(colors);
    if (!target.satisfies(assembly.graph(), PartialColoring.empty()))
      throw new IllegalStateException("Invalid target");
    return target;
  }
}
