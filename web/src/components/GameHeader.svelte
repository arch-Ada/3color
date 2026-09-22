<script lang="ts">
  import { demoMode } from '../demo/mode';
  import { t, locale } from '../i18n';
  import { theme, chooseLocale, chooseTheme } from '../ui/preferences';
  import Icon from './Icon.svelte';
  let { onhelp, onstorageerror }: { onhelp: () => void; onstorageerror: () => void } = $props();
</script>

<header class="site-header">
  <a class="brand" href={demoMode ? './index.html' : '/'} aria-label={$t('app.home')}
    ><svg viewBox="0 0 28 28" aria-hidden="true"
      ><path d="m5 21 9-16 9 16Z" /><circle cx="5" cy="21" r="3.5" class="mark-red" /><circle
        cx="14"
        cy="5"
        r="3.5"
        class="mark-green"
      /><circle cx="23" cy="21" r="3.5" class="mark-blue" /></svg
    ><span>3Color</span>{#if demoMode}<small class="demo-label">Demo</small>{/if}</a
  >
  <div class="utilities">
    <div class="language-switch" role="group" aria-label={$t('settings.language')}>
      <button
        aria-label={$t('settings.english')}
        aria-pressed={$locale === 'en'}
        onclick={() => {
          if (!chooseLocale('en')) onstorageerror();
        }}>EN</button
      >
      <button
        aria-label={$t('settings.german')}
        aria-pressed={$locale === 'de'}
        onclick={() => {
          if (!chooseLocale('de')) onstorageerror();
        }}>DE</button
      >
    </div>
    <button
      class="icon-button theme-switch"
      role="switch"
      aria-checked={$theme === 'dark'}
      aria-label={$t('settings.theme')}
      title={$t($theme === 'dark' ? 'settings.themeLight' : 'settings.themeDark')}
      onclick={() => {
        if (!chooseTheme($theme === 'dark' ? 'light' : 'dark')) onstorageerror();
      }}><Icon name={$theme === 'dark' ? 'moon' : 'sun'} /></button
    >
    <button
      class="icon-button"
      aria-label={$t('help.title')}
      title={$t('help.title')}
      onclick={onhelp}><Icon name="help" /></button
    >
  </div>
</header>

<style>
  .demo-label {
    font-size: 0.65rem;
    font-weight: 500;
    opacity: 0.65;
    align-self: center;
  }
</style>
