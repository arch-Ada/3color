import { test, expect } from '@playwright/test';
import type { Page } from '@playwright/test';
import type { Generated } from '../src/api/dto';
async function settings(page: Page, seed?: string) {
  await page.getByRole('button', { name: 'New puzzle', exact: true }).click();
  if (seed !== undefined) {
    await page.locator('.seed-options summary').click();
    await page.getByLabel('Seed', { exact: true }).fill(seed);
  }
}
async function generate(page: Page) {
  await page.getByRole('button', { name: 'Generate', exact: true }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
}
test('real puzzle: move, undo, progressive hint, reload, and solve', async ({ page, request }) => {
  const errors: string[] = [];
  page.on('pageerror', (e) => errors.push(e.message));
  await page.goto('/');
  await expect(page.getByTestId('node-0')).toBeVisible();
  await settings(page, '42');
  const generatedPromise = page.waitForResponse(
    (r) => r.url().endsWith('/puzzles/generate') && r.request().method() === 'POST',
  );
  await generate(page);
  const generatedResponse = await generatedPromise;
  expect(generatedResponse.ok(), await generatedResponse.text()).toBeTruthy();
  const generated = (await generatedResponse.json()) as Generated;
  await expect(page.getByRole('button', { name: 'New puzzle' })).toBeEnabled();
  expect(generated.generation.masterSeed).toBe('42');
  const solved = await request.post('/api/v1/solve', { data: { puzzle: generated.puzzle } });
  expect(solved.ok()).toBeTruthy();
  const solution = (await solved.json()).solutions[0] as string[];
  const open = solution.map((_, i) => i).filter((i) => !generated.puzzle.givens[i]);
  expect(open.length).toBeGreaterThan(0);
  const key = (color: string) => String(['RED', 'GREEN', 'BLUE'].indexOf(color) + 1);
  await page.getByTestId(`node-${open[0]}`).click();
  await page.keyboard.press(key(solution[open[0]]));
  await expect(page.getByTestId(`node-${open[0]}`)).toHaveAttribute(
    'data-color',
    solution[open[0]],
  );
  await page.getByRole('button', { name: 'Undo', exact: false }).click();
  await expect(page.getByTestId(`node-${open[0]}`)).toHaveAttribute('data-color', '');
  await page.getByRole('button', { name: 'Hint' }).click();
  await expect(page.getByRole('button', { name: 'Walk me through it' })).toBeVisible();
  await page.getByRole('button', { name: 'Walk me through it' }).click();
  await expect(page.getByRole('button', { name: 'Show colour' })).toBeVisible();
  await page.getByRole('button', { name: 'Show colour' }).click();
  await expect(page.locator('.answer')).toContainText('Vertex');
  await page.getByTestId(`node-${open[0]}`).click();
  await page.keyboard.press(key(solution[open[0]]));
  await page.reload();
  await expect(page.getByTestId(`node-${open[0]}`)).toHaveAttribute(
    'data-color',
    solution[open[0]],
  );
  for (const n of open.slice(1)) {
    await page.getByTestId(`node-${n}`).click();
    await page.keyboard.press(key(solution[n]));
  }
  await expect(page.getByTestId('game-status')).toHaveText('Puzzle solved');
  await page.getByText('Under the surface', { exact: false }).click();
  await page.getByText('Explore the reasoning', { exact: true }).click();
  await expect(page.locator('.debug-panel').getByRole('slider')).toBeEnabled();
  expect(errors).toEqual([]);
});
test('mobile board fits and givens remain locked', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/');
  await expect(page.getByTestId('node-0')).toBeVisible();
  const given = page.locator('.node.given').first();
  const color = await given.getAttribute('data-color');
  await given.click();
  await page.keyboard.press('Delete');
  await expect(given).toHaveAttribute('data-color', color!);
  await expect(page.getByRole('button', { name: 'Color Red' })).toBeDisabled();
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
  ).toBeTruthy();
});

