package io.threecolor.generation;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Published constructions are candidate sources, never difficulty certificates. */
public final class CriticalCatalog {
  public record Entry(String id, PlaneTriangulation candidate) {}

  private static final List<Entry> ENTRIES = load();

  private CriticalCatalog() {}

  public static List<Entry> entries() {
    return ENTRIES;
  }

  public static List<Entry> atSize(int n) {
    return ENTRIES.stream().filter(e -> e.candidate().graph().nodeCount() == n).toList();
  }

  private static List<Entry> load() {
    var stream = CriticalCatalog.class.getResourceAsStream("/critical-candidates.tsv");
    if (stream == null) return List.of();
    try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
      return reader
          .lines()
          .filter(line -> !line.isBlank() && !line.startsWith("#"))
          .map(
              line -> {
                var p = line.split("\\|", -1);
                return new Entry(
                    p[0],
                    new PlaneTriangulation(
                        PuzzleRecords.graph(Integer.parseInt(p[1]), p[2]),
                        PuzzleRecords.layout(p[3])));
              })
          .toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
