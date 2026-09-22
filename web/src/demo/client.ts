import type { ColorMap, GenerationRequest, HintLevel, Puzzle } from '../api/dto';
import { ApiError } from '../api/error';
import { savedHint, selectEntry, type Manifest, type Payload } from './catalog';
const base = `${import.meta.env.BASE_URL}demo/`;
let manifest: Manifest | undefined;
let cachedFile = '';
let cached: Record<string, Payload> = {};
async function read<T>(file: string, signal?: AbortSignal): Promise<T> {
  const r = await fetch(base + file, { signal });
  if (!r.ok) throw new ApiError('Demo file unavailable', r.status, 'DEMO_DATA');
  return r.json();
}
async function index(signal?: AbortSignal): Promise<Manifest> {
  if (!manifest) {
    const next = await read<Manifest>('manifest.json', signal);
    if (next.version !== 3 || !next.entries.length)
      throw new ApiError('Unsupported demo data', 500, 'DEMO_DATA');
    manifest = next;
  }
  return manifest;
}
async function payload(file: string, id: string, signal?: AbortSignal): Promise<Payload> {
  if (cachedFile !== file) {
    const data = await read<Record<string, Payload>>(file, signal);
    cached = data;
    cachedFile = file;
  }
  if (!cached[id]) throw new ApiError('Missing puzzle', 404, 'DEMO_DATA');
  return cached[id];
}
async function forPuzzle(puzzle: Puzzle) {
  const entries = (await index()).entries;
  for (const entry of entries.filter((e) => e.logicalHash === puzzle.logicalHash)) {
    const data = await payload(entry.file, entry.id);
    const original = data.generated.puzzle;
    if (
      original.nodeCount === puzzle.nodeCount &&
      original.rules === puzzle.rules &&
      original.explanationModel === puzzle.explanationModel &&
      JSON.stringify(original.edges) === JSON.stringify(puzzle.edges) &&
      JSON.stringify(original.givens) === JSON.stringify(puzzle.givens) &&
      JSON.stringify(original.layout) === JSON.stringify(puzzle.layout)
    )
      return data;
  }
  throw new ApiError('Puzzle not in this demo snapshot', 404, 'DEMO_DATA');
}
export async function generateDemo(request: GenerationRequest, signal?: AbortSignal) {
  signal?.throwIfAborted();
  const entry = selectEntry((await index(signal)).entries, request);
  const data = await payload(entry.file, entry.id, signal);
  signal?.throwIfAborted();
  return structuredClone(data.generated);
}
export async function analyzeDemo(puzzle: Puzzle) {
  return structuredClone((await forPuzzle(puzzle)).analysis);
}
export async function hintDemo(puzzle: Puzzle, current: ColorMap, level: HintLevel) {
  return structuredClone(savedHint(await forPuzzle(puzzle), current, level));
}
