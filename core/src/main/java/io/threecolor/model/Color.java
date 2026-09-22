package io.threecolor.model;

public enum Color {
  RED(1),
  GREEN(2),
  BLUE(4);
  private final int mask;

  Color(int mask) {
    this.mask = mask;
  }

  public int mask() {
    return mask;
  }

  public static Color fromMask(int mask) {
    return switch (mask) {
      case 1 -> RED;
      case 2 -> GREEN;
      case 4 -> BLUE;
      default -> throw new IllegalArgumentException("Not a singleton: " + mask);
    };
  }
}
