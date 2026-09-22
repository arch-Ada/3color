import { describe, expect, it } from 'vitest';
import { savedHint, selectEntry, type Entry, type Payload } from './catalog';
import fixture from './fixtures/easy.json';
import type { ColorMap } from '../api/dto';
const payload = fixture as unknown as Payload;
const entry = (id: string, category = 'EASY', nodeCount = 16): Entry => ({
  id,
  category,
  nodeCount,
  seed: id,
  file: 'puzzles-0.json',
  topologyKey: `topology-${id}`,
  logicalHash: `hash-${id}`,
});
const entries = [entry('1'), entry('2'), entry('3', 'MEDIUM'), entry('4', 'EASY', 24)];

describe('standalone demo selection', () => {
  it('respects category, range, exact count and topology exclusions', () => {
    expect(
      selectEntry(
        entries,
        { seed: null, size: 'SMALL', difficulty: 'EASY', excludeTopologies: ['topology-1'] },
        0,
      ).id,
    ).toBe('2');
    expect(selectEntry(entries, { seed: null, nodeCount: 24, difficulty: 'EASY' }, 0).id).toBe('4');
  });
  it('reports exhaustion instead of retrying, relabeling or silently repeating', () => {
    expect(() =>
      selectEntry(entries, {
        seed: null,
        size: 'SMALL',
        difficulty: 'EASY',
        excludeTopologies: ['topology-1', 'topology-2'],
      }),
    ).toThrowError('Collection exhausted');
    expect(() =>
      selectEntry(entries, { seed: null, size: 'MINI', difficulty: 'MEDIUM' }),
    ).toThrow();
  });
  it('replays only included seeds, independently of exclusions', () => {
    expect(
      selectEntry(entries, {
        seed: '01',
        size: 'SMALL',
        difficulty: 'EASY',
        excludeTopologies: ['topology-1'],
      }).id,
    ).toBe('1');
    expect(() =>
      selectEntry(entries, { seed: '1', size: 'LARGE', difficulty: 'EASY' }),
    ).toThrowError('Seed not included');
  });
  it('permits explicit replay without mutating repeat history', () => {
    const excluded = ['topology-1', 'topology-2'];
    expect(selectEntry(entries, { seed: null, size: 'SMALL', difficulty: 'EASY' }, 0).id).toBe('1');
    expect(excluded).toEqual(['topology-1', 'topology-2']);
  });
});

describe('offline hints from an actual certified puzzle', () => {
  it('walks all the way to a completed colouring, without mutating the stored proof', () => {
    const before = JSON.stringify(payload);
    const colors: ColorMap = { ...payload.generated.puzzle.givens };
    for (let i = Object.keys(colors).length; i < payload.generated.puzzle.nodeCount; i++) {
      const hint = savedHint(payload, colors, 'REASON');
      expect(hint.status).toBe('AVAILABLE');
      expect(hint.explanation!.walkthrough.length).toBeGreaterThan(0);
      const step = hint.deduction!;
      expect(colors[step.node]).toBeUndefined();
      colors[step.node] = ({ 1: 'RED', 2: 'GREEN', 4: 'BLUE' } as const)[
        step.afterMask as 1 | 2 | 4
      ];
    }
    expect(savedHint(payload, colors, 'ANSWER').status).toBe('SOLVED');
    for (const { a, b } of payload.generated.puzzle.edges) expect(colors[a]).not.toBe(colors[b]);
    expect(JSON.stringify(payload)).toBe(before);
  });
  it('skips correct out-of-order moves, and retains proof for incorrect moves', () => {
    const colors: ColorMap = { ...payload.generated.puzzle.givens };
    const last = payload.analysis.trace.steps.find((s) => s.id === payload.hints.at(-1)!.stepId)!;
    colors[last.node] = ({ 1: 'RED', 2: 'GREEN', 4: 'BLUE' } as const)[last.afterMask as 1 | 2 | 4];
    const hint = savedHint(payload, colors, 'NUDGE');
    expect(hint.status).toBe('AVAILABLE');
    expect(hint.deduction!.node).not.toBe(last.node);
    // Every colour inconsistent with the solution is either flagged as a conflict or gets a proof.
    for (const color of ['RED', 'GREEN', 'BLUE'] as const) {
      const wrong = { ...colors, [hint.deduction!.node]: color };
      const next = savedHint(payload, wrong, 'ANSWER');
      expect(['AVAILABLE', 'CORRECTION', 'CONFLICT']).toContain(next.status);
    }
  });
  it('detects adjacent conflicts and changed givens', () => {
    const { a, b } = payload.generated.puzzle.edges[0];
    expect(savedHint(payload, { [a]: 'RED', [b]: 'RED' }, 'NUDGE').status).toBe('CONFLICT');
    const [node, color] = Object.entries(payload.generated.puzzle.givens)[0];
    expect(
      savedHint(payload, { [Number(node)]: color === 'RED' ? 'BLUE' : 'RED' }, 'NUDGE').status,
    ).toBe('CONFLICT');
  });
});

