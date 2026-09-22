package io.threecolor.difficulty;

import io.threecolor.deduction.*;
import java.util.*;

public final class DifficultyAnalyzer {
  public static final String VERSION = "human-profile-v5";
  private final DifficultyCalibration calibration;
  private final Map<DifficultyBand, DifficultyTarget> targets;

  public DifficultyAnalyzer() {
    this(DifficultyCalibration.defaults(), DifficultyTargets.defaults());
  }

  public DifficultyAnalyzer(
      DifficultyCalibration calibration, Map<DifficultyBand, DifficultyTarget> targets) {
    this.calibration = calibration;
    this.targets = Map.copyOf(targets);
    if (this.targets.size() != DifficultyBand.values().length)
      throw new IllegalArgumentException("One target per band required");
  }

  public DifficultyReport analyze(DeductionTrace trace) {
    int[] depths = new int[trace.steps().size()];
    int maxDepth = 0, hardest = 0, count = 0, cost = 0, hypotheses = 0;
    int[] weights = {0, 1, 5, 12, 30};
    var usage = new TreeMap<String, Integer>();
    double initialInformation = 0;
    int[] initial = new int[trace.finalDomains().size()];
    Arrays.fill(initial, 7);
    for (var d : trace.steps()) {
      int parent = 0;
      for (int id : d.premises()) {
        if (id < 0 || id >= d.id()) throw new IllegalArgumentException("Premise must precede fact");
        parent = Math.max(parent, depths[id]);
      }
      depths[d.id()] = parent + (d.tier() > 0 ? 1 : 0);
      maxDepth = Math.max(maxDepth, depths[d.id()]);
      if (d.tier() == 0 && d.conclusion() instanceof LogicalConclusion.NarrowDomain domain)
        initial[domain.node()] = domain.mask();
      if (d.tier() > 0) {
        count++;
        hardest = Math.max(hardest, d.tier());
        cost += weights[d.tier()];
        usage.merge(d.ruleId(), 1, Integer::sum);
        if (d.ruleId().equals("bounded_contradiction")) hypotheses++;
      }
    }
    for (int mask : initial)
      if (mask != 0) initialInformation += Math.log(Integer.bitCount(mask)) / Math.log(2);
    var events = trace.events();
    double total = events.stream().mapToDouble(DeductionEvent::informationGain).sum();
    var efforts = events.stream().map(e -> e.rootEffort().score()).toList();
    double effortTotal = efforts.stream().mapToDouble(Double::doubleValue).sum();
    double maximum = efforts.stream().mapToDouble(Double::doubleValue).max().orElse(0),
        progress = 0,
        nontrivial = 0,
        trivialRun = 0,
        longest = 0;
    int substantial = 0, hard = 0, advanced = 0, relations = 0, unlocks = 0;
    double openingCheapInformation = 0, largestCascade = 0;
    boolean opening = true;
    int[] regions = new int[calibration.progressRegions()];
    var timeline = new ArrayList<DifficultyProfile.EventProgress>();
    for (var event : events) {
      double effort = event.rootEffort().score(), start = total == 0 ? 0 : progress / total;
      progress += event.informationGain();
      double end = total == 0 ? 0 : progress / total;
      boolean significant = effort >= calibration.substantialEffort();
      // Tier 1 is exhausted before any non-cheap move in engine traces. Its initial
      // prefix is exactly the cheap fixed point, without running a second solver.
      int rootTier = trace.steps().get(event.rootFactId()).tier();
      if (rootTier > 1) {
        opening = false;
        unlocks++;
      }
      if (opening) openingCheapInformation += event.informationGain();
      largestCascade = Math.max(largestCascade, event.cascadeInformation());
      if (significant) {
        substantial++;
        nontrivial += event.rootInformation();
        longest = Math.max(longest, trivialRun);
        trivialRun = event.cascadeInformation();
        regions[Math.min(regions.length - 1, (int) (start * regions.length))]++;
      } else trivialRun += event.informationGain();
      if (effort >= calibration.hardEffort()) hard++;
      if (event.ruleId().equals("bounded_contradiction")
          || event.ruleId().equals("implication_chain")) advanced++;
      relations += event.relationFacts();
      timeline.add(
          new DifficultyProfile.EventProgress(
              event.rootFactId(),
              event.ruleId(),
              start,
              end,
              effort,
              event.informationGain(),
              event.propagationFacts().size(),
              event.relationFacts(),
              event.rootInformation(),
              event.cascadeInformation()));
    }
    longest = Math.max(longest, trivialRun);
    double average =
        trace.availableDeductions().stream().mapToInt(Integer::intValue).average().orElse(0);
    int minimum = trace.availableDeductions().stream().mapToInt(Integer::intValue).min().orElse(0);
    // One sample per recorded atomic decision state, including mechanical propagation.
    // No frontier enumeration or extra solve: count canonical conclusions already recorded.
    var cheapBreadths =
        trace.frontiers().stream()
            .map(
                f ->
                    (int)
                        f.efforts().stream()
                            .filter(e -> e < calibration.substantialEffort())
                            .count())
            .toList();
    var profile =
        new DifficultyProfile(
            total,
            initialInformation,
            initialInformation == 0 ? 1 : Math.min(1, total / initialInformation),
            events.size(),
            efforts,
            percentile(efforts, .5),
            percentile(efforts, .75),
            percentile(efforts, .9),
            maximum,
            substantial,
            hard,
            advanced,
            maxDepth,
            usage,
            usage.size(),
            average,
            minimum,
            trace.frontiers().stream().map(FrontierSummary::easiestEffort).toList(),
            total == 0 ? 0 : nontrivial / total,
            total == 0 ? 0 : longest / total,
            Arrays.stream(regions).boxed().toList(),
            (int) Arrays.stream(regions).filter(n -> n > 0).count(),
            effortTotal == 0 ? 0 : maximum / effortTotal,
            hypotheses,
            trace.hypothesesTried(),
            relations,
            timeline,
            initialInformation == 0 ? 1 : Math.min(1, openingCheapInformation / initialInformation),
            initialInformation == 0
                ? 0
                : Math.max(0, 1 - openingCheapInformation / initialInformation),
            unlocks,
            events.isEmpty() ? 0 : (double) substantial / events.size(),
            total == 0 ? 0 : largestCascade / total,
            cheapBreadths,
            cheapBreadths.stream().mapToInt(Integer::intValue).average().orElse(0),
            percentile(cheapBreadths.stream().map(Integer::doubleValue).toList(), .75),
            cheapBreadths.isEmpty()
                ? 0
                : (double)
                        cheapBreadths.stream()
                            .filter(n -> n >= calibration.crowdedCheapFrontierBreadth())
                            .count()
                    / cheapBreadths.size(),
            SubstantialCausality.inspect(trace, calibration.substantialEffort()).maximumDepth(),
            hardest);
    return new DifficultyReport(
        VERSION,
        DifficultyTargets.classify(profile, targets),
        effortTotal,
        count,
        hardest,
        usage,
        cost,
        maxDepth,
        hypotheses,
        average,
        minimum,
        trace.status() == DeductionTrace.Status.SOLVED,
        profile);
  }

  private static double percentile(List<Double> values, double p) {
    if (values.isEmpty()) return 0;
    var sorted = values.stream().sorted().toList();
    double index = (sorted.size() - 1) * p;
    int lo = (int) index, hi = (int) Math.ceil(index);
    return sorted.get(lo) + (sorted.get(hi) - sorted.get(lo)) * (index - lo);
  }
}
