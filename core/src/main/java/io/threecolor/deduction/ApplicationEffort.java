package io.threecolor.deduction;

/** Measured inputs are exposed separately from the provisional scalar used for ordering. */
public record ApplicationEffort(
    double score,
    int witnessCount,
    int pathLength,
    int proofDepth,
    int implicationLength,
    int interactingVertices,
    int hypothesisDepth) {}
