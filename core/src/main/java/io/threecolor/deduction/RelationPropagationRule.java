package io.threecolor.deduction;

import java.util.*;

public final class RelationPropagationRule implements DeductionRule {
  public String id() {
    return "relation_propagation";
  }

  public int tier() {
    return 1;
  }

  public List<RuleApplication> find(DeductionState s) {
    var out = new ArrayList<RuleApplication>();
    var relations = s.relations();
    int n = s.graph().nodeCount();
    for (int a = 0; a < n; a++) {
      if (relations.notEqual(a, a))
        return List.of(
            new RuleApplication(
                new LogicalConclusion.Contradiction(),
                List.of(a),
                Map.of(),
                relations.inequalityProof(a, a),
                List.of()));
      for (int b = 0; b < n; b++)
        if (a != b) {
          int next = s.mask(a);
          List<Integer> refs = List.of();
          String relation = "";
          if (relations.equal(a, b)) {
            next &= s.mask(b);
            if (next != s.mask(a)) {
              refs = relations.equalityProof(a, b);
              relation = "equal";
            }
          } else if (!s.graph().adjacent(a, b)
              && relations.notEqual(a, b)
              && Integer.bitCount(s.mask(b)) == 1) {
            next &= ~s.mask(b);
            if (next != s.mask(a)) {
              refs = relations.inequalityProof(a, b);
              relation = "different";
            }
          }
          if (next != s.mask(a))
            out.add(
                new RuleApplication(
                    new LogicalConclusion.NarrowDomain(a, next),
                    List.of(b),
                    Map.of(
                        "sourceNode",
                        b,
                        "equality",
                        relation.equals("equal") ? 1 : 0,
                        "colorMask",
                        s.mask(b)),
                    refs,
                    List.of()));
        }
    }
    return out;
  }
}
