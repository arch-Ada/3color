package io.threecolor.solve;

public record SolveStats(
    long searchNodes,
    long propagations,
    long backtracks,
    int maximumDepth,
    int solutionsEncountered) {}
