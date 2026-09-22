package io.threecolor.generation;

import io.threecolor.deduction.DeductionTrace;
import io.threecolor.difficulty.DifficultyReport;
import io.threecolor.model.*;
import io.threecolor.validation.GraphMetrics;

/**
 * Hidden solution is core research evidence. The API deliberately projects a different response
 * DTO.
 */
public record GeneratedPuzzle(
    Puzzle puzzle,
    Coloring hiddenSolution,
    GenerationSpec spec,
    String logicalHash,
    DifficultyReport difficulty,
    DeductionTrace trace,
    GraphMetrics metrics,
    GenerationQuality quality)
    implements GenerationOutcome {}
