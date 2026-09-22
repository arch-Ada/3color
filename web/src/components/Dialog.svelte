<script module lang="ts">
  let returnFocus: HTMLElement | null = null;
</script>

<script lang="ts">
  import type { Snippet } from 'svelte';
  import { t } from '../i18n';
  import Icon from './Icon.svelte';
  let {
    open,
    title,
    onclose,
    children,
    compact = false,
  }: {
    open: boolean;
    title: string;
    onclose: () => void;
    children: Snippet;
    compact?: boolean;
  } = $props();
  let dialog: HTMLDialogElement;
  const id = $props.id();
  $effect(() => {
    if (open && !dialog.open) {
      const active = document.activeElement;
      if (active instanceof HTMLElement && !active.closest('dialog')) returnFocus = active;
      dialog.showModal();
    } else if (!open && dialog.open) {
      dialog.close();
      queueMicrotask(() => {
        if (!document.querySelector('dialog[open]') && returnFocus?.isConnected)
          returnFocus.focus();
      });
    }
  });
</script>

<dialog
  bind:this={dialog}
  class:compact
  aria-labelledby={id}
  onkeydown={(event) => {
    if (event.key !== 'Tab') return;
    const items = [
      ...dialog.querySelectorAll<HTMLElement>(
        'button:not(:disabled), input:not(:disabled), select:not(:disabled), summary, a[href], [tabindex="0"]',
      ),
    ].filter((element) => element.getClientRects().length > 0);
    const first = items[0],
      last = items[items.length - 1];
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last?.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first?.focus();
    }
  }}
  oncancel={(event) => {
    event.preventDefault();
    onclose();
  }}
  onclose={() => {
    if (open) onclose();
  }}
  onpointerdown={(event) => {
    if (event.target !== dialog) return;
    const box = dialog.getBoundingClientRect();
    if (
      event.clientX < box.left ||
      event.clientX > box.right ||
      event.clientY < box.top ||
      event.clientY > box.bottom
    )
      onclose();
  }}
>
  <div class="dialog-heading">
    <h2 {id}>{title}</h2>
    <button class="icon-button" aria-label={$t('game.close')} onclick={onclose}
      ><Icon name="close" /></button
    >
  </div>
  <div class="dialog-body">{@render children()}</div>
</dialog>
