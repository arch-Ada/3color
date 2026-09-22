<script lang="ts">
  import { GenerationSession } from './game/generationSession';
  import { HintSession } from './game/hintSession';
  import { demoMode, demoStoragePrefix } from './demo/mode';
  import ReferenceInspector from './components/ReferenceInspector.svelte';
  import type { InspectionFrame } from './game/inspection';
  import type { ReferencePuzzle, ReferenceId } from './api/dto';
  import { onMount, tick } from 'svelte';
  import type { Direction } from './game/navigation';
  let board = $state<{ navigate: (direction: Direction) => number }>();
  import type { Band, Color, Generated, Hint, HintLevel, SizeCategory } from './api/dto';
  import * as api from './api/client';
  import { newGame, type Game } from './game/model';
  import { reduce, type Action } from './game/reducer';
  import { invalidEdges, isComplete } from './game/validation';
  import { load, save } from './game/persistence';
  import { rememberTopology } from './game/variety';
  import {
    findPuzzle,
    currentBands,
    sizeRanges,
    sizeCategories,
    sizeForVertices,
    minimumVertices,
    maximumVertices,
    clampVertices as clamp,
  } from './game/generation';
  import { t, bandName } from './i18n';
  import { errorMessage } from './i18n/errors';
  import GameHeader from './components/GameHeader.svelte';
  import Dialog from './components/Dialog.svelte';
  import Icon from './components/Icon.svelte';
  import PuzzleBoard from './components/PuzzleBoard.svelte';
  import ColorControls from './components/ColorControls.svelte';
  import HintPanel from './components/HintPanel.svelte';
  import { hintFocus } from './game/hints';
  import DebugPanel from './components/DebugPanel.svelte';

  const referenceMode =
    !demoMode && new URLSearchParams(window.location.search).get('workshop') === 'references';
  let references = $state<ReferencePuzzle[]>([]);
  let referenceId = $state<ReferenceId>('dependency');
  let inspection = $state<InspectionFrame | null>(null);
  const storageKey = () =>
    demoMode
      ? `${demoStoragePrefix}-progress`
      : referenceMode
        ? `3color-reference-v1-${referenceId}`
        : undefined;
  const clampVertices = (value: number) => clamp(value);

  let game = $state<Game | null>(null),
    generated = $state<Generated | null>(null);
  let difficulty = $state<Band>('EASY'),
    nodeCount = $state(16),
    seed = $state('');
  let size = $state<SizeCategory>('SMALL');
  let pencil = $state(false);
  let busy = $state(false),
    hintBusy = $state(false),
    error = $state<unknown>(null),
    storageError = $state(false);
  let hint = $state<Hint | null>(null),
    debugHighlight = $state<number[]>([]);
  let panel = $state<'settings' | 'help' | 'more' | 'reset' | null>(null);
  const generationSession = new GenerationSession({
    busy: (value) => {
      busy = value;
    },
    attempt: (value) => {
      attempt = value;
    },
    accepted: (result) => {
      if (demoMode) rememberTopology(result);
      generated = result;
      nodeCount = result.puzzle.nodeCount;
      game = newGame(result.puzzle);
      persist();
    },
    failed: (failure) => {
      error = failure;
    },
  });
  const hintSession = new HintSession({
    current: () => game,
    allowed: () => !busy && !inspection,
    busy: (value) => {
      hintBusy = value;
    },
    accepted: (result) => {
      hint = result;
      debugHighlight = [];
    },
    failed: (failure) => {
      error = failure;
    },
  });
  let attempt = $state(0),
    canceled = $state(false);
  const displayGame = $derived(
    game && inspection ? { ...game, colors: inspection.colors, notes: inspection.notes } : game,
  );
  let complete = $derived(game ? isComplete(game.puzzle, game.colors) : false);
  let conflicts = $derived(game ? invalidEdges(game.puzzle, game.colors).length : 0);
  let colored = $derived(displayGame ? Object.keys(displayGame.colors).length : 0);
  let entered = $derived(
    game
      ? Object.keys({ ...game.colors, ...game.notes }).filter(
          (n) => !game!.puzzle.givens[Number(n)],
        ).length
      : 0,
  );
  let walkthroughIndex = $state<number | null>(null);
  let proofFocus = $derived(hintFocus(hint, walkthroughIndex));
  let highlighted = $derived(
    inspection
      ? inspection.highlighted
      : debugHighlight.length
        ? debugHighlight
        : proofFocus.vertices,
  );
  let status = $derived(
    inspection
      ? referenceMode
        ? $t('reference.replayNotice')
        : ''
      : busy
        ? $t(attempt > 1 ? 'status.retry' : 'status.generating', { attempt })
        : canceled
          ? $t('status.canceled')
          : complete
            ? $t('status.solved')
            : conflicts
              ? $t(conflicts === 1 ? 'status.conflict.one' : 'status.conflict.many', {
                  count: conflicts,
                })
              : '',
  );
  const bands = currentBands;

  let selectionSave: ReturnType<typeof setTimeout> | undefined;
  function flushSelectionSave() {
    if (selectionSave === undefined) return;
    clearTimeout(selectionSave);
    selectionSave = undefined;
    persist();
  }
  onMount(() => {
    const hidden = () => {
      if (document.visibilityState === 'hidden') flushSelectionSave();
    };
    document.addEventListener('visibilitychange', hidden);
    window.addEventListener('pagehide', flushSelectionSave);
    return () => {
      flushSelectionSave();
      generationSession.cancel();
      hintSession.cancel();
      document.removeEventListener('visibilitychange', hidden);
      window.removeEventListener('pagehide', flushSelectionSave);
    };
  });
  onMount(() => {
    if (referenceMode) {
      void loadReferences();
      return;
    }
    const saved = load(storageKey());
    if (saved) {
      game = saved.game;
      generated = saved.generated;
      rememberTopology(saved.generated);
      ({ difficulty, nodeCount, seed } = saved.preferences);

      nodeCount = clampVertices(nodeCount);
      size = saved.preferences.size ?? sizeForVertices(nodeCount);
      changeDifficulty(difficulty);
    } else void generate();
  });
  async function loadReferences() {
    busy = true;
    try {
      references = await api.references();
      const requested = new URLSearchParams(window.location.search).get('reference');
      chooseReference(references.find((r) => r.id === requested)?.id ?? 'dependency');
    } catch (e) {
      error = e;
    } finally {
      busy = false;
    }
  }
  function chooseReference(id: ReferenceId) {
    const reference = references.find((r) => r.id === id);
    if (!reference) return;
    inspection = null;
    hint = null;
    debugHighlight = [];
    panel = null;
    referenceId = id;
    generated = reference.generated;
    const saved = load(storageKey());
    game =
      saved?.game.puzzle.logicalHash === generated.puzzle.logicalHash
        ? { ...saved.game, puzzle: generated.puzzle }
        : newGame(generated.puzzle);
    difficulty = generated.proof!.band;
    nodeCount = generated.puzzle.nodeCount;
    seed = '';
    const url = new URL(window.location.href);
    url.searchParams.set('reference', id);
    history.replaceState(null, '', url);
    persist();
  }
  function persist() {
    clearTimeout(selectionSave);
    selectionSave = undefined;
    if (
      game &&
      generated &&
      !save(
        {
          version: 2,
          game,
          generated,
          preferences: {
            difficulty,
            size,
            nodeCount:
              Number.isInteger(nodeCount) &&
              nodeCount >= minimumVertices &&
              nodeCount <= maximumVertices
                ? nodeCount
                : clampVertices(generated.puzzle.nodeCount),
            seed,
          },
        },
        storageKey(),
      )
    )
      storageError = true;
  }
  function changeDifficulty(value: Band) {
    difficulty = value;
    if ((difficulty === 'MEDIUM' || difficulty === 'CHALLENGING') && size === 'MINI')
      size = 'SMALL';
    if ((difficulty === 'MEDIUM' || difficulty === 'CHALLENGING') && nodeCount < 14) nodeCount = 16;
    nodeCount = clampVertices(nodeCount);
    persist();
  }
  async function generate(replayDemo = false) {
    if (busy || referenceMode) return;
    hintSession.cancel();
    nodeCount = clampVertices(nodeCount);
    panel = null;
    canceled = false;
    error = null;
    hint = null;
    debugHighlight = [];
    await generationSession.run((signal, progress) =>
      demoMode && replayDemo
        ? api.generate({ seed: null, size, difficulty }, signal)
        : findPuzzle(seed, nodeCount, difficulty, signal, progress, size),
    );
  }
  function cancelGeneration() {
    generationSession.cancel();
    canceled = true;
  }
  function dispatch(action: Action) {
    if (!game || busy || inspection) return;
    game = reduce(game, action);
    canceled = false;
    if (action.type !== 'select') {
      hint = null;
      debugHighlight = [];
    }
    if (action.type === 'select') {
      clearTimeout(selectionSave);
      selectionSave = setTimeout(flushSelectionSave, 150);
    } else persist();
  }
  function selectNode(node: number) {
    dispatch({ type: 'select', node });
  }
  function reset() {
    if (entered >= 3) panel = 'reset';
    else {
      dispatch({ type: 'reset' });
      panel = null;
    }
  }
  async function requestHint(level: HintLevel) {
    if (!game || hintBusy || busy || inspection) return;
    if (hint?.deduction && level !== 'NUDGE') {
      hint = { ...hint, level };
      walkthroughIndex = null;
      return;
    }
    walkthroughIndex = null;
    error = null;
    await hintSession.run(game, level);
  }
  function validateSeed(event: Event) {
    const input = event.currentTarget as HTMLInputElement;
    let valid = !input.value.trim();
    if (/^-?\d+$/.test(input.value.trim())) {
      const value = BigInt(input.value.trim());
      valid = value >= -(2n ** 63n) && value < 2n ** 63n;
    }
    input.setCustomValidity(valid ? '' : $t('error.seed'));
  }
  function keyboard(event: KeyboardEvent) {
    if (
      !game ||
      inspection ||
      busy ||
      panel ||
      event.altKey ||
      (event.target instanceof HTMLElement &&
        (['INPUT', 'SELECT', 'TEXTAREA'].includes(event.target.tagName) ||
          event.target.isContentEditable))
    )
      return;
    const key = event.key.toLowerCase();
    if ((event.ctrlKey || event.metaKey) && key === 'z') {
      event.preventDefault();
      dispatch({ type: event.shiftKey ? 'redo' : 'undo' });
    } else if (event.ctrlKey || event.metaKey) return;
    else if (['1', '2', '3'].includes(key)) {
      event.preventDefault();
      dispatch({ type: 'color', color: (['RED', 'GREEN', 'BLUE'] as Color[])[Number(key) - 1] });
    } else if (['q', 'w', 'e'].includes(key)) {
      event.preventDefault();
      if (!event.repeat)
        dispatch({
          type: 'note',
          color: (['RED', 'GREEN', 'BLUE'] as Color[])[['q', 'w', 'e'].indexOf(key)],
        });
    } else if (key === 'backspace' || key === 'delete') {
      event.preventDefault();
      dispatch({ type: 'color', color: null });
    } else if (key.startsWith('arrow')) {
      event.preventDefault();
      const node = board?.navigate(key as Direction) ?? game.selected;
      dispatch({ type: 'select', node });
      void tick().then(() =>
        document
          .querySelector<SVGElement>(`[data-testid="node-${node}"]`)
          ?.focus({ preventScroll: true }),
      );
    } else if (key === 'h') {
      event.preventDefault();
      void requestHint('NUDGE');
    }
  }
