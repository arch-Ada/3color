package io.threecolor.validation;

import io.threecolor.model.*;
import io.threecolor.solve.*;
import java.util.*;

public final class PuzzleValidator {
  public record Failure(String code, String detail) {}

  public record ValidationResult(List<Failure> failures) {
    public ValidationResult {
      failures = List.copyOf(failures);
    }

    public boolean valid() {
      return failures.isEmpty();
    }
  }

  public ValidationResult validate(
      Puzzle p, boolean playable, boolean satisfiable, boolean unique, long budget) {
    var failures = new ArrayList<Failure>();
    var g = p.topology();
    var pts = p.layout().points();
    for (var e : g.edges())
      if (p.givens().colors().containsKey(e.a())
          && p.givens().colors().get(e.a()) == p.givens().colors().get(e.b()))
        failures.add(new Failure("GIVEN_CONFLICT", e.toString()));
    if (playable && GraphMetrics.of(g).connectedComponents() != 1)
      failures.add(new Failure("DISCONNECTED", "Playable graph must be connected"));
    if (pts.size() != g.nodeCount())
      failures.add(new Failure("LAYOUT_COVERAGE", "One point per node required"));
    else if (pts.stream().anyMatch(v -> !Double.isFinite(v.x()) || !Double.isFinite(v.y())))
      failures.add(new Failure("NONFINITE_COORDINATE", "Coordinates must be finite"));
    else {
      for (int i = 0; i < pts.size(); i++)
        for (int j = i + 1; j < pts.size(); j++)
          if (distance(pts.get(i), pts.get(j)) < 1e-16)
            failures.add(new Failure("COINCIDENT_NODES", i + "," + j));
      for (var e : g.edges())
        for (int v = 0; v < g.nodeCount(); v++)
          if (v != e.a().value()
              && v != e.b().value()
              && onSegment(pts.get(e.a().value()), pts.get(e.b().value()), pts.get(v)))
            failures.add(new Failure("NODE_ON_EDGE", v + ":" + e));
      for (int i = 0; i < g.edges().size(); i++)
        for (int j = i + 1; j < g.edges().size(); j++) {
          var a = g.edges().get(i);
          var b = g.edges().get(j);
          if (a.a().equals(b.a())
              || a.a().equals(b.b())
              || a.b().equals(b.a())
              || a.b().equals(b.b())) continue;
          if (intersects(
              pts.get(a.a().value()),
              pts.get(a.b().value()),
              pts.get(b.a().value()),
              pts.get(b.b().value()))) failures.add(new Failure("CROSSING_EDGES", a + ":" + b));
        }
    }
    if (satisfiable || unique) {
      var r = new ExactSolver().uniqueness(g, p.givens(), budget, SolutionEquivalence.LABELED);
      if (r.status() == UniquenessResult.UNSATISFIABLE)
        failures.add(new Failure("UNSATISFIABLE", "No completion"));
      if (r.status() == UniquenessResult.UNKNOWN)
        failures.add(new Failure("BUDGET_EXHAUSTED", "Could not certify"));
      if (unique && r.status() == UniquenessResult.MULTIPLE)
        failures.add(new Failure("MULTIPLE_SOLUTIONS", "Not unique"));
    }
    return new ValidationResult(failures);
  }

  private static double distance(PuzzleLayout.Point a, PuzzleLayout.Point b) {
    return Math.pow(a.x() - b.x(), 2) + Math.pow(a.y() - b.y(), 2);
  }

  private static double cross(PuzzleLayout.Point a, PuzzleLayout.Point b, PuzzleLayout.Point c) {
    return (b.x() - a.x()) * (c.y() - a.y()) - (b.y() - a.y()) * (c.x() - a.x());
  }

  private static boolean onSegment(
      PuzzleLayout.Point a, PuzzleLayout.Point b, PuzzleLayout.Point p) {
    return Math.abs(cross(a, b, p)) < 1e-10
        && p.x() >= Math.min(a.x(), b.x()) - 1e-10
        && p.x() <= Math.max(a.x(), b.x()) + 1e-10
        && p.y() >= Math.min(a.y(), b.y()) - 1e-10
        && p.y() <= Math.max(a.y(), b.y()) + 1e-10;
  }

  public static boolean intersects(
      PuzzleLayout.Point a, PuzzleLayout.Point b, PuzzleLayout.Point c, PuzzleLayout.Point d) {
    double x = cross(a, b, c), y = cross(a, b, d), z = cross(c, d, a), w = cross(c, d, b);
    return x * y < 0 && z * w < 0
        || onSegment(a, b, c)
        || onSegment(a, b, d)
        || onSegment(c, d, a)
        || onSegment(c, d, b);
  }
}
