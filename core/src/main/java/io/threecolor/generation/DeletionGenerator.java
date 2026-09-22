package io.threecolor.generation;

import io.threecolor.deduction.ProofLevels;
import io.threecolor.difficulty.DifficultyBand;
import io.threecolor.layout.LayoutQuality;
import io.threecolor.model.*;
import io.threecolor.solve.*;
import io.threecolor.validation.*;
import java.util.*;

/** Bounded experimental search through connected subgraphs of seeded plane triangulations. */
public final class DeletionGenerator {
  public static final String VERSION = "triangulation-deletion-v6";

  public record Budget(
      int attempts,
      int graphs,
      int exactChecks,
      long exactNodes,
      int proofStates,
      int statesPerLevel) {
    public Budget {
      if (attempts < 1
          || graphs < 1
          || exactChecks < 1
          || exactNodes < 1
          || proofStates < 1
          || statesPerLevel < 1) throw new IllegalArgumentException("Positive budgets required");
    }

    public static Budget defaults() {
      return new Budget(6, 180, 1200, 200000, 60000, 6000);
    }
  }

  public record Stats(
      int attempts,
      int graphs,
      int deletedEdges,
      int bridgeSkips,
      int exactChecks,
      long exactNodes,
      int proofStates,
      int hittingSetNodes,
      Map<String, Integer> outcomes,
      Map<String, Long> work,
      List<String> sources) {
    public Stats(
        int attempts,
        int graphs,
        int deletedEdges,
        int bridgeSkips,
        int exactChecks,
        long exactNodes,
        int proofStates,
        int hittingSetNodes,
        Map<String, Integer> outcomes,
        Map<String, Long> work) {
      this(
          attempts,
          graphs,
          deletedEdges,
          bridgeSkips,
          exactChecks,
          exactNodes,
          proofStates,
          hittingSetNodes,
          outcomes,
          work,
          List.of());
    }

    public Stats(
        int attempts,
        int graphs,
        int deletedEdges,
        int bridgeSkips,
        int exactChecks,
        long exactNodes,
        int proofStates,
        int hittingSetNodes,
        Map<String, Integer> outcomes) {
      this(
          attempts,
          graphs,
          deletedEdges,
          bridgeSkips,
          exactChecks,
          exactNodes,
          proofStates,
          hittingSetNodes,
          outcomes,
          Map.of());
    }

    public Stats {
      outcomes = Collections.unmodifiableMap(new TreeMap<>(outcomes));
      work = Collections.unmodifiableMap(new TreeMap<>(work));
      sources = List.copyOf(sources);
    }
  }

  public record Outcome(
      Puzzle puzzle,
      ProofLevels.Classification proof,
      Stats stats,
      String failure,
      CertifiedPuzzle certificate) {
    public Outcome(Puzzle puzzle, ProofLevels.Classification proof, Stats stats, String failure) {
      this(puzzle, proof, stats, failure, null);
    }

    public Outcome {
      if (certificate != null) certificate.withPuzzle(puzzle);
    }

    public boolean success() {
      return puzzle != null;
    }
  }

  /** Rejection continues this chain. A graph veto skips all clues, never later deletions. */
  public interface Acceptance {
    default boolean skipGraph(GraphTopology graph) {
      return false;
    }

    CertifiedPuzzle.Check evaluate(Puzzle puzzle);
  }

  private static final class Work {
    final Budget budget;
    final GenerationWork ledger;
    int attempts, graphs, deleted, bridges, checks, states, hittingNodes;
    long nodes;
    final Map<String, Integer> outcomes = new TreeMap<>();

    Work(Budget budget, GenerationWork ledger) {
      this.budget = budget;
      this.ledger = ledger;
    }

    boolean exhausted() {
      return Thread.currentThread().isInterrupted()
          || hittingNodes >= 100000
          || checks >= budget.exactChecks()
          || nodes >= budget.exactNodes()
          || states >= budget.proofStates();
    }

    void count(String key) {
      outcomes.merge(key, 1, Integer::sum);
    }

    Stats stats() {
      return new Stats(
          attempts,
          graphs,
          deleted,
          bridges,
          checks,
          nodes,
          states,
          hittingNodes,
          outcomes,
          ledger.snapshot(),
          ledger.sources());
    }
  }

  private record Candidate(PartialColoring clues, ProofLevels.Classification proof) {}

  public Outcome generate(DifficultyBand band, int n, long seed) {
    return generate(band, n, seed, Budget.defaults());
  }

  public Outcome generate(DifficultyBand band, int n, long seed, Budget budget) {
    return generate(band, n, seed, budget, null, null, new GenerationWork());
  }

