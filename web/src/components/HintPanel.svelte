<script lang="ts">
  import type { Hint, HintLevel } from '../api/dto';
  import { conclusion } from '../game/explanations';
  import { hintReason, walkthroughText } from '../game/hints';
  import { t } from '../i18n';
  import Icon from './Icon.svelte';
  let {
    hint,
    busy,
    selected,
    onrequest,
    onclose,
    onstep,
  }: {
    hint: Hint;
    busy: boolean;
    selected: number;
    onrequest: (level: HintLevel) => void;
    onclose: () => void;
    onstep: (index: number | null) => void;
  } = $props();
  let targets = $derived(
    hint.explanation?.primaryTargets ?? (hint.deduction ? [hint.deduction.node] : []),
  );
  let active = $state<number | null>(null);
  let copy: HTMLDivElement;
  $effect(() => {
    hint;
    active;
    if (copy) copy.scrollTop = 0;
  });
  $effect(() => {
    active = hint.level === 'REASON' && hint.explanation?.walkthrough.length ? 0 : null;
  });
  $effect(() => {
    onstep(active);
  });
</script>

<section class="hint-panel" aria-label={$t('game.hint')}>
  <div class="hint-heading">
    <h3>{$t('game.hint')}</h3>
    <button class="icon-button" aria-label={$t('hint.close')} onclick={onclose}
      ><Icon name="close" /></button
    >
  </div>
  <!-- Keyboard users need to focus this scrollable text region. -->
  <!-- svelte-ignore a11y_no_noninteractive_tabindex -->
  <div
    bind:this={copy}
    class="hint-copy"
    role="region"
    aria-label={$t('hint.proof')}
    tabindex="0"
    aria-live="polite"
    aria-atomic="true"
  >
    {#if hint.deduction}
      <p class="hint-target-label">
        {$t('hint.targets', { vertices: targets.map((v) => v + 1).join(', ') })}
        {#if !targets.includes(selected)}
          {$t('hint.selected', { n: selected + 1 })}{/if}
      </p>
      {#if hint.status === 'CORRECTION'}<p>{$t('hint.correction', { n: targets[0] + 1 })}</p>{/if}
      {#if hint.level === 'NUDGE'}
        {#if hint.status !== 'CORRECTION'}<p>{$t('hint.nudge', { n: targets[0] + 1 })}</p>{/if}
      {:else if hint.level === 'REASON' && active !== null && hint.explanation}
        <p class="hint-step-count">
          {$t('hint.step', { current: active + 1, total: hint.explanation.walkthrough.length })}
        </p>
        <p>{walkthroughText(hint.explanation.walkthrough[active], $t)}</p>
      {:else if hint.level === 'REASON'}<p>{hintReason(hint, $t)}</p>
      {:else}<p class="answer">{conclusion(hint.deduction, $t)}</p>{/if}
    {:else}<p>
        {$t(
          hint.status === 'CONFLICT'
            ? hint.explanation?.reasoningType === 'edge_conflict'
              ? 'hint.edgeConflict'
              : 'hint.conflict'
            : hint.status === 'SOLVED'
              ? 'status.solved'
              : 'hint.none',
          { a: (targets[0] ?? 0) + 1, b: (targets[1] ?? 0) + 1 },
        )}
      </p>{/if}
  </div>
  {#if hint.deduction}
    <div class="hint-navigation" class:walking={hint.level === 'REASON'}>
      {#if hint.level === 'NUDGE'}
        <button class="hint-next" disabled={busy} onclick={() => onrequest('REASON')}>
          {$t('hint.guide')}<Icon name="chevron" />
        </button>
      {:else if hint.level === 'REASON'}
        <button
          class="hint-next"
          disabled={busy || active === null || active === 0}
          onclick={() => {
            if (active !== null && active > 0) active--;
          }}>{$t('hint.previous')}</button
        >
        <button
          class="hint-next"
          disabled={busy}
          onclick={() => {
            if (
              active !== null &&
              hint.explanation &&
              active + 1 < hint.explanation.walkthrough.length
            )
              active++;
            else onrequest('ANSWER');
          }}
        >
          {$t(
            active !== null && hint.explanation && active + 1 < hint.explanation.walkthrough.length
              ? 'hint.nextStep'
              : 'hint.showColor',
          )}
          <Icon name="chevron" />
        </button>
      {:else}
        <button class="hint-next" disabled={busy} onclick={() => onrequest('REASON')}
          >{$t('hint.proof')}</button
        >
      {/if}
      {#if hint.level === 'NUDGE' || (hint.level === 'REASON' && active !== null && hint.explanation && active + 1 < hint.explanation.walkthrough.length)}
        <button class="hint-next" disabled={busy} onclick={() => onrequest('ANSWER')}
          >{$t('hint.showColor')}</button
        >
      {/if}
    </div>
  {/if}
</section>
