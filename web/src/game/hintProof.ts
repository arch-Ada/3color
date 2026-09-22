import type { ColorMap, Hint, HintExplanation, Puzzle, Step, WalkthroughStep } from '../api/dto';

/** Only merges adjacent steps within the same scope; branch markers are never crossed. */
export function compactWalkthrough(puzzle: Puzzle, input: WalkthroughStep[]): WalkthroughStep[] {
  const result: WalkthroughStep[] = [];
  for (const current of input) {
    const previous = result.at(-1),
      a = previous?.deduction,
      b = current.deduction;
    if (
      a &&
      b &&
      previous?.kind === 'DEDUCTION' &&
      current.kind === 'DEDUCTION' &&
      a.ruleId === 'adjacent_color_elimination' &&
      b.ruleId === a.ruleId &&
      a.node === b.node &&
      a.beforeMask === 7 &&
      a.afterMask === b.beforeMask &&
      [1, 2, 4].includes(b.afterMask) &&
      a.arguments.sourceNode !== undefined &&
      b.arguments.sourceNode !== undefined
    ) {
      const left = a.arguments.sourceNode,
        right = b.arguments.sourceNode;
      const triangle = puzzle.edges.some(
        (e) => (e.a === left && e.b === right) || (e.b === left && e.a === right),
      );
      const vertices = [b.node, left, right];
      const edges = [...previous.edges, ...current.edges];
      if (triangle) edges.push({ a: left, b: right });
      const deduction: Step = {
        ...b,
        beforeMask: 7,
        witnesses: [left, right],
        explanationKey: triangle ? 'triangle_completion' : 'two_colored_neighbors',
        arguments: {
          neighbourA: left,
          neighbourB: right,
          maskA: a.arguments.colorMask,
          maskB: b.arguments.colorMask,
        },
      };
      result[result.length - 1] = { ...current, deduction, vertices, edges };
    } else result.push(current);
  }
  return result;
}

/** Cut only outer proof facts implied by established colours; keep hypothetical segments whole. */
export function neededFacts(steps: Step[], root: number, known: ColorMap): Set<number> {
  const masks = { RED: 1, GREEN: 2, BLUE: 4 };
  const byId = new Map(steps.map((s) => [s.id, s]));
  const needed = new Set<number>();
  const visit = (id: number) => {
    if (needed.has(id)) return;
    const step = byId.get(id);
    if (!step) throw new Error('Missing proof prerequisite');
    const c = step.conclusion;
    if (c?.kind === 'NARROW_DOMAIN' && known[c.node] && (c.mask & masks[known[c.node]]) !== 0)
      return;
    if (c?.kind === 'EQUAL' && known[c.a] && known[c.a] === known[c.b]) return;
    if (c?.kind === 'NOT_EQUAL' && known[c.a] && known[c.b] && known[c.a] !== known[c.b]) return;
    needed.add(id);
    step.premises.forEach(visit);
  };
  visit(root);
  return needed;
}

export function withWalkthrough(
  explanation: HintExplanation,
  walkthrough: WalkthroughStep[],
): HintExplanation {
  const vertices = [...new Set(walkthrough.flatMap((w) => w.vertices))];
  const edges = new Map(
    walkthrough
      .flatMap((w) => w.edges)
      .map((e) => [`${Math.min(e.a, e.b)}:${Math.max(e.a, e.b)}`, e]),
  );
  return { ...explanation, walkthrough, focusVertices: vertices, focusEdges: [...edges.values()] };
}

export function compareHints(a: Hint, b: Hint): number {
  const x = a.explanation!,
    y = b.explanation!;
  return (
    x.walkthrough.length - y.walkthrough.length ||
    x.walkthrough.filter((w) => w.kind === 'ASSUMPTION').length -
      y.walkthrough.filter((w) => w.kind === 'ASSUMPTION').length ||
    x.focusVertices.length - y.focusVertices.length ||
    a.deduction!.node - b.deduction!.node
  );
}
