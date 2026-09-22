import type { ColorMap, Puzzle } from '../api/dto';
export type Notes = Record<number, number>;
export interface Snapshot {
  colors: ColorMap;
  notes: Notes;
}
export interface Game {
  puzzle: Puzzle;
  colors: ColorMap;
  notes: Notes;
  past: Snapshot[];
  future: Snapshot[];
  selected: number;
}
export const newGame = (puzzle: Puzzle): Game => ({
  puzzle,
  colors: { ...puzzle.givens },
  notes: {},
  past: [],
  future: [],
  selected:
    Array.from({ length: puzzle.nodeCount }, (_, n) => n).find((n) => !puzzle.givens[n]) ?? 0,
});
export const symbols = { RED: '●', GREEN: '▲', BLUE: '■' } as const;
