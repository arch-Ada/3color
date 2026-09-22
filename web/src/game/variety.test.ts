import { beforeEach, afterEach, expect, test, vi } from 'vitest';
import type { Generated } from '../api/dto';
import { rememberTopology, seenTopologies, seenTopologiesInRange } from './variety';
import { findPuzzle } from './generation';
let store: Map<string, string>;
beforeEach(() => {
  store = new Map();
  vi.stubGlobal('localStorage', {
    getItem: (k: string) => store.get(k) ?? null,
    setItem: (k: string, v: string) => store.set(k, v),
  });
});
afterEach(() => vi.unstubAllGlobals());
const generated = (n: number, key: string) =>
  ({ puzzle: { nodeCount: n }, topologyKey: key }) as Generated;
test('history is by graph size across tiers, validates data and preserves other sizes', () => {
  rememberTopology(generated(17, 'a'.repeat(64)));
  rememberTopology(generated(25, 'b'.repeat(64)));
  rememberTopology(generated(17, 'a'.repeat(64)));
  expect(seenTopologies(17)).toEqual(['a'.repeat(64)]);
  expect(seenTopologies(25)).toEqual(['b'.repeat(64)]);
  expect(JSON.parse(store.get('3color-seen-topologies-v1')!)['25']).toEqual(['b'.repeat(64)]);
  rememberTopology(generated(17, 'invalid'));
  expect(seenTopologies(17)).toHaveLength(1);
});
test('random requests exclude earlier structures; explicit seeds remain replayable', async () => {
  rememberTopology(generated(26, 'c'.repeat(64)));
  const fetch = vi
    .fn()
    .mockImplementation(() =>
      Promise.resolve(new Response(JSON.stringify(generated(26, 'd'.repeat(64))), { status: 200 })),
    );
  vi.stubGlobal('fetch', fetch);
  await findPuzzle('', 26, 'MEDIUM', new AbortController().signal, () => {});
  expect(JSON.parse(fetch.mock.calls[0][1].body).excludeTopologies).toContain('c'.repeat(64));
  await findPuzzle('123', 26, 'MEDIUM', new AbortController().signal, () => {});
  expect(JSON.parse(fetch.mock.calls[1][1].body).excludeTopologies).toBeUndefined();
  expect(seenTopologies(26)).toContain('d'.repeat(64));
});

test('range exclusions retain recent smaller graphs when larger sizes fill the cap', () => {
  const keys = Array.from({ length: 1024 }, (_, i) => (i + 100).toString(16).padStart(64, '0'));
  store.set('3color-seen-topologies-v1', JSON.stringify({ 23: keys }));
  const newest = 'f'.repeat(64);
  rememberTopology(generated(14, newest));
  const excluded = seenTopologiesInRange(14, 23);
  expect(excluded).toHaveLength(1024);
  expect(excluded.at(-1)).toBe(newest);
  expect(excluded).toContain(keys.at(-1));
  expect(JSON.parse(store.get('3color-seen-topologies-v1')!)._recent.at(-1)).toBe(newest);
});
