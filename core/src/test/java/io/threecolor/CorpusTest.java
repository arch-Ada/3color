package io.threecolor;

import static org.junit.jupiter.api.Assertions.*;

import io.threecolor.deduction.*;
import io.threecolor.difficulty.*;
import io.threecolor.model.*;
import io.threecolor.solve.*;
import io.threecolor.validation.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;

class CorpusTest {
  @TestFactory
  Stream<DynamicTest> corpus() throws IOException {
    try (var input = getClass().getResourceAsStream("/corpus/index.txt")) {
      return new String(Objects.requireNonNull(input).readAllBytes(), StandardCharsets.UTF_8)
              .lines()
              .filter(s -> !s.isBlank())
              .map(path -> DynamicTest.dynamicTest(path, () -> check(path)))
              .toList()
              .stream();
    }
  }

  private void check(String path) throws IOException {
    var props = new Properties();
    try (var input = getClass().getResourceAsStream("/corpus/" + path)) {
      props.load(Objects.requireNonNull(input));
    }
    int n = Integer.parseInt(props.getProperty("n"));
    var edges = new ArrayList<Edge>();
    for (String s : props.getProperty("edges", "").split(","))
      if (!s.isBlank()) {
        var parts = s.split("-");
        edges.add(new Edge(Integer.parseInt(parts[0]), Integer.parseInt(parts[1])));
      }
    var givens = new TreeMap<NodeId, Color>();
    for (String s : props.getProperty("givens", "").split(","))
      if (!s.isBlank()) {
        var parts = s.split(":");
        givens.put(new NodeId(Integer.parseInt(parts[0])), Color.valueOf(parts[1]));
      }
    var graph = new GraphTopology(n, edges);
    var partial = new PartialColoring(givens);
    var points = new ArrayList<PuzzleLayout.Point>();
    for (String s : props.getProperty("layout", "").split(","))
      if (!s.isBlank()) {
        var parts = s.split(":");
        points.add(
            new PuzzleLayout.Point(Double.parseDouble(parts[0]), Double.parseDouble(parts[1])));
      }
    var puzzle =
        new Puzzle(
            graph,
            partial,
            RuleSet.CLASSIC_V1,
            PuzzleProvenance.supplied(),
            new PuzzleLayout(points));
    assertEquals(
        props.getProperty("status"),
        new ExactSolver()
            .uniqueness(graph, partial, 100000, SolutionEquivalence.LABELED)
            .status()
            .name());
    if (props.containsKey("hash")) assertEquals(props.getProperty("hash"), puzzle.logicalHash());
    if (props.containsKey("band")) {
      var report = new DifficultyAnalyzer().analyze(new DeductionEngine().solve(graph, partial));
      assertTrue(report.humanSolved());
      if (props.containsKey("difficultyModel"))
        assertEquals(
            props.getProperty("currentDifficultyModel", props.getProperty("difficultyModel")),
            report.modelVersion());
      assertEquals(
          props.getProperty("currentBand", props.getProperty("band")), report.band().name());
    }
    if (props.containsKey("validationFailure"))
      assertTrue(
          new PuzzleValidator()
              .validate(puzzle, false, false, false, 1).failures().stream()
                  .anyMatch(f -> f.code().equals(props.getProperty("validationFailure"))));
    if (props.containsKey("generator"))
      assertTrue(new PuzzleValidator().validate(puzzle, true, true, true, 100000).valid());
  }
}
