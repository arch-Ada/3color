package io.threecolor.generation;

import io.threecolor.deduction.*;
import java.util.*;

/** Descriptive proof support, not a claim that every possible proof must cross a boundary. */
public final class TileProofSupport {
  public record Summary(int refutations, int spanningRefutations, int interiorTargetTiles) {}

  public static boolean spans(Set<Integer> support, List<Set<Integer>> regions) {
    return !support.isEmpty() && regions.stream().noneMatch(region -> region.containsAll(support));
  }

  public static Summary inspect(DeductionTrace trace, ThreePortTile.Assembly assembly) {
    var support = new HashMap<Integer, Set<Integer>>();
    var targets = new HashSet<Integer>();
    int refutations = 0, spanning = 0;
    for (var step : trace.steps()) {
      var vertices = local(step);
      for (int premise : step.premises()) vertices.addAll(support.getOrDefault(premise, Set.of()));
      support.put(step.id(), Set.copyOf(vertices));
      if (!step.hypothesisEvidence().isEmpty()) {
        refutations++;
        if (spans(vertices, assembly.regions())) spanning++;
        if (step.node() >= assembly.junctionCount())
          for (int i = 0; i < assembly.regions().size(); i++)
            if (assembly.regions().get(i).contains(step.node())) targets.add(i);
      }
    }
    return new Summary(refutations, spanning, targets.size());
  }

  private static Set<Integer> local(Deduction step) {
    var vertices = new HashSet<Integer>();
    if (step.node() >= 0) vertices.add(step.node());
    vertices.addAll(step.witnesses());
    switch (step.conclusion()) {
      case LogicalConclusion.NarrowDomain d -> vertices.add(d.node());
      case LogicalConclusion.Equal d -> {
        vertices.add(d.a());
        vertices.add(d.b());
      }
      case LogicalConclusion.NotEqual d -> {
        vertices.add(d.a());
        vertices.add(d.b());
      }
      case LogicalConclusion.Contradiction ignored -> {}
    }
    for (var evidence : step.hypothesisEvidence()) vertices.addAll(local(evidence));
    return vertices;
  }
}
