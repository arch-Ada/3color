package io.threecolor.generation;

import io.threecolor.model.*;
import io.threecolor.solve.*;
import java.util.*;

/** Colour-orbit canonicalization used by recolouring tests. */
public final class ProbeTargetSelection {
  public static final String VERSION = "target-selection-v1";

  public record Candidate(int attempt, Coloring target, RecolouringProfile profile) {}

  public static String orbit(Coloring target) {
    var labels = new HashMap<Color, Integer>();
    var key = new StringBuilder();
    for (var c : target.colors()) key.append(labels.computeIfAbsent(c, ignored -> labels.size()));
    return key.toString();
  }
}
