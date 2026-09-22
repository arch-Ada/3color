package io.threecolor.api;

import static org.junit.jupiter.api.Assertions.*;

import io.threecolor.deduction.*;
import io.threecolor.model.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.ObjectMapper;

class HintImprovementTest {
  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void auditedPositionsHaveShortCompleteHints() throws Exception {
    var rows = mapper.readTree(getClass().getResourceAsStream("/hints/positions.json"));
    int[] maximum = {3, 3, 13};
    int index = 0;
    for (var row : rows) {
      var puzzle = mapper.treeToValue(row.get("puzzle"), Dtos.PuzzleDto.class).core();
      var colors = new TreeMap<NodeId, Color>();
      row.get("current")
          .properties()
          .forEach(
              e ->
                  colors.put(
                      new NodeId(Integer.parseInt(e.getKey())),
                      Color.valueOf(e.getValue().asText())));
      var hint = new HintService().hint(puzzle, new PartialColoring(colors), Hint.Level.REASON);
      assertEquals(Hint.Status.AVAILABLE, hint.status());
      assertTrue(
          hint.explanation().walkthrough().size() <= maximum[index++],
          row.get("label").asText() + ": " + hint.explanation().walkthrough().size());
      var ids = new HashSet<Integer>();
      for (var fact : hint.supportingSteps()) {
        assertTrue(ids.containsAll(fact.premises()));
        ids.add(fact.id());
      }
      var root = hint.deduction().orElseThrow();
      assertEquals(1, Integer.bitCount(root.afterMask()));
      assertFalse(colors.containsKey(new NodeId(root.node())));
      var solved = ProofLevels.explanationEngine().solve(puzzle.topology(), puzzle.givens());
      assertEquals(solved.finalDomains().get(root.node()).intValue(), root.afterMask());
    }
  }

  /** Independent rule-by-rule check of the browser's uncompressed evidence. */
  @Test
  @EnabledIfEnvironmentVariable(named = "THREECOLOR_BROWSER_HINT_FIXTURES", matches = ".+")
  void browserDeductionsMatchJavaRules() throws Exception {
    var rules =
        Map.<String, DeductionRule>of(
            "adjacent_color_elimination",
            new AdjacencyRule(),
            "locked_two_color_edge",
            new LockedEdgeRule(),
            "diamond_equality",
            new DiamondRule(),
            "relation_propagation",
            new RelationPropagationRule());
    var rows =
        mapper.readTree(
            Files.readString(Path.of(System.getenv("THREECOLOR_BROWSER_HINT_FIXTURES"))));
    assertTrue(rows.size() > 0);
    int checked = 0;
    for (var row : rows) {
      var p = mapper.treeToValue(row.get("puzzle"), Dtos.PuzzleDto.class).core();
      var masks = new ArrayList<>(Collections.nCopies(p.topology().nodeCount(), 7));
      row.get("current")
          .properties()
          .forEach(
              e ->
                  masks.set(
                      Integer.parseInt(e.getKey()), Color.valueOf(e.getValue().asText()).mask()));
      var relations = new RelationKnowledge(p.topology());
      for (var step : row.get("steps")) {
        if (step.get("tier").asInt() == 0) continue;
        var c = step.get("conclusion");
        LogicalConclusion conclusion =
            c.get("kind").asText().equals("EQUAL")
                ? new LogicalConclusion.Equal(c.get("a").asInt(), c.get("b").asInt())
                : new LogicalConclusion.NarrowDomain(c.get("node").asInt(), c.get("mask").asInt());
        var state = new DeductionState(p.topology(), masks, relations);
        boolean already =
            conclusion instanceof LogicalConclusion.Equal e && relations.equal(e.a(), e.b());
        assertTrue(
            already
                || rules.get(step.get("ruleId").asText()).find(state).stream()
                    .anyMatch(a -> a.conclusion().equals(conclusion)),
            step.toString());
        if (conclusion instanceof LogicalConclusion.Equal e)
          relations = relations.with(e.a(), e.b(), true, step.get("id").asInt());
        if (conclusion instanceof LogicalConclusion.NarrowDomain d) masks.set(d.node(), d.mask());
        checked++;
      }
    }
    assertTrue(checked > 0);
    System.out.println(
        "Browser proof facts checked against Java rules: "
            + checked
            + " in "
            + rows.size()
            + " positions");
  }
}
