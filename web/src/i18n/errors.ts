import { ApiError } from '../api/client';
import type { Translate } from './index';
export function errorMessage(error: unknown, t: Translate): string {
  if (error instanceof ApiError) {
    if (error.code === 'DEMO_EXHAUSTED') return t('demo.exhausted');
    if (error.code === 'DEMO_SEED') return t('demo.seed');
    if (error.code === 'DEMO_DATA') return t('demo.data');
    if (error.code === 'DELETION_NO_MATCH') return t('error.search');
    if (error.status === 400) return t('error.invalid');
    if (error.status === 413) return t('error.large');
    if (error.status === 422) return t('error.generation');
    if (error.status === 429) return t('error.busy');
    return t('error.request', { status: error.status });
  }
  if (error instanceof Error && error.name === 'TimeoutError') return t('error.timeout');
  return t('error.connection');
}
