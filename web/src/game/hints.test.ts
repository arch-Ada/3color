import { expect, it } from 'vitest';
import type { Hint, Step } from '../api/dto';
import { translator } from '../i18n';
import { hintFocus, hintReason, walkthroughText } from './hints';
import { conclusion } from './explanations';
const deduction: Step = {
  id: 10,
  ruleId: 'bounded_contradiction',
  tier: 4,
  node: 2,
  beforeMask: 7,
  afterMask: 6,
  premises: [],
  witnesses: [0, 1, 2, 3, 4, 5, 6],
  explanationKey: 'bounded_contradiction',
  arguments: { assumedMask: 1 },
  hypothesisEvidence: [],
  conclusion: { kind: 'NARROW_DOMAIN', node: 2, mask: 6 },
};
const hint: Hint = {
  level: 'NUDGE',
  status: 'AVAILABLE',
  deduction,
  supportingSteps: [],
  explanation: {
    reasoningType: 'bounded_contradiction',
    primaryTargets: [2],
    conclusion: deduction.conclusion!,
    focusVertices: [2, 4],
    focusEdges: [{ a: 2, b: 4 }],
    orderedPath: [],
    assumption: { node: 2, mask: 1 },
    contradictionPoint: 4,
    walkthrough: [
      { kind: 'ASSUMPTION', node: 2, mask: 1, deduction: null, vertices: [2], edges: [] },
      {
        kind: 'IMPLICATION',
        node: 4,
        mask: 4,
        deduction: null,
        vertices: [2, 4],
        edges: [{ a: 2, b: 4 }],
      },
      { kind: 'CONTRADICTION', node: 4, mask: 0, deduction: null, vertices: [4], edges: [] },
      { kind: 'CONCLUSION', node: 2, mask: 6, deduction, vertices: [2], edges: [] },
    ],
  },
};
it('uses the explicit proof core, never the legacy global witness set', () => {
  expect(hintFocus(hint, null).vertices).toEqual([2, 4]);
  expect(hintFocus(hint, null).edges).toEqual([{ a: 2, b: 4 }]);
  expect(hintFocus(hint, null).assumption).toBeNull();
  expect(hintFocus(hint, null).contradiction).toBeNull();
  expect(hintFocus({ ...hint, level: 'REASON' }, 2).current).toEqual([4]);
  expect(hintFocus({ ...hint, level: 'REASON' }, 1).assumption).toBe(2);
  expect(hintFocus({ ...hint, level: 'REASON' }, 3).assumption).toBeNull();
  const focus = hintFocus({ ...hint, level: 'ANSWER' }, 2);
  expect(focus.vertices).toEqual([4]);
  expect(focus.targets).toEqual([2]);
  expect(focus.contradiction).toBe(4);
  expect(focus.edges).toEqual([]);
});
it('reason proposes an experiment without revealing its result in either language', () => {
  for (const language of ['en', 'de'] as const) {
    const t = translator(language);
    const reason = hintReason(hint, t);
    expect(reason).toContain(language === 'en' ? 'Red' : 'Rot');
    expect(reason).not.toContain(language === 'en' ? 'Green' : 'Grün');
    expect(reason).not.toContain(conclusion(deduction, t));
    expect(reason).not.toMatch(/facts|Beweisschritte/);
  }
});
it('walkthrough keeps assumption, implications, contradiction and conclusion in order', () => {
  for (const language of ['en', 'de'] as const) {
    const steps = hint.explanation!.walkthrough.map((s) =>
      walkthroughText(s, translator(language)),
    );
    expect(steps[0]).toContain(language === 'en' ? 'Red' : 'Rot');
    expect(steps[1]).toContain(language === 'en' ? 'Blue' : 'Blau');
    expect(steps[2]).toContain(language === 'en' ? 'Contradiction' : 'Widerspruch');
    expect(steps[3]).toContain(language === 'en' ? 'Green' : 'Grün');
  }
});
it('relation reason describes the structure without revealing equality', () => {
  const relation = {
    ...hint,
    explanation: { ...hint.explanation!, reasoningType: 'diamond_equality', assumption: null },
  };
  expect(hintReason(relation, translator('en'))).not.toContain('same color');
});
