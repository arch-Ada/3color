package io.threecolor.deduction;

import java.util.List;

public interface DeductionRule {
  String id();

  int tier();

  List<RuleApplication> find(DeductionState state);
}
