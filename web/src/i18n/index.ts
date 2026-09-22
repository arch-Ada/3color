import { derived, writable } from 'svelte/store';
import { en } from './en';
import { de } from './de';
import type { Band, Color } from '../api/dto';
export type Locale = 'en' | 'de';
export type Key = keyof typeof en;
export type Translate = (key: Key, values?: Record<string, string | number>) => string;
export const locale = writable<Locale>('en');
export function translator(language: Locale): Translate {
  const messages = language === 'de' ? de : en;
  return (key, values = {}) =>
    messages[key].replace(/\{(\w+)\}/g, (match, name) => String(values[name] ?? match));
}
export const t = derived(locale, translator);
export const colorName = (translate: Translate, color: Color) => translate(`colors.${color}`);
export const bandName = (translate: Translate, band: Band) => translate(`difficulty.${band}`);
export function vertexLabel(
  translate: Translate,
  n: number,
  color: Color | undefined,
  given: boolean,
  selected: boolean,
  conflict: boolean,
) {
  return [
    translate('vertex.name', { n: n + 1 }),
    color ? colorName(translate, color) : translate('vertex.empty'),
    given && translate('vertex.given'),
    selected && translate('vertex.selected'),
    conflict && translate('vertex.conflict'),
  ]
    .filter(Boolean)
    .join(', ');
}
