import { demoMode } from '../demo/mode';
import { seenTopologies, seenTopologiesInRange, rememberTopology } from './variety';
import type { Band, Generated, GenerationRequest, SizeCategory } from '../api/dto';
import { ApiError, generate } from '../api/client';

export const currentBands: Band[] = ['VERY_EASY', 'EASY', 'MEDIUM', 'CHALLENGING'];
export const minimumVertices = 4;
export const maximumVertices = 53;
export const presetVertices = 16;
export const sizeRanges: Record<SizeCategory, [number, number]> = {
  MINI: [4, 13],
  SMALL: [14, 23],
  MEDIUM: [24, 33],
  LARGE: [34, 43],
  VERY_LARGE: [44, 53],
};
export const sizeCategories = Object.keys(sizeRanges) as SizeCategory[];
export function sizeForVertices(n: number): SizeCategory {
  return sizeCategories.find((size) => n <= sizeRanges[size][1]) ?? 'VERY_LARGE';
}

export function clampVertices(current: number): number {
  return Math.min(
    maximumVertices,
    Math.max(minimumVertices, Number.isFinite(current) ? Math.trunc(current) : presetVertices),
  );
}

function pause(milliseconds: number, signal: AbortSignal): Promise<void> {
  signal.throwIfAborted();
  return new Promise((resolve, reject) => {
    const abort = () => {
      clearTimeout(timer);
      reject(signal.reason);
    };
    const timer = setTimeout(() => {
      signal.removeEventListener('abort', abort);
      resolve();
    }, milliseconds);
    signal.addEventListener('abort', abort, { once: true });
  });
}

/** Bounded server attempts, unlimited sequential seed retries until canceled or successful. */
export async function findPuzzle(
  seed: string,
  nodeCount: number,
  difficulty: Band,
  signal: AbortSignal,
  onAttempt: (attempt: number) => void,
  size?: SizeCategory,
): Promise<Generated> {
  if (!currentBands.includes(difficulty)) throw new Error('Unsupported player difficulty');
  const initialSeed = seed.trim();
  for (let attempt = 1; ; attempt++) {
    signal.throwIfAborted();
    const nextSeed = !initialSeed
      ? null
      : BigInt.asIntN(64, BigInt(initialSeed) + BigInt(attempt - 1)).toString();
    onAttempt(attempt);
    try {
      const result = await generate(
        {
          seed: nextSeed,
          ...(size ? { size } : { nodeCount: clampVertices(nodeCount) }),
          difficulty,
          ...(!initialSeed
            ? {
                excludeTopologies: size
                  ? seenTopologiesInRange(...sizeRanges[size])
                  : seenTopologies(clampVertices(nodeCount)),
              }
            : {}),
        },
        signal,
      );
      signal.throwIfAborted();
      rememberTopology(result);
      return result;
    } catch (error) {
      signal.throwIfAborted();
      const retryable =
        error instanceof ApiError &&
        (error.status === 429 || (error.status === 422 && error.code === 'DELETION_NO_MATCH'));
      if (!retryable) throw error;
      await pause(Math.min(attempt * 300, 2000), signal);
    }
  }
}
