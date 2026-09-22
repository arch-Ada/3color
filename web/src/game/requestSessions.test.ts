import { expect, test, vi } from 'vitest';
import { GenerationSession } from './generationSession';
import { HintSession } from './hintSession';
import type { Generated, Hint } from '../api/dto';
import type { Game } from './model';

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((r) => {
    resolve = r;
  });
  return { promise, resolve };
}

test('superseded generation cannot publish, update attempts, or clear the new busy state', async () => {
  const callbacks = { busy: vi.fn(), attempt: vi.fn(), accepted: vi.fn(), failed: vi.fn() };
  const session = new GenerationSession(callbacks);
  const old = deferred<Generated>(),
    next = deferred<Generated>();
  let progress!: (n: number) => void;
  let signal!: AbortSignal;
  const first = session.run((s, p) => {
    signal = s;
    progress = p;
    return old.promise;
  });
  const second = session.run(() => next.promise);
  expect(signal.aborted).toBe(true);
  progress(100);
  old.resolve({} as Generated);
  await first;
  expect(callbacks.accepted).not.toHaveBeenCalled();
  expect(callbacks.attempt).not.toHaveBeenCalled();
  expect(callbacks.busy).toHaveBeenLastCalledWith(true);
  next.resolve({} as Generated);
  await second;
  expect(callbacks.accepted).toHaveBeenCalledTimes(1);
  expect(callbacks.busy).toHaveBeenLastCalledWith(false);
});

test('canceled generation ignores even a successful late response', async () => {
  const callbacks = { busy: vi.fn(), attempt: vi.fn(), accepted: vi.fn(), failed: vi.fn() };
  const session = new GenerationSession(callbacks);
  const pending = deferred<Generated>();
  const done = session.run(() => pending.promise);
  session.cancel();
  pending.resolve({} as Generated);
  await done;
  expect(callbacks.accepted).not.toHaveBeenCalled();
  expect(callbacks.failed).not.toHaveBeenCalled();
});

test('hint selection changes remain valid, colour changes and cancellation do not', async () => {
  let game = { puzzle: {}, colors: {}, selected: 0 } as Game;
  const callbacks = {
    current: () => game,
    allowed: () => true,
    busy: vi.fn(),
    accepted: vi.fn(),
    failed: vi.fn(),
  };
  for (const change of ['selection', 'colour', 'cancel']) {
    const pending = deferred<Hint>();
    const session = new HintSession(callbacks, () => pending.promise);
    const done = session.run(game, 'NUDGE');
    if (change === 'selection') game = { ...game, selected: 1 };
    if (change === 'colour') game = { ...game, colors: {} };
    if (change === 'cancel') session.cancel();
    pending.resolve({} as Hint);
    await done;
  }
  expect(callbacks.accepted).toHaveBeenCalledTimes(1);
});
