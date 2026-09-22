package io.threecolor.supply;

import static io.threecolor.supply.PuzzleStore.*;

import io.threecolor.difficulty.PlayDifficulty;
import io.threecolor.generation.*;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import javax.sql.DataSource;

/** Fenced leases and atomic puzzle/cursor commits. A crash retries the unfinished seed. */
public final class DiscoveryQueue {
  private final DataSource dataSource;
  private final PuzzleStore store;

  public DiscoveryQueue(DataSource dataSource, PuzzleStore store) {
    this.dataSource = dataSource;
    this.store = store;
  }

  public record Lease(
      String version, PlayDifficulty.Category category, PuzzleSize size, long seed, UUID owner) {}

  public void initialize(String version, long firstSeed) throws SQLException {
    try (var c = dataSource.getConnection();
        var s =
            prepare(
                c,
                """
                INSERT INTO discovery_cell(search_version,category,size,minimum,maximum,next_seed)
                VALUES (?,?,?,?,?,?) ON CONFLICT DO NOTHING
                """)) {
      for (var size : PuzzleSize.values())
        for (var category : PlayDifficulty.Category.values()) {
          if (size == PuzzleSize.MINI
              && category.sourceBand() == io.threecolor.difficulty.DifficultyBand.MEDIUM) continue;
          s.setString(1, version);
          s.setString(2, category.name());
          s.setString(3, size.name());
          s.setInt(4, size.minimum());
          s.setInt(5, size.maximum());
          s.setLong(6, firstSeed);
          s.executeUpdate();
        }
    }
  }

  public Optional<Lease> claim(String version, int target, Duration duration) throws SQLException {
    try (var c = dataSource.getConnection()) {
      c.setAutoCommit(false);
      try {
        Lease lease = null;
        try (var s =
            prepare(
                c,
                """
SELECT d.category,d.size,d.next_seed FROM discovery_cell d
CROSS JOIN LATERAL (SELECT count(*) AS ready FROM puzzle_supply p
  WHERE p.category=d.category AND p.node_count BETWEEN d.minimum AND d.maximum
    AND p.proof_version=? AND p.player_version=? AND p.quarantine_reason IS NULL) supply
WHERE d.search_version=? AND (d.lease_until IS NULL OR d.lease_until < now())
  AND NOT (SELECT paused FROM discovery_control WHERE singleton=true)
  AND (?=0 OR supply.ready < ?)
ORDER BY d.attempts,supply.ready,d.category,d.size LIMIT 1 FOR UPDATE OF d SKIP LOCKED
""")) {
          models(s, 1);
          s.setString(3, version);
          s.setInt(4, target);
          s.setInt(5, target);
          try (var r = s.executeQuery()) {
            if (r.next())
              lease =
                  new Lease(
                      version,
                      PlayDifficulty.Category.valueOf(r.getString(1)),
                      PuzzleSize.valueOf(r.getString(2)),
                      r.getLong(3),
                      UUID.randomUUID());
          }
        }
        if (lease != null)
          try (var s =
              prepare(
                  c,
                  """
UPDATE discovery_cell SET lease_owner=?,lease_until=now()+(? * interval '1 second')
WHERE search_version=? AND category=? AND size=?
""")) {
            s.setObject(1, lease.owner());
            s.setLong(2, duration.toSeconds());
            key(s, 3, lease);
            s.executeUpdate();
          }
        c.commit();
        return Optional.ofNullable(lease);
      } catch (SQLException | RuntimeException e) {
        c.rollback();
        throw e;
      }
    }
  }

  public boolean heartbeat(Lease lease, Duration duration) throws SQLException {
    try (var c = dataSource.getConnection();
        var s =
            prepare(
                c,
                """
UPDATE discovery_cell SET lease_until=now()+(? * interval '1 second')
WHERE search_version=? AND category=? AND size=? AND lease_owner=? AND lease_until>now()
""")) {
      s.setLong(1, duration.toSeconds());
      key(s, 2, lease);
      s.setObject(5, lease.owner());
      return s.executeUpdate() == 1;
    }
  }

  public void release(Lease lease) throws SQLException {
    try (var c = dataSource.getConnection();
        var s =
            prepare(
                c,
                """
                UPDATE discovery_cell SET lease_owner=NULL,lease_until=NULL
                WHERE search_version=? AND category=? AND size=? AND lease_owner=?
                """)) {
      key(s, 1, lease);
      s.setObject(4, lease.owner());
      s.executeUpdate();
    }
  }

  public String complete(Lease lease, DeletionGenerator.Outcome result, long durationMs)
      throws SQLException {
    if (Thread.currentThread().isInterrupted()) throw new LostLease();
    try (var c = dataSource.getConnection()) {
      c.setAutoCommit(false);
      try {
        try (var s =
            prepare(
                c,
                """
SELECT next_seed FROM discovery_cell WHERE search_version=? AND category=? AND size=?
AND lease_owner=? AND lease_until>now() FOR UPDATE
""")) {
          key(s, 1, lease);
          s.setObject(4, lease.owner());
          try (var r = s.executeQuery()) {
            if (!r.next() || r.getLong(1) != lease.seed()) throw new LostLease();
          }
        }
        String outcome = result.failure();
        int added = 0, duplicates = 0, failures = 0;
        if (result.success()) {
          var certificate =
              Objects.requireNonNull(result.certificate(), "Missing worker certificate")
                  .withPuzzle(result.puzzle());
          if (certificate.rating().category() != lease.category()
              || !lease.size().contains(result.puzzle().topology().nodeCount()))
            throw new IllegalArgumentException("Worker result does not match leased cell");
          var insert = store.save(c, certificate.puzzle(), certificate.evidence(), "WORKER");
          outcome = insert.name();
          added = insert == Insert.ADDED ? 1 : 0;
          duplicates = 1 - added;
        } else {
          failures = 1;
        }
        try (var s =
            prepare(
                c,
                """
UPDATE discovery_cell SET next_seed=?,attempts=attempts+1,added=added+?,duplicates=duplicates+?,
  failures=failures+?,lease_owner=NULL,lease_until=NULL,last_completed_at=now(),
  last_outcome=?,last_duration_ms=?,last_stats=? WHERE search_version=? AND category=? AND size=?
""")) {
          s.setLong(1, lease.seed() + 1);
          s.setInt(2, added);
          s.setInt(3, duplicates);
          s.setInt(4, failures);
          s.setString(5, outcome);
          s.setLong(6, durationMs);
          String stats = result.stats().toString();
          s.setString(7, stats.substring(0, Math.min(8000, stats.length())));
          key(s, 8, lease);
          s.executeUpdate();
        }
        if (Thread.currentThread().isInterrupted()) throw new LostLease();
        c.commit();
        return outcome;
      } catch (SQLException | RuntimeException e) {
        c.rollback();
        throw e;
      }
    }
  }

  private static void key(PreparedStatement s, int i, Lease lease) throws SQLException {
    s.setString(i, lease.version());
    s.setString(i + 1, lease.category().name());
    s.setString(i + 2, lease.size().name());
  }

  public static final class LostLease extends RuntimeException {
    public LostLease() {
      super("Discovery lease no longer owned; result not committed");
    }
  }
}