test('four categories and size preferences survive reload', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByTestId('node-0')).toBeVisible();
  await settings(page);
  const difficulty = page.getByRole('combobox', { name: 'Difficulty' });
  expect(
    await difficulty
      .locator('option')
      .evaluateAll((options) => options.map((o) => (o as HTMLOptionElement).value)),
  ).toEqual(['VERY_EASY', 'EASY', 'MEDIUM', 'CHALLENGING']);
  const size = page.getByRole('combobox', { name: 'Size', exact: true });
  await size.selectOption('VERY_LARGE');
  await page.reload();
  await settings(page);
  await expect(size).toHaveValue('VERY_LARGE');
  await expect(page.getByLabel('Vertices', { exact: true })).toHaveCount(0);
});

test('generation retries exhausted attempts and can be canceled and restarted', async ({
  page,
}) => {
  await page.goto('/');
  await expect(page.getByTestId('node-0')).toBeVisible();
  await settings(page, '42');
  const seeds: string[] = [];
  await page.route('**/api/v1/puzzles/generate', async (route) => {
    seeds.push(route.request().postDataJSON().seed);
    if (seeds.length === 1)
      await route.fulfill({
        status: 422,
        json: { code: 'DELETION_NO_MATCH', message: 'No match' },
      });
    else await route.continue();
  });
  await generate(page);
  await expect(page.getByRole('button', { name: 'New puzzle' })).toBeVisible();
  expect(seeds).toEqual(['42', '43']);
  await expect(page.getByTestId('puzzle-seed')).toHaveText('43');
  await page.unroute('**/api/v1/puzzles/generate');
  await page.route('**/api/v1/puzzles/generate', (route) =>
    route.fulfill({ status: 422, json: { code: 'DELETION_NO_MATCH', message: 'No match' } }),
  );
  await settings(page);
  await generate(page);
  await expect(page.getByText(/Searching ·/)).toBeVisible();
  await page.getByRole('button', { name: 'Cancel search' }).click();
  await expect(page.getByText('Search canceled')).toBeVisible();
  await expect(page.getByTestId('puzzle-seed')).toHaveText('43');

  await page.unroute('**/api/v1/puzzles/generate');
  await settings(page);
  await generate(page);
  await expect(page.getByRole('button', { name: 'New puzzle' })).toBeVisible();
  await expect(page.getByTestId('puzzle-seed')).toHaveText('42');
});

test('language, theme, localized labels and game progress survive reload', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByTestId('node-0')).toBeVisible();
  await expect(page.locator('html')).toHaveAttribute('lang', 'en');
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'light');
  const open = page.locator('.node:not(.given)').first();
  await open.click();
  await page.keyboard.press('1');
  const id = await open.getAttribute('data-testid');
  await page.getByRole('button', { name: 'German', exact: true }).click();
  await expect(page.locator('html')).toHaveAttribute('lang', 'de');
  for (const color of ['Rot', 'Grün', 'Blau'])
    await expect(page.getByRole('button', { name: `Farbe ${color}`, exact: true })).toBeVisible();
  await expect(page.getByTestId(id!)).toHaveAttribute('aria-label', /Knoten \d+, Rot, ausgewählt/);
  await page.getByRole('switch', { name: 'Dunkler Modus' }).click();
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
  await page.reload();
  await expect(page.getByTestId(id!)).toHaveAttribute('data-color', 'RED');
  await expect(page.locator('html')).toHaveAttribute('lang', 'de');
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
  await page.getByRole('button', { name: 'Englisch', exact: true }).click();
  for (const color of ['Red', 'Green', 'Blue'])
    await expect(page.getByRole('button', { name: `Color ${color}`, exact: true })).toBeVisible();
  await page.getByRole('switch', { name: 'Dark mode' }).click();
  await page.reload();
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'light');
  await expect(page.locator('html')).toHaveAttribute('lang', 'en');
});

