package io.threecolor.layout;

import static org.junit.jupiter.api.Assertions.*;

import io.threecolor.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class LayoutQualityTest {
  @Test
  void edgeStatisticsAndEntropyHaveExplicitDefinitions() {
    var graph = new GraphTopology(3, List.of(new Edge(0, 1), new Edge(1, 2)));
    var layout =
        new PuzzleLayout(
            List.of(
                new PuzzleLayout.Point(0, 0),
                new PuzzleLayout.Point(.2, 0),
                new PuzzleLayout.Point(.2, .4)));
    var q = LayoutQuality.measure(graph, layout);
    assertEquals(.3, q.meanEdgeLength(), 1e-12);
    assertEquals(.3, q.medianEdgeLength(), 1e-12);
    assertEquals(.4, q.p90EdgeLength(), 1e-12);
    assertEquals(.4, q.maximumEdgeLength(), 1e-12);
    assertEquals(4. / 3, q.maxMedianEdgeRatio(), 1e-12);
    assertEquals(1. / 3, q.edgeLengthCoefficientOfVariation(), 1e-12);
    assertEquals(Math.log(3) / Math.log(25), q.gridEntropy(), 1e-12);
    assertEquals(90, q.minimumIncidentAngleDegrees(), 1e-12);
    assertEquals(
        q,
        LayoutQuality.measure(
            new GraphTopology(3, List.of(new Edge(1, 2), new Edge(0, 1))), layout));
  }

  @Test
  void emptyAndSingleEdgeStatisticsAreDefined() {
    var points =
        new PuzzleLayout(List.of(new PuzzleLayout.Point(.1, .1), new PuzzleLayout.Point(.4, .5)));
    var empty = LayoutQuality.measure(new GraphTopology(2, List.of()), points);
    assertEquals(0, empty.meanEdgeLength());
    assertEquals(0, empty.p90EdgeLength());
    assertEquals(0, empty.maxMedianEdgeRatio());
    assertEquals(0, empty.edgeLengthCoefficientOfVariation());
    assertEquals(180, empty.minimumIncidentAngleDegrees());
    var single = LayoutQuality.measure(new GraphTopology(2, List.of(new Edge(0, 1))), points);
    assertEquals(.5, single.medianEdgeLength(), 1e-12);
    assertEquals(1, single.maxMedianEdgeRatio());
    assertEquals(0, single.edgeLengthCoefficientOfVariation());
  }
}
