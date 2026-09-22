package io.threecolor;

import static org.junit.jupiter.api.Assertions.*;

import io.threecolor.generation.*;
import io.threecolor.model.*;
import io.threecolor.solve.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class GraphNeighbourhoodTest {
  @Test
  void vertexEditsAreDeterministicConnectedPlanarAndWithinRange() {
    var source = VariedPlaneGraphs.create(24, 42);
    var variants = GraphNeighbourhood.variants(source, 21, 27, 17);
    var repeated = GraphNeighbourhood.variants(source, 21, 27, 17);
    assertTrue(variants.stream().anyMatch(v -> v.candidate().graph().nodeCount() < 24));
    assertTrue(variants.stream().anyMatch(v -> v.candidate().graph().nodeCount() > 24));
    assertEquals(variants.size(), repeated.size());
    for (int i = 0; i < variants.size(); i++) {
      var a = variants.get(i).candidate();
      var b = repeated.get(i).candidate();
      assertTrue(a.graph().nodeCount() >= 21 && a.graph().nodeCount() <= 27);
      assertTrue(GraphNeighbourhood.valid(a));
      assertEquals(a.graph().edges(), b.graph().edges());
      assertEquals(a.layout(), b.layout());
    }
    assertEquals(1, GraphNeighbourhood.variants(source, 24, 24, 17).size());
  }

  @Test
  void parentsOutsideBothBoundariesCanEnterRangesAndExactCounts() {
    for (int[] sizes :
        new int[][] {{23, 24, 33}, {43, 44, 53}, {21, 24, 24}, {27, 24, 24}, {36, 24, 33}}) {
      var variants =
          GraphNeighbourhood.variants(
              VariedPlaneGraphs.create(sizes[0], 42), sizes[1], sizes[2], 17);
      assertFalse(variants.isEmpty(), Arrays.toString(sizes));
      for (var v : variants) {
        assertTrue(v.candidate().graph().nodeCount() >= sizes[1]);
        assertTrue(v.candidate().graph().nodeCount() <= sizes[2]);
        assertTrue(GraphNeighbourhood.valid(v.candidate()));
        assertNotEquals("original", v.edit());
      }
    }
  }

  @Test
  void editsRespectAbsoluteLimitsAndReportNoUsableEdit() {
    for (int n : new int[] {4, 53})
      for (var v : GraphNeighbourhood.variants(VariedPlaneGraphs.create(n, 42), 4, 53, 17)) {
        assertTrue(
            v.candidate().graph().nodeCount() >= 4 && v.candidate().graph().nodeCount() <= 53);
        assertTrue(GraphNeighbourhood.valid(v.candidate()));
      }
    var noEdges =
        new PlaneTriangulation(
            new GraphTopology(4, List.of()),
            new PuzzleLayout(
                List.of(
                    new PuzzleLayout.Point(.1, .1),
                    new PuzzleLayout.Point(.9, .1),
                    new PuzzleLayout.Point(.9, .9),
                    new PuzzleLayout.Point(.1, .9))));
    assertTrue(GraphNeighbourhood.variants(noEdges, 5, 5, 0).isEmpty());
    assertTrue(GraphNeighbourhood.variants(VariedPlaneGraphs.create(16, 42), 24, 24, 17).isEmpty());
    assertThrows(
        IllegalArgumentException.class, () -> GraphNeighbourhood.variants(noEdges, 3, 53, 0));
    assertThrows(
        IllegalArgumentException.class, () -> GraphNeighbourhood.variants(noEdges, 4, 54, 0));
  }

  @Test
  void definingSetsRetainHighVertexBitsAtLargestSize() {
    var graph = new GraphTopology(53, List.of(new Edge(51, 52)));
    var colors = new ArrayList<>(Collections.nCopies(53, Color.RED));
    colors.set(52, Color.BLUE);
    var target = new Coloring(colors);
    var components = DefiningSets.kempeComponents(graph, target);
    assertTrue(components.stream().anyMatch(mask -> (mask & (1L << 52)) != 0));
    var changed = new ArrayList<>(colors);
    changed.set(52, Color.GREEN);
    assertEquals(1L << 52, DefiningSets.disagreement(target, new Coloring(changed)));
  }
}
