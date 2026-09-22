package io.threecolor.api;

import static org.junit.jupiter.api.Assertions.*;

import java.net.*;
import java.net.http.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import tools.jackson.databind.ObjectMapper;

class ApiIntegrationTest {
  @Test
  void playerCategoriesKeepProofCertificationAndDisableOldHard() throws Exception {
    String request = "{\"seed\":\"0\",\"nodeCount\":16,\"difficulty\":\"EASY\"}";
    var response = post("puzzles/generate", request);
    assertEquals(200, response.statusCode(), response.body());
    var body = mapper.readTree(response.body());
    assertEquals("EASY", body.path("proof").path("band").asText());
    assertEquals("STALLED", body.path("proof").path("p0").path("status").asText());
    assertEquals("SOLVED", body.path("proof").path("p1").path("status").asText());
    assertEquals(16, body.path("puzzle").path("nodeCount").asInt());
    assertFalse(body.has("hiddenSolution"));
    assertFalse(body.has("certificate"));
    assertFalse(body.has("trace"));
    assertFalse(body.path("search").path("work").has("finalExactChecks"));
    assertFalse(body.path("search").path("work").has("finalProofChecks"));
    assertEquals(1, body.path("search").path("work").path("bankEvidenceReuse").asInt());
    assertEquals(1, body.path("search").path("work").path("explanationChecks").asInt());
    assertFalse(body.path("proof").path("p1").has("domains"));
    assertEquals(body, mapper.readTree(post("puzzles/generate", request).body()));
    var solve = post("solve", "{\"puzzle\":" + body.path("puzzle") + "}");
    assertEquals("UNIQUE", mapper.readTree(solve.body()).path("status").asText());
    assertEquals("proof-level-v2", body.path("proof").path("modelVersion").asText());
    assertEquals(1, body.path("search").path("outcomes").path("CERTIFIED_BANK").asInt());
    var medium =
        mapper.readTree(post("puzzles/generate", request.replace("EASY", "MEDIUM")).body());
    assertEquals("MEDIUM", medium.path("proof").path("band").asText());
    assertEquals(1, medium.path("proof").path("p2").path("refutationRounds").asInt());
    var veryEasy =
        mapper.readTree(post("puzzles/generate", request.replace("EASY", "VERY_EASY")).body());
    assertEquals("VERY_EASY", veryEasy.path("proof").path("band").asText());
    assertEquals("SOLVED", veryEasy.path("proof").path("p0").path("status").asText());
    var challengingResponse = post("puzzles/generate", request.replace("EASY", "CHALLENGING"));
    assertEquals(200, challengingResponse.statusCode(), challengingResponse.body());
    var challenging = mapper.readTree(challengingResponse.body());
    assertEquals("MEDIUM", challenging.path("proof").path("band").asText());
    assertEquals("CHALLENGING", challenging.path("playDifficulty").path("category").asText());
    assertTrue(challenging.path("playDifficulty").path("maximumContradictionSteps").asInt() <= 12);
    assertEquals(400, post("puzzles/generate", request.replace("EASY", "HARD")).statusCode());
    assertEquals(400, post("puzzles/generate", request.replace("16", "80")).statusCode());
    assertEquals(400, post("puzzles/generate", request.replace("EASY", "EXPERT")).statusCode());
  }

  @Test
  void sizeCategoriesPreserveRequestedPlayerCategoryAndRejectAmbiguousSize() throws Exception {
    String request = "{\"seed\":\"42\",\"size\":\"MEDIUM\",\"difficulty\":\"CHALLENGING\"}";
    var response = post("puzzles/generate", request);
    assertEquals(200, response.statusCode(), response.body());
    var body = mapper.readTree(response.body());
    int n = body.path("puzzle").path("nodeCount").asInt();
    assertTrue(n >= 24 && n <= 33);
    assertEquals("CHALLENGING", body.path("playDifficulty").path("category").asText());
    assertEquals(body, mapper.readTree(post("puzzles/generate", request).body()));
    assertEquals(
        400, post("puzzles/generate", request.replace("\"size\":\"MEDIUM\",", "")).statusCode());
    assertEquals(
        400,
        post("puzzles/generate", request.replace("\"size\":", "\"nodeCount\":24,\"size\":"))
            .statusCode());
    assertEquals(400, post("puzzles/generate", request.replace("MEDIUM", "HUGE")).statusCode());
    assertEquals(400, post("puzzles/generate", request.replace("MEDIUM", "MINI")).statusCode());
  }