</script>

<svelte:window onkeydown={keyboard} />
<div class="shell">
  <GameHeader onhelp={() => (panel = 'help')} onstorageerror={() => (storageError = true)} />
  <main class="game-main category-mode">
    {#if referenceMode}
      <section class="reference-picker">
        <h1>{$t('reference.title')}</h1>
        <p>{$t('reference.intro')}</p>
        <label
          >{$t('reference.choose')}<select
            value={referenceId}
            disabled={busy}
            onchange={(e) => chooseReference(e.currentTarget.value as ReferenceId)}
            >{#each references as r}<option value={r.id}>{$t(`reference.${r.id}`)}</option
              >{/each}</select
          ></label
        >
        <p>{$t(`reference.${referenceId}Description`)}</p>
        <a href="/">{$t('reference.generator')}</a>
      </section>
    {/if}
    <div class="game-meta">
      <div class="puzzle-heading">
        <h1>
          {bandName(
            $t,
            generated?.playDifficulty?.category ??
              generated?.proof?.band ??
              generated?.difficulty.band ??
              difficulty,
          )}
        </h1>
        <span
          class="progress-count"
          aria-label={$t('game.progress', { colored, total: game?.puzzle.nodeCount ?? nodeCount })}
          >{colored}<span> / {game?.puzzle.nodeCount ?? nodeCount}</span></span
        >
      </div>
      {#if busy}<button class="secondary new-puzzle" onclick={cancelGeneration}
          ><Icon name="close" />{$t('game.cancelSearch')}</button
        >
      {:else if !referenceMode}<button
          class:primary={complete}
          class="secondary new-puzzle"
          onclick={() => (panel = 'settings')}
          ><Icon name="plus" />{$t(complete ? 'game.another' : 'game.new')}</button
        >{/if}
    </div>
    <div class="play-layout" class:solved={complete}>
      <section class="board-region" aria-label={$t('game.play')} aria-busy={busy}>
        {#if game}{#key game.puzzle.logicalHash}<PuzzleBoard
              bind:this={board}
              game={displayGame!}
              {highlighted}
              hintEdges={inspection || debugHighlight.length ? [] : proofFocus.edges}
              hintTargets={inspection || debugHighlight.length ? [] : proofFocus.targets}
              assumptionVertex={proofFocus.assumption}
              contradictionVertex={proofFocus.contradiction}
              currentStepVertices={proofFocus.current}
              complete={complete && !inspection}
              onselect={selectNode}
            />{/key}
        {:else}<div class="board-stage empty-board">
            <span class="loading-mark" class:loading={busy} aria-hidden="true"
              ><i></i><i></i><i></i></span
            >
          </div>{/if}
        <div class="board-status" class:has-conflict={conflicts > 0} class:is-solved={complete}>
          <span
            class="status-message"
            role="status"
            aria-live="polite"
            aria-atomic="true"
            data-testid="game-status"
            >{#if complete && !busy && !canceled}<Icon
                name="check"
              />{/if}{#if conflicts && !complete && !busy && !canceled}<span aria-hidden="true"
                >△</span
              >{/if}{status}</span
          >
          <div class="progress-track" aria-hidden="true">
            <span style={`width: ${game ? (colored / game.puzzle.nodeCount) * 100 : 0}%`}></span>
          </div>
        </div>
      </section>
      <aside class="control-surface" aria-label={$t('game.controls')}>
        {#if referenceMode && generated}{#key generated.puzzle.logicalHash}<ReferenceInspector
              {generated}
              onframe={(frame) => {
                inspection = frame;
                if (frame) hint = null;
              }}
            />{/key}{/if}
        {#if !inspection}
          {#if hint}<HintPanel
              {hint}
              busy={hintBusy}
              selected={game?.selected ?? 0}
              onstep={(index) => (walkthroughIndex = index)}
              onrequest={requestHint}
              onclose={() => {
                hint = null;
                document.querySelector<HTMLButtonElement>('.hint-button')?.focus();
              }}
            />{/if}
          <div class="selection-heading">
            <h2>{game ? $t('vertex.name', { n: game.selected + 1 }) : $t('game.select')}</h2>
            {#if game?.puzzle.givens[game.selected]}<span class="given-label"
                ><Icon name="check" />{$t('vertex.fixed')}</span
              >{:else}<button
                class="icon-button clear-button"
                aria-label={$t('game.clear')}
                title={$t('game.clear')}
                disabled={!game ||
                  busy ||
                  (!game.colors[game.selected] && !game.notes[game.selected])}
                onclick={() => dispatch({ type: 'color', color: null })}
                ><Icon name="clear" /></button
              >{/if}
          </div>
          <button
            class="pencil-toggle"
            aria-pressed={pencil}
            disabled={!game || busy}
            onclick={() => (pencil = !pencil)}>{$t('game.pencilMode')}</button
          >
          <ColorControls
            selected={game?.colors[game.selected]}
            {pencil}
            notes={game ? (game.notes[game.selected] ?? 0) : 0}
            disabled={!game || busy || !!game.puzzle.givens[game.selected]}
            oncolor={(color) => dispatch({ type: pencil ? 'note' : 'color', color })}
          />
          <div class="game-actions">
            <button
              class="action-button"
              disabled={!game?.past.length || busy}
              onclick={() => dispatch({ type: 'undo' })}
              ><Icon name="undo" /><span>{$t('game.undo')}</span></button
            >
            <button
              class="action-button hint-button"
              disabled={!game || busy || hintBusy}
              aria-busy={hintBusy}
              onclick={() => requestHint('NUDGE')}
              ><Icon name="hint" /><span>{$t(hintBusy ? 'status.thinking' : 'game.hint')}</span
              ></button
            >
            <button class="action-button" aria-haspopup="dialog" onclick={() => (panel = 'more')}
              ><Icon name="more" /><span>{$t('game.more')}</span></button
            >
          </div>
          <button
            class="desktop-redo text-button"
            disabled={!game?.future.length || busy}
            onclick={() => dispatch({ type: 'redo' })}><Icon name="redo" />{$t('game.redo')}</button
          >
        {/if}
      </aside>
    </div>
    {#if error || storageError}<div class="notices">
        {#if error}<p role="alert" class="error-text">{errorMessage(error, $t)}</p>
          {#if demoMode && error instanceof api.ApiError && error.code === 'DEMO_EXHAUSTED'}
            <button class="secondary" onclick={() => generate(true)}>{$t('demo.replay')}</button>
          {/if}
        {/if}
        {#if storageError}<p role="status">{$t('error.storage')}</p>{/if}
      </div>{/if}
    {#if generated && !referenceMode}{#key generated.puzzle.logicalHash}<DebugPanel
          {generated}
          onframe={(frame) => {
            inspection = frame;
            if (frame) {
              hint = null;
              debugHighlight = [];
            }
          }}
        />{/key}{/if}
  </main>
</div>

<Dialog open={panel === 'settings'} title={$t('game.new')} onclose={() => (panel = null)}>
  <form
    class="settings-form"
    onsubmit={(event) => {
      event.preventDefault();
      void generate();
    }}
  >
    <div class="settings-fields">
      <label
        >{$t('settings.difficulty')}<select
          value={difficulty}
          onchange={(event) => changeDifficulty(event.currentTarget.value as Band)}
          >{#each bands as band}<option value={band}>{bandName($t, band)}</option>{/each}</select
        ></label
      >
      <label
        >{$t('settings.size')}<select bind:value={size} onchange={persist}>
          {#each sizeCategories as option}<option
              value={option}
              disabled={option === 'MINI' &&
                (difficulty === 'MEDIUM' || difficulty === 'CHALLENGING')}
              >{$t(`size.${option}`)} ({sizeRanges[option][0]}–{sizeRanges[option][1]})</option
            >{/each}
        </select></label
      >
    </div>
    <details class="seed-options">
      <summary>{$t('settings.seed')}<span>{$t('settings.optional')}</span></summary><label
        class="sr-only"
        for="generation-seed">{$t('settings.seed')}</label
      ><input
        id="generation-seed"
        type="text"
        maxlength="20"
        inputmode="text"
        placeholder={$t('settings.random')}
        bind:value={seed}
        oninput={validateSeed}
      />
    </details>
    <div class="dialog-actions">
      <button class="primary" type="submit"><Icon name="plus" />{$t('game.generate')}</button>
    </div>
  </form>
</Dialog>
<Dialog open={panel === 'help'} title={$t('help.title')} onclose={() => (panel = null)}>
  <ol class="rules">
    <li>{$t('help.rule1')}</li>
    <li>{$t('help.rule2')}</li>
    <li>{$t('help.rule3')}</li>
  </ol>
  <details class="keyboard-help">
    <summary>{$t('help.keyboard')}</summary>
    <dl>
      <div>
        <dt><kbd>1 / 2 / 3</kbd></dt>
        <dd>{$t('help.colors')}</dd>
      </div>
      <div>
        <dt><kbd>Q / W / E</kbd></dt>
        <dd>{$t('help.notes')}</dd>
      </div>
      <div>
        <dt><kbd>{$t('help.arrows')}</kbd></dt>
        <dd>{$t('help.navigate')}</dd>
      </div>
      <div>
        <dt><kbd>{$t('help.delete')}</kbd></dt>
        <dd>{$t('game.clear')}</dd>
      </div>
      <div>
        <dt><kbd>{$t('help.undoKeys')}</kbd></dt>
        <dd>{$t('game.undo')}</dd>
      </div>
      <div>
        <dt><kbd>{$t('help.redoKeys')}</kbd></dt>
        <dd>{$t('game.redo')}</dd>
      </div>
      <div>
        <dt><kbd>H</kbd></dt>
        <dd>{$t('game.hint')}</dd>
      </div>
    </dl>
  </details>
</Dialog>
<Dialog open={panel === 'more'} title={$t('game.more')} onclose={() => (panel = null)} compact>
  <div class="more-actions">
    <button
      disabled={!game?.future.length || busy}
      onclick={() => {
        dispatch({ type: 'redo' });
        panel = null;
      }}><Icon name="redo" />{$t('game.redo')}</button
    >
    <button
      disabled={!game ||
        busy ||
        !!game.puzzle.givens[game.selected] ||
        (!game.colors[game.selected] && !game.notes[game.selected])}
      onclick={() => {
        dispatch({ type: 'color', color: null });
        panel = null;
      }}><Icon name="clear" />{$t('game.clear')}</button
    >
    <button disabled={busy || referenceMode} onclick={() => (panel = 'settings')}
      ><Icon name="plus" />{$t('game.new')}</button
    >
    <button class="reset-action" disabled={!game || busy || !entered} onclick={reset}
      >{$t('game.reset')}</button
    >
  </div>
</Dialog>
<Dialog open={panel === 'reset'} title={$t('reset.title')} onclose={() => (panel = null)} compact>
  <p>{$t('reset.description', { count: entered })}</p>
  <div class="dialog-actions">
    <button class="secondary" onclick={() => (panel = null)}>{$t('game.cancel')}</button><button
      class="primary"
      onclick={() => {
        dispatch({ type: 'reset' });
        panel = null;
      }}>{$t('game.reset')}</button
    >
  </div>
</Dialog>