test('browser language and system theme fallbacks respect later explicit choices', async ({
  browser,
}) => {
  const context = await browser.newContext({ locale: 'de-AT', colorScheme: 'dark' });
  const page = await context.newPage();
  await page.goto('/');
  await expect(page.getByTestId('node-0')).toBeVisible();
  await expect(page.locator('html')).toHaveAttribute('lang', 'de');
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
  await page.emulateMedia({ colorScheme: 'light' });
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'light');
  await page.getByRole('switch', { name: 'Dunkler Modus' }).click();
  await page.emulateMedia({ colorScheme: 'dark' });
  await page.emulateMedia({ colorScheme: 'light' });
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
  await page.getByRole('button', { name: 'Englisch', exact: true }).click();
  await page.reload();
  await expect(page.locator('html')).toHaveAttribute('lang', 'en');
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
  await context.close();
});

test('dialogs trap focus, protect gameplay shortcuts, and restore focus; redo, clear and reset work', async ({
  page,
}) => {
  await page.goto('/');
  await expect(page.getByTestId('node-0')).toBeVisible();
  const open = page.locator('.node:not(.given)');
  await open.first().click();
  await page.keyboard.press('1');
  await page.keyboard.press('Control+z');
  await expect(open.first()).toHaveAttribute('data-color', '');
  await page.keyboard.press('Control+Shift+z');
  await expect(open.first()).toHaveAttribute('data-color', 'RED');
  await page.getByRole('button', { name: 'Clear', exact: true }).click();
  await expect(open.first()).toHaveAttribute('data-color', '');
  await page.getByRole('button', { name: 'How to play' }).click();
  await page.keyboard.press('2');
  await expect(open.first()).toHaveAttribute('data-color', '');
  for (let i = 0; i < 8; i++) {
    await page.keyboard.press('Tab');
    expect(await page.evaluate(() => document.activeElement?.closest('dialog')?.open)).toBe(true);
  }
  await page.keyboard.press('Escape');
  await expect(page.getByRole('button', { name: 'How to play' })).toBeFocused();
  for (let i = 0; i < 3; i++) {
    await open.nth(i).click();
    await page.keyboard.press(String(i + 1));
  }
  await page.getByRole('button', { name: 'More', exact: true }).click();
  await page.getByRole('button', { name: 'Reset', exact: true }).click();
  await expect(page.getByRole('dialog', { name: 'Reset puzzle?' })).toBeVisible();
  await page.getByRole('button', { name: 'Cancel', exact: true }).click();
  await expect(page.getByRole('button', { name: 'More', exact: true })).toBeFocused();
  await expect(page.locator('.node:not(.given)[data-color="RED"]')).toHaveCount(1);
  await page.getByRole('button', { name: 'More', exact: true }).click();
  await page.getByRole('button', { name: 'Reset', exact: true }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Reset', exact: true }).click();
  await expect(page.locator('.node:not(.given):not([data-color=""])')).toHaveCount(0);
});

test('responsive layout matrix and documentation screenshots', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByTestId('node-0')).toBeVisible();
  await settings(page, '42');
  await generate(page);
  await expect(page.getByRole('button', { name: 'New puzzle', exact: true })).toBeVisible();
  const sizes = [
    [320, 700],
    [360, 800],
    [390, 844],
    [430, 932],
    [768, 1024],
    [1024, 768],
    [1280, 800],
    [1440, 900],
    [1920, 1080],
  ];
  for (const locale of ['en', 'de']) {
    await page
      .getByRole('button', { name: locale === 'de' ? 'German' : 'English', exact: true })
      .click();
    for (const theme of ['light', 'dark']) {
      if ((await page.locator('html').getAttribute('data-theme')) !== theme)
        await page.getByRole('switch').click();
      for (const [width, height] of sizes) {
        await page.setViewportSize({ width, height });
        await page.evaluate(() => scrollTo(0, 0));
        expect(
          await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
          `${locale}/${theme}/${width}`,
        ).toBeTruthy();
        const board = await page.locator('.board-stage').boundingBox();
        const palette = await page.locator('.color-controls').boundingBox();
        expect(board!.height).toBeGreaterThanOrEqual(240);
        expect(board!.y).toBeLessThan(200);
        if (width <= 700) expect(palette!.y + palette!.height).toBeLessThan(height);
        await page.screenshot({ path: `/tmp/3color-qa/${locale}-${theme}-${width}.png` });
        const names: Record<string, string> = {
          'en-light-1440': 'game.png',
          'en-dark-1440': 'desktop-dark-en.png',
          'de-light-1440': 'desktop-light-de.png',
          'en-light-390': 'mobile.png',
          'de-dark-390': 'mobile-dark-de.png',
        };
        const name = names[`${locale}-${theme}-${width}`];
        if (name) await page.screenshot({ path: `../docs/screenshots/${name}` });
      }
    }
  }
});

