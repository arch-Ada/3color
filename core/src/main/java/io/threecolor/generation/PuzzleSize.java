package io.threecolor.generation;

public enum PuzzleSize {
  MINI(4, 13),
  SMALL(14, 23),
  MEDIUM(24, 33),
  LARGE(34, 43),
  VERY_LARGE(44, 53);
  private final int minimum, maximum;

  PuzzleSize(int minimum, int maximum) {
    this.minimum = minimum;
    this.maximum = maximum;
  }

  public int minimum() {
    return minimum;
  }

  public int maximum() {
    return maximum;
  }

  public boolean contains(int n) {
    return n >= minimum && n <= maximum;
  }
}
