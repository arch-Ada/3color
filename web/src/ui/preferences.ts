import { writable } from 'svelte/store';
import { locale, type Locale, translator } from '../i18n';
export type Theme = 'light' | 'dark';
export interface InterfacePreferences {
  version: 1;
  locale?: Locale;
  theme?: Theme;
}
export const interfaceKey = '3color-interface-v1';
export const theme = writable<Theme>('light');
export function parsePreferences(raw: string | null): InterfacePreferences {
  try {
    const p = JSON.parse(raw ?? '{}');
    if (p.version !== 1) return { version: 1 };
    return {
      version: 1,
      ...(p.locale === 'en' || p.locale === 'de' ? { locale: p.locale } : {}),
      ...(p.theme === 'light' || p.theme === 'dark' ? { theme: p.theme } : {}),
    };
  } catch {
    return { version: 1 };
  }
}
export function resolvePreferences(
  saved: InterfacePreferences,
  browserLanguage: string,
  prefersDark: boolean,
) {
  return {
    locale:
      saved.locale ?? ((browserLanguage.toLowerCase().startsWith('de') ? 'de' : 'en') as Locale),
    theme: saved.theme ?? ((prefersDark ? 'dark' : 'light') as Theme),
  };
}
function read(): InterfacePreferences {
  try {
    return parsePreferences(localStorage.getItem(interfaceKey));
  } catch {
    return { version: 1 };
  }
}
let explicit = read();
function persist(): boolean {
  try {
    localStorage.setItem(interfaceKey, JSON.stringify(explicit));
    return true;
  } catch {
    return false;
  }
}
export function applyLocale(value: Locale) {
  locale.set(value);
  document.documentElement.lang = value;
  const translate = translator(value);
  document.title = translate('app.title');
  document
    .querySelector('meta[name="description"]')
    ?.setAttribute('content', translate('app.description'));
}
export function applyTheme(value: Theme) {
  theme.set(value);
  document.documentElement.dataset.theme = value;
  document.documentElement.style.colorScheme = value;
  document
    .querySelector('meta[name="theme-color"]')
    ?.setAttribute('content', value === 'dark' ? '#252525' : '#eae6dc');
}
export function chooseLocale(value: Locale): boolean {
  explicit = { ...explicit, locale: value };
  applyLocale(value);
  return persist();
}
export function chooseTheme(value: Theme): boolean {
  explicit = { ...explicit, theme: value };
  applyTheme(value);
  return persist();
}
export function initializeInterface(): () => void {
  explicit = read();
  const media = matchMedia('(prefers-color-scheme: dark)');
  const resolved = resolvePreferences(explicit, navigator.language, media.matches);
  applyLocale(resolved.locale);
  applyTheme(resolved.theme);
  const change = (event: MediaQueryListEvent) => {
    if (!explicit.theme) applyTheme(event.matches ? 'dark' : 'light');
  };
  media.addEventListener('change', change);
  return () => media.removeEventListener('change', change);
}