test('conflicts use patterns and localized state; errors and seed validation localize', async ({
  page,
}) => {
  await page.goto('/');
  await expect(page.getByTestId('node-0')).toBeVisible();
  const saved = await page.evaluate(() => JSON.parse(localStorage.getItem('3color-progress-v2')!));
  const edge = saved.game.puzzle.edges.find(
    (e: { a: number; b: number }) =>
      !saved.game.puzzle.givens[e.a] || !saved.game.puzzle.givens[e.b],
  );
  const a = saved.game.puzzle.givens[edge.a] ? edge.b : edge.a;
  const b = a === edge.a ? edge.b : edge.a;
  const color = saved.game.colors[b] ?? 'RED';
  if (!saved.game.colors[b]) {
    await page.getByTestId(`node-${b}`).click();
    await page.keyboard.press('1');
  }
  await page.getByTestId(`node-${a}`).click();
  await page.keyboard.press(String(['RED', 'GREEN', 'BLUE'].indexOf(color) + 1));
  await expect(page.getByTestId('game-status')).toContainText('conflict');
  await expect(page.getByTestId(`node-${a}`)).toHaveAttribute('aria-label', /conflicting/);
  expect(
    await page
      .locator('.edge-invalid')
      .first()
      .evaluate((el) => getComputedStyle(el).strokeDasharray),
  ).not.toBe('none');
  await page.getByRole('button', { name: 'German', exact: true }).click();
  await expect(page.getByTestId(`node-${a}`)).toHaveAttribute('aria-label', /im Konflikt/);
  await page.getByRole('button', { name: 'Neues Rätsel', exact: true }).click();
  await page.locator('.seed-options summary').click();
  await page.getByLabel('Seed', { exact: true }).fill('9223372036854775808');
  await page.getByRole('button', { name: 'Erstellen', exact: true }).click();
  await expect(page.getByRole('dialog')).toBeVisible();
  expect(
    await page
      .getByLabel('Seed', { exact: true })
      .evaluate((el: HTMLInputElement) => el.validationMessage),
  ).toContain('ganze Zahl');
  await page.getByLabel('Seed', { exact: true }).fill('42');
  await page.route('**/api/v1/puzzles/generate', (r) =>
    r.fulfill({
      status: 400,
      json: { code: 'INVALID_REQUEST', message: 'English server message' },
    }),
  );
  await page.getByRole('button', { name: 'Erstellen', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('Ungültige');
});

test('very large mobile graph supports zoom and keyboard navigation', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/');
  await expect(page.getByTestId('node-0')).toBeVisible();
  await settings(page, '42');
  await page.getByRole('combobox', { name: 'Size', exact: true }).selectOption('VERY_LARGE');
  await generate(page);
  await expect(page.getByTestId('node-43')).toBeVisible({ timeout: 90000 });
  await page.getByRole('button', { name: 'Zoom in', exact: true }).click();
  await page.getByRole('button', { name: 'Zoom in', exact: true }).click();
  expect(
    await page.locator('.board-viewport').evaluate((el) => el.scrollWidth > el.clientWidth),
  ).toBeTruthy();
  await page.keyboard.press('ArrowRight');
  await expect(page.locator('.node[aria-pressed="true"]')).toBeFocused();
  expect(await page.evaluate(() => scrollY)).toBe(0);
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
  ).toBeTruthy();
  await page.screenshot({ path: '/tmp/3color-qa/dense-mobile.png' });
  await page.getByRole('button', { name: 'Zoom out', exact: true }).click();
  await page.getByRole('button', { name: 'Zoom out', exact: true }).click();
  await page.screenshot({ path: '/tmp/3color-qa/dense-mobile-fit.png' });
});

