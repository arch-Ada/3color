import type { Hint, HintLevel } from '../api/dto';
import type { Game } from './model';
import { hint } from '../api/client';

/** A hint belongs to puzzle/colour state, not selection or presentation state. */
export class HintSession {
  private active: AbortController | null = null;

  constructor(
    private readonly callbacks: {
      current: () => Game | null;
      allowed: () => boolean;
      busy: (value: boolean) => void;
      accepted: (result: Hint) => void;
      failed: (error: unknown) => void;
    },
    private readonly load = hint,
  ) {}

  async run(game: Game, level: HintLevel) {
    this.cancel();
    const request = new AbortController();
    this.active = request;
    this.callbacks.busy(true);
    const current = () => {
      const board = this.callbacks.current();
      return (
        this.active === request &&
        !request.signal.aborted &&
        this.callbacks.allowed() &&
        board?.puzzle === game.puzzle &&
        board?.colors === game.colors
      );
    };
    try {
      const result = await this.load(game.puzzle, game.colors, level, request.signal);
      if (current()) this.callbacks.accepted(result);
    } catch (error) {
      if (current()) this.callbacks.failed(error);
    } finally {
      if (this.active === request) {
        this.active = null;
        this.callbacks.busy(false);
      }
    }
  }

  cancel() {
    this.active?.abort();
    this.active = null;
    this.callbacks.busy(false);
  }
}
