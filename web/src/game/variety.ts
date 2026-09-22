import type { Generated } from '../api/dto';
import { demoMode, demoStoragePrefix } from '../demo/mode';
const key = demoMode ? `${demoStoragePrefix}-seen` : '3color-seen-topologies-v1';
let recent: string[] = [];
const memory = new Map<string, string[]>();
const valid = (value: unknown): value is string =>
  typeof value === 'string' && /^[0-9a-f]{64}$/.test(value);
// Keep separate histories by size, across difficulty levels: changing clues does not make a new graph.
export function seenTopologies(nodeCount: number): string[] {
  const cell = String(nodeCount);
  try {
    const stored = JSON.parse(localStorage.getItem(key) ?? '{}');
    if (stored && typeof stored === 'object') {
      if (Array.isArray(stored._recent)) recent = stored._recent.filter(valid);
      for (const [size, values] of Object.entries(stored))
        if (/^\d{1,2}$/.test(size) && Array.isArray(values))
          memory.set(
            size,
            [...new Set<string>([...values.filter(valid), ...(memory.get(size) ?? [])])].slice(
              demoMode ? 0 : -1024,
            ),
          );
    }
  } catch {
    /* Memory still avoids repeats when storage is unavailable. */
  }
  return [...(memory.get(cell) ?? [])];
}
export function rememberTopology(result: Generated): void {
  if (!valid(result.topologyKey)) return;
  const cell = String(result.puzzle.nodeCount);
  const seen = seenTopologies(result.puzzle.nodeCount);
  memory.set(
    cell,
    [...seen.filter((k) => k !== result.topologyKey), result.topologyKey].slice(
      demoMode ? 0 : -1024,
    ),
  );
  recent = [...recent.filter((k) => k !== result.topologyKey), result.topologyKey].slice(-51200);
  try {
    localStorage.setItem(key, JSON.stringify({ ...Object.fromEntries(memory), _recent: recent }));
  } catch {
    /* session memory */
  }
}

/** New entries use chronology; old saves have no cross-size timestamps, so interleave them. */
export function seenTopologiesInRange(minimum: number, maximum: number): string[] {
  const cells = Array.from({ length: maximum - minimum + 1 }, (_, i) =>
    seenTopologies(minimum + i),
  );
  const eligible = new Set(cells.flat());
  const ordered: string[] = [];
  const longest = Math.max(0, ...cells.map((cell) => cell.length));
  for (let i = 0; i < longest; i++)
    for (const cell of cells) {
      const value = cell[cell.length - longest + i];
      if (value) ordered.push(value);
    }
  const chronological = recent.filter((value) => eligible.has(value));
  const known = new Set(chronological);
  return [...new Set([...ordered.filter((value) => !known.has(value)), ...chronological])].slice(
    demoMode ? 0 : -1024,
  );
}
