import type { Color } from '../api/dto';
import type { Game } from './model';
import { undo, redo } from './history';
export type Action =
  | { type: 'color'; color: Color | null }
  | { type: 'note'; color: Color }
  | { type: 'select'; node: number }
  | { type: 'undo' }
  | { type: 'redo' }
  | { type: 'reset' };
export function reduce(game: Game, action: Action): Game {
  if (action.type === 'select')
    return action.node >= 0 && action.node < game.puzzle.nodeCount
      ? { ...game, selected: action.node }
      : game;
  if (action.type === 'undo') return undo(game);
  if (action.type === 'redo') return redo(game);
  if (action.type === 'reset')
    return { ...game, colors: { ...game.puzzle.givens }, notes: {}, past: [], future: [] };
  if (game.puzzle.givens[game.selected]) return game;
  const colors = { ...game.colors };
  const notes = { ...game.notes };
  if (action.type === 'note') {
    const bit = { RED: 1, GREEN: 2, BLUE: 4 }[action.color];
    const mask = (notes[game.selected] ?? 0) ^ bit;
    delete colors[game.selected];
    if (mask) notes[game.selected] = mask;
    else delete notes[game.selected];
  } else {
    if ((colors[game.selected] ?? null) === action.color && !notes[game.selected]) return game;
    delete notes[game.selected];
    if (action.color === null) delete colors[game.selected];
    else colors[game.selected] = action.color;
  }
  return {
    ...game,
    colors,
    notes,
    past: [...game.past.slice(-199), { colors: game.colors, notes: game.notes }],
    future: [],
  };
}
