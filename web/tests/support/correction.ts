import { expect, type Page } from '@playwright/test';
export async function checkCorrection(page: Page, progressKey: string) {
  await expect(page.getByTestId('node-0')).toBeVisible();
  await page.getByTestId('node-3').click();
  await page.keyboard.press('2');
  const before = await page.evaluate((key) => localStorage.getItem(key), progressKey);
  await page.getByRole('button', { name: 'Hint', exact: true }).click();
  await expect(page.locator('.hint-copy')).toContainText('vertex 4 needs correcting');
  await expect(page.locator('.answer')).toHaveCount(0);
  await page.getByRole('button', { name: 'Walk me through it', exact: true }).click();
  await expect(page.locator('.hint-step-count')).toBeVisible();
  await page.getByRole('button', { name: 'Show colour', exact: true }).click();
  await expect(page.locator('.answer')).toContainText('Red');
  await expect(page.getByTestId('node-3')).toHaveAttribute('data-color', 'GREEN');
  expect(await page.evaluate((key) => localStorage.getItem(key), progressKey)).toBe(before);
  await page.getByRole('button', { name: 'German', exact: true }).click();
  await expect(page.locator('.hint-copy')).toContainText('Die Farbe von Knoten 4 stimmt nicht');
  await page.getByRole('button', { name: 'Englisch', exact: true }).click();
  await page.getByTestId('node-3').click();
  await page.keyboard.press('3');
  await page.getByRole('button', { name: 'Hint', exact: true }).click();
  await expect(page.locator('.hint-copy')).toContainText('Vertices 2 and 4 are connected');
  await expect(page.locator('[data-edge="1-3"]')).toHaveClass(/edge-hint/);
  await expect(page.getByTestId('node-3')).toHaveAttribute('data-color', 'BLUE');
}
