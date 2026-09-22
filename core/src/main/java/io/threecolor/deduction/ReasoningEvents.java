package io.threecolor.deduction;

import java.util.*;

public final class ReasoningEvents {
  private ReasoningEvents() {}

  public static double information(Deduction d) {
    if (!(d.conclusion() instanceof LogicalConclusion.NarrowDomain)
        || d.tier() == 0
        || d.beforeMask() == 0
        || d.afterMask() == 0) return 0;
    return Math.log((double) Integer.bitCount(d.beforeMask()) / Integer.bitCount(d.afterMask()))
        / Math.log(2);
  }

  public static List<DeductionEvent> from(List<Deduction> steps) {
    var result = new ArrayList<DeductionEvent>();
    var group = new ArrayList<Deduction>();
    var ids = new HashSet<Integer>();
    for (var d : steps)
      if (d.tier() > 0) {
        boolean cascade =
            d.tier() == 1 && !group.isEmpty() && d.premises().stream().anyMatch(ids::contains);
        if (!cascade && !group.isEmpty()) {
          result.add(event(group));
          group.clear();
          ids.clear();
        }
        group.add(d);
        ids.add(d.id());
      }
    if (!group.isEmpty()) result.add(event(group));
    return List.copyOf(result);
  }

  private static DeductionEvent event(List<Deduction> facts) {
    var root = facts.getFirst();
    var ids = new HashSet<Integer>();
    facts.forEach(d -> ids.add(d.id()));
    var refs = new TreeSet<Integer>();
    facts.forEach(d -> d.premises().stream().filter(i -> !ids.contains(i)).forEach(refs::add));
    return new DeductionEvent(
        root.id(),
        root.ruleId(),
        root.effort(),
        List.of(root.id()),
        facts.stream().skip(1).map(Deduction::id).toList(),
        List.copyOf(refs),
        facts.stream().mapToDouble(ReasoningEvents::information).sum(),
        information(root),
        facts.stream().skip(1).mapToDouble(ReasoningEvents::information).sum(),
        (int)
            facts.stream()
                .filter(
                    d ->
                        d.conclusion() instanceof LogicalConclusion.Equal
                            || d.conclusion() instanceof LogicalConclusion.NotEqual)
                .count());
  }
}
