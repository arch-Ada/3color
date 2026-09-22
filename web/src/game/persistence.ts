import type { Band, Generated, Puzzle, ColorMap, SizeCategory } from '../api/dto';
import type { Game, Notes } from './model';
export interface Preferences {
  size?: SizeCategory;
  difficulty: Band;
  nodeCount: number;
  seed: string;
}
export interface Saved {
  version: 2;
  game: Game;
  generated: Generated;
  preferences: Preferences;
}
export const storageKey = '3color-progress-v2';
export const serialize = (saved: Saved) => JSON.stringify(saved);
const bands = ['VERY_EASY', 'EASY', 'MEDIUM', 'HARD', 'EXPERT', 'CHALLENGING'];
function colorsValid(colors: ColorMap, p: Puzzle): boolean {
  return (
    !!colors &&
    typeof colors === 'object' &&
    Object.entries(colors).every(
      ([n, c]) =>
        Number.isInteger(Number(n)) &&
        Number(n) >= 0 &&
        Number(n) < p.nodeCount &&
        ['RED', 'GREEN', 'BLUE'].includes(c),
    ) &&
    Object.entries(p.givens).every(([n, c]) => colors[Number(n)] === c)
  );
}
function notesValid(notes: Notes, colors: ColorMap, p: Puzzle): boolean {
  return (
    !!notes &&
    typeof notes === 'object' &&
    !Array.isArray(notes) &&
    Object.entries(notes).every(
      ([n, mask]) =>
        Number.isInteger(Number(n)) &&
        Number(n) >= 0 &&
        Number(n) < p.nodeCount &&
        Number.isInteger(mask) &&
        mask >= 1 &&
        mask <= 7 &&
        !colors[Number(n)],
    )
  );
}
export function deserialize(raw: string): Saved | null {
  try {
    if (raw.length > 1_000_000) return null;
    const saved = JSON.parse(raw) as Saved;
    const p = saved.game?.puzzle;
    if (
      saved.version !== 2 ||
      !p ||
      !Number.isInteger(p.nodeCount) ||
      p.nodeCount < 1 ||
      p.nodeCount > 80 ||
      p.rules !== 'CLASSIC_V1' ||
      p.explanationModel !== 'PLAYER_V1'
    )
      return null;
    if (
      !Array.isArray(p.layout) ||
      p.layout.length !== p.nodeCount ||
      p.layout.some((v) => !Number.isFinite(v.x) || !Number.isFinite(v.y))
    )
      return null;
    if (
      !Array.isArray(p.edges) ||
      p.edges.length > 400 ||
      p.edges.some(
        (e) =>
          !Number.isInteger(e.a) ||
          !Number.isInteger(e.b) ||
          e.a < 0 ||
          e.b < 0 ||
          e.a >= p.nodeCount ||
          e.b >= p.nodeCount ||
          e.a === e.b,
      )
    )
      return null;
    if (
      !p.givens ||
      !colorsValid(p.givens, p) ||
      !colorsValid(saved.game.colors, p) ||
      !p.provenance ||
      typeof p.provenance.masterSeed !== 'string'
    )
      return null;
    if (!notesValid(saved.game.notes, saved.game.colors, p)) return null;
    if (
      !Array.isArray(saved.game.past) ||
      !Array.isArray(saved.game.future) ||
      saved.game.past.length > 200 ||
      saved.game.future.length > 200 ||
      [...saved.game.past, ...saved.game.future].some((value) => {
        return !value || !colorsValid(value.colors, p) || !notesValid(value.notes, value.colors, p);
      })
    )
      return null;
    if (
      !Number.isInteger(saved.game.selected) ||
      saved.game.selected < 0 ||
      saved.game.selected >= p.nodeCount
    )
      return null;
    if (
      !saved.generated?.difficulty ||
      !saved.generated.metrics ||
      saved.generated.puzzle.logicalHash !== p.logicalHash ||
      !saved.generated.generation ||
      !bands.includes(saved.generated.difficulty.band) ||
      !Number.isFinite(saved.generated.difficulty.score) ||
      !Number.isFinite(saved.generated.difficulty.totalDeductionCount) ||
      !Number.isFinite(saved.generated.difficulty.maximumProofDepth) ||
      !saved.generated.difficulty.ruleUsageCounts ||
      !Number.isFinite(saved.generated.metrics.averageDegree) ||
      !saved.generated.metrics.degreeDistribution ||
      !bands.includes(saved.preferences?.difficulty) ||
      (saved.preferences.size !== undefined &&
        !['MINI', 'SMALL', 'MEDIUM', 'LARGE', 'VERY_LARGE'].includes(saved.preferences.size)) ||
      !Number.isInteger(saved.preferences.nodeCount) ||
      saved.preferences.nodeCount < 4 ||
      saved.preferences.nodeCount > 80 ||
      typeof saved.preferences.seed !== 'string'
    )
      return null;
    return saved;
  } catch {
    return null;
  }
}
export function load(key = storageKey): Saved | null {
  try {
    const raw = localStorage.getItem(key);
    return raw ? deserialize(raw) : null;
  } catch {
    return null;
  }
}
export function save(saved: Saved, key = storageKey): boolean {
  try {
    localStorage.setItem(key, serialize(saved));
    return true;
  } catch {
    return false;
  }
}
