<script lang="ts">
  import type { Analysis, Generated } from '../api/dto';
  import { analyze } from '../api/client';
  import { inspectionFrames, type InspectionFrame } from '../game/inspection';
  import { explain, conclusion } from '../game/explanations';
  import { t } from '../i18n';
  import { errorMessage } from '../i18n/errors';
  let {
    generated,
    onframe,
  }: { generated: Generated; onframe: (frame: InspectionFrame | null) => void } = $props();
  let analysis = $state<Analysis | null>(null),
    open = $state(false),
    loading = $state(false),
    error = $state<unknown>(null),
    index = $state(0);
  const frames = $derived(analysis ? inspectionFrames(generated.puzzle, analysis.trace) : []);
  const current = $derived(frames[index]);
  const step = $derived(current?.step);
  const frontier = $derived(analysis?.trace.frontiers?.find((f) => f.selectedFactId === step?.id));
  const milestones = $derived(
    frames.map((f, i) => ({ f, i })).filter((x) => x.f.step?.hypothesisEvidence.length),
  );
  const premises = $derived(
    frames
      .map((f, i) => ({ f, i }))
      .filter((x) => x.f.step && step?.premises.includes(x.f.step.id)),
  );
  $effect(() => {
    onframe(open && current ? current : null);
  });
  async function inspect() {
    if (analysis) {
      open = true;
      return;
    }
    loading = true;
    error = null;
    try {
      analysis = await analyze(generated.puzzle);
      index = 0;
      open = true;
    } catch (e) {
      error = e;
    } finally {
      loading = false;
    }
  }
</script>

<section class="reference-inspector" aria-label={$t('reference.inspector')}>
  {#if !open}
    <button class="secondary" disabled={loading} onclick={inspect}
      >{$t(loading ? 'debug.analyzing' : 'reference.reveal')}</button
    >
    <p class="reference-caption">{$t('reference.spoilers')}</p>
  {:else}
    <div class="reference-inspector-heading">
      <h2>{$t('reference.inspector')}</h2>
      <button class="text-button" onclick={() => (open = false)}>{$t('reference.return')}</button>
    </div>
    <p role="status">{$t('reference.replayNotice')}</p>
    <p data-testid="reference-trace-status">
      {analysis?.trace.status === 'SOLVED' ? $t('debug.solved') : $t('reference.incomplete')}
    </p>
    <div class="trace-controls">
      <button
        class="secondary"
        aria-label={$t('debug.previous')}
        disabled={index === 0}
        onclick={() => index--}>←</button
      >
      <label
        >{$t('reference.step', { step: index, total: frames.length - 1 })}<input
          aria-label={$t('reference.position')}
          type="range"
          min="0"
          max={frames.length - 1}
          bind:value={index}
        /></label
      >
      <button
        class="secondary"
        aria-label={$t('debug.next')}
        disabled={index === frames.length - 1}
        onclick={() => index++}>→</button
      >
    </div>
    <p>
      {$t('reference.fixed', {
        count: Object.keys(current?.colors ?? {}).length,
        total: generated.puzzle.nodeCount,
      })}
    </p>
    {#if step}
      <p data-testid="reference-deduction">{explain(step, $t)} {conclusion(step, $t)}</p>
      {#if frontier}<p class="reference-caption">
          {$t(
            frontier.hypothesesDeferred ? 'reference.localChoices' : 'reference.refutationsFound',
            { count: frontier.size },
          )}{frontier.budgetExhausted ? ` · ${$t('debug.budget')}` : ''}
        </p>{/if}
      {#if premises.length}<div class="reference-premises">
          <span>{$t('reference.premises')}</span>{#each premises as p}<button
              class="text-button"
              onclick={() => (index = p.i)}>{$t('reference.shortStep', { n: p.i })}</button
            >{/each}
        </div>{/if}
      {#if step.hypothesisEvidence.length}<details>
          <summary>{$t('reference.assumptionProof')}</summary>
          <ol class="reference-evidence">
            {#each step.hypothesisEvidence.filter((s) => s.tier > 0 || s.ruleId === 'hypothesis') as evidence}<li
              >
                {explain(evidence, $t)}
                {conclusion(evidence, $t)}
              </li>{/each}
          </ol>
        </details>{/if}
    {:else}<p>{$t('reference.initial')}</p>{/if}
    {#if milestones.length}<div class="reference-milestones">
        <span>{$t('reference.milestones')}</span>{#each milestones as m, i}<button
            class="secondary"
            class:active={index === m.i}
            onclick={() => (index = m.i)}>{i + 1}</button
          >{/each}
      </div>{/if}
    <p class="reference-caption">{$t('reference.traceLimit')}</p>
  {/if}
  {#if error}<p role="alert">{errorMessage(error, $t)}</p>{/if}
</section>
