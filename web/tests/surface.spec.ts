import { test, expect } from '@playwright/test';

test('surface explains the actual category and keeps solution and diagnostics opt-in', async ({
  page,
  request,
}) => {
  const response = await request.post('http://127.0.0.1:18080/api/v1/puzzles/generate', {
    data: { seed: '42', size: 'SMALL', difficulty: 'CHALLENGING' },
  });
  expect(response.ok()).toBeTruthy();
  const generated = await response.json();
  await page.route('**/puzzles/generate', (route) => route.fulfill({ json: generated }));
  let releaseAnalysis!: () => void;
  const analysisGate = new Promise<void>((resolve) => {
    releaseAnalysis = resolve;
  });
  await page.route('**/analyze', async (route) => {
    await analysisGate;
    await route.continue();
  });
  let analyses = 0;
  page.on('request', (request) => {
    if (request.url().endsWith('/analyze')) analyses++;
  });
  await page.goto('/?generator=deletion');
  await expect(page.getByTestId('node-0')).toBeVisible();
  await page.getByText('Under the surface', { exact: true }).click();
  const panel = page.locator('.debug-panel');
  await expect(panel.getByText('Logical hash · SHA-256', { exact: true })).not.toBeVisible();
  await expect(panel.locator('.surface-section').first().locator('summary').first()).toHaveText(
    'Explore the reasoning',
  );
  await expect(panel.getByText('Score', { exact: true })).toHaveCount(0);
  await expect(panel.getByRole('button', { name: 'Inspect deduction trace' })).not.toBeVisible();
  expect(analyses).toBe(0);
  await panel.getByText('Explore the reasoning', { exact: true }).click();
  await expect(panel.getByRole('slider')).toBeVisible();
  await expect(panel.getByRole('slider')).toBeDisabled();
  releaseAnalysis();
  await expect(panel.getByText('Structured proof', { exact: true })).toHaveCount(0);
  await expect(panel.getByRole('button', { name: 'Inspect deduction trace' })).toHaveCount(0);
  await expect(panel.getByRole('slider')).toBeEnabled();
  await expect(panel.getByTestId('trace-status')).toHaveCount(0);
  await panel.getByRole('button', { name: 'Next step', exact: true }).click();
  await panel.getByText('Explore the reasoning', { exact: true }).click();
  await expect(panel.getByRole('slider')).not.toBeVisible();
  await panel.getByText('Explore the reasoning', { exact: true }).click();
  await expect(panel.getByRole('slider')).toBeVisible();
  expect(analyses).toBe(1);
  await panel.getByText('Technical details', { exact: true }).click();
  await expect(panel.getByText('Search diagnostics', { exact: true })).toHaveCount(0);
  await expect(panel.getByText('Logical hash · SHA-256', { exact: true })).toBeVisible();
  await expect(panel).toContainText('play-difficulty-v1');
  await expect(panel).toContainText('Challenging');
});

test('stored replay IDs and missing certification remain honest on a small screen', async ({
  page,
  request,
}) => {
  const response = await request.post('http://127.0.0.1:18080/api/v1/puzzles/generate', {
    data: { seed: '42', size: 'MINI', difficulty: 'VERY_EASY' },
  });
  expect(response.ok()).toBeTruthy();
  const generated = await response.json();
  // Controlled older metadata: the UI must not infer a certificate from a stored ID.
  generated.supplyId = 'a'.repeat(64);
  delete generated.playDifficulty;
  delete generated.proof;
  await page.route('**/puzzles/generate', (route) => route.fulfill({ json: generated }));
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/?generator=deletion');
  await expect(page.getByTestId('node-0')).toBeVisible();
  await page.getByText('Under the surface', { exact: true }).click();
  const panel = page.locator('.debug-panel');
  await expect(panel.getByText('Unique solution', { exact: true })).toHaveCount(0);
  await panel.getByText('Technical details', { exact: true }).click();
  await expect(panel.getByRole('link', { name: 'Open stored puzzle data (JSON)' })).toHaveAttribute(
    'href',
    `/api/v1/puzzles/supply/${generated.supplyId}`,
  );
  await expect(panel.getByText('Original rating unavailable', { exact: true })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
});

test('reasoning slider colours the backend board and restores play state and zoom', async ({
  page,
  request,
}) => {
  const response = await request.post('http://127.0.0.1:18080/api/v1/puzzles/generate', {
    data: { seed: '42', size: 'LARGE', difficulty: 'EASY' },
  });
  expect(response.ok()).toBeTruthy();
  const generated = await response.json();
  await page.route('**/puzzles/generate', (route) => route.fulfill({ json: generated }));
  await page.goto('/?generator=deletion');
  await expect(page.getByTestId('node-0')).toBeVisible();
  const node = Array.from({ length: generated.puzzle.nodeCount }, (_, n) => n).find(
    (n) => !generated.puzzle.givens[n],
  )!;
  await page.getByTestId(`node-${node}`).click();
  await page.keyboard.press('q');
  await page.keyboard.press('w');
  await page.getByRole('button', { name: 'Zoom in', exact: true }).click();
  const zoom = await page.locator('svg.puzzle-board').getAttribute('style');
  const saved = await page.evaluate(() => localStorage.getItem('3color-progress-v2'));
  const colours = await page
    .locator('[data-testid^="node-"]')
    .evaluateAll((nodes) => nodes.map((n) => n.getAttribute('data-color')));
  await page.getByText('Under the surface', { exact: true }).click();
  await page.getByText('Explore the reasoning', { exact: true }).click();
  const slider = page.locator('.debug-panel input[type=range]');
  await expect(slider).toBeEnabled();
  await slider.fill((await slider.getAttribute('max')) ?? '0');
  await expect(page.locator('[data-testid^="node-"][data-color=""]')).toHaveCount(0);
  await expect(page.locator('svg.puzzle-board')).toHaveAttribute('style', zoom!);
  expect(await page.evaluate(() => localStorage.getItem('3color-progress-v2'))).toBe(saved);
  await slider.fill('0');
  await expect(page.getByTestId(`node-${node}`)).toHaveAttribute('data-color', '');
  await page.getByText('Under the surface', { exact: true }).click();
  expect(
    await page
      .locator('[data-testid^="node-"]')
      .evaluateAll((nodes) => nodes.map((n) => n.getAttribute('data-color'))),
  ).toEqual(colours);
  await expect(page.getByTestId(`node-${node}`)).toHaveAttribute('data-notes', '3');
  await expect(page.locator('svg.puzzle-board')).toHaveAttribute('style', zoom!);
  await page.getByRole('button', { name: 'Undo', exact: false }).click();
  await expect(page.getByTestId(`node-${node}`)).toHaveAttribute('data-notes', '1');
});
