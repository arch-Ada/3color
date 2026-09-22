import { test, expect } from 'vitest';
import { inspectionFrames } from './inspection';
import type { Puzzle, Step, Trace } from '../api/dto';

test('replay preserves givens, isolates assumptions and snapshots candidate masks', () => {
  const puzzle = { nodeCount: 3, givens: { 0: 'RED' } } as unknown as Puzzle;
  const step: Step = {
    id: 1,
    ruleId: 'test',
    tier: 1,
    node: 1,
    beforeMask: 7,
    afterMask: 6,
    premises: [],
    witnesses: [0],
    explanationKey: '',
    arguments: {},
    hypothesisEvidence: [],
    conclusion: { kind: 'NARROW_DOMAIN', node: 1, mask: 6 },
  };
  const assumption = {
    ...step,
    node: 2,
    conclusion: { kind: 'NARROW_DOMAIN' as const, node: 2, mask: 1 },
  };
  const trace = {
    steps: [
      step,
      {
        ...step,
        id: 2,
        afterMask: 2,
        conclusion: { kind: 'NARROW_DOMAIN', node: 1, mask: 2 },
        hypothesisEvidence: [assumption],
      },
    ],
    status: 'SOLVED',
    finalDomains: [],
    availableDeductions: [],
    hypothesesTried: 1,
  } as Trace;
  const frames = inspectionFrames(puzzle, trace);
  expect(frames[0].colors).toEqual({ 0: 'RED' });
  expect(frames[0].notes).toEqual({ 1: 7, 2: 7 });
  expect(frames[1].notes[1]).toBe(6);
  expect(frames[2].colors).toEqual({ 0: 'RED', 1: 'GREEN' });
  expect(frames[2].notes[2]).toBe(7);
  expect(puzzle.givens).toEqual({ 0: 'RED' });
});
