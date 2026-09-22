package io.threecolor.difficulty;

import java.util.*;

/** Inspectable weighted distance to named ranges. Zero means every pacing constraint is met. */
public record DifficultyTarget(
    DifficultyBand band,
    Map<ProfileMetric, Range> criteria,
    Map<ProfileMetric, Preference> preferences) {
  public record Range(double minimum, double maximum, double weight) {
    public Range {
      if (Double.isNaN(minimum)
          || Double.isNaN(maximum)
          || minimum > maximum
          || !Double.isFinite(weight)
          || weight <= 0) throw new IllegalArgumentException();
    }

    public static Range atLeast(double minimum) {
      return new Range(minimum, Double.POSITIVE_INFINITY, 1);
    }

    public static Range atMost(double maximum) {
      return new Range(0, maximum, 1);
    }

    public double distance(double value) {
      if (value < minimum) return weight * (minimum - value) / Math.max(1, minimum);
      if (value > maximum) return weight * (value - maximum) / Math.max(1, maximum);
      return 0;
    }
  }

  public record Preference(double ideal, double scale, double weight) {
    public Preference {
      if (!Double.isFinite(ideal)
          || !Double.isFinite(scale)
          || scale <= 0
          || !Double.isFinite(weight)
          || weight <= 0) throw new IllegalArgumentException();
    }

    public double penalty(double value) {
      return weight * Math.abs(value - ideal) / scale;
    }
  }

  public record Match(
      double distance,
      double constraintDistance,
      Map<ProfileMetric, Double> penalties,
      Map<ProfileMetric, Double> preferencePenalties) {
    public Match {
      penalties = Collections.unmodifiableMap(new TreeMap<>(penalties));
      preferencePenalties = Collections.unmodifiableMap(new TreeMap<>(preferencePenalties));
    }

    public boolean accepted() {
      return constraintDistance <= 1e-9;
    }
  }

  public DifficultyTarget(DifficultyBand band, Map<ProfileMetric, Range> criteria) {
    this(band, criteria, Map.of());
  }

  public DifficultyTarget {
    criteria = Collections.unmodifiableMap(new TreeMap<>(criteria));
    preferences = Collections.unmodifiableMap(new TreeMap<>(preferences));
  }

  public Match compare(DifficultyProfile profile) {
    var penalties = new TreeMap<ProfileMetric, Double>();
    criteria.forEach(
        (metric, range) -> penalties.put(metric, range.distance(metric.value(profile))));
    var preferred = new TreeMap<ProfileMetric, Double>();
    preferences.forEach(
        (metric, pref) -> preferred.put(metric, pref.penalty(metric.value(profile))));
    double violations = penalties.values().stream().mapToDouble(Double::doubleValue).sum();
    return new Match(
        100 * violations + preferred.values().stream().mapToDouble(Double::doubleValue).sum(),
        violations,
        penalties,
        preferred);
  }
}
