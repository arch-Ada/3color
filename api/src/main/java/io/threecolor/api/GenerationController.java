package io.threecolor.api;

import static io.threecolor.api.Dtos.*;

import io.threecolor.deduction.*;
import io.threecolor.difficulty.*;
import io.threecolor.generation.DeletionGenerator;
import io.threecolor.generation.PuzzleSize;
import io.threecolor.generation.TopologyFingerprint;
import io.threecolor.validation.GraphMetrics;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Player generation and stored-puzzle replay. */
@RestController
@RequestMapping("/api/v1/puzzles")
public final class GenerationController {
  private final io.threecolor.supply.SupplyService supply;

  public GenerationController(
      org.springframework.beans.factory.ObjectProvider<io.threecolor.supply.PuzzleStore> stores) {
    supply = new io.threecolor.supply.SupplyService(stores.getIfAvailable());
  }

  @GetMapping("/supply/{key:[0-9a-f]{64}}")
  public ResponseEntity<?> replay(@PathVariable String key) {
    try {
      var found = supply.replay(key);
      return found.isPresent()
          ? response(found.get().outcome(), key)
          : ResponseEntity.notFound().build();
    } catch (java.sql.SQLException e) {
      return ResponseEntity.status(503).build();
    }
  }

  public record Request(
      String seed,
      @Min(4) @Max(53) Integer nodeCount,
      @Pattern(regexp = "MINI|SMALL|MEDIUM|LARGE|VERY_LARGE") String size,
      @NotNull @Pattern(regexp = "VERY_EASY|EASY|MEDIUM|CHALLENGING") String difficulty,
      @Size(max = 1024)
          java.util.List<@NotNull @Pattern(regexp = "[0-9a-f]{64}") String> excludeTopologies) {
    @AssertTrue(message = "Supply exactly one of size or nodeCount")
    public boolean isSizeSelectionValid() {
      return (nodeCount == null) != (size == null);
    }

    @AssertTrue(message = "Mini currently supports Very Easy and Easy only")
    public boolean isSupportedCombination() {
      return !("MEDIUM".equals(difficulty) || "CHALLENGING".equals(difficulty))
          || !("MINI".equals(size) || nodeCount != null && nodeCount < 14);
    }
  }

  public record Response(
      PuzzleDto puzzle,
      ProvenanceDto generation,
      DifficultyDto difficulty,
      MetricsDto metrics,
      ProofDto proof,
      DeletionGenerator.Stats search,
      String topologyKey,
      PlayDifficulty.Rating playDifficulty,
      String supplyId) {
    public Response(
        PuzzleDto puzzle,
        ProvenanceDto generation,
        DifficultyDto difficulty,
        MetricsDto metrics,
        ProofDto proof,
        DeletionGenerator.Stats search,
        String topologyKey,
        PlayDifficulty.Rating playDifficulty) {
      this(
          puzzle,
          generation,
          difficulty,
          metrics,
          proof,
          search,
          topologyKey,
          playDifficulty,
          null);
    }
  }

  /** Deliberately excludes final domains, which would reveal the solution. */
  public record LevelDto(
      String status, int states, int hypotheses, int refutations, int refutationRounds) {
    static LevelDto from(ProofLevels.Result r) {
      return r == null
          ? null
          : new LevelDto(
              r.status().name(), r.states(), r.hypotheses(), r.refutations(), r.refutationRounds());
    }
  }

  public record ProofDto(String modelVersion, String band, LevelDto p0, LevelDto p1, LevelDto p2) {
    static ProofDto from(ProofLevels.Classification p) {
      return new ProofDto(
          ProofLevels.VERSION,
          p.band().name(),
          LevelDto.from(p.p0()),
          LevelDto.from(p.p1()),
          LevelDto.from(p.p2()));
    }
  }

  public record Failure(
      String code, String message, String reason, DeletionGenerator.Stats search) {}

  @PostMapping("/generate")
  public ResponseEntity<?> generate(@Valid @RequestBody Request request) {
    long seed =
        request.seed() == null || request.seed().isBlank()
            ? new java.security.SecureRandom().nextLong()
            : Long.parseLong(request.seed());
    var category = PlayDifficulty.Category.valueOf(request.difficulty());
    var band = category.sourceBand();
    var size = request.size() == null ? null : PuzzleSize.valueOf(request.size());
    int minimum = size == null ? request.nodeCount() : size.minimum();
    int maximum = size == null ? request.nodeCount() : size.maximum();
    var excluded =
        request.excludeTopologies() == null
            ? Set.<String>of()
            : Set.copyOf(request.excludeTopologies());
    var selection =
        supply.generate(
            category,
            minimum,
            maximum,
            seed,
            excluded,
            request.seed() != null && !request.seed().isBlank());
    var result = selection.outcome();
    if (result.success() && excluded.contains(TopologyFingerprint.of(result.puzzle().topology())))
      result = new DeletionGenerator.Outcome(null, null, result.stats(), "SEEN_TOPOLOGY");
    if (!result.success())
      return ResponseEntity.unprocessableEntity()
          .body(
              new Failure(
                  "DELETION_NO_MATCH",
                  "The bounded search did not find a qualifying puzzle. No difficulty was"
                      + " substituted.",
                  result.failure(),
                  result.stats()));
    return response(result, selection.supplyId());
  }

  private ResponseEntity<?> response(DeletionGenerator.Outcome result, String supplyId) {
    var certified =
        Objects.requireNonNull(result.certificate(), "Missing final certificate")
            .withPuzzle(result.puzzle());
    var puzzle = PuzzleDto.from(result.puzzle());
    // Diagnostics reuse the accepted explanation; no solution-bearing certificate enters the DTO.
    var trace = certified.trace();
    return ResponseEntity.ok(
        new Response(
            puzzle,
            puzzle.provenance(),
            DifficultyDto.from(new DifficultyAnalyzer().analyze(trace)),
            MetricsDto.from(GraphMetrics.of(result.puzzle().topology())),
            ProofDto.from(result.proof()),
            result.stats(),
            TopologyFingerprint.of(result.puzzle().topology()),
            certified.rating(),
            supplyId));
  }
}
