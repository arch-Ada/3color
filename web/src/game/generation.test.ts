import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../api/client';
import { findPuzzle, clampVertices, currentBands } from './generation';

const success = () =>
  new Response(JSON.stringify({ puzzle: { logicalHash: 'found' } }), { status: 200 });

describe.each([['current', findPuzzle, 'DELETION_NO_MATCH']] as const)(
  '%s cancelable puzzle search',
  (_name, search, code) => {
    const exhausted = () =>
      new Response(JSON.stringify({ code, message: 'No match' }), { status: 422 });
    beforeEach(() => vi.useFakeTimers());
    afterEach(() => {
      vi.useRealTimers();
      vi.unstubAllGlobals();
    });

    it('retries exhausted searches with new seeds until one succeeds', async () => {
      const fetch = vi
        .fn()
        .mockResolvedValueOnce(exhausted())
        .mockResolvedValueOnce(exhausted())
        .mockResolvedValueOnce(success());
      vi.stubGlobal('fetch', fetch);
      const progress = vi.fn();
      const result = search('42', 36, 'EASY', new AbortController().signal, progress);
      await vi.runAllTimersAsync();
      expect((await result).puzzle.logicalHash).toBe('found');
      expect(fetch.mock.calls.map(([, request]) => JSON.parse(request.body).seed)).toEqual([
        '42',
        '43',
        '44',
      ]);
      expect(progress.mock.calls.flat()).toEqual([1, 2, 3]);
      for (const [, request] of fetch.mock.calls) {
        expect(JSON.parse(request.body)).toMatchObject({
          nodeCount: 36,
          difficulty: 'EASY',
        });
      }
    });

    it('keeps random retries random and wraps explicit seeds within signed 64-bit range', async () => {
      const fetch = vi.fn().mockResolvedValueOnce(exhausted()).mockResolvedValueOnce(success());
      vi.stubGlobal('fetch', fetch);
      const random = search('', 24, 'EASY', new AbortController().signal, vi.fn());
      await vi.runAllTimersAsync();
      await random;
      expect(fetch.mock.calls.map(([, request]) => JSON.parse(request.body).seed)).toEqual([
        null,
        null,
      ]);
      fetch.mockClear().mockResolvedValueOnce(exhausted()).mockResolvedValueOnce(success());
      const wrapped = search(
        '9223372036854775807',
        24,
        'EASY',
        new AbortController().signal,
        vi.fn(),
      );
      await vi.runAllTimersAsync();
      await wrapped;
      expect(JSON.parse(fetch.mock.calls[1][1].body).seed).toBe('-9223372036854775808');
    });

    it('cancels between retries without sending another request', async () => {
      const controller = new AbortController();
      const fetch = vi.fn().mockResolvedValue(exhausted());
      vi.stubGlobal('fetch', fetch);
      const result = search('42', 36, 'EASY', controller.signal, vi.fn());
      const rejected = expect(result).rejects.toMatchObject({ name: 'AbortError' });
      await vi.advanceTimersByTimeAsync(0);
      controller.abort();
      await rejected;
      await vi.runAllTimersAsync();
      expect(fetch).toHaveBeenCalledTimes(1);
    });

    it('aborts an in-flight request and discards even a late successful response', async () => {
      const controller = new AbortController();
      let resolve!: (value: Response) => void;
      const fetch = vi.fn(
        () =>
          new Promise<Response>((done) => {
            resolve = done;
          }),
      );
      vi.stubGlobal('fetch', fetch);
      const result = search('42', 36, 'EASY', controller.signal, vi.fn());
      const rejected = expect(result).rejects.toMatchObject({ name: 'AbortError' });
      controller.abort();
      expect((fetch.mock.calls[0] as unknown as [string, RequestInit])[1].signal?.aborted).toBe(
        true,
      );
      resolve(success());
      await rejected;
      expect(fetch).toHaveBeenCalledTimes(1);
    });

    it('retries a busy server but surfaces invalid requests without an endless retry', async () => {
      const fetch = vi
        .fn()
        .mockResolvedValueOnce(new Response(JSON.stringify({ code: 'BUSY' }), { status: 429 }))
        .mockResolvedValueOnce(
          new Response(JSON.stringify({ code: 'INVALID_REQUEST', message: 'Bad seed' }), {
            status: 400,
          }),
        );
      vi.stubGlobal('fetch', fetch);
      const result = search('42', 36, 'EASY', new AbortController().signal, vi.fn());
      const rejected = expect(result).rejects.toEqual(
        new ApiError('Bad seed', 400, 'INVALID_REQUEST'),
      );
      await vi.runAllTimersAsync();
      await rejected;
      expect(fetch).toHaveBeenCalledTimes(2);
    });
  },
);

it('offers the four player bands and current size bounds', () => {
  expect(currentBands).toEqual(['VERY_EASY', 'EASY', 'MEDIUM', 'CHALLENGING']);
  expect(clampVertices(1)).toBe(4);
  expect(clampVertices(80)).toBe(53);
  expect(clampVertices(NaN)).toBe(16);
});

it('passes a size category through retries without fixing a vertex count', async () => {
  const fetch = vi.fn().mockResolvedValue(success());
  vi.stubGlobal('fetch', fetch);
  try {
    await findPuzzle('42', 24, 'CHALLENGING', new AbortController().signal, vi.fn(), 'MEDIUM');
    expect(JSON.parse(fetch.mock.calls[0][1].body)).toEqual({
      seed: '42',
      size: 'MEDIUM',
      difficulty: 'CHALLENGING',
    });
  } finally {
    vi.unstubAllGlobals();
  }
});
