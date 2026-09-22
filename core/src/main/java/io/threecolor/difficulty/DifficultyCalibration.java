package io.threecolor.difficulty;

/** Named provisional cutoffs; raw effort measurements remain available for recalibration. */
public record DifficultyCalibration(
    double substantialEffort,
    double hardEffort,
    int progressRegions,
    int crowdedCheapFrontierBreadth) {
  public DifficultyCalibration {
    if (substantialEffort <= 1
        || hardEffort < substantialEffort
        || progressRegions < 1
        || crowdedCheapFrontierBreadth < 1
        || !Double.isFinite(substantialEffort)
        || !Double.isFinite(hardEffort)) throw new IllegalArgumentException();
  }

  public DifficultyCalibration(double substantialEffort, double hardEffort, int progressRegions) {
    this(substantialEffort, hardEffort, progressRegions, 4);
  }

  public static DifficultyCalibration defaults() {
    return new DifficultyCalibration(3, 5, 5);
  }
}
