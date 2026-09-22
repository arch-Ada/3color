package io.threecolor.solve;

import io.threecolor.model.Coloring;

public enum SolutionEquivalence {
  LABELED,
  GLOBAL_COLOR_PERMUTATION;

  public String canonicalize(Coloring coloring) {
    int[] labels = {-1, -1, -1};
    int next = 0;
    var key = new StringBuilder();
    for (var c : coloring.colors()) {
      int i = c.ordinal();
      if (this == LABELED) key.append(i);
      else {
        if (labels[i] < 0) labels[i] = next++;
        key.append(labels[i]);
      }
    }
    return key.toString();
  }
}
