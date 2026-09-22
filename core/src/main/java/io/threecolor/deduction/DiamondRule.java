package io.threecolor.deduction;

import java.util.*;

/** Common neighbors of a physical edge share the third color, even if no color is known. */
public final class DiamondRule implements DeductionRule {
  public String id() {
    return "diamond_equality";
  }

  public int tier() {
    return 2;
  }

  public List<RuleApplication> find(DeductionState s) {
    var out = new ArrayList<RuleApplication>();
    var g = s.graph();
    for (var edge : g.edges()) {
      int a = edge.a().value(), b = edge.b().value();
      int anchor = -1;
      for (int v : g.neighbors(a))
        if (g.adjacent(v, b)) {
          if (anchor < 0) {
            anchor = v;
            continue;
          }
          if (!s.relations().equal(anchor, v))
            out.add(
                new RuleApplication(
                    new LogicalConclusion.Equal(anchor, v),
                    List.of(a, b, anchor, v),
                    Map.of("edgeA", a, "edgeB", b)));
        }
    }
    return out;
  }
}
