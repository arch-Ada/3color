package io.threecolor.supply;

import io.threecolor.deduction.ProofLevels;
import io.threecolor.difficulty.PlayDifficulty;
import io.threecolor.generation.*;
import io.threecolor.model.*;
import java.sql.*;
import java.util.*;
import javax.sql.DataSource;

/** Stored graphs are immutable. No solutions or serialized proof traces are persisted. */
public final class PuzzleStore {
  private final DataSource dataSource;

  public PuzzleStore(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  public record Stored(String key, PlayDifficulty.Category category, Puzzle puzzle) {}

  public enum Insert {
    ADDED,
    DUPLICATE
  }

  static PreparedStatement prepare(Connection c, String sql) throws SQLException {
    var statement = c.prepareStatement(sql);
    statement.setQueryTimeout(15);
    return statement;
  }

  static void models(PreparedStatement s, int offset) throws SQLException {
    s.setString(offset, ProofLevels.VERSION);
    s.setString(offset + 1, PlayDifficulty.VERSION);
  }

  public Insert save(CertifiedPuzzle certificate, String source) throws SQLException {
    try (var c = dataSource.getConnection()) {
      return save(c, certificate.puzzle(), certificate.evidence(), source);
    }
  }

  Insert save(Connection c, Puzzle puzzle, CertifiedPuzzle.Evidence evidence, String source)
      throws SQLException {
    if (evidence == null || !evidence.matches(puzzle) || !evidence.rating().accepted())
      throw new IllegalArgumentException(
          "Only currently certified player puzzles may enter supply");
    var provenance = puzzle.provenance();
    try (var s =
        prepare(
            c,
            """
INSERT INTO puzzle_supply(puzzle_key,logical_hash,topology_key,proof_version,player_version,
  category,node_count,edges,givens,points,rules,generator_version,origin_seed,origin_attempt,generation_spec,source,explanation_model)
VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT DO NOTHING
""")) {
      s.setString(1, SupplyIdentity.puzzleKey(puzzle));
      s.setString(2, puzzle.logicalHash());
      s.setString(3, TopologyFingerprint.of(puzzle.topology()));
      models(s, 4);
      s.setString(6, evidence.rating().category().name());
      s.setInt(7, puzzle.topology().nodeCount());
      s.setString(8, PuzzleRecords.edges(puzzle.topology()));
      s.setString(9, PuzzleRecords.clues(puzzle.givens()));
      s.setString(10, PuzzleRecords.points(puzzle.layout()));
      s.setString(11, puzzle.rules().name());
      s.setString(12, provenance.generatorVersion());
      s.setLong(13, provenance.masterSeed());
      s.setInt(14, provenance.attemptIndex());
      s.setString(15, provenance.generationSpec());
      s.setString(16, source);
      s.setString(17, puzzle.explanationModel().name());
      return s.executeUpdate() == 1 ? Insert.ADDED : Insert.DUPLICATE;
    }
  }

  /**
   * All-or-nothing import with a database lock; concurrent web/worker starts cannot double-import.
   */
  public int importBank(PuzzleBank bank, String bankHash) throws SQLException {
    try (var c = dataSource.getConnection()) {
      c.setAutoCommit(false);
      try {
        try (var lock = prepare(c, "SELECT pg_advisory_xact_lock(7340319001)")) {
          lock.execute();
        }
        try (var s =
            prepare(
                c,
                "SELECT 1 FROM supply_import WHERE bank_hash=? AND proof_version=? AND"
                    + " player_version=?")) {
          s.setString(1, bankHash);
          models(s, 2);
          try (var rows = s.executeQuery()) {
            if (rows.next()) {
              c.commit();
              return 0;
            }
          }
        }
        int inserted = 0;
        for (var entry : bank.entries()) {
          if (!entry.playRating().accepted()) continue;
          if (Thread.currentThread().isInterrupted()) throw new SQLException("Import interrupted");
          if (save(c, entry.puzzle(), entry.evidence(), "BUNDLED") == Insert.ADDED) inserted++;
        }
        try (var s =
            prepare(
                c,
                "INSERT INTO supply_import(bank_hash,proof_version,player_version) VALUES"
                    + " (?,?,?)")) {
          s.setString(1, bankHash);
          models(s, 2);
          s.executeUpdate();
        }
        c.commit();
        return inserted;
      } catch (SQLException | RuntimeException e) {
        c.rollback();
        throw e;
      }
    }
  }

  public List<String> candidates(
      PlayDifficulty.Category category, int minimum, int maximum, long seed, Set<String> excluded)
      throws SQLException {
    try (var c = dataSource.getConnection();
        var s =
            prepare(
                c,
                """
SELECT puzzle_key FROM puzzle_supply WHERE category=? AND node_count BETWEEN ? AND ?
AND proof_version=? AND player_version=? AND quarantine_reason IS NULL
AND NOT (topology_key = ANY (?)) ORDER BY md5(puzzle_key || ?) LIMIT 3
""")) {
      s.setString(1, category.name());
      s.setInt(2, minimum);
      s.setInt(3, maximum);
      models(s, 4);
      var array = c.createArrayOf("varchar", excluded.toArray(String[]::new));
      try {
        s.setArray(6, array);
        s.setString(7, Long.toString(seed));
        var keys = new ArrayList<String>();
        try (var rows = s.executeQuery()) {
          while (rows.next()) keys.add(rows.getString(1));
        }
        return List.copyOf(keys);
      } finally {
        array.free();
      }
    }
  }

  public Optional<Stored> load(String key) throws SQLException {
    try (var c = dataSource.getConnection();
        var s =
            prepare(
                c,
                """
SELECT * FROM puzzle_supply WHERE puzzle_key=? AND proof_version=? AND player_version=?
AND quarantine_reason IS NULL
""")) {
      s.setString(1, key);
      models(s, 2);
      try (var r = s.executeQuery()) {
        if (!r.next()) return Optional.empty();
        Puzzle p;
        try {
          p =
              new Puzzle(
                  PuzzleRecords.graph(r.getInt("node_count"), r.getString("edges")),
                  PuzzleRecords.clues(r.getString("givens")),
                  RuleSet.valueOf(r.getString("rules")),
                  new PuzzleProvenance(
                      r.getString("generator_version"),
                      r.getLong("origin_seed"),
                      r.getInt("origin_attempt"),
                      r.getString("generation_spec")),
                  PuzzleRecords.layout(r.getString("points")),
                  ExplanationModel.valueOf(r.getString("explanation_model")));
        } catch (IllegalArgumentException | IndexOutOfBoundsException e) {
          throw new CorruptPuzzle("Malformed stored puzzle", e);
        }
        if (!key.equals(SupplyIdentity.puzzleKey(p))
            || !p.logicalHash().equals(r.getString("logical_hash"))
            || !TopologyFingerprint.of(p.topology()).equals(r.getString("topology_key")))
          throw new CorruptPuzzle("Stored puzzle identity mismatch");
        return Optional.of(
            new Stored(key, PlayDifficulty.Category.valueOf(r.getString("category")), p));
      }
    }
  }

  public void quarantine(String key, String reason) throws SQLException {
    try (var c = dataSource.getConnection();
        var s =
            prepare(
                c,
                "UPDATE puzzle_supply SET quarantine_reason=? WHERE puzzle_key=? AND"
                    + " proof_version=? AND player_version=?")) {
      s.setString(1, reason);
      s.setString(2, key);
      models(s, 3);
      s.executeUpdate();
    }
  }

  /**
   * Conservative structural exclusion across categories, scoped to the requested vertex interval.
   */
  public Set<String> topologies(int minimum, int maximum) throws SQLException {
    try (var c = dataSource.getConnection();
        var s =
            prepare(
                c,
                "SELECT DISTINCT topology_key FROM puzzle_supply WHERE node_count BETWEEN ? AND ?"
                    + " AND proof_version=? AND player_version=? AND quarantine_reason IS NULL")) {
      s.setInt(1, minimum);
      s.setInt(2, maximum);
      models(s, 3);
      var found = new HashSet<String>();
      try (var r = s.executeQuery()) {
        while (r.next()) found.add(r.getString(1));
      }
      return Set.copyOf(found);
    }
  }

  public static final class CorruptPuzzle extends RuntimeException {
    CorruptPuzzle(String message) {
      super(message);
    }

    CorruptPuzzle(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
