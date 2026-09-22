import type { Generated } from '../api/dto';

/** Owns request lifetime; the existing generator owns seed retries and search policy. */
export class GenerationSession {
  private active: AbortController | null = null;

  constructor(
    private readonly callbacks: {
      busy: (value: boolean) => void;
      attempt: (value: number) => void;
      accepted: (result: Generated) => void;
      failed: (error: unknown) => void;
    },
  ) {}

  async run(
    search: (signal: AbortSignal, progress: (attempt: number) => void) => Promise<Generated>,
  ) {
    this.cancel();
    const request = new AbortController();
    this.active = request;
    this.callbacks.busy(true);
    const current = () => this.active === request && !request.signal.aborted;
    try {
      const result = await search(request.signal, (value) => {
        if (current()) this.callbacks.attempt(value);
      });
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
