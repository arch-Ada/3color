package io.threecolor.generation;

import io.threecolor.deduction.*;
import io.threecolor.difficulty.*;
import io.threecolor.model.*;
import io.threecolor.solve.*;

/** Immutable final evidence, never an API DTO. Only this verifier can construct it. */
public final class CertifiedPuzzle {
  public static final int EXACT_NODES = 200000, PROOF_STATES_PER_LEVEL = 20000;
  private final Puzzle puzzle;
  private final String logicalHash;
  private final ProofLevels.Classification proof;
  private final DeductionTrace trace;
  private final PlayDifficulty.Rating rating;
  private final String proofVersion = ProofLevels.VERSION;
  private final String playerVersion = PlayDifficulty.VERSION;
  private final DeductionConfiguration explanationConfig = DeductionConfiguration.defaults();

  private CertifiedPuzzle(
      Puzzle puzzle,
      ProofLevels.Classification proof,
      DeductionTrace trace,
      PlayDifficulty.Rating rating) {
    this.puzzle = puzzle;
    this.logicalHash = puzzle.logicalHash();
    this.proof = proof;
    this.trace = trace;
    this.rating = rating;
  }

  public Puzzle puzzle() {
    return puzzle;
  }

  public ProofLevels.Classification proof() {
    return proof;
  }

  public DeductionTrace trace() {
    return trace;
  }

  public PlayDifficulty.Rating rating() {
    return rating;
  }

  /** Provenance is not logical or geometric evidence. Everything else must match. */
  public CertifiedPuzzle withPuzzle(Puzzle changed) {
    if (!logicalHash.equals(changed.logicalHash())
        || puzzle.explanationModel() != changed.explanationModel()
        || !puzzle.layout().equals(changed.layout())
        || !proofVersion.equals(ProofLevels.VERSION)
        || !playerVersion.equals(PlayDifficulty.VERSION)
        || !explanationConfig.equals(DeductionConfiguration.defaults()))
      throw new IllegalArgumentException("Certificate does not bind this puzzle");
    return new CertifiedPuzzle(changed, proof, trace, rating);
  }

  /** Compact bank evidence: deliberately does not retain the full explanation. */
  public static final class Evidence {
    private final Puzzle puzzle;
    private final ProofLevels.Classification proof;
    private final PlayDifficulty.Rating rating;
    private final String logicalHash, proofVersion, playerVersion;
    private final DeductionConfiguration explanationConfig;

    public PlayDifficulty.Rating rating() {
      return rating;
    }

    public boolean matches(Puzzle candidate) {
      return logicalHash.equals(candidate.logicalHash())
          && puzzle.explanationModel() == candidate.explanationModel()
          && puzzle.layout().equals(candidate.layout())
          && proofVersion.equals(ProofLevels.VERSION)
          && playerVersion.equals(PlayDifficulty.VERSION)
          && explanationConfig.equals(DeductionConfiguration.defaults());
    }

    private Evidence(CertifiedPuzzle c) {
      puzzle = c.puzzle;
      proof = c.proof;
      rating = c.rating;
      logicalHash = c.logicalHash;
      proofVersion = c.proofVersion;
      playerVersion = c.playerVersion;
      explanationConfig = c.explanationConfig;
    }
  }

  public Evidence evidence() {
    return new Evidence(this);
  }

  static Check explainBank(Evidence evidence, Puzzle puzzle, GenerationWork work) {
    if (evidence == null || !evidence.matches(puzzle))
      throw new IllegalArgumentException("Stale bank evidence");
    if (Thread.currentThread().isInterrupted())
      return new Check(Status.INTERRUPTED, null, "INTERRUPTED");
    if (!work.beginFinal()) return new Check(Status.EXHAUSTED, null, "FINAL_BUDGET_EXHAUSTED");
    work.count("bankEvidenceReuse");
    work.count("explanationChecks");
    var measured =
        ProofLevels.explanationEngine().solveMeasured(puzzle.topology(), puzzle.givens());
    work.add("explanationStates", measured.states());
    work.add("explanationFacts", measured.facts());
    work.add("explanationHypotheses", measured.trace().hypothesesTried());
    if (Thread.currentThread().isInterrupted())
      return new Check(Status.INTERRUPTED, null, "INTERRUPTED");
    if (measured.trace().status() != DeductionTrace.Status.SOLVED)
      return Check.rejected("INCOMPLETE_TRACE");
    var rating = PlayDifficulty.rate(evidence.proof.band(), measured.trace());
    if (!rating.equals(evidence.rating))
      throw new IllegalStateException("Bank explanation model changed");
    work.count("finalAccepted");
    return new Check(
        Status.ACCEPTED,
        new CertifiedPuzzle(puzzle, evidence.proof, measured.trace(), rating),
        null);
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof CertifiedPuzzle c
        && logicalHash.equals(c.logicalHash)
        && puzzle.explanationModel() == c.puzzle.explanationModel()
        && puzzle.layout().equals(c.puzzle.layout())
        && puzzle.provenance().equals(c.puzzle.provenance())
        && proofVersion.equals(c.proofVersion)
        && playerVersion.equals(c.playerVersion)
        && explanationConfig.equals(c.explanationConfig)
        && proof.equals(c.proof)
        && trace.equals(c.trace)
        && rating.equals(c.rating);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(
        logicalHash,
        puzzle.explanationModel(),
        puzzle.layout(),
        puzzle.provenance(),
        proofVersion,
        playerVersion,
        explanationConfig,
        proof,
        trace,
        rating);
  }

