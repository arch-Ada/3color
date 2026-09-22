package io.threecolor.supply;

import static org.junit.jupiter.api.Assertions.*;

import com.zaxxer.hikari.*;
import io.threecolor.difficulty.*;
import io.threecolor.generation.*;
import io.threecolor.model.*;
import java.time.Duration;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Uses a unique disposable schema, never cleans the caller's database. */
@EnabledIfEnvironmentVariable(named = "THREECOLOR_TEST_DB_URL", matches = ".+")
class PuzzleSupplyTest {
  HikariDataSource ds;
  PuzzleStore store;
  DiscoveryQueue queue;
  String schema;
  static CertifiedPuzzle certificate;

  @BeforeAll
  static void fixture() {
    var graph =
        new GraphTopology(
            4,
            List.of(
                new Edge(0, 1), new Edge(1, 2), new Edge(2, 3), new Edge(0, 3), new Edge(0, 2)));
    var layout =
        new PuzzleLayout(
            List.of(
                new PuzzleLayout.Point(.1, .1),
                new PuzzleLayout.Point(.9, .1),
                new PuzzleLayout.Point(.9, .9),
                new PuzzleLayout.Point(.1, .9)));
    var result =
        new DeletionGenerator()
            .generateFrom(
                DifficultyBand.VERY_EASY,
                0,
                new DeletionGenerator.Budget(1, 18, 200, 50000, 12000, 3000),
                new CriticalCatalog.Entry("test", new PlaneTriangulation(graph, layout)),
                p ->
                    CertifiedPuzzle.verify(
                        p,
                        DifficultyBand.VERY_EASY,
                        PlayDifficulty.Category.VERY_EASY,
                        new GenerationWork()),
                new GenerationWork());
    assertTrue(result.success());
    certificate = result.certificate();
  }

  @BeforeEach
  void setup() {
    schema = "supply_test_" + UUID.randomUUID().toString().replace("-", "");
    var config = new HikariConfig();
    config.setJdbcUrl(System.getenv("THREECOLOR_TEST_DB_URL"));
    config.setUsername(System.getenv().getOrDefault("THREECOLOR_TEST_DB_USER", "ez"));
    config.setPassword(System.getenv().getOrDefault("THREECOLOR_TEST_DB_PASSWORD", ""));
    config.setMaximumPoolSize(4);
    ds = new HikariDataSource(config);
    Flyway.configure().dataSource(ds).schemas(schema).defaultSchema(schema).load().migrate();
    ds.close();
    config.setSchema(schema);
    ds = new HikariDataSource(config);
    store = new PuzzleStore(ds);
    queue = new DiscoveryQueue(ds, store);
  }

  @AfterEach
  void cleanup() throws Exception {
    try (var c = ds.getConnection();
        var s = c.createStatement()) {
      s.execute("DROP SCHEMA " + schema + " CASCADE");
    } finally {
      ds.close();
    }
  }

  void sql(String sql) throws Exception {
    try (var c = ds.getConnection();
        var s = c.createStatement()) {
      s.execute(sql);
    }
  }

  long number(String sql) throws Exception {
    try (var c = ds.getConnection();
        var s = c.createStatement();
        var r = s.executeQuery(sql)) {
      assertTrue(r.next());
      return r.getLong(1);
    }
  }

  @Test
  void immutableRoundTripExclusionAndReplay() throws Exception {
    assertEquals(PuzzleStore.Insert.ADDED, store.save(certificate, "WORKER"));
    assertEquals(PuzzleStore.Insert.DUPLICATE, store.save(certificate, "WORKER"));
    String key = SupplyIdentity.puzzleKey(certificate.puzzle());
    assertTrue(certificate.evidence().matches(store.load(key).orElseThrow().puzzle()));
    assertEquals(
        certificate.puzzle().provenance(), store.load(key).orElseThrow().puzzle().provenance());
    assertEquals(
        List.of(key), store.candidates(PlayDifficulty.Category.VERY_EASY, 4, 13, 42, Set.of()));
    assertTrue(store.candidates(PlayDifficulty.Category.EASY, 4, 13, 42, Set.of()).isEmpty());
    assertTrue(store.candidates(PlayDifficulty.Category.VERY_EASY, 5, 13, 42, Set.of()).isEmpty());
    assertTrue(
        store
            .candidates(PlayDifficulty.Category.VERY_EASY, 4, 13, 42, store.topologies(4, 13))
            .isEmpty());
    var response = new SupplyService(store).replay(key).orElseThrow();
    assertEquals(key, response.supplyId());
    assertTrue(certificate.evidence().matches(response.outcome().puzzle()));
    assertEquals(1L, response.outcome().stats().work().get("finalExactChecks"));
    sql("UPDATE puzzle_supply SET points='0.0,0.0'");
    assertTrue(new SupplyService(store).replay(key).isEmpty());
    assertEquals(
        1, number("SELECT count(*) FROM puzzle_supply WHERE quarantine_reason IS NOT NULL"));
  }

