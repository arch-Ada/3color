package io.threecolor.deduction;

import java.util.*;

/**
 * Canonical conclusions with the simplest available proof. Hypotheses are lazy and explicitly
 * marked.
 */
public record DeductionFrontier(
    List<AvailableDeduction> deductions, boolean hypothesesDeferred, boolean budgetExhausted) {
  public DeductionFrontier {
    deductions = List.copyOf(deductions);
  }

  public static DeductionFrontier canonical(
      Collection<AvailableDeduction> input, boolean deferred, boolean exhausted) {
    var best = new TreeMap<String, AvailableDeduction>();
    for (var d : input)
      best.merge(d.key(), d, (a, b) -> AvailableDeduction.order().compare(a, b) <= 0 ? a : b);
    return new DeductionFrontier(
        best.values().stream().sorted(AvailableDeduction.order()).toList(), deferred, exhausted);
  }

  public Optional<AvailableDeduction> easiest() {
    return deductions.stream().findFirst();
  }

  public FrontierSummary summary(int factId) {
    return new FrontierSummary(
        factId,
        deductions.size(),
        deductions.stream().map(d -> d.effort().score()).toList(),
        hypothesesDeferred,
        budgetExhausted);
  }
}