  @Test
  void referencesAreUniqueCertifiedPlayableComparisons() throws Exception {
    var response =
        HttpClient.newHttpClient()
            .send(
                HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/api/v1/references"))
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString());
    assertEquals(200, response.statusCode());
    var refs = mapper.readTree(response.body());
    assertEquals(3, refs.size());
    var a = refs.get(0).path("generated");
    var b = refs.get(1).path("generated");
    assertEquals("HARD", a.path("proof").path("band").asText());
    assertEquals("MEDIUM", b.path("proof").path("band").asText());
    assertEquals(a.path("puzzle").path("edges"), b.path("puzzle").path("edges"));
    assertEquals(
        a.path("puzzle").path("givens").size() + 1, b.path("puzzle").path("givens").size());
    assertEquals(47, refs.get(2).path("generated").path("puzzle").path("nodeCount").asInt());
    for (var ref : refs) {
      var generated = ref.path("generated");
      assertFalse(generated.has("trace"));
      assertFalse(generated.path("puzzle").has("hiddenSolution"));
      var puzzle = generated.path("puzzle");
      var solve = mapper.readTree(post("solve", "{\"puzzle\":" + puzzle + "}").body());
      assertEquals("UNIQUE", solve.path("status").asText());
      var analysis = mapper.readTree(post("analyze", "{\"puzzle\":" + puzzle + "}").body());
      assertEquals("SOLVED", analysis.path("trace").path("status").asText());
    }
  }

  @Test
  void exclusionsAvoidRepeatingTopologyAndExplicitSeedsRemainDeterministic() throws Exception {
    String request = "{\"seed\":\"42\",\"nodeCount\":24,\"difficulty\":\"MEDIUM\"}";
    var first = mapper.readTree(post("puzzles/generate", request).body());
    String key = first.path("topologyKey").asText();
    assertTrue(key.matches("[0-9a-f]{64}"));
    var next =
        post(
            "puzzles/generate",
            request.substring(0, request.length() - 1)
                + ",\"excludeTopologies\":[\""
                + key
                + "\"]}");
    assertEquals(200, next.statusCode(), next.body());
    assertNotEquals(key, mapper.readTree(next.body()).path("topologyKey").asText());
    assertEquals(first, mapper.readTree(post("puzzles/generate", request).body()));
    assertEquals(
        400,
        post(
                "puzzles/generate",
                request.substring(0, request.length() - 1)
                    + ",\"excludeTopologies\":[\"invalid\"]}")
            .statusCode());
  }

  @Test
  void explanationModelIsRequiredAndIndependentOfGeneratorName() throws Exception {
    var generated =
        mapper.readTree(
            post("puzzles/generate", "{\"seed\":\"42\",\"size\":\"SMALL\",\"difficulty\":\"EASY\"}")
                .body());
    var puzzle = (tools.jackson.databind.node.ObjectNode) generated.get("puzzle");
    var original = mapper.readTree(post("analyze", "{\"puzzle\":" + puzzle + "}").body());
    ((tools.jackson.databind.node.ObjectNode) puzzle.get("provenance"))
        .put("generatorVersion", "completely-different-source");
    assertEquals(original, mapper.readTree(post("analyze", "{\"puzzle\":" + puzzle + "}").body()));
    puzzle.remove("explanationModel");
    assertEquals(400, post("analyze", "{\"puzzle\":" + puzzle + "}").statusCode());
    assertEquals(404, post("puzzles/deletion", "{}").statusCode());
    assertEquals(
        400,
        post("puzzles/generate", "{\"size\":\"SMALL\",\"difficulty\":\"EASY\",\"advanced\":{}}")
            .statusCode());
  }

  private static ConfigurableApplicationContext application;
  private static int port;

  @BeforeAll
  static void startApplication() {
    application = SpringApplication.run(ThreeColorApplication.class, "--server.port=0");
    port = application.getEnvironment().getRequiredProperty("local.server.port", Integer.class);
  }

  @AfterAll
  static void stopApplication() {
    if (application != null) application.close();
  }

  private final HttpClient client = HttpClient.newHttpClient();
  private final ObjectMapper mapper = new ObjectMapper();

