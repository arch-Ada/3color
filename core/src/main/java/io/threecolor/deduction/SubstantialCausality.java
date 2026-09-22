package io.threecolor.deduction;

import java.util.*;

/** Longest proof path counting only distinct substantial event roots, not propagation facts. */
public record SubstantialCausality(Map<Integer, Integer> rootDepths, int maximumDepth) {
  public SubstantialCausality {
    rootDepths = Collections.unmodifiableMap(new TreeMap<>(rootDepths));
  }

  public static SubstantialCausality inspect(DeductionTrace trace, double substantialEffort) {
    var roots = new TreeSet<Integer>();
    for (var event : trace.events())
      if (event.rootEffort().score() >= substantialEffort) roots.add(event.rootFactId());
    var factDepth = new HashMap<Integer, Integer>();
    var rootDepth = new TreeMap<Integer, Integer>();
    int maximum = 0;
    for (var fact : trace.steps()) {
      int depth = 0;
      for (int premise : fact.premises()) {
        if (premise >= fact.id() || !factDepth.containsKey(premise))
          throw new IllegalArgumentException("Missing or non-prior premise");
        depth = Math.max(depth, factDepth.get(premise));
      }
      if (roots.contains(fact.id())) {
        depth++;
        rootDepth.put(fact.id(), depth);
        maximum = Math.max(maximum, depth);
      }
      if (factDepth.put(fact.id(), depth) != null)
        throw new IllegalArgumentException("Duplicate fact ID");
    }
    if (rootDepth.size() != roots.size()) throw new IllegalArgumentException("Missing event root");
    return new SubstantialCausality(rootDepth, maximum);
  }
}
