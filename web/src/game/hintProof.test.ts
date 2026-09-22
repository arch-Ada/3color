import { expect, it } from 'vitest';
import { neededFacts, compactWalkthrough } from './hintProof';
import type { Step, Puzzle, WalkthroughStep } from '../api/dto';
const step = (id: number, node: number, mask: number, premises: number[] = []): Step => ({
  id,
  node,
  beforeMask: 7,
  afterMask: mask,
  premises,
  tier: 1,
  ruleId: 'test',
  explanationKey: 'test',
  witnesses: [],
  arguments: {},
  hypothesisEvidence: [],
  conclusion: { kind: 'NARROW_DOMAIN', node, mask },
});
it('cuts proof dependencies at a stronger established colour, but not a contradictory colour', () => {
  const steps = [step(0, 0, 3), step(1, 1, 6, [0]), step(2, 2, 4, [1])];
  expect([...neededFacts(steps, 2, { 1: 'GREEN' })]).toEqual([2]);
  expect([...neededFacts(steps, 2, { 1: 'RED' })]).toEqual([2, 1, 0]);
});
it('retains complete hypothetical evidence and relational prerequisites', () => {
  const relation = { ...step(0, 0, 7), conclusion: { kind: 'EQUAL' as const, a: 0, b: 1 } };
  const root = { ...step(1, 2, 4, [0]), hypothesisEvidence: [step(0, 1, 1), step(1, 1, 0, [0])] };
  const before = JSON.stringify(root);
  expect([...neededFacts([relation, root], 1, { 0: 'RED' })]).toEqual([1, 0]);
  expect([...neededFacts([relation, root], 1, { 0: 'RED', 1: 'RED' })]).toEqual([1]);
  expect(JSON.stringify(root)).toBe(before);
});
it('does not combine exclusions across an assumption boundary', () => {
  const p = {
    edges: [
      { a: 0, b: 1 },
      { a: 0, b: 2 },
      { a: 1, b: 2 },
    ],
  } as Puzzle;
  const a = {
    ...step(0, 0, 6),
    ruleId: 'adjacent_color_elimination',
    arguments: { sourceNode: 1, colorMask: 1 },
  };
  const b = {
    ...step(1, 0, 4),
    beforeMask: 6,
    ruleId: a.ruleId,
    arguments: { sourceNode: 2, colorMask: 2 },
  };
  const wrap = (d: Step): WalkthroughStep => ({
    kind: 'DEDUCTION',
    node: d.node,
    mask: d.afterMask,
    deduction: d,
    vertices: [0, 1, 2],
    edges: p.edges,
  });
  const boundary: WalkthroughStep = {
    kind: 'ASSUMPTION',
    node: 2,
    mask: 2,
    deduction: null,
    vertices: [2],
    edges: [],
  };
  expect(compactWalkthrough(p, [wrap(a), wrap(b)])).toHaveLength(1);
  expect(compactWalkthrough(p, [wrap(a), boundary, wrap(b)])).toHaveLength(3);
});
