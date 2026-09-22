package io.threecolor.difficulty;

import static io.threecolor.difficulty.DifficultyTarget.Range.*;
import static io.threecolor.difficulty.ProfileMetric.*;

import java.util.*;

/** Provisional pacing policy. Version alongside the effort model, not as mathematical truth. */
public final class DifficultyTargets {
  private DifficultyTargets() {}

  public static Map<DifficultyBand, DifficultyTarget> defaults() {
    var targets = new EnumMap<DifficultyBand, DifficultyTarget>(DifficultyBand.class);
    targets.put(
        DifficultyBand.VERY_EASY,
        new DifficultyTarget(
            DifficultyBand.VERY_EASY,
            Map.of(
                SUBSTANTIAL_EVENTS,
                atMost(0),
                PROOF_DEPTH,
                atMost(6),
                INFORMATION,
                atMost(20),
                EVENTS,
                atMost(8))));
    targets.put(
        DifficultyBand.EASY,
        new DifficultyTarget(
            DifficultyBand.EASY,
            Map.of(
                INFORMATION,
                atLeast(8),
                SUBSTANTIAL_EVENTS,
                atMost(2),
                SUBSTANTIAL_CAUSAL_DEPTH,
                atMost(1),
                MAXIMUM_RULE_TIER,
                atMost(2))));
    targets.put(
        DifficultyBand.MEDIUM,
        new DifficultyTarget(
            DifficultyBand.MEDIUM,
            Map.of(
                SUBSTANTIAL_CAUSAL_DEPTH,
                atLeast(2),
                MAXIMUM_RULE_TIER,
                atMost(2),
                SUBSTANTIAL_EVENTS,
                atLeast(2),
                ACTIVE_REGIONS,
                atLeast(2),
                SUBSTANTIAL_ROOT_FRACTION,
                atLeast(.15),
                UNLOCKS,
                atLeast(2),
                CASCADE_SHARE,
                atMost(.5)))); // Provisional: one cheap avalanche must not consume a majority.
    // Conservative v3 sustained-reasoning gates. Relation roots need not gain domain bits.
    targets.put(
        DifficultyBand.HARD,
        new DifficultyTarget(
            DifficultyBand.HARD,
            Map.ofEntries(
                Map.entry(SUBSTANTIAL_EVENTS, atLeast(3)),
                Map.entry(HARD_EVENTS, atLeast(3)),
                Map.entry(ACTIVE_REGIONS, atLeast(3)),
                Map.entry(TRIVIAL_STRETCH, atMost(.65)),
                Map.entry(LARGEST_EVENT_SHARE, atMost(.65)),
                Map.entry(CHEAP_CORE, atLeast(.5)),
                Map.entry(UNLOCKS, atLeast(4)),
                Map.entry(SUBSTANTIAL_ROOT_FRACTION, atLeast(.45)),
                Map.entry(P75_EFFORT, atLeast(3)),
                Map.entry(CASCADE_SHARE, atMost(.45)))));
    targets.put(
        DifficultyBand.EXPERT,
        new DifficultyTarget(
            DifficultyBand.EXPERT,
            Map.ofEntries(
                Map.entry(SUBSTANTIAL_EVENTS, atLeast(4)),
                Map.entry(HARD_EVENTS, atLeast(4)),
                Map.entry(ADVANCED_EVENTS, atLeast(1)),
                Map.entry(ACTIVE_REGIONS, atLeast(3)),
                Map.entry(TRIVIAL_STRETCH, atMost(.5)),
                Map.entry(LARGEST_EVENT_SHARE, atMost(.5)),
                Map.entry(CHEAP_CORE, atLeast(.65)),
                Map.entry(UNLOCKS, atLeast(5)),
                Map.entry(SUBSTANTIAL_ROOT_FRACTION, atLeast(.6)),
                Map.entry(P75_EFFORT, atLeast(5)),
                Map.entry(CASCADE_SHARE, atMost(.35)))));
    for (var band : DifficultyBand.values()) {
      var preferred = new EnumMap<ProfileMetric, DifficultyTarget.Preference>(ProfileMetric.class);
      if (band.ordinal() >= DifficultyBand.MEDIUM.ordinal()) {
        preferred.put(
            ACTIVE_REGIONS,
            new DifficultyTarget.Preference(band == DifficultyBand.MEDIUM ? 3 : 4, 5, 2));
        preferred.put(CASCADE_SHARE, new DifficultyTarget.Preference(.15, 1, 2));
        preferred.put(CHEAP_CORE, new DifficultyTarget.Preference(.75, 1, 1));
        preferred.put(TRIVIAL_STRETCH, new DifficultyTarget.Preference(.2, 1, 2));
        preferred.put(LARGEST_EVENT_SHARE, new DifficultyTarget.Preference(.25, 1, 1));
        preferred.put(RULE_DIVERSITY, new DifficultyTarget.Preference(4, 4, 1));

      } else
        preferred.put(
            INFORMATION,
            new DifficultyTarget.Preference(band == DifficultyBand.VERY_EASY ? 10 : 20, 20, 1));
      // Moderate parallelism, not a forced unique move. No breadth penalty for Very Easy.
      // Total frontier size remains diagnostic; expensive alternatives are not cheap clutter.
      if (band != DifficultyBand.VERY_EASY) {
        double weight = band == DifficultyBand.EASY ? 1 : 2;
        preferred.put(MEAN_CHEAP_FRONTIER, new DifficultyTarget.Preference(3, 4, weight));
        preferred.put(
            CROWDED_CHEAP_FRONTIER_FRACTION, new DifficultyTarget.Preference(.25, 1, weight));
        if (band == DifficultyBand.EASY)
          preferred.put(CASCADE_SHARE, new DifficultyTarget.Preference(.15, 1, 1));
      }
      var target = targets.get(band);
      targets.put(band, new DifficultyTarget(band, target.criteria(), preferred));
    }
    return Collections.unmodifiableMap(targets);
  }

  public static DifficultyBand classify(
      DifficultyProfile p, Map<DifficultyBand, DifficultyTarget> targets) {
    for (var band : List.of(DifficultyBand.EXPERT, DifficultyBand.HARD, DifficultyBand.MEDIUM))
      if (targets.get(band).compare(p).accepted()) return band;
    // A Tier-2 chain is not Easy even when its pacing fails Medium publication gates.
    // Match.accepted remains the authority for generation, not this fallback label.
    if (p.maximumRuleTier() <= 2 && p.substantialCausalDepth() >= 2) return DifficultyBand.MEDIUM;
    return targets.get(DifficultyBand.VERY_EASY).compare(p).accepted()
        ? DifficultyBand.VERY_EASY
        : DifficultyBand.EASY;
  }
}