test('German mobile dialogs and progressive hints fit with reduced motion', async ({ page }) => {
  await page.setViewportSize({ width: 320, height: 700 });
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await page.goto('/');
  await expect(page.getByTestId('node-0')).toBeVisible();
  await page.getByRole('button', { name: 'German', exact: true }).click();
  await page.getByRole('switch').click();
  await page.getByRole('button', { name: 'Neues Rätsel', exact: true }).click();
  await page.locator('.seed-options summary').click();
  await page.getByLabel('Seed', { exact: true }).fill('42');
  await page.screenshot({ path: '/tmp/3color-qa/settings-mobile-de.png' });
  expect(await page.getByRole('dialog').evaluate((el) => getComputedStyle(el).animationName)).toBe(
    'none',
  );
  await page.getByRole('button', { name: 'Erstellen', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Neues Rätsel', exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Hinweis', exact: true }).click();
  await page.getByRole('button', { name: 'Schritt für Schritt erklären', exact: true }).click();
  await page.getByRole('button', { name: 'Farbe zeigen', exact: true }).click();
  await expect(page.locator('.answer')).toContainText('Knoten');
  await expect(page.locator('.node.highlighted').first()).toBeVisible();
  await page.screenshot({ path: '/tmp/3color-qa/hint-mobile-de.png' });
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
  ).toBeTruthy();
  await page.getByRole('button', { name: 'Hinweis schließen' }).click();
  await expect(page.getByRole('button', { name: 'Hinweis', exact: true })).toBeFocused();
});

test('small size categories preserve their intervals across reload', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByTestId('node-0')).toBeVisible();
  for (const [size, minimum, maximum] of [
    ['MINI', 4, 13],
    ['SMALL', 14, 23],
  ] as const) {
    await settings(page, '42');
    await page.getByRole('combobox', { name: 'Size', exact: true }).selectOption(size);
    const pending = page.waitForResponse((r) => r.url().endsWith('/puzzles/generate'));
    await generate(page);
    const response = await pending;
    expect(response.ok()).toBeTruthy();
    const data = await response.json();
    expect(data.puzzle.nodeCount).toBeGreaterThanOrEqual(minimum);
    expect(data.puzzle.nodeCount).toBeLessThanOrEqual(maximum);
    await page.reload();
    await settings(page);
    await expect(page.getByRole('combobox', { name: 'Size', exact: true })).toHaveValue(size);
    await page.keyboard.press('Escape');
  }
});