  public Outcome generateFrom(
      DifficultyBand band, long seed, Budget budget, CriticalCatalog.Entry source) {
    return generate(
        band,
        source.candidate().graph().nodeCount(),
        seed,
        budget,
        source,
        null,
        new GenerationWork());
  }

  public Outcome generateFrom(
      DifficultyBand band,
      long seed,
      Budget budget,
      CriticalCatalog.Entry source,
      Acceptance acceptance,
      GenerationWork ledger) {
    return generate(
        band, source.candidate().graph().nodeCount(), seed, budget, source, acceptance, ledger);
  }

  private Outcome generate(
      DifficultyBand band,
      int n,
      long seed,
      Budget budget,
      CriticalCatalog.Entry source,
      Acceptance acceptance,
      GenerationWork ledger) {
    if (!List.of(
            DifficultyBand.VERY_EASY,
            DifficultyBand.EASY,
            DifficultyBand.MEDIUM,
            DifficultyBand.HARD)
        .contains(band))
      throw new IllegalArgumentException("Prototype bands: VERY_EASY, EASY, MEDIUM, HARD");
    if (n < (source == null ? 6 : 4) || n > (source == null ? 40 : 53))
      throw new IllegalArgumentException("Prototype sizes: 6..40, or supplied sources 4..53");
    var work = new Work(budget, ledger);
    for (int attempt = 0; attempt < budget.attempts() && !work.exhausted(); attempt++) {
      work.attempts++;
      long attemptSeed = GenerationSeeds.attemptSeed(seed, attempt);
      var catalog = CriticalCatalog.atSize(n);
      boolean advanced = band == DifficultyBand.MEDIUM || band == DifficultyBand.HARD;
      var selected =
          source != null
              ? source
              : (advanced && attempt % 3 == 2 && !catalog.isEmpty()
                  ? catalog.get(Math.floorMod(attemptSeed, catalog.size()))
                  : null);
      boolean mixed = selected == null && advanced && attempt % 3 == 0;
      var triangulation =
          selected != null
              ? selected.candidate()
              : mixed
                  ? MixedFaceRegions.create(n, attemptSeed)
                  : PlaneTriangulation.create(n, attemptSeed);
      String sourceId =
          selected != null ? selected.id() : mixed ? MixedFaceRegions.VERSION : "grid";
      work.count(
          sourceId.startsWith(MixedFaceRegions.VERSION)
              ? "MIXED_FACE_ATTEMPT"
              : sourceId.equals("grid") ? "GRID_ATTEMPT" : "CRITICAL_ATTEMPT");
      if (sourceId.startsWith(MixedFaceRegions.VERSION))
        for (var face : PlaneFaces.bounded(triangulation.graph(), triangulation.layout()))
          work.count("SOURCE_FACE_" + face.vertices().size());
      var order = new ArrayList<>(triangulation.graph().edges());
      Collections.shuffle(order, new Random(attemptSeed ^ 0x514e77aaL));
      var edges = new ArrayList<>(triangulation.graph().edges());
      var random = new Random(attemptSeed ^ 0x443dc1e2L);
      Coloring target = null;
      int cursor = 0;
      boolean previousUncolourable = false;
      Edge lastRemoved = null;
      while (!work.exhausted() && work.graphs < budget.graphs()) {
        var graph = new GraphTopology(n, edges);
        work.graphs++;
        ledger.count("graphStates");
        boolean skip = acceptance != null && acceptance.skipGraph(graph);
        if (skip) {
          work.count("SKIPPED_TOPOLOGY");
          previousUncolourable = false;
        }
        Edge boundaryEquality = null;
        if (target == null && !skip) {
          ledger.count("preColourabilityStates");
          var solved = exact(graph, PartialColoring.empty(), false, work);
          if (solved != null && !solved.solutions().isEmpty()) {
            target = solved.solutions().getFirst();
            if (previousUncolourable && lastRemoved != null) {
              // Restoring this edge would be impossible: its endpoints must agree.
              boundaryEquality = lastRemoved;
              work.count("BOUNDARY_EQUALITY");
              if (target.colors().get(lastRemoved.a().value())
                  != target.colors().get(lastRemoved.b().value()))
                throw new IllegalStateException("Invalid colourability-boundary witness");
            }
          } else {
            previousUncolourable = solved != null && !solved.budgetExhausted();
            work.count(previousUncolourable ? "UNCOLOURABLE" : "COLOURABILITY_UNKNOWN");
          }
        }
        if (target != null && !skip) {
          ledger.count("colourableStates");
          // The target remains proper throughout this deletion chain.
          ledger.count("candidateClearanceChecks");
          var quality = LayoutQuality.measure(graph, triangulation.layout());
          if (quality.minimumVertexDistance() < .35 / Math.sqrt(n)
              || quality.minimumEdgeClearance() < .035 / Math.sqrt(n))
            work.count("LAYOUT_CLEARANCE");
          else {
            for (int clueOrder = 0; clueOrder < 2 && !work.exhausted(); clueOrder++) {
              ledger.count("clueSearches");
              var candidate =
                  clues(
                      graph, target, band, random, clueOrder == 0 ? boundaryEquality : null, work);
              if (candidate != null && !work.exhausted()) {
                var proof = candidate.proof();
                if (acceptance == null) {
                  // Compatibility route for callers requesting only an internal band.
                  var exact = exact(graph, candidate.clues(), true, work);
                  proof = classify(graph, candidate.clues(), work);
                  if (exact == null
                      || exact.status() != UniquenessResult.UNIQUE
                      || !matches(proof, band)) continue;
                }
                var puzzle =
                    new Puzzle(
                        graph,
                        candidate.clues(),
                        RuleSet.CLASSIC_V1,
                        new PuzzleProvenance(
                            VERSION,
                            seed,
                            attempt,
                            "band="
                                + band
                                + ";nodes="
                                + n
                                + ";budget="
                                + budget
                                + ";source="
                                + sourceId
                                + ";proof="
                                + ProofLevels.VERSION),
                        triangulation.layout());
                ledger.count("candidateGeometryChecks");
                if (!new PuzzleValidator().validate(puzzle, true, false, false, 1).valid()) {
                  work.count("GEOMETRY");
                  continue;
                }
                if (acceptance != null) {
                  var decision = acceptance.evaluate(puzzle);
                  if (decision.status() == CertifiedPuzzle.Status.ACCEPTED) {
                    var certificate = decision.certificate().withPuzzle(puzzle);
                    return new Outcome(
                        puzzle, certificate.proof(), work.stats(), null, certificate);
                  }
                  work.count(decision.reason());
                  ledger.count("rejected." + decision.reason());
                  if (decision.status() == CertifiedPuzzle.Status.EXHAUSTED
                      || decision.status() == CertifiedPuzzle.Status.INTERRUPTED)
                    return new Outcome(null, null, work.stats(), decision.reason());
                  continue;
                }
                return new Outcome(puzzle, proof, work.stats(), null);
              }
            }
          }
        }
        boolean deleted = false;
        while (cursor < order.size()) {
          var edge = order.get(cursor++);
          edges.remove(edge);
          if (GraphMetrics.of(new GraphTopology(n, edges)).connectedComponents() == 1) {
            lastRemoved = edge;
            work.deleted++;
            deleted = true;
            break;
          }
          edges.add(edge);
          work.bridges++;
        }
        if (!deleted) break;
      }
      if (work.graphs >= budget.graphs()) break;
    }
    return new Outcome(
        null,
        null,
        work.stats(),
        Thread.currentThread().isInterrupted()
            ? "INTERRUPTED"
            : work.exhausted() || work.graphs >= budget.graphs()
                ? "SEARCH_BUDGET_EXHAUSTED"
                : "NO_MATCH_WITHIN_BUDGET");
  }

