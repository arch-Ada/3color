package io.threecolor.generation;

import io.threecolor.model.*;
import java.util.*;

/** Preserved shuffled removal + random beam remove/restore/swap baseline. */
public final class RandomBeamClueSearch implements ClueSearchStrategy {
  public GenerationStrategy id() {
    return GenerationStrategy.RANDOM_BEAM;
  }

  public EvaluatedCandidate search(CandidateVerifier verifier, Random random) {
    var candidate = verifier.topology();
    var spec = verifier.spec();
    var budget = verifier.budget();
    var work = verifier.work();
    var clues = new TreeMap<NodeId, Color>();
    for (int v = 0; v < spec.nodeCount(); v++)
      clues.put(new NodeId(v), candidate.solution().colors().get(v));
    EvaluatedCandidate current = verifier.evaluate(clues), best = null;
    if (current == null) return null;
    var beam = new ArrayList<EvaluatedCandidate>();
    beam.add(current);
    var order = new ArrayList<>(clues.keySet());
    Collections.shuffle(order, random);
    // A deterministic removal pass supplies diverse starting states. Keep useful intermediate
    // states.
    for (var node : order) {
      if (work.exhausted()) break;
      var proposed = new TreeMap<>(current.clues());
      proposed.remove(node);
      work.removals++;
      var evaluated = verifier.evaluate(proposed);
      if (evaluated != null) {
        current = evaluated;
        ClueSearchStrategy.retain(
            beam, evaluated, budget.beamWidth(), EvaluatedCandidate.RANDOM_BEAM_ORDER);
        if (verifier.acceptable(evaluated)
            && (best == null || EvaluatedCandidate.RANDOM_BEAM_ORDER.compare(evaluated, best) < 0))
          best = evaluated;
      }
    }
    // Seeded beam proposals can restore a clue or swap its location, escaping monotonic removal.
    for (int mutation = 0;
        mutation < budget.localMutationAttempts() && !work.exhausted() && !beam.isEmpty();
        mutation++) {
      work.localProposals++;
      var base = beam.get(random.nextInt(beam.size()));
      var proposed = new TreeMap<>(base.clues());
      var present = new ArrayList<>(proposed.keySet());
      var absent = new ArrayList<NodeId>();
      for (int v = 0; v < spec.nodeCount(); v++)
        if (!proposed.containsKey(new NodeId(v))) absent.add(new NodeId(v));
      int kind = mutation % 3;
      if (kind == 0 && !present.isEmpty()) {
        proposed.remove(present.get(random.nextInt(present.size())));
        work.removals++;
      } else if (kind == 1 && !absent.isEmpty()) {
        var node = absent.get(random.nextInt(absent.size()));
        proposed.put(node, candidate.solution().colors().get(node.value()));
        work.restorations++;
      } else if (kind == 2 && !present.isEmpty() && !absent.isEmpty()) {
        proposed.remove(present.get(random.nextInt(present.size())));
        var node = absent.get(random.nextInt(absent.size()));
        proposed.put(node, candidate.solution().colors().get(node.value()));
        work.swaps++;
      } else continue;
      var evaluated = verifier.evaluate(proposed);
      if (evaluated != null) {
        ClueSearchStrategy.retain(
            beam, evaluated, budget.beamWidth(), EvaluatedCandidate.RANDOM_BEAM_ORDER);
        if (verifier.acceptable(evaluated)
            && (best == null || EvaluatedCandidate.RANDOM_BEAM_ORDER.compare(evaluated, best) < 0))
          best = evaluated;
      }
    }
    return best;
  }
}
