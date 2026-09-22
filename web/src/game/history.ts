import type { Game } from './model';
export function undo(game: Game): Game {
  return game.past.length
    ? {
        ...game,
        ...game.past.at(-1)!,
        past: game.past.slice(0, -1),
        future: [{ colors: game.colors, notes: game.notes }, ...game.future],
      }
    : game;
}
export function redo(game: Game): Game {
  return game.future.length
    ? {
        ...game,
        ...game.future[0],
        past: [...game.past, { colors: game.colors, notes: game.notes }],
        future: game.future.slice(1),
      }
    : game;
}
