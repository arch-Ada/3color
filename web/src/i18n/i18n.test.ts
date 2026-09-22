import { describe, expect, it } from 'vitest';
import { en } from './en';
import { de } from './de';
import { translator, vertexLabel } from './index';
import { parsePreferences, resolvePreferences } from '../ui/preferences';
describe('interface localization and preferences', () => {
  it('has matching catalogs and interpolation parameters', () => {
    for (const key of Object.keys(en) as (keyof typeof en)[]) {
      expect(de[key]).toBeTruthy();
      expect([...new Set(de[key].match(/\{\w+\}/g))].sort()).toEqual(
        [...new Set(en[key].match(/\{\w+\}/g))].sort(),
      );
    }
  });
  it('names colors and all vertex states in each language', () => {
    expect(
      ['RED', 'GREEN', 'BLUE'].map((c) => translator('en')(`colors.${c}` as keyof typeof en)),
    ).toEqual(['Red', 'Green', 'Blue']);
    expect(
      ['RED', 'GREEN', 'BLUE'].map((c) => translator('de')(`colors.${c}` as keyof typeof en)),
    ).toEqual(['Rot', 'Grün', 'Blau']);
    expect(vertexLabel(translator('en'), 2, 'RED', true, true, true)).toBe(
      'Vertex 3, Red, given, selected, conflicting',
    );
    expect(vertexLabel(translator('de'), 2, 'RED', true, true, true)).toBe(
      'Knoten 3, Rot, vorgegeben, ausgewählt, im Konflikt',
    );
  });
  it('uses browser fallbacks and lets explicit choices win', () => {
    expect(resolvePreferences({ version: 1 }, 'de-CH', true)).toEqual({
      locale: 'de',
      theme: 'dark',
    });
    expect(resolvePreferences({ version: 1 }, 'fr-FR', false)).toEqual({
      locale: 'en',
      theme: 'light',
    });
    expect(resolvePreferences({ version: 1, locale: 'en', theme: 'light' }, 'de-DE', true)).toEqual(
      { locale: 'en', theme: 'light' },
    );
  });
  it('recovers from malformed or unsupported saved preferences', () => {
    for (const raw of [
      null,
      'null',
      '{broken',
      '{"version":2}',
      '{"version":1,"theme":"neon","locale":"fr"}',
    ])
      expect(parsePreferences(raw)).toEqual({ version: 1 });
    expect(parsePreferences('{"version":1,"theme":"dark","locale":"de"}')).toEqual({
      version: 1,
      theme: 'dark',
      locale: 'de',
    });
  });
});
