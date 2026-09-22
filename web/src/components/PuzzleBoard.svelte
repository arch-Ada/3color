<script lang="ts">
  import { tick } from 'svelte';
  import { spatialNeighbor, type Direction } from '../game/navigation';
  import type { Game } from '../game/model';
  import { invalidEdges } from '../game/validation';
  import { t } from '../i18n';
  import PuzzleNode from './PuzzleNode.svelte';
  import Icon from './Icon.svelte';
  let {
    game,
    highlighted = [],
    hintEdges = [],
    hintTargets = [],
    assumptionVertex = null,
    contradictionVertex = null,
    currentStepVertices = [],
    complete = false,
    onselect,
  }: {
    game: Game;
    highlighted?: number[];
    hintEdges?: { a: number; b: number }[];
    hintTargets?: number[];
    assumptionVertex?: number | null;
    contradictionVertex?: number | null;
    currentStepVertices?: number[];
    complete?: boolean;
    onselect: (node: number) => void;
  } = $props();
  let width = $state(660),
    height = $state(490),
    zoom = $state(1);
  let viewport: HTMLDivElement;
  let violations = $derived(invalidEdges(game.puzzle, game.colors));
  let neighbors = $derived(
    game.puzzle.edges.flatMap((e) =>
      e.a === game.selected ? [e.b] : e.b === game.selected ? [e.a] : [],
    ),
  );
  // Rotate the whole layout on portrait boards; preserve geometry and node IDs.
  let portrait = $derived(height > width);
  let points = $derived(
    game.puzzle.layout.map((p) => {
      const x = p.x * 570,
        y = p.y * 405;
      return portrait ? { x: y, y: 570 - x } : { x, y };
    }),
  );
  export function navigate(direction: Direction): number {
    return spatialNeighbor(points, game.selected, direction);
  }
  let bounds = $derived({
    left: Math.min(...points.map((p) => p.x)) - 60,
    top: Math.min(...points.map((p) => p.y)) - 60,
    width: Math.max(...points.map((p) => p.x)) - Math.min(...points.map((p) => p.x)) + 120,
    height: Math.max(...points.map((p) => p.y)) - Math.min(...points.map((p) => p.y)) + 120,
  });
  let scale = $derived(
    Math.max(0.1, Math.min(width / bounds.width, height / bounds.height) * zoom),
  );
  let radius = $derived(
    Math.max(game.puzzle.nodeCount > 36 ? 14 : game.puzzle.nodeCount > 20 ? 18 : 22, 13 / scale),
  );
  let hitRadius = $derived(Math.max(radius + 9, 22 / scale));
  let labelSize = $derived(Math.max(11, 9 / scale));
  const x = (v: number) => points[v].x;
  const y = (v: number) => points[v].y;
  $effect(() => {
    const selected = game.selected;
    if (zoom > 1)
      void tick().then(() => {
        const target = viewport?.querySelector(`[data-testid="node-${selected}"]`);
        if (!target) return;
        const node = target.getBoundingClientRect(),
          frame = viewport.getBoundingClientRect();
        if (node.left < frame.left) viewport.scrollLeft += node.left - frame.left;
        else if (node.right > frame.right) viewport.scrollLeft += node.right - frame.right;
        if (node.top < frame.top) viewport.scrollTop += node.top - frame.top;
        else if (node.bottom > frame.bottom) viewport.scrollTop += node.bottom - frame.bottom;
      });
  });
</script>

<div class="board-stage" class:complete>
  <div
    class="board-viewport"
    bind:this={viewport}
    bind:clientWidth={width}
    bind:clientHeight={height}
  >
    <svg
      class="puzzle-board"
      class:dense={game.puzzle.nodeCount > 36}
      viewBox={`${bounds.left} ${bounds.top} ${bounds.width} ${bounds.height}`}
      style={`width: ${zoom * 100}%; height: ${zoom * 100}%`}
      aria-label={$t('board.name')}
    >
      <title>{$t('board.name')}</title><desc>{$t('board.instructions')}</desc>
      {#each game.puzzle.edges as e}<line
          x1={x(e.a)}
          y1={y(e.a)}
          x2={x(e.b)}
          y2={y(e.b)}
          vector-effect="non-scaling-stroke"
          class:edge-invalid={violations.includes(e)}
          class:edge-selected={e.a === game.selected || e.b === game.selected}
          data-edge={`${e.a}-${e.b}`}
          class:edge-hint={hintEdges.some(
            (h) => (h.a === e.a && h.b === e.b) || (h.a === e.b && h.b === e.a),
          )}
        />{/each}
      {#each game.puzzle.layout as _, node}<PuzzleNode
          {node}
          x={x(node)}
          y={y(node)}
          {radius}
          {hitRadius}
          {labelSize}
          color={game.colors[node]}
          notes={game.notes[node] ?? 0}
          given={!!game.puzzle.givens[node]}
          selected={game.selected === node}
          neighbor={neighbors.includes(node)}
          highlighted={highlighted.includes(node)}
          hintTarget={hintTargets.includes(node)}
          hintAssumption={assumptionVertex === node}
          hintContradiction={contradictionVertex === node}
          hintCurrent={currentStepVertices.includes(node)}
          invalid={violations.some((e) => e.a === node || e.b === node)}
          {onselect}
        />{/each}
    </svg>
  </div>
  {#if game.puzzle.nodeCount > 24 || zoom > 1}
    <div class="board-zoom" role="group" aria-label={$t('board.zoom')}>
      <button
        class="icon-button"
        disabled={zoom === 1}
        aria-label={$t('board.zoomOut')}
        onclick={() => (zoom = Math.max(1, zoom - 0.5))}><Icon name="minus" /></button
      >
      <button
        class="icon-button"
        disabled={zoom === 3}
        aria-label={$t('board.zoomIn')}
        onclick={() => (zoom = Math.min(3, zoom + 0.5))}><Icon name="plus" /></button
      >
    </div>
  {/if}
</div>