  @Test
  void webServesSupplyWithoutLeakingEvidenceAndNumericReplayIsUnchanged() throws Exception {
    String url = System.getenv("THREECOLOR_TEST_DB_URL");
    var app =
        new org.springframework.boot.SpringApplication(
            io.threecolor.api.ThreeColorApplication.class);
    try (var context =
        app.run(
            "--server.port=0",
            "--threecolor.supply.enabled=true",
            "--threecolor.supply.jdbc-url="
                + url
                + (url.contains("?") ? "&" : "?")
                + "currentSchema="
                + schema,
            "--threecolor.supply.username="
                + System.getenv().getOrDefault("THREECOLOR_TEST_DB_USER", "ez"),
            "--threecolor.supply.password="
                + System.getenv().getOrDefault("THREECOLOR_TEST_DB_PASSWORD", ""))) {
      var client = java.net.http.HttpClient.newHttpClient();
      String base =
          "http://127.0.0.1:"
              + context.getEnvironment().getProperty("local.server.port")
              + "/api/v1/puzzles/";
      var mapper = new tools.jackson.databind.ObjectMapper();
      String request = "{\"size\":\"MINI\",\"difficulty\":\"VERY_EASY\"}";
      var response =
          client.send(
              java.net.http.HttpRequest.newBuilder(java.net.URI.create(base + "generate"))
                  .header("Content-Type", "application/json")
                  .POST(java.net.http.HttpRequest.BodyPublishers.ofString(request))
                  .build(),
              java.net.http.HttpResponse.BodyHandlers.ofString());
      assertEquals(200, response.statusCode(), response.body());
      var body = mapper.readTree(response.body());
      String key = body.path("supplyId").asText();
      assertEquals(64, key.length());
      assertFalse(body.has("certificate"));
      assertFalse(body.has("trace"));
      assertFalse(response.body().contains("finalDomains"));
      var replay =
          client.send(
              java.net.http.HttpRequest.newBuilder(java.net.URI.create(base + "supply/" + key))
                  .GET()
                  .build(),
              java.net.http.HttpResponse.BodyHandlers.ofString());
      assertEquals(200, replay.statusCode());
      assertEquals(body.path("puzzle"), mapper.readTree(replay.body()).path("puzzle"));
      assertEquals(0, store.importBank(PuzzleBank.bundled(), SupplyIdentity.bankHash()));
      var service = new SupplyService(store);
      var expected =
          new RangeGenerator()
              .generate(
                  PlayDifficulty.Category.VERY_EASY, 4, 13, 42, PuzzleBank.bundled(), Set.of());
      var actual = service.generate(PlayDifficulty.Category.VERY_EASY, 4, 13, 42, Set.of(), true);
      assertNull(actual.supplyId());
      assertEquals(expected.puzzle().logicalHash(), actual.outcome().puzzle().logicalHash());
      assertEquals(expected.puzzle().layout(), actual.outcome().puzzle().layout());
      assertEquals(expected.stats(), actual.outcome().stats());
      javax.sql.DataSource unavailable =
          (javax.sql.DataSource)
              java.lang.reflect.Proxy.newProxyInstance(
                  getClass().getClassLoader(),
                  new Class<?>[] {javax.sql.DataSource.class},
                  (proxy, method, args) -> {
                    throw new java.sql.SQLException("Test outage", "08006");
                  });
      var fallback =
          new SupplyService(new PuzzleStore(unavailable))
              .generate(PlayDifficulty.Category.VERY_EASY, 4, 13, 42, Set.of(), false);
      assertEquals(expected.puzzle().logicalHash(), fallback.outcome().puzzle().logicalHash());
      var landing =
          client.send(
              java.net.http.HttpRequest.newBuilder(
                      java.net.URI.create(base.replace("/api/v1/puzzles/", "/")))
                  .GET()
                  .build(),
              java.net.http.HttpResponse.BodyHandlers.ofString());
      assertEquals(200, landing.statusCode());
      assertTrue(landing.headers().firstValue("location").isEmpty());
    }
  }

