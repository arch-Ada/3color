package io.threecolor.generation;

import java.util.*;
import java.util.function.Supplier;

/** Deterministic lazy round-robin; empty groups and invalid entries consume no search slot. */
final class SourceSchedule {
  static Optional<CriticalCatalog.Entry> nextUsable(
      Iterator<CriticalCatalog.Entry> sources,
      int minimum,
      int maximum,
      Set<String> seen,
      GenerationWork work) {
    while (!Thread.currentThread().isInterrupted() && sources.hasNext()) {
      var source = sources.next();
      work.count("sourceVariants");
      int n = source.candidate().graph().nodeCount();
      if (n < minimum || n > maximum) throw new IllegalStateException("Out-of-range source");
      if (!GraphNeighbourhood.valid(source.candidate(), work)) {
        work.count("invalidSources");
        continue;
      }
      if (!seen.add(TopologyFingerprint.of(source.candidate().graph()))) {
        work.count("duplicateSources");
        continue;
      }
      return Optional.of(source);
    }
    return Optional.empty();
  }

  static <T> Iterator<T> deferred(Supplier<List<T>> build) {
    return new Iterator<>() {
      Iterator<T> values;

      private Iterator<T> values() {
        if (values == null) values = build.get().iterator();
        return values;
      }

      public boolean hasNext() {
        return values().hasNext();
      }

      public T next() {
        return values().next();
      }
    };
  }

  static <T> Iterator<T> interleave(List<Iterator<T>> groups) {
    return new Iterator<>() {
      int cursor;

      public boolean hasNext() {
        for (int i = 0; i < groups.size(); i++)
          if (groups.get((cursor + i) % groups.size()).hasNext()) return true;
        return false;
      }

      public T next() {
        for (int i = 0; i < groups.size(); i++) {
          var group = groups.get(cursor);
          cursor = (cursor + 1) % groups.size();
          if (group.hasNext()) return group.next();
        }
        throw new NoSuchElementException();
      }
    };
  }
}
