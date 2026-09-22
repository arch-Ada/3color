import { test, expect } from '@playwright/test';

test('deletion generator plays Easy, preserves size, and cancels repeated Challenging requests', async ({
  page,
}, testInfo) => {
  const errors: string[] = [];
  page.on('pageerror', (error) => errors.push(error.message));
  let requests = 0;
  await page.route('**/puzzles/generate', async (route) => {
    requests++;
    const body = route.request().postDataJSON();
    if (body.difficulty === 'CHALLENGING') {
      await route.fulfill({
        status: 422,
        contentType: 'application/json',
        body: JSON.stringify({ code: 'DELETION_NO_MATCH' }),
      });
      return;
    }
    await route.continue({ postData: JSON.stringify({ ...body, seed: body.seed ?? '0' }) });
  });
  await page.goto('/?generator=deletion');
  await expect(page.locator('.prototype-notice')).toHaveCount(0);
  await expect(page.getByTestId('node-0')).toBeVisible();
  await page.getByRole('button', { name: 'New puzzle', exact: true }).click();
  const difficulty = page.getByRole('combobox', { name: 'Difficulty' });
  await difficulty.selectOption('EASY');
  await page.getByRole('combobox', { name: 'Size', exact: true }).selectOption('SMALL');
  const response = page.waitForResponse((r) => r.url().endsWith('/puzzles/generate'));
  await page.getByRole('button', { name: 'Generate', exact: true }).click();
  const generated = await response;
  expect(generated.ok()).toBeTruthy();
  const data = await generated.json();
  expect(data.puzzle.nodeCount).toBeGreaterThanOrEqual(14);
  expect(data.puzzle.nodeCount).toBeLessThanOrEqual(23);
  await expect(page.getByRole('heading', { name: 'Easy', exact: true })).toBeVisible();
  await expect(page.locator('.node')).toHaveCount(data.puzzle.nodeCount);
  await page.getByRole('button', { name: 'Hint', exact: true }).click();
  await expect(page.locator('.hint-copy')).toBeVisible();
  for (const [width, height] of [
    [1440, 1000],
    [390, 844],
  ]) {
    await page.setViewportSize({ width, height });
    const ratio = await page.locator('.puzzle-board').evaluate((svg) => {
      const discs = [...svg.querySelectorAll('.disc')].map((e) => e.getBoundingClientRect());
      let minimum = Infinity;
      for (let i = 0; i < discs.length; i++)
        for (let j = 0; j < i; j++) {
          const a = discs[i],
            b = discs[j];
          minimum = Math.min(
            minimum,
            Math.hypot(
              a.x + a.width / 2 - b.x - b.width / 2,
              a.y + a.height / 2 - b.y - b.height / 2,
            ) /
              ((a.width + b.width) / 2),
          );
        }
      return minimum;
    });
    expect(ratio).toBeGreaterThan(1);
    await page.screenshot({ path: testInfo.outputPath(`medium-${width}.png`) });
  }
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Easy', exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'New puzzle', exact: true }).click();
  await difficulty.selectOption('CHALLENGING');
  await expect(page.getByRole('combobox', { name: 'Size', exact: true })).toHaveValue('SMALL');
  const before = requests;
  await page.getByRole('button', { name: 'Generate', exact: true }).click();
  await expect.poll(() => requests).toBeGreaterThan(before + 1);
  await page.getByRole('button', { name: 'Cancel search', exact: true }).click();
  const stopped = requests;
  await page.waitForTimeout(2200);
  expect(requests).toBe(stopped);
  await expect(page.getByText('Search canceled', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'New puzzle', exact: true })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Easy', exact: true })).toBeVisible();
  expect(errors).toEqual([]);
});

test('four player categories expose a certified Medium puzzle with working hints', async ({
  page,
}) => {
  await page.goto('/?generator=deletion');
  await expect(page.getByTestId('node-0')).toBeVisible();
  await page.getByRole('button', { name: 'New puzzle', exact: true }).click();
  const difficulty = page.getByRole('combobox', { name: 'Difficulty' });
  await expect(difficulty.locator('option')).toHaveCount(4);
  expect(
    await difficulty
      .locator('option')
      .evaluateAll((options) => options.map((o) => (o as HTMLOptionElement).value)),
  ).toEqual(['VERY_EASY', 'EASY', 'MEDIUM', 'CHALLENGING']);
  await difficulty.selectOption('MEDIUM');
  await page.locator('.seed-options summary').click();
  await page.getByLabel('Seed', { exact: true }).fill('3');
  const response = page.waitForResponse((r) => r.url().endsWith('/puzzles/generate'));
  await page.getByRole('button', { name: 'Generate', exact: true }).click();
  const body = await (await response).json();
  expect(body.generation.masterSeed).toBe('3');
  expect(body.proof.modelVersion).toBe('proof-level-v2');
  expect(body.proof.p1.status).toBe('STALLED');
  expect(body.proof.p2.refutationRounds).toBe(1);
  expect(body.search.outcomes.CERTIFIED_BANK).toBe(1);
  await expect(page.getByRole('heading', { name: 'Medium', exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Hint', exact: true }).click();
  await expect(page.locator('.hint-copy')).toBeVisible();
});