  @Test
  void cursorResumePauseAndExclusiveClaims() throws Exception {
    queue.initialize("test", 100);
    queue.initialize("test", 900);
    assertEquals(18, number("SELECT count(*) FROM discovery_cell"));
    assertEquals(100, number("SELECT min(next_seed) FROM discovery_cell"));
    var leases = new ArrayList<DiscoveryQueue.Lease>();
    try (var executor = java.util.concurrent.Executors.newFixedThreadPool(4)) {
      var jobs = new ArrayList<java.util.concurrent.Callable<DiscoveryQueue.Lease>>();
      for (int i = 0; i < 18; i++)
        jobs.add(() -> queue.claim("test", 0, Duration.ofSeconds(60)).orElseThrow());
      for (var result : executor.invokeAll(jobs)) leases.add(result.get());
    }
    assertEquals(18, leases.stream().map(l -> l.category() + ":" + l.size()).distinct().count());
    assertTrue(queue.claim("test", 0, Duration.ofSeconds(60)).isEmpty());
    for (var lease : leases) queue.release(lease);
    sql("UPDATE discovery_control SET paused=true");
    assertTrue(queue.claim("test", 0, Duration.ofSeconds(60)).isEmpty());
    sql("UPDATE discovery_control SET paused=false");
    var lease = queue.claim("test", 0, Duration.ofSeconds(60)).orElseThrow();
    var failed =
        new DeletionGenerator.Outcome(
            null, null, new DeletionGenerator.Stats(0, 0, 0, 0, 0, 0, 0, 0, Map.of()), "EXHAUSTED");
    queue.complete(lease, failed, 10);
    assertEquals(1, number("SELECT sum(attempts) FROM discovery_cell"));
    assertEquals(101, number("SELECT max(next_seed) FROM discovery_cell"));
  }

  @Test
  void expiredOwnerCannotAdvanceOrPersistAndResultCommitIsAtomic() throws Exception {
    queue.initialize("test", 100);
    sql("DELETE FROM discovery_cell WHERE category<>'VERY_EASY' OR size<>'MINI'");
    var old = queue.claim("test", 0, Duration.ofSeconds(60)).orElseThrow();
    sql("UPDATE discovery_cell SET lease_until=now()-interval '1 second'");
    var next = queue.claim("test", 0, Duration.ofSeconds(60)).orElseThrow();
    assertEquals(old.seed(), next.seed());
    assertNotEquals(old.owner(), next.owner());
    assertFalse(queue.heartbeat(old, Duration.ofSeconds(60)));
    var result =
        new DeletionGenerator.Outcome(
            certificate.puzzle(),
            certificate.proof(),
            new DeletionGenerator.Stats(0, 0, 0, 0, 0, 0, 0, 0, Map.of()),
            null,
            certificate);
    assertThrows(DiscoveryQueue.LostLease.class, () -> queue.complete(old, result, 1));
    assertEquals(0, number("SELECT count(*) FROM puzzle_supply"));
    sql("ALTER TABLE discovery_cell ADD CONSTRAINT reject_commit CHECK(attempts=0)");
    assertThrows(java.sql.SQLException.class, () -> queue.complete(next, result, 1));
    assertEquals(0, number("SELECT count(*) FROM puzzle_supply"));
    assertEquals(100, number("SELECT next_seed FROM discovery_cell"));
    sql("ALTER TABLE discovery_cell DROP CONSTRAINT reject_commit");
    Thread.currentThread().interrupt();
    try {
      assertThrows(DiscoveryQueue.LostLease.class, () -> queue.complete(next, result, 1));
    } finally {
      Thread.interrupted();
    }
    assertEquals(100, number("SELECT next_seed FROM discovery_cell"));
    assertEquals("ADDED", queue.complete(next, result, 1));
    assertEquals(101, number("SELECT next_seed FROM discovery_cell"));
    assertTrue(queue.claim("test", 1, Duration.ofSeconds(60)).isEmpty());
    var duplicate = queue.claim("test", 0, Duration.ofSeconds(60)).orElseThrow();
    assertEquals("DUPLICATE", queue.complete(duplicate, result, 1));
    assertEquals(1, number("SELECT count(*) FROM puzzle_supply"));
  }
}
