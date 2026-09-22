import type {
  Generated,
  GenerationRequest,
  Puzzle,
  ColorMap,
  Hint,
  HintLevel,
  Analysis,
  ReferencePuzzle,
} from './dto';
import { ApiError } from './error';
export { ApiError } from './error';
import { demoMode } from '../demo/mode';
async function post<T>(path: string, body: unknown, signal?: AbortSignal): Promise<T> {
  const response = await fetch(`/api/v1/${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
    signal: signal
      ? AbortSignal.any([signal, AbortSignal.timeout(120_000)])
      : AbortSignal.timeout(120_000),
  });
  const data = await response.json();
  if (!response.ok)
    throw new ApiError(
      data.message ?? `Request failed (${response.status})`,
      response.status,
      data.code,
    );
  return data as T;
}
export const generate = (request: GenerationRequest, signal?: AbortSignal) =>
  demoMode
    ? import('../demo/client').then((d) => d.generateDemo(request, signal))
    : post<Generated>('puzzles/generate', request, signal);
export const hint = (puzzle: Puzzle, current: ColorMap, level: HintLevel, signal?: AbortSignal) =>
  demoMode
    ? import('../demo/client').then((d) => d.hintDemo(puzzle, current, level))
    : post<Hint>('hints', { puzzle, current, level }, signal);
export const analyze = (puzzle: Puzzle) =>
  demoMode
    ? import('../demo/client').then((d) => d.analyzeDemo(puzzle))
    : post<Analysis>('analyze', { puzzle });

export async function references(): Promise<ReferencePuzzle[]> {
  const response = await fetch('/api/v1/references', { signal: AbortSignal.timeout(120_000) });
  if (!response.ok) throw new ApiError(`Request failed (${response.status})`, response.status);
  return response.json();
}
