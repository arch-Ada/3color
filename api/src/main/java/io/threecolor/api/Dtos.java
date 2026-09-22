package io.threecolor.api;

import io.threecolor.deduction.*;
import io.threecolor.difficulty.*;
import io.threecolor.model.*;
import io.threecolor.validation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;

public final class Dtos {
  private Dtos() {}

  public record EdgeDto(@Min(0) @Max(79) int a, @Min(0) @Max(79) int b) {}

  public record PointDto(double x, double y) {}

  public record ProvenanceDto(
      @NotNull String generatorVersion,
      @NotNull String masterSeed,
      @Min(0) int attemptIndex,
      @NotNull String generationSpec) {}

  public record PuzzleDto(
      @Min(1) @Max(80) int nodeCount,
      @NotNull @Size(max = 400) List<@NotNull @Valid EdgeDto> edges,
      @NotNull @Size(max = 80) Map<@Min(0) @Max(79) Integer, @NotNull String> givens,
      @NotNull @Size(max = 80) List<@NotNull @Valid PointDto> layout,
      @NotNull String rules,
      @Valid ProvenanceDto provenance,
      String logicalHash,
      @NotNull ExplanationModel explanationModel) {
    public Puzzle core() {
      var colors = new TreeMap<NodeId, Color>();
      givens.forEach((n, c) -> colors.put(new NodeId(n), Color.valueOf(c)));
      return new Puzzle(
          new GraphTopology(nodeCount, edges.stream().map(e -> new Edge(e.a, e.b)).toList()),
          new PartialColoring(colors),
          RuleSet.valueOf(rules),
          provenance == null
              ? PuzzleProvenance.supplied()
              : new PuzzleProvenance(
                  provenance.generatorVersion,
                  Long.parseLong(provenance.masterSeed),
                  provenance.attemptIndex,
                  provenance.generationSpec),
          new PuzzleLayout(layout.stream().map(p -> new PuzzleLayout.Point(p.x, p.y)).toList()),
          explanationModel);
    }

    public static PuzzleDto from(Puzzle p) {
      var givens = new TreeMap<Integer, String>();
      p.givens().colors().forEach((n, c) -> givens.put(n.value(), c.name()));
      var v = p.provenance();
      return new PuzzleDto(
          p.topology().nodeCount(),
          p.topology().edges().stream()
              .map(e -> new EdgeDto(e.a().value(), e.b().value()))
              .toList(),
          givens,
          p.layout().points().stream().map(x -> new PointDto(x.x(), x.y())).toList(),
          p.rules().name(),
          new ProvenanceDto(
              v.generatorVersion(),
              Long.toString(v.masterSeed()),
              v.attemptIndex(),
              v.generationSpec()),
          p.logicalHash(),
          p.explanationModel());
    }
  }

  public record PuzzleRequest(
      @NotNull @Valid PuzzleDto puzzle,
      @Min(1) @Max(200000) Long maxSearchNodes,
      String equivalence) {}

  public record HintRequest(
      @NotNull @Valid PuzzleDto puzzle,
      @NotNull @Size(max = 80) Map<@Min(0) @Max(79) Integer, @NotNull String> current,
      @NotNull String level) {}

  public record ConclusionDto(String kind, Integer node, Integer mask, Integer a, Integer b) {
    public static ConclusionDto from(LogicalConclusion c) {
      return switch (c) {
        case LogicalConclusion.NarrowDomain d ->
            new ConclusionDto(c.kind().name(), d.node(), d.mask(), null, null);
        case LogicalConclusion.Equal e ->
            new ConclusionDto(c.kind().name(), null, null, e.a(), e.b());
        case LogicalConclusion.NotEqual e ->
            new ConclusionDto(c.kind().name(), null, null, e.a(), e.b());
        case LogicalConclusion.Contradiction x ->
            new ConclusionDto(c.kind().name(), null, null, null, null);
      };
    }
  }

  public record StepDto(
      int id,
      String ruleId,
      int tier,
      int node,
      int beforeMask,
      int afterMask,
      List<Integer> premises,
      List<Integer> witnesses,
      String explanationKey,
      Map<String, Integer> arguments,
      List<StepDto> hypothesisEvidence,
      ConclusionDto conclusion,
      ApplicationEffort effort,
      List<ImplicationLink> implicationChain) {
    public static StepDto from(Deduction d) {
      return new StepDto(
          d.id(),
          d.ruleId(),
          d.tier(),
          d.node(),
          d.beforeMask(),
          d.afterMask(),
          d.premises(),
          d.witnesses(),
          d.explanationKey(),
          d.arguments(),
          d.hypothesisEvidence().stream().map(StepDto::from).toList(),
          ConclusionDto.from(d.conclusion()),
          d.effort(),
          d.implicationChain());
    }
  }

