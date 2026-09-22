package io.threecolor.generation;

import io.threecolor.difficulty.PlayDifficulty;
import io.threecolor.model.*;
import java.util.*;

/** Bounded local search; callers may retry with a new seed until canceled. */
public final class RangeGenerator {
  public static final String VERSION = "range-neighbourhood-v2";

  public DeletionGenerator.Outcome generate(
      PlayDifficulty.Category category,
      int minimum,
      int maximum,
      long seed,
      PuzzleBank bank,
      Set<String> excluded) {
    if (minimum < 4 || maximum > 53 || minimum > maximum)
      throw new IllegalArgumentException("Size range 4..53");
    var ready = bank.select(category, minimum, maximum, seed, excluded);
    if (ready.isPresent()) return ready.get();
    return search(category, minimum, maximum, seed, bank, excluded);
  }

  public DeletionGenerator.Outcome search(
      PlayDifficulty.Category category,
      int minimum,
      int maximum,
      long seed,
      PuzzleBank bank,
      Set<String> excluded) {
    if (minimum < 4 || maximum > 53 || minimum > maximum)
      throw new IllegalArgumentException("Size range 4..53");
    var random = new Random(seed);
    int n = minimum + random.nextInt(maximum - minimum + 1);
    var ledger = new GenerationWork();
    if (Thread.currentThread().isInterrupted())
      return new DeletionGenerator.Outcome(
          null, null, new DeletionGenerator.Stats(0, 0, 0, 0, 0, 0, 0, 0, Map.of()), "INTERRUPTED");
    var parents =
        new ArrayList<>(
            bank.entries().stream()
                .filter(e -> e.band() == category.sourceBand())
                .filter(
                    e ->
                        e.puzzle().topology().nodeCount() >= Math.max(4, minimum - 3)
                            && e.puzzle().topology().nodeCount() <= Math.min(53, maximum + 3))
                .toList());
    Collections.shuffle(parents, random);
    ledger.count("freshConstructions");
    var fresh = VariedPlaneGraphs.create(n, seed);
    var original = new CriticalCatalog.Entry(VERSION + ";fresh=" + n, fresh);
    var bankGroups = new ArrayList<Iterator<CriticalCatalog.Entry>>();
    for (var entry : parents.subList(0, Math.min(5, parents.size()))) {
      var p = entry.puzzle();
      long editSeed = random.nextLong();
      bankGroups.add(
          SourceSchedule.deferred(
              () -> {
                ledger.count("bankParentsExpanded");
                return edited(
                    new PlaneTriangulation(p.topology(), p.layout()),
                    minimum,
                    maximum,
                    editSeed,
                    VERSION + ";parent=" + p.logicalHash(),
                    ledger);
              }));
    }
    var sources =
        SourceSchedule.interleave(
            List.of(
                List.of(original).iterator(),
                SourceSchedule.deferred(
                    () ->
                        edited(
                            fresh,
                            minimum,
                            maximum,
                            seed ^ 0x758aL,
                            VERSION + ";fresh=" + n,
                            ledger)),
                SourceSchedule.interleave(bankGroups)));
    var acceptance =
        new DeletionGenerator.Acceptance() {
          public boolean skipGraph(GraphTopology graph) {
            boolean skip = excluded.contains(TopologyFingerprint.of(graph));
            if (skip) ledger.count("seenTopologies");
            return skip;
          }

          public CertifiedPuzzle.Check evaluate(Puzzle puzzle) {
            int count = puzzle.topology().nodeCount();
            if (count < minimum || count > maximum)
              return CertifiedPuzzle.Check.rejected("OUT_OF_RANGE");
            return CertifiedPuzzle.verify(puzzle, category.sourceBand(), category, ledger);
          }
        };
    var seen = new HashSet<String>();
    var counts = new TreeMap<String, Integer>();
    int attempts = 0, graphs = 0, deleted = 0, bridges = 0, checks = 0, states = 0, hitting = 0;
    long nodes = 0;
    String failure = "NO_PLAY_MATCH";
    while (attempts < 5 && !Thread.currentThread().isInterrupted() && sources.hasNext()) {
      var next = SourceSchedule.nextUsable(sources, minimum, maximum, seen, ledger);
      if (next.isEmpty()) break;
      var source = next.get();
      ledger.count("sourcesSearched");
      ledger.source(source.id());
      ledger.count(
          source.id().contains(";parent=")
              ? "bankEditedSources"
              : source.id().contains(";edit=") ? "freshEditedSources" : "freshOriginalSources");
      long attemptSeed = GenerationSeeds.attemptSeed(seed, attempts++);
      var result =
          new DeletionGenerator()
              .generateFrom(
                  category.sourceBand(),
                  attemptSeed,
                  new DeletionGenerator.Budget(1, 18, 200, 50000, 12000, 3000),
                  source,
                  acceptance,
                  ledger);
      var s = result.stats();
      graphs += s.graphs();
      deleted += s.deletedEdges();
      bridges += s.bridgeSkips();
      checks += s.exactChecks();
      nodes += s.exactNodes();
      states += s.proofStates();
      hitting += s.hittingSetNodes();
      s.outcomes().forEach((k, v) -> counts.merge(k, v, Integer::sum));
      counts.merge(
          source.id().contains(";edit=") ? "NEIGHBOUR_EDIT" : "FRESH_GRID", 1, Integer::sum);
      if (!result.success()) {
        failure = result.failure();
        if (failure.equals("FINAL_BUDGET_EXHAUSTED") || failure.equals("INTERRUPTED")) break;
        continue;
      }
      var accepted = result.puzzle();
      var puzzle =
          new Puzzle(
              accepted.topology(),
              accepted.givens(),
              accepted.rules(),
              new PuzzleProvenance(
                  DeletionGenerator.VERSION,
                  seed,
                  attempts - 1,
                  "search="
                      + VERSION
                      + ";range="
                      + minimum
                      + "-"
                      + maximum
                      + ";play="
                      + category
                      + ";"
                      + accepted.provenance().generationSpec()),
              accepted.layout());
      return new DeletionGenerator.Outcome(
          puzzle,
          result.proof(),
          new DeletionGenerator.Stats(
              attempts,
              graphs,
              deleted,
              bridges,
              checks,
              nodes,
              states,
              hitting,
              counts,
              ledger.snapshot(),
              ledger.sources()),
          null,
          result.certificate().withPuzzle(puzzle));
    }
    return new DeletionGenerator.Outcome(
        null,
        null,
        new DeletionGenerator.Stats(
            attempts,
            graphs,
            deleted,
            bridges,
            checks,
            nodes,
            states,
            hitting,
            counts,
            ledger.snapshot(),
            ledger.sources()),
        Thread.currentThread().isInterrupted() ? "INTERRUPTED" : failure);
  }

  private static List<CriticalCatalog.Entry> edited(
      PlaneTriangulation parent,
      int minimum,
      int maximum,
      long seed,
      String provenance,
      GenerationWork ledger) {
    var variants =
        new ArrayList<>(GraphNeighbourhood.variants(parent, minimum, maximum, seed, ledger));
    variants.removeIf(v -> v.edit().equals("original"));
    Collections.shuffle(variants, new Random(seed));
    return variants.stream()
        .map(v -> new CriticalCatalog.Entry(provenance + ";edit=" + v.edit(), v.candidate()))
        .toList();
  }
}
