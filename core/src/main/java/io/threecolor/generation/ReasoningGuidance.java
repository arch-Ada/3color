package io.threecolor.generation;

import io.threecolor.deduction.*;
import io.threecolor.difficulty.*;
import io.threecolor.model.*;
import java.util.*;

/** Bounded proposal heuristics, never a substitute for solving or target comparison. */
public final class ReasoningGuidance {
  public static final int MAX_EVIDENCE_FACTS = 256;

  public record Deficit(
      ProfileMetric metric, boolean constraint, boolean increase, double penalty) {}

  public record Advice(
      Deficit deficit,
      List<Integer> evidenceFacts,
      Set<Integer> protectedNodes,
      List<NodeId> removals,
      List<NodeId> restorations,
      boolean restoreFirst) {
    public Advice {
      evidenceFacts = List.copyOf(evidenceFacts);
      protectedNodes = Collections.unmodifiableSet(new TreeSet<>(protectedNodes));
      removals = List.copyOf(removals);
      restorations = List.copyOf(restorations);
    }
  }

  public Deficit dominant(EvaluatedCandidate candidate, DifficultyTarget target) {
    var match = candidate.match();
    var entry = largest(match.penalties());
    boolean constraint = entry != null;
    if (entry == null) entry = largest(match.preferencePenalties());
    if (entry == null) return null;
    var metric = entry.getKey();
    double value = metric.value(candidate.report().profile());
    boolean increase =
        constraint
            ? value < target.criteria().get(metric).minimum()
            : value < target.preferences().get(metric).ideal();
    return new Deficit(metric, constraint, increase, entry.getValue());
  }

  private static Map.Entry<ProfileMetric, Double> largest(Map<ProfileMetric, Double> penalties) {
    Map.Entry<ProfileMetric, Double> best = null;
    // Enum order is the explicit tie break, irrespective of caller map iteration order.
    for (var entry : new TreeMap<>(penalties).entrySet())
      if (entry.getValue() > 0 && (best == null || entry.getValue() > best.getValue()))
        best = entry;
    return best;
  }

