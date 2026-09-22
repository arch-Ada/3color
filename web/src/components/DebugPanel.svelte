<script lang="ts">
  import { onDestroy } from 'svelte';
  import { inspectionFrames, type InspectionFrame } from '../game/inspection';
  import { demoMode } from '../demo/mode';
  import type { Generated, Analysis } from '../api/dto';
  import { analyze } from '../api/client';
  import { explain, conclusion } from '../game/explanations';
  import { t, bandName, type Key } from '../i18n';
  import { errorMessage } from '../i18n/errors';
  import Icon from './Icon.svelte';
  let {
    generated,
    onframe,
  }: { generated: Generated; onframe: (frame: InspectionFrame | null) => void } = $props();
  let analysis = $state<Analysis | null>(null),
    loading = $state(false),
    error = $state<unknown>(null),
    index = $state(0),
    open = $state(false),
    reasoningOpen = $state(false);
  const category = $derived(generated.playDifficulty?.category);
  let steps = $derived(analysis?.trace.steps.filter((s) => s.tier > 0) ?? []);
  const frames = $derived(analysis ? inspectionFrames(generated.puzzle, analysis.trace) : []);
  const ready = $derived(!loading && analysis?.trace.status === 'SOLVED' && steps.length > 0);
  const statuses: Record<string, Key> = {
    SOLVED: 'debug.solved',
    STALLED: 'debug.stalled',
    CONTRADICTION: 'debug.contradiction',
    BUDGET_EXHAUSTED: 'debug.budget',
  };
  async function inspect() {
    loading = true;
    error = null;
    try {
      analysis = await analyze(generated.puzzle);
      index = 0;
    } catch (e) {
      error = e;
    } finally {
      loading = false;
    }
  }
  $effect(() => {
    onframe(open && reasoningOpen && ready ? (frames[index] ?? null) : null);
  });
  onDestroy(() => onframe(null));
</script>

