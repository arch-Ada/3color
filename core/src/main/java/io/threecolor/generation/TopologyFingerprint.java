package io.threecolor.generation;

import io.threecolor.model.GraphTopology;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/**
 * Label/layout/colour-invariant refinement fingerprint. Collisions conservatively exclude graphs;
 * equality is not claimed to prove isomorphism. Offline publication also uses exact isomorphism.
 */
public final class TopologyFingerprint {
  private static String hash(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }
  }

  public static String of(GraphTopology graph) {
    String[] labels = new String[graph.nodeCount()];
    for (int v = 0; v < labels.length; v++) labels[v] = Integer.toString(graph.degree(v));
    for (int round = 0; round < 6; round++) {
      String[] next = new String[labels.length];
      for (int v = 0; v < labels.length; v++) {
        var neighbors = new ArrayList<String>();
        for (int w : graph.neighbors(v)) neighbors.add(labels[w]);
        Collections.sort(neighbors);
        next[v] = hash(labels[v] + ":" + String.join(",", neighbors));
      }
      labels = next;
    }
    Arrays.sort(labels);
    return hash(graph.nodeCount() + ":" + graph.edges().size() + ":" + String.join(",", labels));
  }
}
