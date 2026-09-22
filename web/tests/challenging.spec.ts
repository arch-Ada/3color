import { test, expect } from '@playwright/test';

test('Challenging uses filtered Medium proofs, size ranges and working hints', async ({ page }) => {
  const errors: string[] = [];
  page.on('pageerror', (error) => errors.push(error.message));
  await page.goto('/');
  await expect(page.getByTestId('node-0')).toBeVisible();
  await page.getByRole('button', { name: 'New puzzle', exact: true }).click();
  await expect(page.getByLabel('Exact vertex count (advanced)')).toHaveCount(0);
  await expect(page.getByLabel('Vertices', { exact: true })).toHaveCount(0);
  await page.getByRole('combobox', { name: 'Difficulty' }).selectOption('VERY_EASY');
  await page.getByRole('combobox', { name: 'Size', exact: true }).selectOption('MINI');
  await page.getByRole('combobox', { name: 'Difficulty' }).selectOption('MEDIUM');
  await expect(page.getByRole('combobox', { name: 'Size', exact: true })).toHaveValue('SMALL');
  await expect(page.locator('option[value="MINI"]')).toHaveJSProperty('disabled', true);
  await page.getByRole('combobox', { name: 'Difficulty' }).selectOption('CHALLENGING');
  await expect(page.locator('option[value="MINI"]')).toHaveJSProperty('disabled', true);
  await expect(
    page.getByRole('combobox', { name: 'Difficulty' }).locator('option[value=HARD]'),
  ).toHaveCount(0);
  await page.getByRole('combobox', { name: 'Size', exact: true }).selectOption('MEDIUM');
  await page.locator('.seed-options summary').click();
  await page.getByLabel('Seed', { exact: true }).fill('0');
  const response = page.waitForResponse((r) => r.url().endsWith('/puzzles/generate'));
  await page.getByRole('button', { name: 'Generate', exact: true }).click();
  const result = await response;
  expect(result.ok()).toBeTruthy();
  const data = await result.json();
  expect(data.proof.band).toBe('MEDIUM');
  expect(data.playDifficulty.category).toBe('CHALLENGING');
  expect(data.playDifficulty.maximumContradictionSteps).toBeLessThanOrEqual(12);
  expect(data.proof.p2.refutationRounds).toBe(1);
  expect(data.search.outcomes.CERTIFIED_BANK).toBe(1);
  await expect(page.getByRole('heading', { name: 'Challenging', exact: true })).toBeVisible();
  expect(data.puzzle.nodeCount).toBeGreaterThanOrEqual(24);
  expect(data.puzzle.nodeCount).toBeLessThanOrEqual(33);
  await expect(page.locator('.node')).toHaveCount(data.puzzle.nodeCount);
  await page.getByRole('button', { name: 'Hint', exact: true }).click();
  await expect(page.locator('.hint-copy')).toBeVisible();
  await page.setViewportSize({ width: 390, height: 844 });
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
  ).toBeTruthy();
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Challenging', exact: true })).toBeVisible();
  expect(errors).toEqual([]);
});

test('default game preserves its saved board and offers current categories', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByTestId('node-0')).toBeVisible();
  await page.locator('.node:not(.given)').first().click();
  await page.keyboard.press('q');
  const before = await page.evaluate(
    () => JSON.parse(localStorage.getItem('3color-progress-v2')!).game,
  );
  await page.goto('/');
  await expect(page.getByTestId('node-0')).toBeVisible();
  const after = await page.evaluate(
    () => JSON.parse(localStorage.getItem('3color-progress-v2')!).game,
  );
  expect(after).toEqual(before);
  await page.getByRole('button', { name: 'New puzzle', exact: true }).click();
  await expect(page.getByRole('combobox', { name: 'Difficulty' }).locator('option')).toHaveCount(4);
  await expect(page.getByRole('combobox', { name: 'Size', exact: true })).toBeVisible();
  await expect(page.getByLabel('Vertices', { exact: true })).toHaveCount(0);
});
