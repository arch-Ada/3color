package io.threecolor.deduction;

import java.util.*;

/** One insight plus its immediately following causally dependent mechanical cascade. */
public record DeductionEvent(
    int rootFactId,
    String ruleId,
    ApplicationEffort rootEffort,
    List<Integer> directFacts,
    List<Integer> propagationFacts,
    List<Integer> premises,
    double informationGain,
    double rootInformation,
    double cascadeInformation,
    int relationFacts) {
  public DeductionEvent {
    directFacts = List.copyOf(directFacts);
    propagationFacts = List.copyOf(propagationFacts);
    premises = List.copyOf(premises);
  }
}
