package io.threecolor.model;

public record PuzzleLayout(java.util.List<Point> points) {
  public PuzzleLayout {
    points = java.util.List.copyOf(points);
  }

  public record Point(double x, double y) {}
}
