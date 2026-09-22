import { test, expect } from '@playwright/test';

for (const viewport of [
  { width: 1280, height: 900 },
  { width: 390, height: 844 },
]) {
  test(`hint steps preserve board transform and pan with compact panels at ${viewport.width}px`, async ({
    page,
  }) => {
    await page.setViewportSize(viewport);
    await page.goto('/?generator=deletion');
    await expect(page.getByTestId('node-0')).toBeVisible();
    await page.getByRole('button', { name: 'New puzzle', exact: true }).click();
    await page.getByRole('combobox', { name: 'Size', exact: true }).selectOption('LARGE');
    await page.getByRole('button', { name: 'Generate', exact: true }).click();
    await expect(page.getByRole('button', { name: 'Zoom in', exact: true })).toBeVisible();
    await page.getByRole('button', { name: 'Zoom in', exact: true }).click();
    await page.locator('.board-viewport').evaluate((el) => {
      el.scrollLeft = 30;
      el.scrollTop = 25;
    });
    const geometry = () =>
      page.locator('.board-viewport').evaluate((el) => {
        const svg = el.querySelector('svg')!;
        const matrix = svg.getScreenCTM()!;
        return {
          width: el.clientWidth,
          height: el.clientHeight,
          left: el.scrollLeft,
          top: el.scrollTop,
          scaleX: matrix.a,
          scaleY: matrix.d,
          viewBox: svg.getAttribute('viewBox'),
        };
      });
    const before = await geometry();
    await page.getByRole('button', { name: 'Hint', exact: true }).click();
    await expect(page.locator('.hint-panel')).toBeVisible();
    await expect.poll(geometry).toEqual(before);
    const expectCompactPanel = async () => {
      const sizes = await page.locator('.hint-panel').evaluate((el) => {
        const copy = el.querySelector('.hint-copy')!;
        const nav = el.querySelector('.hint-navigation')!;
        return {
          copyHeight: copy.clientHeight,
          textHeight: [...copy.children].reduce((sum, child) => {
            const style = getComputedStyle(child);
            return (
              sum +
              child.getBoundingClientRect().height +
              parseFloat(style.marginTop) +
              parseFloat(style.marginBottom)
            );
          }, 0),
          navHeight: nav.getBoundingClientRect().height,
        };
      });
      expect(sizes.copyHeight).toBeLessThanOrEqual(sizes.textHeight + 2);
      expect(sizes.navHeight).toBeGreaterThan(0);
    };
    await expectCompactPanel();
    await page.getByRole('button', { name: 'Walk me through it', exact: true }).click();
    for (let i = 0; i < 12; i++) {
      await expect.poll(geometry).toEqual(before);
      await expectCompactPanel();
      const next = page.getByRole('button', { name: 'Next step', exact: true });
      if (!(await next.count())) break;
      await next.click();
    }
    await page.getByRole('button', { name: 'Show colour', exact: true }).click();
    await expect(page.locator('.answer')).toBeVisible();
    await expect.poll(geometry).toEqual(before);
    await expectCompactPanel();
    expect(
      await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
    ).toBeTruthy();
  });
}
