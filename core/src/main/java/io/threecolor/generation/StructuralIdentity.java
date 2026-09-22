package io.threecolor.generation;

import io.threecolor.model.GraphTopology;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** Labeled edge-set identity; deliberately not graph-isomorphism canonicalization. */
public final class StructuralIdentity {
  private StructuralIdentity() {}

  public static String of(GraphTopology graph) {
    return hash(graph.nodeCount() + ";" + graph.edges());
  }

  /**
   * Color/numbering-independent 1-WL fingerprint. Different values certify structural difference;
   * equal values are only potential duplicates, not an isomorphism proof.
   */
  public static String unlabeledFingerprint(GraphTopology graph) {
    int n = graph.nodeCount();
    int[] colors = new int[n];
    for (int v = 0; v < n; v++) colors[v] = graph.degree(v);
    for (int round = 0; round < n; round++) {
      String[] signatures = new String[n];
      var palette = new TreeMap<String, Integer>();
      for (int v = 0; v < n; v++) {
        int[] neighbors = graph.neighbors(v);
        for (int i = 0; i < neighbors.length; i++) neighbors[i] = colors[neighbors[i]];
        Arrays.sort(neighbors);
        signatures[v] = colors[v] + ":" + Arrays.toString(neighbors);
        palette.put(signatures[v], 0);
      }
      int next = 0;
      for (String key : palette.keySet()) palette.put(key, next++);
      for (int v = 0; v < n; v++) colors[v] = palette.get(signatures[v]);
    }
    var classes = new TreeMap<Integer, Integer>();
    for (int color : colors) classes.merge(color, 1, Integer::sum);
    var links = new TreeMap<String, Integer>();
    for (var edge : graph.edges()) {
      int a = colors[edge.a().value()], b = colors[edge.b().value()];
      links.merge(Math.min(a, b) + ":" + Math.max(a, b), 1, Integer::sum);
    }
    return hash("wl1-v1;" + n + ";" + classes + ";" + links);
  }

  static String hash(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
