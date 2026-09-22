package io.threecolor.deduction;

import java.util.*;

public final class AdjacencyRule implements DeductionRule {
  public String id() {
    return "adjacent_color_elimination";
  }

  public int tier() {
    return 1;
  }

  public List<RuleApplication> find(DeductionState s) {
    var out = new ArrayList<RuleApplication>();
    for (int u = 0; u < s.graph().nodeCount(); u++)
      if (Integer.bitCount(s.mask(u)) == 1)
        for (int v : s.graph().neighbors(u))
          if ((s.mask(v) & s.mask(u)) != 0)
            out.add(
                new RuleApplication(
                    v,
                    s.mask(v) & ~s.mask(u),
                    List.of(u),
                    Map.of("sourceNode", u, "colorMask", s.mask(u))));
    return out;
  }
}