  public enum Status {
    ACCEPTED,
    REJECTED,
    EXHAUSTED,
    INTERRUPTED
  }

  public record Check(Status status, CertifiedPuzzle certificate, String reason) {
    public static Check rejected(String reason) {
      return new Check(Status.REJECTED, null, reason);
    }
  }

  /** Fresh verification from immutable input, independent of construction witnesses/caches. */
  public static Check verify(
      Puzzle puzzle, DifficultyBand band, PlayDifficulty.Category category, GenerationWork work) {
    if (puzzle.explanationModel() != ExplanationModel.PLAYER_V1)
      return Check.rejected("EXPLANATION_MODEL");
    if (Thread.currentThread().isInterrupted())
      return new Check(Status.INTERRUPTED, null, "INTERRUPTED");
    if (!work.beginFinal()) return new Check(Status.EXHAUSTED, null, "FINAL_BUDGET_EXHAUSTED");
    work.count("finalGeometryChecks");
    if (puzzle.givens().colors().size() > Math.max(2, puzzle.topology().nodeCount() / 3))
      return Check.rejected("CLUE_CAP");
    if (!GraphNeighbourhood.valid(new PlaneTriangulation(puzzle.topology(), puzzle.layout())))
      return Check.rejected("GEOMETRY");
    // Explanation is also a cheap(er) player-category filter before exact verification.
    work.count("explanationChecks");
    var measured =
        ProofLevels.explanationEngine().solveMeasured(puzzle.topology(), puzzle.givens());
    var trace = measured.trace();
    work.add("explanationStates", measured.states());
    work.add("explanationFacts", measured.facts());
    work.add("explanationHypotheses", trace.hypothesesTried());
    if (Thread.currentThread().isInterrupted())
      return new Check(Status.INTERRUPTED, null, "INTERRUPTED");
    if (trace.status() == DeductionTrace.Status.BUDGET_EXHAUSTED)
      return Check.rejected("EXPLANATION_UNKNOWN");
    if (trace.status() != DeductionTrace.Status.SOLVED) return Check.rejected("INCOMPLETE_TRACE");
    var rating = PlayDifficulty.rate(band, trace);
    if (category != null && rating.category() != category)
      return Check.rejected("PLAY_FILTER_REJECTED");
    work.count("finalExactChecks");
    var exact =
        new ExactSolver()
            .uniqueness(
                puzzle.topology(), puzzle.givens(), EXACT_NODES, SolutionEquivalence.LABELED);
    work.add("finalExactNodes", exact.stats().searchNodes());
    if (Thread.currentThread().isInterrupted())
      return new Check(Status.INTERRUPTED, null, "INTERRUPTED");
    if (exact.status() != UniquenessResult.UNIQUE)
      return Check.rejected(
          exact.status() == UniquenessResult.UNKNOWN ? "FINAL_EXACT_UNKNOWN" : "FINAL_NOT_UNIQUE");
    work.count("finalProofChecks");
    var proof =
        new ProofLevels().classify(puzzle.topology(), puzzle.givens(), PROOF_STATES_PER_LEVEL);
    work.add("finalProofStates", proof.states());
    if (Thread.currentThread().isInterrupted())
      return new Check(Status.INTERRUPTED, null, "INTERRUPTED");
    if (proof.band() != band)
      return Check.rejected(proof.band() == null ? "FINAL_PROOF_UNKNOWN" : "FINAL_BAND_REJECTED");
    work.count("finalAccepted");
    return new Check(Status.ACCEPTED, new CertifiedPuzzle(puzzle, proof, trace, rating), null);
  }
}
