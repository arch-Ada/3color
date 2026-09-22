import type { ColorMap, Puzzle } from '../api/dto';
export const invalidEdges = (puzzle: Puzzle, colors: ColorMap) =>
  puzzle.edges.filter((e) => colors[e.a] && colors[e.a] === colors[e.b]);
export const isComplete = (puzzle: Puzzle, colors: ColorMap) =>
  Array.from({ length: puzzle.nodeCount }, (_, i) => i).every((i) => !!colors[i]) &&
  Object.entries(puzzle.givens).every(([n, c]) => colors[Number(n)] === c) &&
  invalidEdges(puzzle, colors).length === 0;
