package io.threecolor.generation;

import io.threecolor.difficulty.*;
import io.threecolor.model.*;
import java.util.*;

/** Shared exact/human/target authority and per-topology clue cache. No strategy-specific solver. */
public final class CandidateVerifier {
  private final TopologyCandidate topology;
  private final GenerationSpec spec;
  private final DifficultyTarget target;
  private final SearchWork work;
  private final Map<String, EvaluatedCandidate> cache = new HashMap<>();
  private final Set<String> invalid = new HashSet<>();

  public CandidateVerifier(
      TopologyCandidate topology,
      GenerationSpec spec,
      GenerationBudget budget,
      DifficultyTarget target,
      GenerationStrategy strategy) {
    this(topology, spec, target, new SearchWork(budget));
    work.context =
        new GenerationContext(
            SparseGridGenerator.VERSION,
            strategy,
            spec,
            budget,
            target,
            topology.attemptIndex(),
            topology.identity(),
            topology.topologyStrategy(),
            topology.structuralHash());
  }

  CandidateVerifier(
      TopologyCandidate topology, GenerationSpec spec, DifficultyTarget target, SearchWork work) {
    if (target.band() != spec.targetDifficulty()
        || topology.graph().nodeCount() != spec.nodeCount())
      throw new IllegalArgumentException("Candidate/spec/target mismatch");
    this.topology = topology;
    this.spec = spec;
    this.target = target;
    this.work = work;
  }

  public TopologyCandidate topology() {
    return topology;
  }

  public GenerationSpec spec() {
    return spec;
  }

  public DifficultyTarget target() {
    return target;
  }

  public GenerationBudget budget() {
    return work.budget;
  }

  SearchWork work() {
    return work;
  }

  public EvaluatedCandidate closest() {
    return work.best;
  }

  public GenerationQuality quality(EvaluatedCandidate candidate) {
    return new GenerationQuality(
        candidate == null ? null : candidate.match(),
        work.evaluations,
        work.removals,
        work.restorations,
        work.swaps,
        work.nodes,
        work.localProposals,
        work.context);
  }

  public boolean acceptable(EvaluatedCandidate e) {
    return e != null
        && e.certified()
        && e.report().humanSolved()
        && e.match().accepted()
        && e.report().band() == spec.targetDifficulty();
  }

  public EvaluatedCandidate evaluate(SortedMap<NodeId, Color> clues) {
    var canonical = new TreeMap<NodeId, Color>();
    canonical.putAll(clues);
    clues = canonical;
    String key = clues.toString();
    if (cache.containsKey(key)) return cache.get(key);
    if (invalid.contains(key) || work.exhausted()) return null;
    if (new HashSet<>(clues.values()).size() < 2) return null;
    work.evaluations++;
    var evaluated =
        new ClueEvaluator()
            .evaluate(
                topology.graph(),
                new PartialColoring(clues),
                Math.min(
                    work.budget.searchNodesPerCheck(),
                    work.budget.totalSearchNodes() - work.nodes));
    work.nodes += evaluated.exact().stats().searchNodes();
    if (!evaluated.certified()
        || spec.requireHumanSolvable() && !evaluated.difficulty().humanSolved()) {
      invalid.add(key);
      return null;
    }
    var result =
        new EvaluatedCandidate(
            clues,
            evaluated.trace(),
            evaluated.difficulty(),
            target.compare(evaluated.difficulty().profile()),
            key,
            evaluated.exact().status());
    cache.put(key, result);
    if (work.best == null || EvaluatedCandidate.RANDOM_BEAM_ORDER.compare(result, work.best) < 0) {
      work.best = result;
      work.bestContext = work.context;
    }
    return result;
  }
}
