package io.threecolor.generation;

import io.threecolor.deduction.ProofLevels;

/**
 * Necessary Hard filter, applied only after labelled uniqueness has been independently certified. A
 * satisfiable unique binary-domain colouring is 2-SAT; failed-literal propagation settles it in one
 * complete round. Three-colour residual domains are necessary, never sufficient, for Hard.
 */
public record HardResidual(ProofLevels.Status status, int singles, int pairs, int triples) {
  public static HardResidual of(ProofLevels.Result p1) {
    int singles = 0, pairs = 0, triples = 0;
    for (int mask : p1.domains())
      switch (Integer.bitCount(mask)) {
        case 1 -> singles++;
        case 2 -> pairs++;
        case 3 -> triples++;
        default -> {}
      }
    return new HardResidual(p1.status(), singles, pairs, triples);
  }

  public boolean binaryOnly() {
    return status == ProofLevels.Status.STALLED && triples == 0;
  }
}
