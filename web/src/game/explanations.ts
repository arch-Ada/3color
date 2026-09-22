import type { Step } from '../api/dto';
import { colorName, type Translate } from '../i18n';
export const maskNames = (t: Translate, mask: number) =>
  (
    [
      ['RED', 1],
      ['GREEN', 2],
      ['BLUE', 4],
    ] as const
  )
    .filter(([, bit]) => (mask & bit) !== 0)
    .map(([color]) => colorName(t, color))
    .join(t('explain.or'));
export function explain(step: Step, t: Translate): string {
  const values = {
    n: step.node + 1,
    witnesses: step.witnesses.map((v) => v + 1).join(', '),
    source: (step.arguments.sourceNode ?? step.witnesses[0]) + 1,
    color: maskNames(t, step.arguments.colorMask ?? step.arguments.assumedMask),
    colors: maskNames(t, step.arguments.pairMask),
    facts: step.hypothesisEvidence.length,
  };
  switch (step.explanationKey) {
    case 'triangle_completion':
    case 'two_colored_neighbors':
      return t(
        step.explanationKey === 'triangle_completion' ? 'explain.triangle' : 'explain.twoNeighbors',
        {
          a: step.arguments.neighbourA + 1,
          b: step.arguments.neighbourB + 1,
          colorA: maskNames(t, step.arguments.maskA),
          colorB: maskNames(t, step.arguments.maskB),
        },
      );
    case 'diamond_equality':
      return t('explain.diamond', { a: step.arguments.edgeA + 1, b: step.arguments.edgeB + 1 });
    case 'neighborhood_parity':
      return t(
        step.conclusion?.kind === 'CONTRADICTION'
          ? 'explain.neighborhoodConflict'
          : 'explain.neighborhood',
        { center: step.arguments.center + 1, witnesses: values.witnesses },
      );
    case 'relation_propagation':
      if (step.conclusion?.kind === 'CONTRADICTION') return t('explain.chainConflict');
      return t(
        step.arguments.equality === 1 ? 'explain.equalPropagation' : 'explain.differentPropagation',
        values,
      );
    case 'implication_chain': {
      const chain = step.implicationChain ?? [];
      const labels = chain.length
        ? [
            t('vertex.name', { n: chain[0].source + 1 }) +
              ' = ' +
              maskNames(t, chain[0].sourceMask),
            ...chain.map((link) =>
              link.targetMask === 0
                ? t('explain.chainConflict')
                : t('vertex.name', { n: link.target + 1 }) + ' = ' + maskNames(t, link.targetMask),
            ),
          ]
        : [];
      return t('explain.implication', { ...values, chain: labels.join(' → ') });
    }
    case 'relation_snapshot':
      return t('explain.relationSnapshot');
    case 'adjacent_color_elimination':
      return t('explain.adjacent', values);
    case 'locked_two_color_edge':
      return t('explain.locked', values);
    case 'two_color_parity':
      return t(step.afterMask === 0 ? 'explain.oddConflict' : 'explain.parity', values);
    case 'bounded_contradiction':
      return t('explain.hypothesis', values);
    case 'given':
      return t('explain.given', values);
    default:
      return t('explain.initial', values);
  }
}
export function conclusion(step: Step, t: Translate): string {
  if (step.conclusion?.kind === 'EQUAL' || step.conclusion?.kind === 'NOT_EQUAL')
    return t(step.conclusion.kind === 'EQUAL' ? 'explain.equal' : 'explain.notEqual', {
      a: step.conclusion.a + 1,
      b: step.conclusion.b + 1,
    });
  if (step.conclusion?.kind === 'CONTRADICTION') return t('explain.chainConflict');
  return t(
    step.afterMask === 0
      ? 'explain.contradiction'
      : [1, 2, 4].includes(step.afterMask)
        ? 'explain.forced'
        : 'explain.remaining',
    { n: step.node + 1, colors: maskNames(t, step.afterMask) },
  );
}
