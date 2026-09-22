import { test, expect } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { newGame } from '../src/game/model';
import { checkCorrection } from './support/correction';

test('backend hints explain wrong colours before forward moves and preserve the board', async ({
  page,
}) => {
  const { generated } = JSON.parse(readFileSync('src/demo/fixtures/easy.json', 'utf8'));
  await page.addInitScript(
    (state) => localStorage.setItem('3color-progress-v2', JSON.stringify(state)),
    {
      version: 2,
      game: newGame(generated.puzzle),
      generated,
      preferences: { difficulty: 'EASY', size: 'MINI', nodeCount: 6, seed: '' },
    },
  );
  await page.goto('/?generator=deletion');
  await checkCorrection(page, '3color-progress-v2');
});

for (const changeColor of [false, true]) {
  test(`pending hint ${changeColor ? 'rejects changed colours' : 'survives selection changes'}`, async ({
    page,
  }) => {
    await page.goto('/');
    await expect(page.getByTestId('node-0')).toBeVisible();
    let release!: () => void;
    const gate = new Promise<void>((resolve) => {
      release = resolve;
    });
    let arrived!: () => void;
    const waiting = new Promise<void>((resolve) => {
      arrived = resolve;
    });
    await page.route('**/api/v1/hints', async (route) => {
      const response = await route.fetch();
      arrived();
      await gate;
      await route.fulfill({ response });
    });
    await page.getByRole('button', { name: 'Hint', exact: true }).click();
    await waiting;
    await page.locator('.node:not(.given)').last().click();
    if (changeColor) await page.keyboard.press('1');
    const response = page.waitForResponse((r) => r.url().endsWith('/hints'));
    release();
    await response;
    await expect(page.getByRole('button', { name: 'Hint', exact: true })).toBeEnabled();
    await expect(page.locator('.hint-panel')).toHaveCount(changeColor ? 0 : 1);
  });
}

test('selection saves are coalesced and flushed before leaving', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByTestId('node-0')).toBeVisible();
  await page.clock.install();
  await page.clock.pauseAt(new Date());
  await page.evaluate(() => {
    const original = Storage.prototype.setItem;
    (window as any).progressWrites = 0;
    Storage.prototype.setItem = function (key, value) {
      if (key === '3color-progress-v2') (window as any).progressWrites++;
      return original.call(this, key, value);
    };
  });
  await page.getByTestId('node-1').click();
  await page.getByTestId('node-2').click();
  await expect.poll(() => page.evaluate(() => (window as any).progressWrites)).toBe(0);
  await page.clock.runFor(151);
  expect(await page.evaluate(() => (window as any).progressWrites)).toBe(1);
  await page.getByTestId('node-3').click();
  await page.evaluate(() => window.dispatchEvent(new Event('pagehide')));
  expect(
    await page.evaluate(
      () => JSON.parse(localStorage.getItem('3color-progress-v2')!).game.selected,
    ),
  ).toBe(3);
  expect(await page.evaluate(() => (window as any).progressWrites)).toBe(2);
});