test('arrows follow displayed neighbors in landscape and portrait and stop at boundaries', async ({
  page,
}) => {
  await page.goto('/');
  await expect(page.getByTestId('node-0')).toBeVisible();
  await settings(page, '42');
  await generate(page);
  await expect(page.getByRole('button', { name: 'New puzzle', exact: true })).toBeVisible();
  // Navigation fixture: use controlled coordinates, independent of generator seed ordering.
  await page.evaluate(() => {
    const saved = JSON.parse(localStorage.getItem('3color-progress-v2')!);
    const layout = Array.from({ length: saved.game.puzzle.nodeCount }, (_, i) => ({
      x: 0.1 + (i % 4) * 0.25,
      y: 0.1 + Math.floor(i / 4) * 0.15,
    }));
    saved.game.puzzle.layout = layout;
    saved.generated.puzzle.layout = layout;
    localStorage.setItem('3color-progress-v2', JSON.stringify(saved));
  });
  await page.reload();
  await expect(page.getByTestId('node-0')).toBeVisible();
  for (const portrait of [false, true]) {
    await page.setViewportSize(
      portrait ? { width: 390, height: 1000 } : { width: 1440, height: 900 },
    );
    await expect
      .poll(() =>
        page.locator('.board-viewport').evaluate((el) => el.clientHeight > el.clientWidth),
      )
      .toBe(portrait);
    const expected = portrait
      ? { ArrowUp: 6, ArrowDown: 4, ArrowLeft: 1, ArrowRight: 9 }
      : { ArrowUp: 1, ArrowDown: 9, ArrowLeft: 4, ArrowRight: 6 };
    for (const [key, node] of Object.entries(expected)) {
      await page.getByTestId('node-5').click();
      await page.keyboard.press(key);
      await expect(page.getByTestId(`node-${node}`)).toHaveAttribute('aria-pressed', 'true');
      await expect(page.getByTestId(`node-${node}`)).toBeFocused();
    }
    for (let i = 0; i < 12; i++) await page.keyboard.press('ArrowUp');
    const top = await page.locator('.node[aria-pressed="true"]').getAttribute('data-testid');
    await page.keyboard.press('ArrowUp');
    await expect(page.locator('.node[aria-pressed="true"]')).toHaveAttribute('data-testid', top!);
    expect(await page.evaluate(() => scrollY)).toBe(0);
  }
});

test('technical details are inspectable without changing play', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByTestId('node-0')).toBeVisible();
  await settings(page, '7');
  await page.getByRole('combobox', { name: 'Difficulty' }).selectOption('EASY');
  await generate(page);
  await expect(page.getByRole('button', { name: 'New puzzle', exact: true })).toBeVisible({
    timeout: 90000,
  });
  await page.locator('.debug-panel > summary').click();
  await page.getByText('Technical details', { exact: true }).click();
  await expect(page.getByText('Logical hash · SHA-256', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'German', exact: true }).click();
  await expect(page.getByText('Technische Details', { exact: true })).toBeVisible();
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
  ).toBeTruthy();
});

test('guided hints use one request, preserve selection, and highlight explicit proof edges', async ({
  page,
}) => {
  await page.goto('/');
  await expect(page.getByTestId('node-0')).toBeVisible();
  let requests = 0;
  page.on('request', (r) => {
    if (r.url().endsWith('/hints')) requests++;
  });
  const responsePromise = page.waitForResponse((r) => r.url().endsWith('/hints'));
  await page.getByRole('button', { name: 'Hint', exact: true }).click();
  const response = await responsePromise;
  expect(response.ok()).toBeTruthy();
  const hint = await response.json();
  const e = hint.explanation;
  expect(e.primaryTargets.length).toBeGreaterThan(0);
  expect(e.focusVertices.length).toBeLessThan(await page.locator('.node').count());
  const other = [...Array(await page.locator('.node').count()).keys()].find(
    (v) => !e.primaryTargets.includes(v),
  )!;
  await page.getByTestId(`node-${other}`).click();
  await expect(page.locator('.hint-target-label')).toContainText(
    `Your selection is vertex ${other + 1}`,
  );
  await expect(page.locator('.answer')).toHaveCount(0);
  await expect(page.locator('.hint-proof')).toHaveCount(0);
  await expect(page.locator('.node.highlighted')).toHaveCount(e.focusVertices.length);
  await expect(page.locator('.edge-hint')).toHaveCount(e.focusEdges.length);
  for (const v of e.primaryTargets)
    await expect(page.getByTestId(`node-${v}`)).toHaveClass(/hint-target/);
  for (const edge of e.focusEdges)
    await expect(page.locator(`[data-edge="${edge.a}-${edge.b}"]`)).toHaveClass(/edge-hint/);
  await page.getByRole('button', { name: 'Walk me through it', exact: true }).click();
  await expect(page.locator('.answer')).toHaveCount(0);
  await expect(page.locator('.hint-proof')).toHaveCount(0);
  await page.getByRole('button', { name: 'Show colour', exact: true }).click();
  await expect(page.locator('.answer')).toBeVisible();
  expect(requests).toBe(1);
  await expect(page.getByTestId(`node-${other}`)).toHaveAttribute('aria-pressed', 'true');
  await page.getByRole('button', { name: 'Follow the reasoning', exact: true }).click();
  await expect(page.locator('.node.hint-current')).toHaveCount(e.walkthrough[0].vertices.length);
  await page.getByRole('button', { name: 'German', exact: true }).click();
  await expect(page.locator('.hint-target-label')).toContainText('Dieser Hinweis hilft dir bei:');
  await expect(page.locator('.hint-copy')).not.toContainText('Vertex');
  await page.setViewportSize({ width: 390, height: 844 });
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
  ).toBeTruthy();
  await page.screenshot({ path: '/tmp/3color-hint-v3-mobile.png', fullPage: true });
});

