import { expect, it } from 'vitest';
import type { Step } from '../api/dto';
import { translator } from '../i18n';
import { explain, conclusion } from './explanations';
const step: Step = {
  id: 4,
  ruleId: 'diamond_equality',
  tier: 2,
  node: 2,
  beforeMask: 1,
  afterMask: 1,
  premises: [0, 1],
  witnesses: [0, 1, 2, 3],
  explanationKey: 'diamond_equality',
  arguments: { edgeA: 0, edgeB: 1 },
  hypothesisEvidence: [],
  conclusion: { kind: 'EQUAL', a: 2, b: 3 },
};
it('describes a relation rather than mistaking the legacy mask for a color assignment', () => {
  expect(conclusion(step, translator('en'))).toBe('Vertices 3 and 4 must have the same color.');
  expect(
    conclusion({ ...step, conclusion: { kind: 'NOT_EQUAL', a: 2, b: 3 } }, translator('de')),
  ).toContain('verschiedene Farben');
  expect(explain(step, translator('en'))).toContain('third color');
});
it('renders an explicit implication path in both languages', () => {
  const chain: Step = {
    ...step,
    explanationKey: 'implication_chain',
    arguments: { assumedMask: 1 },
    conclusion: { kind: 'NARROW_DOMAIN', node: 2, mask: 2 },
    implicationChain: [
      { source: 2, sourceMask: 1, target: 3, targetMask: 4 },
      { source: 3, sourceMask: 4, target: 2, targetMask: 2 },
    ],
  };
  expect(explain(chain, translator('en'))).toContain(
    'Vertex 3 = Red → Vertex 4 = Blue → Vertex 3 = Green',
  );
  expect(explain(chain, translator('de'))).toContain(
    'Knoten 3 = Rot → Knoten 4 = Blau → Knoten 3 = Grün',
  );
});
it('handles relational contradictions without inventing a source color', () => {
  const conflict: Step = {
    ...step,
    explanationKey: 'relation_propagation',
    arguments: {},
    conclusion: { kind: 'CONTRADICTION' },
  };
  expect(explain(conflict, translator('en'))).toContain('Contradiction');
  expect(explain(conflict, translator('de'))).not.toContain('NaN');
});
it('keeps pre-v2 stored domain steps readable', () => {
  const legacy: Step = {
    ...step,
    conclusion: undefined,
    explanationKey: 'adjacent_color_elimination',
    arguments: { sourceNode: 0, colorMask: 1 },
    afterMask: 4,
  };
  expect(explain(legacy, translator('en'))).toContain('Red');
  expect(conclusion(legacy, translator('en'))).toContain('Blue');
});
