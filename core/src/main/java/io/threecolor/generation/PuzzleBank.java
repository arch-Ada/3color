package io.threecolor.generation;

import io.threecolor.deduction.ProofLevels;
import io.threecolor.difficulty.DifficultyBand;
import io.threecolor.difficulty.PlayDifficulty;
import io.threecolor.model.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Immutable offline bank. Every entry is independently recertified when loaded. */
public final class PuzzleBank {
  public static final String HEADER =
      "# puzzle-bank-v2 " + ProofLevels.VERSION + " " + ExplanationModel.PLAYER_V1;

  public record Entry(
      DifficultyBand band,
      Puzzle puzzle,
      ProofLevels.Classification proof,
      PlayDifficulty.Rating playRating,
      CertifiedPuzzle.Evidence evidence) {
    public Entry(
        DifficultyBand band,
        Puzzle puzzle,
        ProofLevels.Classification proof,
        PlayDifficulty.Rating playRating) {
      this(band, puzzle, proof, playRating, null);
    }

    public Entry(DifficultyBand band, Puzzle puzzle, ProofLevels.Classification proof) {
      this(
          band,
          puzzle,
          proof,
          PlayDifficulty.rate(
              band, ProofLevels.explanationEngine().solve(puzzle.topology(), puzzle.givens())));
    }
  }

  private final List<Entry> entries;
  private final Map<Puzzle, String> fingerprints;

  private PuzzleBank(List<Entry> entries) {
    this.entries = List.copyOf(entries);
    var keys = new HashMap<Puzzle, String>();
    for (var entry : entries)
      keys.put(entry.puzzle(), TopologyFingerprint.of(entry.puzzle().topology()));
    fingerprints = Map.copyOf(keys);
  }

  public List<Entry> entries() {
    return entries;
  }

  private static final class Bundled {
    static final PuzzleBank INSTANCE = loadBundled();
  }

  public static PuzzleBank bundled() {
    return Bundled.INSTANCE;
  }

  private static PuzzleBank loadBundled() {
    var stream = PuzzleBank.class.getResourceAsStream("/puzzle-bank.tsv");
    return stream == null ? new PuzzleBank(List.of()) : read(stream);
  }

  public static PuzzleBank read(InputStream stream) {
    try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
      if (!HEADER.equals(reader.readLine()))
        throw new IllegalArgumentException("Stale puzzle bank proof version");
      var entries = new ArrayList<Entry>();
      for (String line; (line = reader.readLine()) != null; ) {
        if (line.isBlank() || line.startsWith("#")) continue;
        var p = line.split("\\|", -1);
        var band = DifficultyBand.valueOf(p[0]);
        var puzzle =
            new Puzzle(
                PuzzleRecords.graph(Integer.parseInt(p[4]), p[5]),
                PuzzleRecords.clues(p[6]),
                RuleSet.CLASSIC_V1,
                new PuzzleProvenance(
                    DeletionGenerator.VERSION, Long.parseLong(p[2]), Integer.parseInt(p[3]), p[1]),
                PuzzleRecords.layout(p[7]));
        entries.add(certify(band, puzzle));
      }
      return new PuzzleBank(entries);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static Entry certify(DifficultyBand band, Puzzle puzzle) {
    var checked = CertifiedPuzzle.verify(puzzle, band, null, new GenerationWork(1));
    if (checked.status() != CertifiedPuzzle.Status.ACCEPTED)
      throw new IllegalArgumentException("Bank certificate: " + checked.reason());
    var certified = checked.certificate();
    return new Entry(band, puzzle, certified.proof(), certified.rating(), certified.evidence());
  }

  public Optional<DeletionGenerator.Outcome> select(DifficultyBand band, int n, long seed) {
    return select(band, n, seed, Set.of());
  }

  public Optional<DeletionGenerator.Outcome> select(
      DifficultyBand band, int n, long seed, Set<String> excluded) {
    var matching =
        entries.stream()
            .filter(e -> e.band() == band && e.puzzle().topology().nodeCount() == n)
            .filter(e -> !excluded.contains(fingerprints.get(e.puzzle())))
            .toList();
    return choose(matching, seed);
  }

  public Optional<DeletionGenerator.Outcome> select(
      PlayDifficulty.Category category, int minimum, int maximum, long seed, Set<String> excluded) {
    return choose(
        entries.stream()
            .filter(e -> e.playRating().category() == category)
            .filter(
                e ->
                    e.puzzle().topology().nodeCount() >= minimum
                        && e.puzzle().topology().nodeCount() <= maximum)
            .filter(e -> !excluded.contains(fingerprints.get(e.puzzle())))
            .toList(),
        seed);
  }

  private static Optional<DeletionGenerator.Outcome> choose(List<Entry> matching, long seed) {
    if (matching.isEmpty()) return Optional.empty();
    var entry = matching.get(Math.floorMod(seed, matching.size()));
    var original = entry.puzzle();
    var puzzle =
        new Puzzle(
            original.topology(),
            original.givens(),
            original.rules(),
            new PuzzleProvenance(
                DeletionGenerator.VERSION,
                seed,
                0,
                "bank="
                    + original.logicalHash()
                    + ";originSeed="
                    + original.provenance().masterSeed()
                    + ";proof="
                    + ProofLevels.VERSION),
            original.layout());
    // Keep full traces request-scoped rather than caching them for the entire bank.
    var work = new GenerationWork(1);
    var checked = CertifiedPuzzle.explainBank(entry.evidence(), puzzle, work);
    var stats =
        new DeletionGenerator.Stats(
            0, 0, 0, 0, 0, 0, 0, 0, Map.of("CERTIFIED_BANK", 1), work.snapshot());
    if (checked.status() != CertifiedPuzzle.Status.ACCEPTED)
      return Optional.of(new DeletionGenerator.Outcome(null, null, stats, checked.reason()));
    return Optional.of(
        new DeletionGenerator.Outcome(
            puzzle, checked.certificate().proof(), stats, null, checked.certificate()));
  }

  public static String encode(Entry entry) {
    var p = entry.puzzle();
    return entry.band()
        + "|"
        + p.provenance().generationSpec()
        + "|"
        + p.provenance().masterSeed()
        + "|"
        + p.provenance().attemptIndex()
        + "|"
        + p.topology().nodeCount()
        + "|"
        + PuzzleRecords.edges(p.topology())
        + "|"
        + PuzzleRecords.clues(p.givens())
        + "|"
        + PuzzleRecords.points(p.layout());
  }
}
