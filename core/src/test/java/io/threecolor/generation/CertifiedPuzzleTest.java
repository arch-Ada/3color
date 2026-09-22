package io.threecolor.generation;

import static org.junit.jupiter.api.Assertions.*;

import io.threecolor.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class CertifiedPuzzleTest {
  @Test
  void certificateBindsLogicAndLayoutButAllowsProvenanceWithoutAnyCheckerCalls() {
    var source = SearchContinuationTest.source().candidate();
    var p =
        new Puzzle(
            source.graph(),
            new PartialColoring(Map.of(new NodeId(0), Color.RED, new NodeId(2), Color.BLUE)),
            RuleSet.CLASSIC_V1,
            PuzzleProvenance.supplied(),
            source.layout());
    var work = new GenerationWork();
    var c = SearchContinuationTest.certify(p, work).certificate();
    assertNotNull(c);
    var bankWork = new GenerationWork();
    var bank = CertifiedPuzzle.explainBank(c.evidence(), p, bankWork);
    assertEquals(CertifiedPuzzle.Status.ACCEPTED, bank.status());
    assertEquals(0, bankWork.get("finalExactChecks"));
    assertEquals(0, bankWork.get("finalProofChecks"));
    assertEquals(1, bankWork.get("explanationChecks"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            CertifiedPuzzle.explainBank(
                c.evidence(),
                new Puzzle(
                    p.topology(), PartialColoring.empty(), p.rules(), p.provenance(), p.layout()),
                new GenerationWork()));
    var changedModel =
        new Puzzle(
            p.topology(),
            p.givens(),
            p.rules(),
            p.provenance(),
            p.layout(),
            ExplanationModel.BASIC_V1);
    assertFalse(c.evidence().matches(changedModel));
    assertThrows(IllegalArgumentException.class, () -> c.withPuzzle(changedModel));
    var before = work.snapshot();
    var rewritten =
        new Puzzle(
            p.topology(),
            p.givens(),
            p.rules(),
            new PuzzleProvenance("test", 7, 1, "only provenance"),
            p.layout());
    assertSame(c.trace(), c.withPuzzle(rewritten).trace());
    assertSame(c.proof(), c.withPuzzle(rewritten).proof());
    assertSame(c.rating(), c.withPuzzle(rewritten).rating());
    assertEquals(before, work.snapshot());
    assertEquals(1, work.get("finalExactChecks"));
    assertEquals(1, work.get("explanationChecks"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            c.withPuzzle(
                new Puzzle(
                    p.topology(), PartialColoring.empty(), p.rules(), p.provenance(), p.layout())));
    var edges = new ArrayList<>(p.topology().edges());
    edges.removeFirst();
    assertThrows(
        IllegalArgumentException.class,
        () ->
            c.withPuzzle(
                new Puzzle(
                    new GraphTopology(4, edges),
                    p.givens(),
                    p.rules(),
                    p.provenance(),
                    p.layout())));
    var points = new ArrayList<>(p.layout().points());
    points.set(0, new PuzzleLayout.Point(.2, .1));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            c.withPuzzle(
                new Puzzle(
                    p.topology(),
                    p.givens(),
                    p.rules(),
                    p.provenance(),
                    new PuzzleLayout(points))));
  }
}
