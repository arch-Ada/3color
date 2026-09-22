package io.threecolor.supply;

import io.threecolor.deduction.ProofLevels;
import io.threecolor.difficulty.PlayDifficulty;
import io.threecolor.generation.*;
import io.threecolor.model.Puzzle;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.HexFormat;

public final class SupplyIdentity {
  private SupplyIdentity() {}

  public static String hash(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }
  }

  public static String puzzleKey(Puzzle p) {
    return hash(
        ("supply-puzzle-v1\n" + p.logicalHash() + "\n" + PuzzleRecords.points(p.layout()))
            .getBytes(StandardCharsets.UTF_8));
  }

  public static String bankHash() {
    try (var stream = PuzzleBank.class.getResourceAsStream("/puzzle-bank.tsv")) {
      if (stream == null) throw new IllegalStateException("Missing bundled bank");
      return hash(stream.readAllBytes());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static String searchVersion() {
    return "supply-worker-v1:"
        + DeletionGenerator.VERSION
        + ":"
        + RangeGenerator.VERSION
        + ":"
        + ProofLevels.VERSION
        + ":"
        + PlayDifficulty.VERSION
        + ":"
        + bankHash();
  }
}
