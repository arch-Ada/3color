package io.threecolor.deduction;

import java.util.*;

public final class LockedEdgeRule implements DeductionRule {
  public String id() {
    return "locked_two_color_edge";
  }

  public int tier() {
    return 2;
  }

  public List<RuleApplication> find(DeductionState s) {
    var result = new ArrayList<RuleApplication>();
    var g = s.graph();
    for (var e : g.edges()) {
      int a = e.a().value(), b = e.b().value(), pair = s.mask(a) | s.mask(b);
      if (Integer.bitCount(pair) != 2) continue;
      for (int v : g.neighbors(a))
        if (g.adjacent(v, b) && (s.mask(v) & pair) != 0)
          result.add(
              new RuleApplication(v, s.mask(v) & ~pair, List.of(a, b), Map.of("pairMask", pair)));
    }
    return result;
  }
}
