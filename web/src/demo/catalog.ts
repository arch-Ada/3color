import type {
  Analysis,
  ColorMap,
  Generated,
  GenerationRequest,
  Hint,
  HintExplanation,
  HintLevel,
  WalkthroughStep,
  Step,
  Color,
} from '../api/dto';
import { compactWalkthrough, neededFacts, withWalkthrough, compareHints } from '../game/hintProof';
import { localHints } from '../game/localHints';
import { ApiError } from '../api/error';

export interface Entry {
  id: string;
  file: string;
  category: string;
  nodeCount: number;
  seed: string;
  topologyKey: string;
  logicalHash: string;
}
export interface Manifest {
  version: number;
  contentHash: string;
  entries: Entry[];
}
export interface Payload {
  generated: Generated;
  analysis: Analysis;
  hints: {
    stepId: number;
    segments?: { stepId: number; walkthrough: number[] }[];
    explanation: Omit<HintExplanation, 'walkthrough'> & { walkthrough: number[] };
  }[];
  walkthrough: WalkthroughStep[];
}
const ranges = {
  MINI: [4, 13],
  SMALL: [14, 23],
  MEDIUM: [24, 33],
  LARGE: [34, 43],
  VERY_LARGE: [44, 53],
};
export function selectEntry(
  entries: Entry[],
  request: GenerationRequest,
  random = Math.random(),
): Entry {
  const [min, max] = request.size
    ? ranges[request.size]
    : [request.nodeCount ?? 0, request.nodeCount ?? 0];
  let candidates = entries.filter(
    (e) => e.category === request.difficulty && e.nodeCount >= min && e.nodeCount <= max,
  );
  if (request.seed?.trim()) {
    const seed = BigInt(request.seed.trim()).toString();
    candidates = candidates.filter((e) => e.seed === seed);
    if (!candidates.length) throw new ApiError('Seed not included', 404, 'DEMO_SEED');
    // Explicit replay is stable within this immutable collection, independent of play history.
    return candidates[0];
  }
  const excluded = new Set(request.excludeTopologies ?? []);
  candidates = candidates.filter((e) => !excluded.has(e.topologyKey));
  if (!candidates.length) throw new ApiError('Collection exhausted', 409, 'DEMO_EXHAUSTED');
  return candidates[Math.min(candidates.length - 1, Math.floor(random * candidates.length))];
}

const colorMask: Record<Color, number> = { RED: 1, GREEN: 2, BLUE: 4 };

/** Present direct adjacency as one human step, using only verified current colours. */
function directHint(payload: Payload, colors: ColorMap, level: HintLevel): Hint | null {
  const puzzle = payload.generated.puzzle;
  const neighbours: number[][] = Array.from({ length: puzzle.nodeCount }, () => []);
  for (const { a, b } of puzzle.edges) {
    neighbours[a].push(b);
    neighbours[b].push(a);
  }
  for (let node = 0; node < puzzle.nodeCount; node++) {
    if (colors[node]) continue;
    const byColor = new Map<number, number>();
    for (const neighbour of neighbours[node]) {
      const mask = colorMask[colors[neighbour]];
      // A wrong player move must not become a premise for a supposedly correct hint.
      if (mask && mask === payload.analysis.trace.finalDomains[neighbour])
        byColor.set(mask, neighbour);
    }
    if (byColor.size !== 2) continue;
    const [[maskA, a], [maskB, b]] = [...byColor];
    const mask = 7 & ~(maskA | maskB);
    const edges = [
      { a: node, b: a },
      { a: node, b },
    ];
    const triangle = neighbours[a].includes(b);
    if (triangle) edges.push({ a, b });
    const deduction: Step = {
      id: -1,
      ruleId: 'adjacent_color_elimination',
      tier: 1,
      node,
      beforeMask: 7,
      afterMask: mask,
      premises: [],
      witnesses: [a, b],
      explanationKey: triangle ? 'triangle_completion' : 'two_colored_neighbors',
      arguments: { neighbourA: a, neighbourB: b, maskA, maskB },
      hypothesisEvidence: [],
      conclusion: { kind: 'NARROW_DOMAIN', node, mask },
    };
    return {
      level,
      status: 'AVAILABLE',
      deduction,
      supportingSteps: [],
      explanation: {
        reasoningType: deduction.ruleId,
        primaryTargets: [node],
        conclusion: deduction.conclusion!,
        focusVertices: [node, a, b],
        focusEdges: edges,
        orderedPath: [],
        assumption: null,
        contradictionPoint: null,
        walkthrough: [{ kind: 'DEDUCTION', node, mask, deduction, vertices: [node, a, b], edges }],
      },
    };
  }
  return null;
}

