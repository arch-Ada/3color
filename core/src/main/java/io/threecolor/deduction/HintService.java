package io.threecolor.deduction;

import io.threecolor.model.*;
import java.util.*;

public final class HintService {
  private final ClueProofCache clueProofs = new ClueProofCache(8, 20000);

  public Hint hint(Puzzle puzzle, PartialColoring current, Hint.Level level) {
    current.validateNodes(puzzle.topology());
    var colors = new TreeMap<>(puzzle.givens().colors());
    for (var e : current.colors().entrySet()) {
      if (colors.containsKey(e.getKey()) && colors.get(e.getKey()) != e.getValue())
        return new Hint(level, Hint.Status.CONFLICT, Optional.empty());
      colors.put(e.getKey(), e.getValue());
    }
    for (var edge : puzzle.topology().edges())
      if (colors.containsKey(edge.a()) && colors.get(edge.a()) == colors.get(edge.b()))
        return edgeConflict(level, edge.a().value(), edge.b().value());
    var engine = ProofLevels.explanationEngineFor(puzzle);
    DeductionTrace original = null;
    if (!colors.equals(puzzle.givens().colors())) {
      original =
          clueProofs.get(
              puzzle,
              puzzle.explanationModel().name(),
              DeductionConfiguration.defaults(),
              () -> engine.solve(puzzle.topology(), puzzle.givens()));
      if (original.status() == DeductionTrace.Status.CONTRADICTION)
        return new Hint(level, Hint.Status.CONFLICT, Optional.empty());
      var correction =
          candidates(puzzle, original, level, puzzle.givens().colors()).stream()
              .filter(
                  h -> {
                    var d = h.deduction().orElseThrow();
                    var entered = colors.get(new NodeId(d.node()));
                    return entered != null && entered.mask() != d.afterMask();
                  })
              .min(Comparator.comparingInt(h -> h.explanation().walkthrough().size()));
      if (correction.isPresent()) {
        var h = correction.get();
        return new Hint(
            level, Hint.Status.CORRECTION, h.deduction(), h.supportingSteps(), h.explanation());
      }
      // Do not base a forward hint on entries the bounded clue proof could not establish.
      for (var entry : colors.entrySet())
        if (original.finalDomains().get(entry.getKey().value()) != entry.getValue().mask())
          return new Hint(
              level,
              original.status() == DeductionTrace.Status.BUDGET_EXHAUSTED
                  ? Hint.Status.BUDGET_EXHAUSTED
                  : Hint.Status.NO_DEDUCTION,
              Optional.empty());
    }
    var trace = engine.nextColor(puzzle.topology(), new PartialColoring(colors));
    if (trace.status() == DeductionTrace.Status.CONTRADICTION)
      return new Hint(level, Hint.Status.CONFLICT, Optional.empty());
    var candidates = candidates(puzzle, trace, level, colors);
    // A one-step explanation cannot be shortened. Avoid broader search for routine moves.
    if (candidates.stream().noneMatch(h -> h.explanation().walkthrough().size() == 1)) {
      var bounded =
          engine.solve(
              puzzle.topology(),
              new PartialColoring(colors),
              new DeductionConfiguration(4, 32, 2000, 256, 24, EffortModel.defaults()));
      if (bounded.status() == DeductionTrace.Status.CONTRADICTION)
        return new Hint(level, Hint.Status.CONFLICT, Optional.empty());
      candidates.addAll(candidates(puzzle, bounded, level, colors));
      // The original clue proof can take a shorter route than the current-state greedy trace.
      // Rebase its outer premises on visible colours; hypothetical scopes remain untouched.
      if (original != null) {
        candidates.addAll(candidates(puzzle, original, level, colors));
      }
    }
    return candidates.stream()
        .min(
            Comparator.comparingInt((Hint h) -> h.explanation().walkthrough().size())
                .thenComparingLong(
                    h ->
                        h.explanation().walkthrough().stream()
                            .filter(w -> w.kind().equals("ASSUMPTION"))
                            .count())
                .thenComparingInt(h -> h.explanation().focusVertices().size())
                .thenComparingInt(h -> h.deduction().orElseThrow().node()))
        .orElseGet(
            () ->
                new Hint(
                    level,
                    trace.status() == DeductionTrace.Status.SOLVED
                        ? Hint.Status.SOLVED
                        : trace.status() == DeductionTrace.Status.BUDGET_EXHAUSTED
                            ? Hint.Status.BUDGET_EXHAUSTED
                            : Hint.Status.NO_DEDUCTION,
                    Optional.empty()));
  }

  private static Hint edgeConflict(Hint.Level level, int a, int b) {
    var vertices = List.of(a, b);
    var explanation =
        new HintExplanation(
            "edge_conflict",
            vertices,
            new LogicalConclusion.Contradiction(),
            vertices,
            List.of(new HintExplanation.FocusEdge(a, b)),
            List.of(),
            null,
            null,
            List.of());
    return new Hint(level, Hint.Status.CONFLICT, Optional.empty(), List.of(), explanation);
  }

  private static ArrayList<Hint> candidates(
      Puzzle puzzle, DeductionTrace trace, Hint.Level level, Map<NodeId, Color> boardColors) {
    var colors = new TreeMap<Integer, Color>();
    boardColors.forEach((node, color) -> colors.put(node.value(), color));
    var steps =
        trace.steps().stream()
            .map(
                d ->
                    established(d.conclusion(), colors)
                        ? new Deduction(
                            d.id(),
                            "current_board",
                            0,
                            d.node(),
                            d.beforeMask(),
                            d.afterMask(),
                            List.of(),
                            d.witnesses(),
                            "current_board",
                            Map.of(),
                            List.of(),
                            d.conclusion(),
                            d.effort(),
                            List.of())
                        : d)
            .toList();
    var result = new ArrayList<Hint>();
    for (var d : steps) {
      if (d.tier() <= 0
          || !(d.conclusion() instanceof LogicalConclusion.NarrowDomain n)
          || Integer.bitCount(n.mask()) != 1
          || colors.containsKey(n.node())) continue;
      var support = ProofCore.slice(steps, d.id());
      result.add(
          new Hint(
              level,
              Hint.Status.AVAILABLE,
              Optional.of(d),
              support,
              HintExplanation.from(puzzle.topology(), d, support)));
    }
    return result;
  }

  private static boolean established(LogicalConclusion conclusion, Map<Integer, Color> colors) {
    return switch (conclusion) {
      case LogicalConclusion.NarrowDomain n ->
          colors.containsKey(n.node()) && (n.mask() & colors.get(n.node()).mask()) != 0;
      case LogicalConclusion.Equal e ->
          colors.containsKey(e.a())
              && colors.containsKey(e.b())
              && colors.get(e.a()) == colors.get(e.b());
      case LogicalConclusion.NotEqual e ->
          colors.containsKey(e.a())
              && colors.containsKey(e.b())
              && colors.get(e.a()) != colors.get(e.b());
      case LogicalConclusion.Contradiction c -> false;
    };
  }
}