<details class="debug-panel" bind:open>
  <summary>{$t('debug.title')}<Icon name="plus" /></summary>
  <div class="debug-body">
    <details
      bind:open={reasoningOpen}
      class="surface-section"
      ontoggle={(event) => {
        if (event.currentTarget.open && !analysis && !loading) void inspect();
      }}
    >
      <summary>{$t('surface.reasoning')}</summary>
      {#if error}<p role="alert" class="error-text">{errorMessage(error, $t)}</p>{/if}
      {#if analysis && analysis.trace.status !== 'SOLVED'}
        <p role="alert">{$t(statuses[analysis.trace.status] ?? 'debug.stalled')}</p>
      {/if}
      <div class="trace-controls" aria-busy={loading}>
        <button
          class="icon-button"
          aria-label={$t('debug.previous')}
          disabled={!ready || index === 0}
          onclick={() => index--}><Icon name="undo" /></button
        >
        <label>
          <span class="trace-position"
            >{#if ready}{$t('debug.step', { step: index, total: steps.length })}{/if}</span
          >
          <input
            type="range"
            aria-label={$t('surface.reasoning')}
            min="0"
            max={steps.length}
            bind:value={index}
            disabled={!ready}
          />
        </label>
        <button
          class="icon-button"
          aria-label={$t('debug.next')}
          disabled={!ready || index === steps.length}
          onclick={() => index++}><Icon name="redo" /></button
        >
      </div>
      {#if ready && index > 0}
        <p>{explain(steps[index - 1], $t)} {conclusion(steps[index - 1], $t)}</p>
        {#if steps[index - 1].hypothesisEvidence.length}
          <ol>
            {#each steps[index - 1].hypothesisEvidence as fact}<li>
                {explain(fact, $t)}
                {conclusion(fact, $t)}
              </li>{/each}
          </ol>
        {/if}
      {/if}
    </details>
    <details class="surface-section">
      <summary>{$t('surface.technical')}</summary>
      <dl class="debug-grid">
        <div>
          <dt>{$t('surface.vertices')}</dt>
          <dd>{generated.puzzle.nodeCount}</dd>
        </div>
        <div>
          <dt>{$t('surface.edges')}</dt>
          <dd>{generated.puzzle.edges.length}</dd>
        </div>
        <div>
          <dt>{$t('surface.clues')}</dt>
          <dd>{Object.keys(generated.puzzle.givens).length}</dd>
        </div>
        <div>
          <dt>{$t('settings.difficulty')}</dt>
          <dd>{category ? bandName($t, category) : $t('surface.unrated')}</dd>
        </div>
      </dl>
      <dl class="debug-list">
        <div>
          <dt>{$t('surface.originSeed')}</dt>
          <dd data-testid="puzzle-seed">{generated.generation.masterSeed}</dd>
        </div>
        {#if generated.supplyId}<div>
            <dt>{$t('surface.replayId')}</dt>
            <dd class="hash">{generated.supplyId}</dd>
          </div>{/if}
      </dl>
      {#if generated.supplyId && !demoMode}<a
          href={`/api/v1/puzzles/supply/${generated.supplyId}`}
          target="_blank"
          rel="noreferrer">{$t('surface.record')}</a
        >{/if}

      <dl class="debug-grid">
        <div>
          <dt>{$t('debug.generator')}</dt>
          <dd>{generated.generation.generatorVersion}</dd>
        </div>
        <div>
          <dt>{$t('debug.attempt')}</dt>
          <dd>{generated.generation.attemptIndex}</dd>
        </div>
        <div>
          <dt>{$t('debug.averageDegree')}</dt>
          <dd>{generated.metrics.averageDegree.toFixed(2)}</dd>
        </div>
        <div>
          <dt>{$t('debug.degreeRange')}</dt>
          <dd>{generated.metrics.minDegree} – {generated.metrics.maxDegree}</dd>
        </div>
        <div>
          <dt>{$t('debug.triangles')}</dt>
          <dd>{generated.metrics.triangleCount}</dd>
        </div>
        <div>
          <dt>{$t('debug.cycleRank')}</dt>
          <dd>{generated.metrics.cycleRank}</dd>
        </div>
        <div>
          <dt>{$t('debug.components')}</dt>
          <dd>{generated.metrics.connectedComponents}</dd>
        </div>
        <div>
          <dt>{$t('debug.degreeDistribution')}</dt>
          <dd>
            {Object.entries(generated.metrics.degreeDistribution)
              .map(([degree, count]) => `${degree}: ${count}`)
              .join(' · ')}
          </dd>
        </div>
      </dl>
      {#if generated.proof}
        <p>
          {generated.proof.modelVersion ?? 'proof-level-v1'}
          {#if generated.playDifficulty}
            · {generated.playDifficulty.modelVersion}{/if}
        </p>
        <table class="profile-table">
          <thead
            ><tr
              ><th>{$t('experiment.level')}</th><th>{$t('experiment.status')}</th><th
                >{$t('experiment.states')}</th
              ></tr
            ></thead
          >
          <tbody
            >{#each [['P0', generated.proof.p0], ['P1', generated.proof.p1], ['P2', generated.proof.p2]] as [level, result]}
              {#if typeof result === 'object' && result}<tr
                  ><td>{level}</td><td>{result.status}</td><td>{result.states}</td></tr
                >{/if}
            {/each}</tbody
          >
        </table>
        {#if generated.proof.p2}<p>
            {$t('experiment.rounds')}: {generated.proof.p2.refutationRounds}
          </p>{/if}
      {/if}
      <dl class="debug-list">
        <div>
          <dt>{$t('debug.ruleUsage')}</dt>
          <dd>
            {#each Object.entries(generated.difficulty.ruleUsageCounts) as [rule, count]}<span
                >{rule}: {count}</span
              >{/each}
          </dd>
        </div>
        <div>
          <dt>{$t('debug.hash')}</dt>
          <dd class="hash">{generated.puzzle.logicalHash}</dd>
        </div>
      </dl>
      {#if !demoMode}<a class="api-link" href="/swagger-ui.html">{$t('debug.api')}</a>{/if}
    </details>
  </div>
</details>

<style>
  .trace-position {
    display: block;
    min-height: 1.65em;
  }
  .trace-controls input:disabled {
    opacity: 0.4;
    filter: grayscale(1);
    cursor: default;
  }

  .debug-body {
    padding-top: 0;
  }
  .surface-section + .surface-section {
    border-top: var(--stroke-thin) solid var(--line);
  }
  .debug-grid {
    margin-bottom: var(--space-4);
  }
</style>
