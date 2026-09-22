import { describe, it, expect } from 'vitest';
import type { Puzzle } from '../api/dto';
import { newGame } from './model';
import { reduce } from './reducer';
import { invalidEdges, isComplete } from './validation';
import { serialize, deserialize, type Saved } from './persistence';
const puzzle: Puzzle = {
  nodeCount: 3,
  edges: [
    { a: 0, b: 1 },
    { a: 1, b: 2 },
    { a: 0, b: 2 },
  ],
  givens: { 0: 'RED', 1: 'GREEN' },
  layout: [
    { x: 0, y: 0 },
    { x: 1, y: 0 },
    { x: 0.5, y: 1 },
  ],
  rules: 'CLASSIC_V1',
  explanationModel: 'PLAYER_V1',
  provenance: { generatorVersion: 'test', masterSeed: '42', attemptIndex: 0, generationSpec: '' },
  logicalHash: 'test-hash',
};
describe('game state', () => {
  it('locks givens, applies moves immutably, and clears', () => {
    const initial = newGame(puzzle);
    const moved = reduce(initial, { type: 'color', color: 'BLUE' });
    expect(initial.colors[2]).toBeUndefined();
    expect(moved.colors[2]).toBe('BLUE');
    expect(reduce(moved, { type: 'color', color: 'BLUE' })).toBe(moved);
    expect(reduce(moved, { type: 'color', color: null }).colors[2]).toBeUndefined();
    const given = reduce(initial, { type: 'select', node: 0 });
    expect(reduce(given, { type: 'color', color: 'GREEN' })).toBe(given);
  });
  it('detects conflicts and requires a full valid coloring', () => {
    expect(invalidEdges(puzzle, { 0: 'RED', 1: 'GREEN', 2: 'RED' })).toHaveLength(1);
    expect(isComplete(puzzle, puzzle.givens)).toBe(false);
    expect(isComplete(puzzle, { ...puzzle.givens, 2: 'BLUE' })).toBe(true);
    expect(isComplete(puzzle, { ...puzzle.givens, 2: 'GREEN' })).toBe(false);
  });
  it('undoes, redoes, clears redo after a new move, and resets history', () => {
    const first = reduce(newGame(puzzle), { type: 'color', color: 'BLUE' });
    const back = reduce(first, { type: 'undo' });
    expect(back.colors[2]).toBeUndefined();
    expect(reduce(back, { type: 'redo' }).colors[2]).toBe('BLUE');
    expect(reduce(back, { type: 'color', color: 'RED' }).future).toEqual([]);
    const reset = reduce(first, { type: 'reset' });
    expect(reset.colors).toEqual(puzzle.givens);
    expect(reset.past).toEqual([]);
  });
  it('toggles notes, replaces colors, and restores both through history', () => {
    let game = reduce(newGame(puzzle), { type: 'note', color: 'RED' });
    game = reduce(game, { type: 'note', color: 'BLUE' });
    expect(game.notes[2]).toBe(5);
    expect(isComplete(puzzle, game.colors)).toBe(false);
    const filled = reduce(game, { type: 'color', color: 'BLUE' });
    expect(filled.notes[2]).toBeUndefined();
    expect(reduce(filled, { type: 'undo' }).notes[2]).toBe(5);
    expect(reduce(reduce(filled, { type: 'undo' }), { type: 'redo' }).colors[2]).toBe('BLUE');
    expect(reduce(filled, { type: 'note', color: 'RED' }).colors[2]).toBeUndefined();
    expect(reduce(game, { type: 'note', color: 'RED' }).notes[2]).toBe(4);
    expect(reduce(game, { type: 'color', color: null }).notes).toEqual({});
    expect(reduce(game, { type: 'reset' }).notes).toEqual({});
    const given = reduce(game, { type: 'select', node: 0 });
    expect(reduce(given, { type: 'note', color: 'BLUE' })).toBe(given);
  });
  it('round-trips progress and rejects corrupt storage', () => {
    const saved: Saved = {
      version: 2,
      game: newGame(puzzle),
      generated: {
        puzzle,
        generation: puzzle.provenance,
        difficulty: {
          modelVersion: 'human-v1',
          band: 'VERY_EASY',
          score: 3,
          totalDeductionCount: 2,
          hardestRuleTier: 1,
          ruleUsageCounts: { adjacent_color_elimination: 2 },
          weightedRuleCost: 2,
          maximumProofDepth: 2,
          hypothesisSteps: 0,
          averageAvailableDeductions: 1,
          minimumAvailableDeductions: 1,
          humanSolved: true,
        },
        metrics: {
          n: 3,
          m: 3,
          averageDegree: 2,
          minDegree: 2,
          maxDegree: 2,
          degreeDistribution: { '2': 3 },
          triangleCount: 1,
          connectedComponents: 1,
          cycleRank: 1,
        },
      },
      preferences: { difficulty: 'VERY_EASY', nodeCount: 12, seed: '42' },
    };
    expect(deserialize(serialize(saved))).toEqual(saved);
    const noted = { ...saved, game: reduce(saved.game, { type: 'note', color: 'BLUE' }) };
    expect(deserialize(serialize(noted))).toEqual(noted);
    const legacy = JSON.parse(serialize(saved));
    delete legacy.game.notes;
    legacy.game.past = [saved.game.colors];
    expect(deserialize(JSON.stringify(legacy))).toBeNull();
    expect(
      deserialize(serialize({ ...saved, game: { ...saved.game, notes: { 0: 1 } } })),
    ).toBeNull();
    expect(
      deserialize(serialize({ ...saved, game: { ...saved.game, notes: { 2: 8 } } })),
    ).toBeNull();
    expect(deserialize('broken')).toBeNull();
    expect(
      deserialize(serialize({ ...saved, game: { ...saved.game, colors: { 0: 'BLUE' } } })),
    ).toBeNull();
    expect(deserialize(serialize({ ...saved, game: { ...saved.game, selected: 9 } }))).toBeNull();
    expect(
      deserialize(serialize({ ...saved, generated: { ...saved.generated, metrics: {} } } as Saved)),
    ).toBeNull();
  });
});