/** Prefer immediate board deductions, then a certified proof from the starting clues. */
export function savedHint(payload: Payload, current: ColorMap, level: HintLevel): Hint {
  const puzzle = payload.generated.puzzle;
  const colors = { ...puzzle.givens, ...current };
  const empty = (status: string): Hint => ({ level, status, deduction: null, supportingSteps: [] });
  if (Object.entries(puzzle.givens).some(([n, c]) => colors[Number(n)] !== c))
    return empty('CONFLICT');
  const conflict = puzzle.edges.find(({ a, b }) => colors[a] && colors[a] === colors[b]);
  if (conflict) {
    const vertices = [conflict.a, conflict.b];
    return {
      ...empty('CONFLICT'),
      explanation: {
        reasoningType: 'edge_conflict',
        primaryTargets: vertices,
        conclusion: { kind: 'CONTRADICTION' },
        focusVertices: vertices,
        focusEdges: [conflict],
        orderedPath: [],
        assumption: null,
        contradictionPoint: null,
        walkthrough: [],
      },
    };
  }
  // Corrections use the complete clue proof, never another player entry as a premise.
  const corrections: Hint[] = [];
  for (const h of payload.hints) {
    const step = payload.analysis.trace.steps.find((s) => s.id === h.stepId)!;
    if (
      !colors[step.node] ||
      colorMask[colors[step.node]] === payload.analysis.trace.finalDomains[step.node]
    )
      continue;
    const needed = neededFacts(payload.analysis.trace.steps, h.stepId, puzzle.givens);
    const walkthrough = h.explanation.walkthrough.map((i) => payload.walkthrough[i]);
    corrections.push({
      level,
      status: 'CORRECTION',
      deduction: step,
      supportingSteps: payload.analysis.trace.steps.filter((s) => needed.has(s.id)),
      explanation: withWalkthrough({ ...h.explanation, walkthrough }, walkthrough),
    });
  }
  if (corrections.length) return corrections.sort(compareHints)[0];
  const direct = directHint(payload, colors, level);
  if (direct) return direct;
  const known: ColorMap = {};
  for (const [n, color] of Object.entries(colors))
    if (colorMask[color] === payload.analysis.trace.finalDomains[Number(n)])
      known[Number(n)] = color;
  const candidates: Hint[] = localHints(puzzle, known, level);
  for (const h of payload.hints) {
    const step = payload.analysis.trace.steps.find((s) => s.id === h.stepId)!;
    if (colorMask[colors[step.node]] === step.afterMask) continue;
    const needed = neededFacts(payload.analysis.trace.steps, h.stepId, known);
    const indices = h.segments
      ? h.segments.filter((s) => needed.has(s.stepId)).flatMap((s) => s.walkthrough)
      : h.explanation.walkthrough;
    const walkthrough = compactWalkthrough(
      puzzle,
      indices.map((i) => payload.walkthrough[i]),
    );
    candidates.push({
      level,
      status: 'AVAILABLE',
      deduction: step,
      supportingSteps: payload.analysis.trace.steps.filter((s) => needed.has(s.id)),
      explanation: withWalkthrough({ ...h.explanation, walkthrough }, walkthrough),
    });
  }
  return candidates.sort(compareHints)[0] ?? empty('SOLVED');
}
