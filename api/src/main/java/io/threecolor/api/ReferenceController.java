package io.threecolor.api;

import io.threecolor.deduction.ProofLevels;
import io.threecolor.difficulty.DifficultyAnalyzer;
import io.threecolor.generation.PuzzleBank;
import io.threecolor.validation.GraphMetrics;
import java.util.*;
import org.springframework.web.bind.annotation.*;

/** Small, explicitly curated playtest set. No solution or trace is sent before inspection. */
@RestController
@RequestMapping("/api/v1/references")
public final class ReferenceController {
  public record Reference(String id, GenerationController.Response generated) {}

  private final List<Reference> references;

  public ReferenceController() {
    var stream = ReferenceController.class.getResourceAsStream("/reference-puzzles.tsv");
    if (stream == null) throw new IllegalStateException("Missing reference puzzles");
    references =
        PuzzleBank.read(stream).entries().stream()
            .map(
                entry -> {
                  var p = entry.puzzle();
                  String id = p.provenance().generationSpec().replace("reference=", "");
                  if (!Set.of("dependency", "shortcut", "larger").contains(id))
                    throw new IllegalStateException("Unknown reference puzzle");
                  var dto = Dtos.PuzzleDto.from(p);
                  return new Reference(
                      id,
                      new GenerationController.Response(
                          dto,
                          dto.provenance(),
                          Dtos.DifficultyDto.from(
                              new DifficultyAnalyzer()
                                  .analyze(
                                      ProofLevels.explanationEngine()
                                          .solve(p.topology(), p.givens()))),
                          Dtos.MetricsDto.from(GraphMetrics.of(p.topology())),
                          GenerationController.ProofDto.from(entry.proof()),
                          null,
                          io.threecolor.generation.TopologyFingerprint.of(p.topology()),
                          null));
                })
            .toList();
  }

  @GetMapping
  public List<Reference> list() {
    return references;
  }
}
