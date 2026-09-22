import { test, expect } from '@playwright/test';

test('reference replay preserves play progress across switching and reload', async ({ page }) => {
  const errors: string[] = [];
  page.on('pageerror', (e) => errors.push(e.message));
  await page.goto('/?workshop=references');
  await expect(page.locator('.node')).toHaveCount(33);
  const node = page.locator('.node:not(.given)').first();
  const id = await node.getAttribute('data-testid');
  await node.click();
  await page.keyboard.press('q');
  await expect(node).toHaveAttribute('data-notes', '1');
  await page.getByRole('button', { name: 'Reveal solving sequence' }).click();
  await expect(page.getByTestId('reference-trace-status')).toHaveText('Trace solved');
  await page.getByRole('slider', { name: 'Replay position' }).focus();
  await page.keyboard.press('End');
  await expect(
    page.locator('.node[data-color="RED"], .node[data-color="GREEN"], .node[data-color="BLUE"]'),
  ).toHaveCount(33);
  await page.getByRole('button', { name: 'Return to my game' }).click();
  await expect(page.getByTestId(id!)).toHaveAttribute('data-notes', '1');
  await page.getByRole('combobox', { name: 'Reference puzzle' }).selectOption('shortcut');
  await expect(page.getByRole('heading', { name: 'Medium', exact: true })).toBeVisible();
  await page.getByRole('combobox', { name: 'Reference puzzle' }).selectOption('larger');
  await expect(page.locator('.node')).toHaveCount(47);
  await page.setViewportSize({ width: 390, height: 844 });
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
  ).toBeTruthy();
  await page.getByRole('combobox', { name: 'Reference puzzle' }).selectOption('dependency');
  await expect(page.getByTestId(id!)).toHaveAttribute('data-notes', '1');
  await page.reload();
  await expect(page.getByTestId(id!)).toHaveAttribute('data-notes', '1');
  expect(errors).toEqual([]);
});
