import type { ColorMap, Hint, HintLevel, Puzzle, Step, WalkthroughStep } from '../api/dto';
import { compactWalkthrough, compareHints, neededFacts, withWalkthrough } from './hintProof';

/** Bounded versions of Java's adjacency, locked-edge, diamond and equality propagation rules.
 * No guesses, solution lookup, nested assumptions, or generator/difficulty policy lives here.
 */
export function localHints(puzzle: Puzzle, known: ColorMap, level: HintLevel, limit = 256): Hint[] {
  const bits = { RED: 1, GREEN: 2, BLUE: 4 };
  const n = puzzle.nodeCount;
  const masks = Array.from({ length: n }, (_, v) => bits[known[v]] ?? 7);
  const neighbours = Array.from({ length: n }, () => new Set<number>());
  for (const { a, b } of puzzle.edges) {
    neighbours[a].add(b);
    neighbours[b].add(a);
  }
  const steps: Step[] = masks.map((mask, node) => ({
    id: node,
    node,
    ruleId: 'given',
    explanationKey: 'given',
    tier: 0,
    beforeMask: 7,
    afterMask: mask,
    premises: [],
    witnesses: [],
    arguments: { colorMask: mask },
    hypothesisEvidence: [],
    conclusion: { kind: 'NARROW_DOMAIN', node, mask },
  }));
  const last = Array.from({ length: n }, (_, i) => i);
  const equalities = new Map<string, { a: number; b: number; id: number }>();
  const roots: number[] = [];
  const narrow = (
    node: number,
    mask: number,
    ruleId: string,
    tier: number,
    witnesses: number[],
    args: Record<string, number>,
    extra: number[] = [],
  ): Step => ({
    id: 0,
    node,
    ruleId,
    explanationKey: ruleId,
    tier,
    beforeMask: masks[node],
    afterMask: mask,
    premises: [...new Set([last[node], ...witnesses.map((v) => last[v]), ...extra])],
    witnesses,
    arguments: args,
    hypothesisEvidence: [],
    conclusion: { kind: 'NARROW_DOMAIN', node, mask },
  });
  for (let round = 0; round < limit; round++) {
    const candidates: Step[] = [];
    for (let a = 0; a < n; a++)
      if ([1, 2, 4].includes(masks[a]))
        for (const b of neighbours[a])
          if (masks[b] & masks[a])
            candidates.push(
              narrow(b, masks[b] & ~masks[a], 'adjacent_color_elimination', 1, [a], {
                sourceNode: a,
                colorMask: masks[a],
              }),
            );
    for (const e of equalities.values())
      for (const [a, b] of [
        [e.a, e.b],
        [e.b, e.a],
      ])
        if ((masks[a] & masks[b]) !== masks[a])
          candidates.push(
            narrow(
              a,
              masks[a] & masks[b],
              'relation_propagation',
              1,
              [b],
              { sourceNode: b, equality: 1 },
              [e.id],
            ),
          );
    for (const { a, b } of puzzle.edges) {
      const common = [...neighbours[a]].filter((v) => neighbours[b].has(v)).sort((x, y) => x - y);
      const pair = masks[a] | masks[b];
      if ([3, 5, 6].includes(pair))
        for (const v of common)
          if (masks[v] & pair)
            candidates.push(
              narrow(v, masks[v] & ~pair, 'locked_two_color_edge', 2, [a, b], { pairMask: pair }),
            );
      const anchor = common[0];
      for (const other of common.slice(1))
        if (!equalities.has(`${anchor}:${other}`))
          candidates.push({
            id: 0,
            node: anchor,
            ruleId: 'diamond_equality',
            explanationKey: 'diamond_equality',
            tier: 2,
            beforeMask: masks[anchor],
            afterMask: masks[anchor],
            premises: [],
            witnesses: [a, b, anchor, other],
            arguments: { edgeA: a, edgeB: b },
            hypothesisEvidence: [],
            conclusion: { kind: 'EQUAL', a: anchor, b: other },
          });
    }
    candidates.sort(
      (a, b) => a.tier - b.tier || a.ruleId.localeCompare(b.ruleId) || a.node - b.node,
    );
    const step = candidates[0];
    if (!step) break;
    step.id = steps.length;
    steps.push(step);
    const c = step.conclusion!;
    if (c.kind === 'NARROW_DOMAIN') {
      if (!c.mask) return []; // Contradictory assumptions provide no hint.
      masks[c.node] = c.mask;
      last[c.node] = step.id;
      if (!known[c.node] && [1, 2, 4].includes(c.mask)) roots.push(step.id);
    } else if (c.kind === 'EQUAL') equalities.set(`${c.a}:${c.b}`, { ...c, id: step.id });
  }
  return roots
    .map((id) => {
      const needed = neededFacts(steps, id, known);
      const proof = steps.filter((s) => needed.has(s.id));
      const deduction = steps[id];
      const walkthrough = compactWalkthrough(
        puzzle,
        proof
          .filter((s) => s.tier > 0)
          .map((s): WalkthroughStep => {
            const vertices = [...new Set([s.node, ...s.witnesses])];
            const edges = puzzle.edges.filter(
              (e) => vertices.includes(e.a) && vertices.includes(e.b),
            );
            return {
              kind: 'DEDUCTION',
              node: s.node,
              mask: s.afterMask,
              deduction: s,
              vertices,
              edges,
            };
          }),
      );
      return {
        level,
        status: 'AVAILABLE',
        deduction,
        supportingSteps: proof,
        explanation: withWalkthrough(
          {
            reasoningType: deduction.ruleId,
            primaryTargets: [deduction.node],
            conclusion: deduction.conclusion!,
            focusVertices: [],
            focusEdges: [],
            orderedPath: [],
            assumption: null,
            contradictionPoint: null,
            walkthrough,
          },
          walkthrough,
        ),
      };
    })
    .sort(compareHints);
}
