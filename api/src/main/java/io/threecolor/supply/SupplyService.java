package io.threecolor.supply;

import io.threecolor.difficulty.PlayDifficulty;
import io.threecolor.generation.*;
import java.sql.SQLException;
import java.util.*;
import org.slf4j.LoggerFactory;

/** Optional catalogue. Numeric replay always bypasses this evolving supply. */
public final class SupplyService {
  private final PuzzleStore store;

  public SupplyService(PuzzleStore store) {
    this.store = store;
  }

  public record Selection(DeletionGenerator.Outcome outcome, String supplyId) {}

  public Selection generate(
      PlayDifficulty.Category category,
      int min,
      int max,
      long seed,
      Set<String> excluded,
      boolean explicit) {
    var work = new GenerationWork(3);
    if (!explicit && store != null) {
      try {
        for (String key : store.candidates(category, min, max, seed, excluded)) {
          var accepted = read(key, work);
          if (accepted.isPresent()) return accepted.get();
          if (Thread.currentThread().isInterrupted()) break;
        }
      } catch (SQLException e) {
        LoggerFactory.getLogger(getClass())
            .warn(
                "Puzzle supply unavailable; using bundled/live supply (SQL state {})",
                e.getSQLState());
      }
    }
    var result =
        new RangeGenerator().generate(category, min, max, seed, PuzzleBank.bundled(), excluded);
    if (!work.snapshot().isEmpty()) {
      var stats = result.stats();
      var counts = new TreeMap<>(stats.work());
      work.snapshot().forEach((key, value) -> counts.merge(key, value, Long::sum));
      result =
          new DeletionGenerator.Outcome(
              result.puzzle(),
              result.proof(),
              new DeletionGenerator.Stats(
                  stats.attempts(),
                  stats.graphs(),
                  stats.deletedEdges(),
                  stats.bridgeSkips(),
                  stats.exactChecks(),
                  stats.exactNodes(),
                  stats.proofStates(),
                  stats.hittingSetNodes(),
                  stats.outcomes(),
                  counts,
                  stats.sources()),
              result.failure(),
              result.certificate());
    }
    return new Selection(result, null);
  }

  public Optional<Selection> replay(String key) throws SQLException {
    if (store == null) throw new SQLException("Puzzle supply disabled");
    return read(key, new GenerationWork(1));
  }

  private Optional<Selection> read(String key, GenerationWork work) throws SQLException {
    PuzzleStore.Stored stored;
    try {
      var found = store.load(key);
      if (found.isEmpty()) return Optional.empty();
      stored = found.get();
    } catch (PuzzleStore.CorruptPuzzle e) {
      store.quarantine(key, "CORRUPT_RECORD");
      return Optional.empty();
    }
    var check =
        CertifiedPuzzle.verify(
            stored.puzzle(), stored.category().sourceBand(), stored.category(), work);
    if (check.status() != CertifiedPuzzle.Status.ACCEPTED) {
      if (check.status() == CertifiedPuzzle.Status.REJECTED && !check.reason().endsWith("UNKNOWN"))
        store.quarantine(key, check.reason());
      return Optional.empty();
    }
    var c = check.certificate();
    var stats =
        new DeletionGenerator.Stats(
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            Map.of("CERTIFIED_SUPPLY", 1),
            work.snapshot(),
            List.of("supply:" + key));
    return Optional.of(
        new Selection(new DeletionGenerator.Outcome(c.puzzle(), c.proof(), stats, null, c), key));
  }
}