  private static SolveResult exact(
      GraphTopology graph, PartialColoring clues, boolean unique, Work work) {
    if (work.exhausted()) return null;
    work.checks++;
    work.ledger.count(unique ? "clueExactChecks" : "colourabilityChecks");
    long limit = Math.min(5000, work.budget.exactNodes() - work.nodes);
    var solver = new ExactSolver();
    var result =
        unique
            ? solver.uniqueness(graph, clues, limit, SolutionEquivalence.LABELED)
            : solver.solve(graph, clues, limit);
    work.nodes += result.stats().searchNodes();
    work.ledger.add(unique ? "clueExactNodes" : "colourabilityNodes", result.stats().searchNodes());
    return result;
  }

  private static ProofLevels.Classification classify(
      GraphTopology graph, PartialColoring clues, Work work) {
    if (work.exhausted()) return null;
    // At most three levels, each with its own explicit share of remaining work.
    int limit =
        Math.min(work.budget.statesPerLevel(), (work.budget.proofStates() - work.states) / 3);
    if (limit < 1) return null;
    work.ledger.count("searchProofChecks");
    var proof = new ProofLevels().classify(graph, clues, limit);
    work.states += proof.states();
    work.ledger.add("searchProofStates", proof.states());
    work.count(proof.band() == null ? "PROOF_UNRESOLVED" : "TIER_" + proof.band());
    if (proof.band() == DifficultyBand.MEDIUM) work.count("MEDIUM_SINGLE_ROUND");
    return proof;
  }