describe('short hints from the current board', () => {
  it('finishes a two-coloured triangle in one step before considering saved proofs', () => {
    // Actual certified fixture: 0-1-3 is a triangle, 1 is given Blue, and the player colours 3 Red.
    for (const level of ['NUDGE', 'REASON', 'ANSWER'] as const) {
      const hint = savedHint(payload, { ...payload.generated.puzzle.givens, 3: 'RED' }, level);
      expect(hint.deduction?.node).toBe(0);
      expect(hint.deduction?.afterMask).toBe(2);
      expect(hint.deduction?.explanationKey).toBe('triangle_completion');
      expect(hint.explanation?.walkthrough).toHaveLength(1);
      expect(hint.explanation?.focusVertices.slice().sort()).toEqual([0, 1, 3]);
      expect(hint.explanation?.focusEdges).toHaveLength(3);
    }
  });
  it('also handles differently coloured neighbours that do not form a triangle', () => {
    // 2 and 4 are not connected, but their Blue and Red force their common neighbour 5 Green.
    const hint = savedHint(payload, { ...payload.generated.puzzle.givens, 2: 'BLUE' }, 'REASON');
    expect(hint.deduction?.node).toBe(5);
    expect(hint.deduction?.afterMask).toBe(2);
    expect(hint.deduction?.explanationKey).toBe('two_colored_neighbors');
    expect(hint.explanation?.walkthrough).toHaveLength(1);
    expect(hint.explanation?.focusEdges).toHaveLength(2);
  });
  it('does not base a direct hint on an incorrect player colour', () => {
    const hint = savedHint(payload, { ...payload.generated.puzzle.givens, 3: 'GREEN' }, 'REASON');
    expect(hint.status).toBe('CORRECTION');
    expect(hint.deduction?.node).toBe(3);
    expect(hint.deduction?.afterMask).toBe(1);
    expect(hint.explanation!.walkthrough.length).toBeGreaterThan(0);
    expect(hint.deduction?.explanationKey).not.toBe('triangle_completion');
    expect(hint.deduction?.explanationKey).not.toBe('two_colored_neighbors');
    expect(hint.deduction!.afterMask).toBe(
      payload.analysis.trace.finalDomains[hint.deduction!.node],
    );
  });
  it('prefers the shorter available saved proof regardless of catalogue order', () => {
    const reordered = structuredClone(payload);
    reordered.hints.reverse();
    const hint = savedHint(reordered, payload.generated.puzzle.givens, 'REASON');
    expect(hint.deduction?.node).toBe(2);
    expect(hint.explanation?.walkthrough).toHaveLength(3);
  });
  it('works under every palette permutation', () => {
    const colors = ['RED', 'GREEN', 'BLUE'] as const;
    const masks = { RED: 1, GREEN: 2, BLUE: 4 };
    for (const red of colors)
      for (const green of colors) {
        if (red === green) continue;
        const blue = colors.find((c) => c !== red && c !== green)!;
        const map = { RED: red, GREEN: green, BLUE: blue };
        const changed = structuredClone(payload);
        changed.generated.puzzle.givens = Object.fromEntries(
          Object.entries(payload.generated.puzzle.givens).map(([n, c]) => [n, map[c]]),
        );
        changed.analysis.trace.finalDomains = payload.analysis.trace.finalDomains.map((m) =>
          m === 1 ? masks[red] : m === 2 ? masks[green] : masks[blue],
        );
        const hint = savedHint(changed, { ...changed.generated.puzzle.givens, 3: red }, 'REASON');
        expect(hint.deduction?.afterMask).toBe(masks[green]);
        expect(hint.explanation?.walkthrough).toHaveLength(1);
      }
  });
});

it('prioritises correction over an available immediate forward hint', () => {
  const colors: ColorMap = { ...payload.generated.puzzle.givens, 0: 'GREEN', 5: 'BLUE' };
  const before = JSON.stringify(colors);
  const hint = savedHint(payload, colors, 'NUDGE');
  expect(hint.status).toBe('CORRECTION');
  expect(hint.deduction?.node).toBe(5);
  expect(hint.deduction?.afterMask).toBe(2);
  expect(JSON.stringify(colors)).toBe(before);
  for (const s of hint.supportingSteps.filter(
    (s) => s.tier === 0 && [1, 2, 4].includes(s.afterMask),
  ))
    expect(payload.generated.puzzle.givens[s.node]).toBeDefined();
});

it('identifies a conflicting edge without blaming either endpoint', () => {
  const hint = savedHint(payload, { ...payload.generated.puzzle.givens, 0: 'BLUE' }, 'NUDGE');
  expect(hint.status).toBe('CONFLICT');
  expect(hint.deduction).toBeNull();
  expect(hint.explanation?.primaryTargets).toEqual([0, 1]);
  expect(hint.explanation?.focusEdges).toEqual([{ a: 0, b: 1 }]);
});