  public Advice inspect(
      EvaluatedCandidate candidate, DifficultyTarget target, GraphTopology graph) {
    var deficit = dominant(candidate, target);
    var profile = candidate.report().profile();
    var trace = candidate.trace();
    var facts = new TreeMap<Integer, Deduction>();
    trace.steps().forEach(f -> facts.put(f.id(), f));
    var protectedNodes = new TreeSet<Integer>();
    double substantial = DifficultyCalibration.defaults().substantialEffort();
    for (var event : trace.events())
      if (event.rootEffort().score() >= substantial)
        addNodes(facts.get(event.rootFactId()), protectedNodes);

    var starts = new ArrayList<Integer>();
    ProfileMetric metric = deficit == null ? null : deficit.metric();
    boolean pressure =
        metric == ProfileMetric.MEAN_CHEAP_FRONTIER
            || metric == ProfileMetric.P75_CHEAP_FRONTIER
            || metric == ProfileMetric.CROWDED_CHEAP_FRONTIER_FRACTION
            || metric == ProfileMetric.FRONTIER_MEAN;
    if (pressure) {
      int best = -1, breadth = -1;
      for (int i = 0; i < trace.frontiers().size(); i++) {
        // Official v4 breadth distinguishes cheap alternatives from merely large frontiers.
        int count =
            i < profile.cheapFrontierBreadths().size()
                ? profile.cheapFrontierBreadths().get(i)
                : (int)
                    trace.frontiers().get(i).efforts().stream()
                        .filter(e -> e < substantial)
                        .count();
        if (count > breadth) {
          breadth = count;
          best = i;
        }
      }
      if (best >= 0) starts.add(trace.frontiers().get(best).selectedFactId());
    }
    Comparator<DeductionEvent> focusOrder;
    if (metric == ProfileMetric.CASCADE_SHARE
        || metric == ProfileMetric.TRIVIAL_STRETCH
        || pressure) {
      focusOrder =
          Comparator.comparingDouble(
                  (DeductionEvent e) ->
                      e.cascadeInformation()
                          + (e.rootEffort().score() < substantial ? e.rootInformation() : 0))
              .reversed();
    } else if (metric == ProfileMetric.LARGEST_EVENT_SHARE
        || deficit != null && !deficit.increase()) {
      focusOrder =
          Comparator.comparingDouble((DeductionEvent e) -> e.rootEffort().score()).reversed();
    } else {
      // Additional reasoning: cheap work first, least active progress region next, then
      // information.
      var activity = new TreeMap<Integer, Integer>();
      int regions = profile.substantialEventsByRegion().size();
      if (regions > 0)
        for (var progress : profile.timeline()) {
          int region = Math.min(regions - 1, (int) (progress.progressStart() * regions));
          activity.put(progress.rootFactId(), profile.substantialEventsByRegion().get(region));
        }
      focusOrder =
          Comparator.comparingInt(
                  (DeductionEvent e) -> e.rootEffort().score() < substantial ? 0 : 1)
              .thenComparingInt(e -> activity.getOrDefault(e.rootFactId(), 0))
              .thenComparing(
                  Comparator.comparingDouble(DeductionEvent::informationGain).reversed());
    }
    var chosen =
        trace.events().stream()
            .min(focusOrder.thenComparingInt(DeductionEvent::rootFactId))
            .orElse(null);
    if (chosen != null) {
      starts.add(chosen.rootFactId());
      starts.addAll(chosen.directFacts());
      starts.addAll(chosen.propagationFacts());
    }
    var evidence = new TreeSet<Integer>();
    var local = new TreeSet<Integer>();
    var supports = new TreeSet<Integer>();
    var queue = new ArrayDeque<Integer>(starts);
    while (!queue.isEmpty() && evidence.size() < MAX_EVIDENCE_FACTS) {
      int id = queue.removeFirst();
      if (!evidence.add(id)) continue;
      var fact = facts.get(id);
      if (fact == null) continue;
      addNodes(fact, local);
      if ("given".equals(fact.ruleId())) addNodes(fact, supports);
      queue.addAll(fact.premises());
    }
    var nearby = new TreeSet<Integer>(local);
    for (int node : local)
      if (node >= 0 && node < graph.nodeCount())
        for (int neighbor : graph.neighbors(node)) nearby.add(neighbor);
    boolean protect = deficit != null && deficit.increase() && !pressure;
    Comparator<NodeId> order =
        Comparator.comparingInt((NodeId n) -> protect && protectedNodes.contains(n.value()) ? 1 : 0)
            .thenComparingInt(n -> supports.contains(n.value()) ? 0 : 1)
            .thenComparingInt(n -> local.contains(n.value()) ? 0 : 1)
            .thenComparingInt(n -> nearby.contains(n.value()) ? 0 : 1)
            .thenComparingInt(NodeId::value);
    var removals = new ArrayList<NodeId>();
    var restorations = new ArrayList<NodeId>();
    for (int v = 0; v < graph.nodeCount(); v++) {
      var node = new NodeId(v);
      (candidate.clues().containsKey(node) ? removals : restorations).add(node);
    }
    removals.sort(order);
    restorations.sort(order);
    boolean restoreFirst =
        deficit != null
            && (pressure ? deficit.increase() : !deficit.increase())
            && metric != ProfileMetric.CASCADE_SHARE
            && metric != ProfileMetric.TRIVIAL_STRETCH;
    return new Advice(
        deficit, new ArrayList<>(evidence), protectedNodes, removals, restorations, restoreFirst);
  }

  private static void addNodes(Deduction fact, Set<Integer> nodes) {
    if (fact == null) return;
    if (fact.node() >= 0) nodes.add(fact.node());
    nodes.addAll(fact.witnesses());
    if (fact.conclusion() instanceof LogicalConclusion.Equal e) {
      nodes.add(e.a());
      nodes.add(e.b());
    }
    if (fact.conclusion() instanceof LogicalConclusion.NotEqual e) {
      nodes.add(e.a());
      nodes.add(e.b());
    }
  }
}