test('triangle hint gives one complete explanation without changing the board', async ({
  page,
}) => {
  await page.goto('/');
  await expect(page.getByTestId('node-0')).toBeVisible();
  await page.evaluate(() => {
    const saved = JSON.parse(localStorage.getItem('3color-progress-v2')!);
    const puzzle = {
      ...saved.game.puzzle,
      nodeCount: 3,
      givens: { 0: 'RED', 1: 'GREEN' },
      logicalHash: 'hint-chain',
      edges: [
        { a: 0, b: 1 },
        { a: 1, b: 2 },
        { a: 0, b: 2 },
      ],
      layout: [
        { x: 0.1, y: 0.1 },
        { x: 0.9, y: 0.1 },
        { x: 0.5, y: 0.9 },
      ],
    };
    saved.game = { puzzle, colors: puzzle.givens, notes: {}, past: [], future: [], selected: 2 };
    saved.generated.puzzle = puzzle;
    localStorage.setItem('3color-progress-v2', JSON.stringify(saved));
  });
  await page.reload();
  await expect(page.locator('.node')).toHaveCount(3);
  await page.getByRole('button', { name: 'Hint', exact: true }).click();
  await page.getByRole('button', { name: 'Walk me through it', exact: true }).click();
  await expect(page.locator('.hint-step-count')).toHaveText('Step 1 of 1');
  await expect(page.locator('.hint-copy')).toContainText('In this triangle');
  await expect(page.locator('.hint-copy')).toContainText('Blue');
  await expect(page.locator('.node.hint-current')).not.toHaveCount(0);
  await page.getByRole('button', { name: 'German', exact: true }).click();
  await expect(page.locator('.hint-step-count')).toHaveText('Schritt 1 von 1');
  await expect(page.locator('.hint-copy')).toContainText('In diesem Dreieck');
  await page.getByRole('button', { name: 'Farbe zeigen', exact: true }).click();
  await expect(page.locator('.answer')).toContainText('Blau');
  await expect(page.getByTestId('node-2')).toHaveAttribute('data-color', '');
});

test('pre-release v1 saves are ignored without being deleted', async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('3color-progress-v1', '{"obsolete":true}'));
  await page.goto('/');
  await expect(page.getByTestId('node-0')).toBeVisible();
  expect(await page.evaluate(() => localStorage.getItem('3color-progress-v1'))).toBe(
    '{"obsolete":true}',
  );
  expect(
    await page.evaluate(
      () => JSON.parse(localStorage.getItem('3color-progress-v2')!).game.puzzle.explanationModel,
    ),
  ).toBe('PLAYER_V1');
});
