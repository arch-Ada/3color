// Runs before CSS and the app to prevent a flash of an explicitly saved theme.
(() => {
  let saved = {};
  try {
    const value = JSON.parse(localStorage.getItem('3color-interface-v1') || '{}');
    if (value?.version === 1) saved = value;
  } catch {
    /* Storage is optional. */
  }
  const theme = ['light', 'dark'].includes(saved.theme)
    ? saved.theme
    : matchMedia('(prefers-color-scheme: dark)').matches
      ? 'dark'
      : 'light';
  document.documentElement.dataset.theme = theme;
  document.documentElement.style.colorScheme = theme;
  document.documentElement.lang = ['en', 'de'].includes(saved.locale)
    ? saved.locale
    : navigator.language.toLowerCase().startsWith('de')
      ? 'de'
      : 'en';
})();
