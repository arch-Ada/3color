<script lang="ts">
  let {
    name,
  }: {
    name:
      | 'undo'
      | 'redo'
      | 'help'
      | 'sun'
      | 'moon'
      | 'plus'
      | 'minus'
      | 'close'
      | 'hint'
      | 'more'
      | 'clear'
      | 'check'
      | 'chevron';
  } = $props();
  const paths = {
    undo: 'M8 4 3 9l5 5M3 9h10a7 7 0 0 1 0 14',
    redo: 'm16 4 5 5-5 5M21 9H11a7 7 0 0 0 0 14',
    help: 'M9.5 8a2.5 2.5 0 1 1 4 2c-1.5 1-1.5 1.5-1.5 3M12 17v.1',
    sun: 'M12 2v2m0 16v2M2 12h2m16 0h2M5 5l1.5 1.5m11 11L19 19M5 19l1.5-1.5m11-11L19 5',
    moon: 'M20.7 14A9 9 0 0 1 10 3.3 9 9 0 1 0 20.7 14Z',
    plus: 'M12 5v14M5 12h14',
    minus: 'M5 12h14',
    close: 'm6 6 12 12M6 18 18 6',
    hint: 'M9 18h6m-6 3h6M8 15c0-2-3-3-3-7a7 7 0 0 1 14 0c0 4-3 5-3 7H8Z',
    more: 'M5 12h.01M12 12h.01M19 12h.01',
    clear: 'm8 17-5-5 10-9 8 8-7 7H8Zm0 0 6-6m-3 7h10',
    check: 'm5 12 4 4L19 6',
    chevron: 'm9 5 7 7-7 7',
  };
</script>

<svg
  class="icon"
  viewBox="0 0 24 24"
  fill="none"
  stroke="currentColor"
  stroke-width={name === 'more' ? 3 : 1.5}
  stroke-linecap="round"
  stroke-linejoin="round"
  aria-hidden="true"
>
  {#if name === 'help'}<circle cx="12" cy="12" r="10" />{/if}
  {#if name === 'sun'}<circle cx="12" cy="12" r="4" />{/if}
  <path d={paths[name]} />
</svg>