  public record TraceDto(
      List<StepDto> steps,
      List<Integer> finalDomains,
      String status,
      List<Integer> availableDeductions,
      int hypothesesTried,
      List<FrontierSummary> frontiers,
      List<DeductionEvent> events) {
    public static TraceDto from(DeductionTrace t) {
      return new TraceDto(
          t.steps().stream().map(StepDto::from).toList(),
          t.finalDomains(),
          t.status().name(),
          t.availableDeductions(),
          t.hypothesesTried(),
          t.frontiers(),
          t.events());
    }
  }

  public record DifficultyDto(
      String modelVersion,
      String band,
      double score,
      int totalDeductionCount,
      int hardestRuleTier,
      Map<String, Integer> ruleUsageCounts,
      int weightedRuleCost,
      int maximumProofDepth,
      int hypothesisSteps,
      double averageAvailableDeductions,
      int minimumAvailableDeductions,
      boolean humanSolved,
      DifficultyProfile profile) {
    public static DifficultyDto from(DifficultyReport d) {
      return new DifficultyDto(
          d.modelVersion(),
          d.band().name(),
          d.score(),
          d.totalDeductionCount(),
          d.hardestRuleTier(),
          d.ruleUsageCounts(),
          d.weightedRuleCost(),
          d.maximumProofDepth(),
          d.hypothesisSteps(),
          d.averageAvailableDeductions(),
          d.minimumAvailableDeductions(),
          d.humanSolved(),
          d.profile());
    }
  }

  public record MetricsDto(
      int n,
      int m,
      double averageDegree,
      int minDegree,
      int maxDegree,
      Map<Integer, Integer> degreeDistribution,
      int triangleCount,
      int connectedComponents,
      int cycleRank) {
    public static MetricsDto from(GraphMetrics m) {
      return new MetricsDto(
          m.n(),
          m.m(),
          m.averageDegree(),
          m.minDegree(),
          m.maxDegree(),
          m.degreeDistribution(),
          m.triangleCount(),
          m.connectedComponents(),
          m.cycleRank());
    }
  }

  public record AnalysisResponse(TraceDto trace, DifficultyDto difficulty, MetricsDto metrics) {}

  public record HintResponse(
      String level,
      String status,
      StepDto deduction,
      List<StepDto> supportingSteps,
      HintExplanationDto explanation) {}

  public record WalkthroughDto(
      String kind,
      Integer node,
      Integer mask,
      StepDto deduction,
      List<Integer> vertices,
      List<HintExplanation.FocusEdge> edges) {}

  public record HintExplanationDto(
      String reasoningType,
      List<Integer> primaryTargets,
      ConclusionDto conclusion,
      List<Integer> focusVertices,
      List<HintExplanation.FocusEdge> focusEdges,
      List<Integer> orderedPath,
      HintExplanation.Assignment assumption,
      Integer contradictionPoint,
      List<WalkthroughDto> walkthrough) {
    static HintExplanationDto from(HintExplanation e) {
      if (e == null) return null;
      return new HintExplanationDto(
          e.reasoningType(),
          e.primaryTargets(),
          ConclusionDto.from(e.conclusion()),
          e.focusVertices(),
          e.focusEdges(),
          e.orderedPath(),
          e.assumption(),
          e.contradictionPoint(),
          e.walkthrough().stream()
              .map(
                  w ->
                      new WalkthroughDto(
                          w.kind(),
                          w.node(),
                          w.mask(),
                          w.deduction() == null ? null : StepDto.from(w.deduction()),
                          w.vertices(),
                          w.edges()))
              .toList());
    }
  }

  public record StatsDto(
      long searchNodes,
      long propagations,
      long backtracks,
      int maximumDepth,
      int solutionsEncountered) {}

  public record SolveResponse(
      String status, List<List<String>> solutions, StatsDto stats, boolean budgetExhausted) {}

  public record ErrorResponse(
      String code,
      String message,
      Map<String, Integer> details,
      io.threecolor.generation.GenerationDiagnostics generation) {
    public ErrorResponse(String code, String message, Map<String, Integer> details) {
      this(code, message, details, null);
    }
  }
}