  private static boolean matches(ProofLevels.Classification proof, DifficultyBand band) {
    return proof != null
        && proof.band() == band
        && (band != DifficultyBand.HARD || proof.sustainedHard());
  }

  private static Candidate clues(
      GraphTopology graph,
      Coloring target,
      DifficultyBand band,
      Random random,
      Edge boundaryEquality,
      Work work) {
    var clues = new TreeMap<NodeId, Color>();
    var constraints = new ArrayList<>(DefiningSets.kempeComponents(graph, target));
    var vertexOrder = new ArrayList<Integer>();
    for (int v = 0; v < graph.nodeCount(); v++) vertexOrder.add(v);
    Collections.shuffle(vertexOrder, random);
    if (boundaryEquality != null) {
      // Prefer anchoring one endpoint; the other remains a deduction, not another given.
      int anchor = boundaryEquality.a().value();
      vertexOrder.remove(Integer.valueOf(anchor));
      vertexOrder.addFirst(anchor);
      work.count("BOUNDARY_ANCHOR_ORDER");
    }
    // Counterexample-guided minimum defining set for this target, including palette symmetry.
    while (!work.exhausted()) {
      var cover =
          DefiningSets.minimum(
              constraints,
              vertexOrder,
              Math.max(2, graph.nodeCount() / 3),
              Math.min(10000, 100000 - work.hittingNodes));
      work.hittingNodes += cover.nodes();
      work.ledger.count("hittingSetChecks");
      work.ledger.add("hittingSetNodes", cover.nodes());
      if (cover.status() != DefiningSets.Status.FOUND) {
        work.count(
            cover.status() == DefiningSets.Status.UNKNOWN
                ? "HITTING_SET_UNKNOWN"
                : "CLUE_LOWER_BOUND");
        return null;
      }
      clues.clear();
      for (int v : vertexOrder)
        if ((cover.vertices() & (1L << v)) != 0) clues.put(new NodeId(v), target.colors().get(v));
      if (work.ledger.candidates.contains(candidateKey(graph, new PartialColoring(clues)))) {
        work.count("DUPLICATE_CLUES");
        work.ledger.count("duplicateCandidates");
        return null;
      }
      var result = exact(graph, new PartialColoring(clues), true, work);
      if (result == null || result.status() == UniquenessResult.UNKNOWN) {
        work.count("UNIQUENESS_UNKNOWN");
        return null;
      }
      if (result.status() == UniquenessResult.UNIQUE) {
        work.count("MINIMUM_DEFINING_SET");
        break;
      }
      for (var alternative : result.solutions()) {
        long differences = DefiningSets.disagreement(target, alternative);
        if (differences != 0 && !constraints.contains(differences)) constraints.add(differences);
      }
      work.count("DEFINING_SET_COUNTEREXAMPLE");
    }
    if (work.exhausted()) return null;
    return eligible(graph, clues, band, work);
  }

  private static String candidateKey(GraphTopology graph, PartialColoring clues) {
    // Exact labelled graph+givens identity, deliberately not the structural history fingerprint.
    return graph.nodeCount() + ":" + graph.edges() + ":" + clues.colors();
  }

  private static Candidate eligible(
      GraphTopology graph, SortedMap<NodeId, Color> clues, DifficultyBand band, Work work) {
    if (clues.size() > Math.max(2, graph.nodeCount() / 3)) {
      work.count("TOO_MANY_CLUES");
      return null;
    }
    var partial = new PartialColoring(clues);
    if (!work.ledger.candidates.add(candidateKey(graph, partial))) {
      work.count("DUPLICATE_CLUES");
      work.ledger.count("duplicateCandidates");
      return null;
    }
    work.ledger.count("uniqueCandidates");
    if (band == DifficultyBand.HARD) {
      int limit = Math.min(work.budget.statesPerLevel(), work.budget.proofStates() - work.states);
      if (work.exhausted() || limit < 1) return null;
      var p1 = new ProofLevels().solve(graph, partial, 1, limit);
      work.states += p1.states();
      work.ledger.count("searchProofChecks");
      work.ledger.add("searchProofStates", p1.states());
      var residual = HardResidual.of(p1);
      work.count("HARD_P1_TRIPLES_" + residual.triples());
      if (p1.status() != ProofLevels.Status.STALLED) {
        work.count("HARD_P1_" + p1.status());
        return null;
      }
      if (residual.binaryOnly()) {
        work.count("HARD_2SAT_EXCLUDED");
        return null;
      }
      work.count("HARD_THREE_COLOUR_RESIDUAL");
    }
    var proof = classify(graph, partial, work);
    return matches(proof, band) ? new Candidate(partial, proof) : null;
  }
}