  HttpResponse<String> post(String path, String body) throws Exception {
    return client.send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/" + path))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  @Test
  void generationAnalysisHintsAndSolvingUseRealCore() throws Exception {
    var r =
        post("puzzles/generate", "{\"seed\":\"42\",\"nodeCount\":12,\"difficulty\":\"VERY_EASY\"}");
    assertEquals(200, r.statusCode(), r.body());
    assertFalse(r.body().contains("hiddenSolution"));
    assertFalse(r.body().contains("solutions"));
    var body = mapper.readTree(r.body());
    assertEquals("human-profile-v5", body.get("difficulty").get("modelVersion").asText());
    assertEquals("PLAYER_V1", body.get("puzzle").get("explanationModel").asText());
    var puzzle = body.get("puzzle").toString();
    var solved = post("solve", "{\"puzzle\":" + puzzle + "}");
    assertEquals(200, solved.statusCode());
    assertEquals("UNIQUE", mapper.readTree(solved.body()).get("status").asText());
    var analyzed = post("analyze", "{\"puzzle\":" + puzzle + "}");
    assertEquals("SOLVED", mapper.readTree(analyzed.body()).get("trace").get("status").asText());
    var hint = post("hints", "{\"puzzle\":" + puzzle + ",\"current\":{},\"level\":\"NUDGE\"}");
    assertEquals(200, hint.statusCode());
    assertEquals("AVAILABLE", mapper.readTree(hint.body()).get("status").asText());
    var analyzedBody = mapper.readTree(analyzed.body());
    assertTrue(analyzedBody.get("trace").has("frontiers"));
    assertTrue(analyzedBody.get("trace").has("events"));
    assertTrue(analyzedBody.get("trace").get("steps").get(0).has("conclusion"));
    assertTrue(analyzedBody.get("trace").get("steps").get(0).has("effort"));
    var hintBody = mapper.readTree(hint.body());
    assertTrue(hintBody.get("explanation").get("primaryTargets").size() > 0);
    assertTrue(hintBody.get("explanation").get("focusEdges").size() > 0);
    assertTrue(hintBody.get("explanation").get("walkthrough").size() > 0);
    assertEquals(
        "NARROW_DOMAIN", hintBody.get("explanation").get("conclusion").get("kind").asText());
    assertTrue(body.get("difficulty").get("profile").has("remainingCheapCoreFraction"));

    var steps = hintBody.get("supportingSteps");
    var ids = new java.util.HashSet<Integer>();
    steps.forEach(step -> ids.add(step.get("id").asInt()));
    steps.forEach(
        step -> step.get("premises").forEach(ref -> assertTrue(ids.contains(ref.asInt()))));
  }

  @Test
  void relationFactsHaveExplicitAdditiveConclusions() throws Exception {
    var response =
        post(
            "analyze",
            """
{"puzzle":{"nodeCount":4,"edges":[{"a":0,"b":1},{"a":0,"b":2},{"a":1,"b":2},{"a":0,"b":3},{"a":1,"b":3}],"givens":{},"layout":[],"rules":"CLASSIC_V1","explanationModel":"PLAYER_V1"}}
""");
    assertEquals(200, response.statusCode());
    var steps = mapper.readTree(response.body()).get("trace").get("steps");
    boolean found = false;
    for (var step : steps)
      if (step.get("conclusion").get("kind").asText().equals("EQUAL")) {
        found = true;
        assertEquals(2, step.get("conclusion").get("a").asInt());
        assertEquals(3, step.get("conclusion").get("b").asInt());
        assertTrue(step.has("node"));
        assertTrue(step.has("beforeMask"));
        assertTrue(step.has("afterMask"));
      }
    assertTrue(found);
  }

  @Test
  void invalidAndOversizedRequestsAreRejected() throws Exception {
    assertEquals(
        400, post("puzzles/generate", "{\"nodeCount\":9000,\"difficulty\":\"EASY\"}").statusCode());
    assertEquals(
        400,
        post("puzzles/generate", "{\"nodeCount\":12,\"difficulty\":\"INVALID\"}").statusCode());
    assertEquals(413, post("solve", " ".repeat(140000)).statusCode());
    assertEquals(
        400,
        post(
                "solve",
                """
{"puzzle":{"nodeCount":2,"edges":[null],"givens":{},"layout":[],"rules":"CLASSIC_V1","explanationModel":"PLAYER_V1"}}
""")
            .statusCode());
  }

  @Test
  void healthAndOpenApiAreAvailable() throws Exception {
    for (String path :
        new String[] {"/actuator/health", "/v3/api-docs", "/swagger-ui/index.html"}) {
      var r =
          client.send(
              HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
              HttpResponse.BodyHandlers.ofString());
      assertEquals(200, r.statusCode(), path);
    }
  }
}
