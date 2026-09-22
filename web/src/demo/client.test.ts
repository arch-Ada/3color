import { afterEach, expect, it, vi } from 'vitest';
import fixture from './fixtures/easy.json';
afterEach(() => vi.unstubAllGlobals());
it('aborts a pending collection request and can retry after cancellation', async () => {
  vi.resetModules();
  const { generateDemo, analyzeDemo } = await import('./client');
  const fetch = vi.fn(
    (_url, { signal }) =>
      new Promise((_resolve, reject) => {
        signal.addEventListener('abort', () => reject(signal.reason));
      }),
  );
  vi.stubGlobal('fetch', fetch);
  const controller = new AbortController();
  const pending = generateDemo({ seed: null, size: 'MINI', difficulty: 'EASY' }, controller.signal);
  controller.abort();
  await expect(pending).rejects.toMatchObject({ name: 'AbortError' });
  expect(fetch).toHaveBeenCalledTimes(1);
  const entry = {
    id: 'fixture',
    file: 'puzzles-0.json',
    category: 'EASY',
    nodeCount: 6,
    seed: '4000',
    topologyKey: fixture.generated.topologyKey,
    logicalHash: fixture.generated.puzzle.logicalHash,
  };
  vi.stubGlobal(
    'fetch',
    vi.fn(
      async (url: string) =>
        new Response(
          JSON.stringify(
            url.endsWith('manifest.json') ? { version: 3, entries: [entry] } : { fixture },
          ),
        ),
    ),
  );
  const generated = await generateDemo({ seed: null, size: 'MINI', difficulty: 'EASY' });
  expect(generated.puzzle).toEqual(fixture.generated.puzzle);
  expect((await analyzeDemo(generated.puzzle)).trace.status).toBe('SOLVED');
  for (const edit of ['edges', 'givens', 'layout', 'explanationModel'] as const) {
    const changed = structuredClone(generated.puzzle);
    if (edit === 'explanationModel') changed.explanationModel = 'BASIC_V1';
    if (edit === 'edges') changed.edges.pop();
    if (edit === 'givens') changed.givens[0] = 'RED';
    if (edit === 'layout') changed.layout[0].x += 0.01;
    await expect(analyzeDemo(changed)).rejects.toThrow('Puzzle not in this demo snapshot');
  }
});
