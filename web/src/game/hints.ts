import type { Hint, WalkthroughStep } from '../api/dto';
import type { Key, Translate } from '../i18n';
import { conclusion, explain, maskNames } from './explanations';

export function hintReason(hint: Hint, t: Translate): string {
  const assumption = hint.explanation?.assumption;
  if (assumption)
    return t('hint.tryAssumption', {
      n: assumption.node + 1,
      color: maskNames(t, assumption.mask),
    });
  const techniques: Record<string, Key> = {
    adjacent_color_elimination: 'hint.technique.adjacency',
    locked_two_color_edge: 'hint.technique.locked',
    diamond_equality: 'hint.technique.diamond',
    neighborhood_parity: 'hint.technique.neighborhood',
    two_color_parity: 'hint.technique.parity',
    relation_propagation: 'hint.technique.relation',
  };
  return t(
    techniques[hint.explanation?.reasoningType ?? hint.deduction?.ruleId ?? ''] ??
      'hint.technique.generic',
  );
}

export function walkthroughText(step: WalkthroughStep, t: Translate): string {
  if (step.kind === 'ASSUMPTION')
    return t('hint.assume', { n: (step.node ?? 0) + 1, color: maskNames(t, step.mask ?? 0) });
  if (step.kind === 'CONTRADICTION') {
    if (step.deduction?.conclusion?.kind === 'NARROW_DOMAIN' && step.deduction.afterMask === 0)
      return conclusion(step.deduction, t);
    return step.node === null
      ? t('explain.chainConflict')
      : t('hint.conflictAt', { n: step.node + 1 });
  }
  if (step.kind === 'IMPLICATION')
    return t('explain.forced', { n: (step.node ?? 0) + 1, colors: maskNames(t, step.mask ?? 0) });
  if (!step.deduction) return '';
  return step.kind === 'CONCLUSION'
    ? conclusion(step.deduction, t)
    : `${explain(step.deduction, t)} ${conclusion(step.deduction, t)}`;
}

export function hintFocus(hint: Hint | null, index: number | null) {
  const e = hint?.explanation;
  const step = hint?.level !== 'NUDGE' && index !== null ? e?.walkthrough[index] : undefined;
  let activeAssumption: number | null = null;
  if (step && index !== null)
    for (const item of e!.walkthrough.slice(0, index + 1)) {
      if (item.kind === 'ASSUMPTION') activeAssumption = item.node;
      if (item.kind === 'CONCLUSION') activeAssumption = null;
    }
  return {
    targets: e?.primaryTargets ?? (hint?.deduction ? [hint.deduction.node] : []),
    vertices: step?.vertices ?? e?.focusVertices ?? [],
    edges: step?.edges ?? e?.focusEdges ?? [],
    assumption: step
      ? activeAssumption
      : hint?.level === 'ANSWER'
        ? (e?.assumption?.node ?? null)
        : null,
    contradiction: step
      ? step.kind === 'CONTRADICTION'
        ? step.node
        : null
      : hint?.level === 'ANSWER'
        ? (e?.contradictionPoint ?? null)
        : null,
    current: step?.vertices ?? [],
  };
}
