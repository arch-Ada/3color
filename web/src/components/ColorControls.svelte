<script lang="ts">
  import type { Color } from '../api/dto';
  import { symbols } from '../game/model';
  import { t, colorName } from '../i18n';
  let {
    disabled,
    selected,
    pencil = false,
    notes = 0,
    oncolor,
  }: {
    disabled: boolean;
    selected?: Color;
    pencil?: boolean;
    notes?: number;
    oncolor: (color: Color) => void;
  } = $props();
  const colors: Color[] = ['RED', 'GREEN', 'BLUE'];
</script>

<div class="color-controls" role="group" aria-label={$t('game.chooseColor')}>
  {#each colors as color, i}
    <button
      class={`color-button ${color}`}
      {disabled}
      aria-label={$t(pencil ? 'game.noteColor' : 'game.color', { color: colorName($t, color) })}
      aria-pressed={pencil ? !!(notes & (1 << i)) : selected === color}
      onclick={() => oncolor(color)}
    >
      <span class="color-symbol" aria-hidden="true">{symbols[color]}</span><span class="color-name"
        >{colorName($t, color)}</span
      ><kbd aria-hidden="true">{pencil ? ['Q', 'W', 'E'][i] : i + 1}</kbd>
    </button>
  {/each}
</div>
