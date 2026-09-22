package io.threecolor.generation;

import io.threecolor.deduction.*;
import java.util.*;

/** Bounded proof-support checks used by proof diagnostics tests. */
public final class ProbeHardDependencies {
  public enum Outcome {
    REFUTED,
    DIRECT,
    UNSUPPORTED,
    UNKNOWN
  }

  public record Support(List<ProofLevels.Elimination> retained, boolean minimal, int trials) {
    public Support {
      retained = List.copyOf(retained);
    }
  }

  public static Outcome test(
      DeductionState before,
      List<ProofLevels.Elimination> supplied,
      ProofLevels.Elimination target,
      int budget) {
    var domains = new ArrayList<>(before.domains());
    for (var e : supplied) domains.set(e.node(), domains.get(e.node()) & ~e.bit());
    var levels = new ProofLevels();
    var closed =
        levels.closeRelational(
            new DeductionState(before.graph(), domains, before.relations()), budget);
    if (closed.status() == ProofLevels.Status.UNKNOWN) return Outcome.UNKNOWN;
    if (closed.status() == ProofLevels.Status.CONTRADICTION)
      throw new IllegalArgumentException("Supplied eliminations contradict baseline");
    if ((closed.state().mask(target.node()) & target.bit()) == 0) return Outcome.DIRECT;
    var branch = new ArrayList<>(closed.state().domains());
    branch.set(target.node(), target.bit());
    var proof =
        levels.closeRelational(
            new DeductionState(before.graph(), branch, closed.state().relations()), budget);
    return switch (proof.status()) {
      case CONTRADICTION -> Outcome.REFUTED;
      case UNKNOWN -> Outcome.UNKNOWN;
      default -> Outcome.UNSUPPORTED;
    };
  }

  public static Support minimize(
      DeductionState before,
      List<ProofLevels.Elimination> supplied,
      ProofLevels.Elimination target,
      int budget) {
    var retained = new ArrayList<>(supplied);
    if (!sufficient(test(before, retained, target, budget)))
      throw new IllegalArgumentException("Full support does not establish target");
    int trials = 1;
    boolean changed;
    do {
      changed = false;
      for (var e : List.copyOf(retained)) {
        var subset = new ArrayList<>(retained);
        subset.remove(e);
        trials++;
        if (sufficient(test(before, subset, target, budget))) {
          retained = subset;
          changed = true;
        }
      }
    } while (changed);
    boolean minimal = true;
    for (var e : retained) {
      var subset = new ArrayList<>(retained);
      subset.remove(e);
      trials++;
      var outcome = test(before, subset, target, budget);
      if (outcome != Outcome.UNSUPPORTED) minimal = false;
    }
    return new Support(retained, minimal, trials);
  }

  private static boolean sufficient(Outcome o) {
    return o == Outcome.REFUTED || o == Outcome.DIRECT;
  }

  public static String encode(List<ProofLevels.Elimination> list) {
    return String.join(";", list.stream().map(e -> e.node() + ":" + e.bit()).toList());
  }
}
