import type { Color, ColorMap, Puzzle, Step, Trace } from '../api/dto';
import type { Notes } from './model';

export interface InspectionFrame {
  step: Step | null;
  colors: ColorMap;
  notes: Notes;
  highlighted: number[];
}

/** Replays only the outer proof; temporary assumptions never become board assignments. */
export function inspectionFrames(puzzle: Puzzle, trace: Trace): InspectionFrame[] {
  const colors: Color[] = ['RED', 'GREEN', 'BLUE'];
  const masks = Array.from({ length: puzzle.nodeCount }, (_, v) =>
    puzzle.givens[v] ? 1 << colors.indexOf(puzzle.givens[v]) : 7,
  );
  function frame(step: Step | null): InspectionFrame {
    const fixed: ColorMap = {},
      notes: Notes = {};
    masks.forEach((mask, v) => {
      if ([1, 2, 4].includes(mask)) fixed[v] = colors[Math.log2(mask)];
      else if (mask > 0) notes[v] = mask;
    });
    const vertices = step ? [step.node, ...step.witnesses] : [];
    if (step?.conclusion?.kind === 'EQUAL' || step?.conclusion?.kind === 'NOT_EQUAL')
      vertices.push(step.conclusion.a, step.conclusion.b);
    return {
      step,
      colors: fixed,
      notes,
      highlighted: [...new Set(vertices.filter((v) => v >= 0))],
    };
  }
  const frames = [frame(null)];
  for (const step of trace.steps) {
    if (step.tier === 0) continue;
    if (step.conclusion?.kind === 'NARROW_DOMAIN')
      masks[step.conclusion.node] &= step.conclusion.mask;
    else if (!step.conclusion && step.afterMask !== step.beforeMask)
      masks[step.node] &= step.afterMask;
    frames.push(frame(step));
  }
  return frames;
}
