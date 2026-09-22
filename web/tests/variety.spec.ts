import { test, expect } from '@playwright/test';

test('ordinary new puzzles avoid previously seen graphs across reloads', async ({ page }) => {
  const errors: string[] = [];
  page.on('pageerror', (e) => errors.push(e.message));
  await page.goto('/?generator=deletion');
  await expect(page.getByTestId('node-0')).toBeVisible();
  const seen = new Set<string>();
  for (let i = 0; i < 8; i++) {
    await page.getByRole('button', { name: 'New puzzle', exact: true }).click();
    await page.getByRole('combobox', { name: 'Difficulty' }).selectOption('MEDIUM');
    await page.getByRole('combobox', { name: 'Size', exact: true }).selectOption('MEDIUM');
    const response = page.waitForResponse((r) => r.url().endsWith('/puzzles/generate') && r.ok());
    await page.getByRole('button', { name: 'Generate', exact: true }).click();
    const data = await (await response).json();
    expect(data.proof.band).toBe('MEDIUM');
    expect(seen.has(data.topologyKey)).toBeFalsy();
    seen.add(data.topologyKey);
    await expect(page.getByRole('heading', { name: 'Medium', exact: true })).toBeVisible();
    if (i === 3) {
      await page.reload();
      await expect(page.getByTestId('node-0')).toBeVisible();
    }
  }
  const stored = await page.evaluate(() =>
    JSON.parse(localStorage.getItem('3color-seen-topologies-v1')!),
  );
  expect(
    Object.entries(stored)
      .filter(([n]) => Number(n) >= 24 && Number(n) <= 33)
      .flatMap(([, keys]) => keys as string[]),
  ).toHaveLength(8);
  await page.getByRole('button', { name: 'Hint', exact: true }).click();
  await expect(page.locator('.hint-copy')).toBeVisible();
  await page.setViewportSize({ width: 390, height: 844 });
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
  ).toBeTruthy();
  expect(errors).toEqual([]);
});

test('explicit seed replay ignores stored graph history', async ({ page }) => {
  await page.goto('/?generator=deletion');
  await expect(page.getByTestId('node-0')).toBeVisible();
  const results: string[] = [];
  for (let replay = 0; replay < 2; replay++) {
    await page.getByRole('button', { name: 'New puzzle', exact: true }).click();
    await page.getByRole('combobox', { name: 'Difficulty' }).selectOption('EASY');
    await page.getByRole('combobox', { name: 'Size', exact: true }).selectOption('SMALL');
    if ((await page.locator('.seed-options').getAttribute('open')) === null)
      await page.locator('.seed-options summary').click();
    await page.getByLabel('Seed', { exact: true }).fill('4000');
    const response = page.waitForResponse((r) => r.url().endsWith('/puzzles/generate'));
    await page.getByRole('button', { name: 'Generate', exact: true }).click();
    const returned = await response;
    expect(returned.ok()).toBeTruthy();
    expect(returned.request().postDataJSON().excludeTopologies).toBeUndefined();
    const data = await returned.json();
    results.push(data.topologyKey);
    await page.evaluate(
      ({ n, key }) => {
        localStorage.setItem('3color-seen-topologies-v1', JSON.stringify({ [n]: [key] }));
      },
      { n: data.puzzle.nodeCount, key: data.topologyKey },
    );
    await page.reload();
    await expect(page.getByTestId('node-0')).toBeVisible();
  }
  expect(results[0]).toBe(results[1]);
});
