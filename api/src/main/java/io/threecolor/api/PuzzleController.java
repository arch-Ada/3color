package io.threecolor.api;

import static io.threecolor.api.Dtos.*;

import io.threecolor.deduction.*;
import io.threecolor.difficulty.*;
import io.threecolor.model.*;
import io.threecolor.solve.*;
import io.threecolor.validation.*;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class PuzzleController {
  private final HintService hints = new HintService();

  @PostMapping("/solve")
  public SolveResponse solve(@Valid @RequestBody PuzzleRequest request) {
    var p = request.puzzle().core();
    var r =
        new ExactSolver()
            .uniqueness(
                p.topology(),
                p.givens(),
                request.maxSearchNodes() == null ? 100000 : request.maxSearchNodes(),
                request.equivalence() == null
                    ? SolutionEquivalence.LABELED
                    : SolutionEquivalence.valueOf(request.equivalence()));
    var s = r.stats();
    return new SolveResponse(
        r.status().name(),
        r.solutions().stream().map(c -> c.colors().stream().map(Enum::name).toList()).toList(),
        new StatsDto(
            s.searchNodes(),
            s.propagations(),
            s.backtracks(),
            s.maximumDepth(),
            s.solutionsEncountered()),
        r.budgetExhausted());
  }

  @PostMapping("/analyze")
  public AnalysisResponse analyze(@Valid @RequestBody PuzzleRequest request) {
    var p = request.puzzle().core();
    var engine = ProofLevels.explanationEngineFor(p);
    var trace = engine.solve(p.topology(), p.givens());
    return new AnalysisResponse(
        TraceDto.from(trace),
        DifficultyDto.from(new DifficultyAnalyzer().analyze(trace)),
        MetricsDto.from(GraphMetrics.of(p.topology())));
  }

  @PostMapping("/hints")
  public HintResponse hint(@Valid @RequestBody HintRequest request) {
    var p = request.puzzle().core();
    var current = new TreeMap<NodeId, Color>();
    request.current().forEach((n, c) -> current.put(new NodeId(n), Color.valueOf(c)));
    var h = hints.hint(p, new PartialColoring(current), Hint.Level.valueOf(request.level()));
    var support = h.supportingSteps().stream().map(StepDto::from).toList();
    return new HintResponse(
        h.level().name(),
        h.status().name(),
        h.deduction().map(StepDto::from).orElse(null),
        support,
        HintExplanationDto.from(h.explanation()));
  }
}
