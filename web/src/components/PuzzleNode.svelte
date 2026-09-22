<script lang="ts">
  import type { Color } from '../api/dto';
  import { symbols } from '../game/model';
  import { t, vertexLabel, colorName } from '../i18n';
  let {
    node,
    x,
    y,
    radius,
    hitRadius,
    labelSize,
    color,
    notes = 0,
    given,
    selected,
    neighbor,
    highlighted,
    hintTarget = false,
    hintAssumption = false,
    hintContradiction = false,
    hintCurrent = false,
    invalid,
    onselect,
  }: {
    node: number;
    x: number;
    y: number;
    radius: number;
    hitRadius: number;
    labelSize: number;
    color?: Color;
    notes?: number;
    given: boolean;
    selected: boolean;
    neighbor: boolean;
    highlighted: boolean;
    hintTarget?: boolean;
    hintAssumption?: boolean;
    hintContradiction?: boolean;
    hintCurrent?: boolean;
    invalid: boolean;
    onselect: (node: number) => void;
  } = $props();
  const noteColors: Color[] = ['RED', 'GREEN', 'BLUE'];
  function sector(index: number) {
    const start = ((-90 + index * 120) * Math.PI) / 180;
    const end = start + (2 * Math.PI) / 3;
    return `M 0 0 L ${radius * Math.cos(start)} ${radius * Math.sin(start)} A ${radius} ${radius} 0 0 1 ${radius * Math.cos(end)} ${radius * Math.sin(end)} Z`;
  }
</script>

<g
  role="button"
  tabindex={selected ? 0 : -1}
  aria-label={[
    vertexLabel($t, node, color, given, selected, invalid),
    notes &&
      $t('game.notesLabel', {
        colors: noteColors
          .filter((_, i) => notes & (1 << i))
          .map((c) => colorName($t, c))
          .join(', '),
      }),
    hintTarget && $t('hint.targetRole'),
    hintAssumption && $t('hint.assumptionRole'),
    hintContradiction && $t('hint.contradictionRole'),
    hintCurrent && $t('hint.currentRole'),
  ]
    .filter(Boolean)
    .join(', ')}
  aria-pressed={selected}
  data-testid={`node-${node}`}
  data-color={color ?? ''}
  data-notes={notes}
  class="node"
  class:selected
  class:neighbor
  class:highlighted
  class:hint-target={hintTarget}
  class:hint-assumption={hintAssumption}
  class:hint-contradiction={hintContradiction}
  class:hint-current={hintCurrent}
  class:invalid
  class:given
  transform={`translate(${x},${y})`}
  onclick={() => onselect(node)}
  onkeydown={(event) => {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      onselect(node);
    }
  }}
  onfocus={() => onselect(node)}
>
  <circle class="hit-area" r={hitRadius} />
  <circle class="neighbor-ring" r={radius + 7} vector-effect="non-scaling-stroke" />
  <circle class="selection-ring" r={radius + 7} vector-effect="non-scaling-stroke" />
  <circle class="hint-ring" r={radius + 12} vector-effect="non-scaling-stroke" />
  <circle class={`disc ${color ?? 'empty'}`} r={radius} vector-effect="non-scaling-stroke" />
  {#if !color && notes}
    {#each noteColors as noteColor, i}
      {#if notes & (1 << i)}<path
          class={`note-sector ${noteColor}`}
          d={sector(i)}
          aria-hidden="true"
        />{/if}
    {/each}
  {/if}
  {#if color}<text
      class="symbol"
      dy=".35em"
      style={`font-size: ${radius}px`}
      text-anchor="middle"
      aria-hidden="true">{symbols[color]}</text
    >{:else}<circle r="2.5" class="empty-dot" />{/if}
  {#if given}<g class="given-mark" transform={`translate(${radius * 0.75},${-radius * 0.75})`}
      ><circle r="7" /><path d="m-3 0 2 2 4-4" vector-effect="non-scaling-stroke" /></g
    >{/if}
  {#if invalid}<path
      class="conflict-mark"
      d={`M ${-radius - 4},${-radius - 4} l 8,0 -4,-7 Z`}
      vector-effect="non-scaling-stroke"
    />{/if}
  <text
    style={`font-size: ${labelSize}px`}
    class="node-label"
    y={radius + 19}
    text-anchor="middle"
    aria-hidden="true">{node + 1}</text
  >
</g>
