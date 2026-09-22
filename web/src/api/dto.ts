export type Color = 'RED' | 'GREEN' | 'BLUE';
export type Band = 'VERY_EASY' | 'EASY' | 'MEDIUM' | 'HARD' | 'EXPERT' | 'CHALLENGING';
export type SizeCategory = 'MINI' | 'SMALL' | 'MEDIUM' | 'LARGE' | 'VERY_LARGE';
export interface GenerationRequest {
  size?: SizeCategory;
  excludeTopologies?: string[];
  seed: string | null;
  nodeCount?: number;
  difficulty: Band;
}
export type ColorMap = Record<number, Color>;
export interface Provenance {
  generatorVersion: string;
  masterSeed: string;
  attemptIndex: number;
  generationSpec: string;
}
export interface Puzzle {
  nodeCount: number;
  edges: { a: number; b: number }[];
  givens: ColorMap;
  layout: { x: number; y: number }[];
  rules: 'CLASSIC_V1';
  explanationModel: 'PLAYER_V1' | 'BASIC_V1';
  provenance: Provenance;
  logicalHash: string;
}
export interface Difficulty {
  modelVersion: string;
  band: Band;
  score: number;
  totalDeductionCount: number;
  hardestRuleTier: number;
  ruleUsageCounts: Record<string, number>;
  weightedRuleCost: number;
  maximumProofDepth: number;
  hypothesisSteps: number;
  averageAvailableDeductions: number;
  minimumAvailableDeductions: number;
  humanSolved: boolean;
  profile?: DifficultyProfile;
}
export interface Metrics {
  n: number;
  m: number;
  averageDegree: number;
  minDegree: number;
  maxDegree: number;
  degreeDistribution: Record<string, number>;
  triangleCount: number;
  connectedComponents: number;
  cycleRank: number;
}
export interface ProofLevelResult {
  status: 'SOLVED' | 'STALLED' | 'CONTRADICTION' | 'UNKNOWN';
  states: number;
  hypotheses: number;
  refutations: number;
  refutationRounds: number;
}
export interface Generated {
  supplyId?: string | null;
  playDifficulty?: {
    modelVersion: string;
    category: Band | null;
    rejection: string | null;
    contradictionCount: number;
    maximumContradictionSteps: number;
    maximumContradictionVertices: number;
    maximumPathLength: number;
  };
  topologyKey?: string;
  proof?: {
    modelVersion?: string;
    band: Band;
    p0: ProofLevelResult;
    p1: ProofLevelResult | null;
    p2: ProofLevelResult | null;
  };
  search?: {
    graphs: number;
    exactChecks: number;
    proofStates: number;
    outcomes: Record<string, number>;
    work?: Record<string, number>;
    sources?: string[];
  } | null;
  puzzle: Puzzle;
  generation: Provenance;
  difficulty: Difficulty;
  metrics: Metrics;
}
export interface Step {
  id: number;
  ruleId: string;
  tier: number;
  node: number;
  beforeMask: number;
  afterMask: number;
  premises: number[];
  witnesses: number[];
  explanationKey: string;
  arguments: Record<string, number>;
  hypothesisEvidence: Step[];
  conclusion?: LogicalConclusion;
  effort?: {
    score: number;
    witnessCount: number;
    pathLength: number;
    proofDepth: number;
    implicationLength: number;
    interactingVertices: number;
    hypothesisDepth: number;
  };
  implicationChain?: { source: number; sourceMask: number; target: number; targetMask: number }[];
}
export interface Trace {
  steps: Step[];
  finalDomains: number[];
  status: string;
  availableDeductions: number[];
  hypothesesTried: number;
  frontiers?: {
    selectedFactId: number;
    size: number;
    hypothesesDeferred: boolean;
    budgetExhausted: boolean;
  }[];
}
export type ReferenceId = 'dependency' | 'shortcut' | 'larger';
export interface ReferencePuzzle {
  id: ReferenceId;
  generated: Generated;
}
export interface Analysis {
  trace: Trace;
  difficulty: Difficulty;
  metrics: Metrics;
}
export type HintLevel = 'NUDGE' | 'REASON' | 'ANSWER';
export interface Hint {
  level: HintLevel;
  status: string;
  deduction: Step | null;
  supportingSteps: Step[];
  explanation?: HintExplanation | null;
}

export interface DifficultyProfile {
  substantialCausalDepth?: number;
  maximumRuleTier?: number;
  cheapFrontierBreadths?: number[];
  meanCheapFrontier?: number;
  p75CheapFrontier?: number;
  crowdedCheapFrontierFraction?: number;
  initialCheapClosureProgressFraction?: number;
  remainingCheapCoreFraction?: number;
  nonCheapUnlocks?: number;
  substantialRootFraction?: number;
  largestCheapCascadeShare?: number;
  totalInformation: number;
  initialInformation: number;
  completionFraction: number;
  eventCount: number;
  medianEffort: number;
  p75Effort: number;
  p90Effort: number;
  maximumEffort: number;
  substantialEvents: number;
  hardEvents: number;
  advancedEvents: number;
  nontrivialProgressFraction: number;
  longestTrivialProgressFraction: number;
  substantialEventsByRegion: number[];
  largestEventEffortShare: number;
  relationFacts: number;
  timeline: {
    rootFactId: number;
    ruleId: string;
    progressStart: number;
    progressEnd: number;
    effort: number;
    informationGain: number;
    rootInformation?: number;
    cascadeInformation?: number;
    cascadeFacts: number;
    relationFacts: number;
  }[];
}
export type LogicalConclusion =
  | { kind: 'NARROW_DOMAIN'; node: number; mask: number }
  | { kind: 'EQUAL' | 'NOT_EQUAL'; a: number; b: number }
  | { kind: 'CONTRADICTION' };

export interface HintExplanation {
  reasoningType: string;
  primaryTargets: number[];
  conclusion: LogicalConclusion;
  focusVertices: number[];
  focusEdges: { a: number; b: number }[];
  orderedPath: number[];
  assumption: { node: number; mask: number } | null;
  contradictionPoint: number | null;
  walkthrough: WalkthroughStep[];
}
export interface WalkthroughStep {
  kind: 'ASSUMPTION' | 'IMPLICATION' | 'DEDUCTION' | 'CONTRADICTION' | 'CONCLUSION';
  node: number | null;
  mask: number | null;
  deduction: Step | null;
  vertices: number[];
  edges: { a: number; b: number }[];
}
