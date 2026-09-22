package io.threecolor.generation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SourceScheduleTest {
  @Test
  void invalidAndDuplicateVariantsDoNotConsumeUsableSlots() {
    var first = SearchContinuationTest.source();
    var parent = first.candidate();
    var edges = new ArrayList<>(parent.graph().edges());
    edges.removeFirst();
    var second =
        new CriticalCatalog.Entry(
            "second",
            new PlaneTriangulation(
                new io.threecolor.model.GraphTopology(4, edges), parent.layout()));
    var invalid =
        new CriticalCatalog.Entry(
            "invalid",
            new PlaneTriangulation(
                new io.threecolor.model.GraphTopology(4, List.of()), parent.layout()));
    var sources = List.of(first, first, invalid, second).iterator();
    var seen = new HashSet<String>();
    var work = new GenerationWork();
    assertEquals(first, SourceSchedule.nextUsable(sources, 4, 4, seen, work).orElseThrow());
    assertEquals(second, SourceSchedule.nextUsable(sources, 4, 4, seen, work).orElseThrow());
    assertTrue(SourceSchedule.nextUsable(sources, 4, 4, seen, work).isEmpty());
    assertEquals(1, work.get("duplicateSources"));
    assertEquals(1, work.get("invalidSources"));
  }

  @Test
  void interleavesGroupsAndParentsWithoutSpendingSlotsOnDuplicates() {
    var bank =
        SourceSchedule.interleave(
            List.of(List.of("b1a", "b1b").iterator(), List.of("b2a", "b2b").iterator()));
    var sources =
        SourceSchedule.interleave(
            List.of(
                List.of("fresh").iterator(), List.of("fresh", "edit1", "edit2").iterator(), bank));
    var accepted = new LinkedHashSet<String>();
    while (accepted.size() < 5 && sources.hasNext()) accepted.add(sources.next());
    assertEquals(List.of("fresh", "b1a", "edit1", "b2a", "edit2"), new ArrayList<>(accepted));
  }

  @Test
  void emptyGroupsDoNotStarveAndLaterSourcesAreLazy() {
    var built = new AtomicInteger();
    var sources =
        SourceSchedule.interleave(
            List.of(
                Collections.<String>emptyIterator(),
                List.of("fresh").iterator(),
                SourceSchedule.deferred(
                    () -> {
                      built.incrementAndGet();
                      return List.of("bank");
                    })));
    assertTrue(sources.hasNext());
    assertEquals("fresh", sources.next());
    assertEquals(0, built.get());
    assertEquals("bank", sources.next());
    assertFalse(sources.hasNext());
    assertEquals(1, built.get());
    assertFalse(SourceSchedule.interleave(List.of()).hasNext());
  }
}
