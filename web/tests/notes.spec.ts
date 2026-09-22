import { test, expect } from '@playwright/test';

for (const mobile of [false, true]) {
  test(`pencil marks, history and persistence (${mobile ? 'touch' : 'desktop'})`, async ({
    page,
  }) => {
    if (mobile) await page.setViewportSize({ width: 390, height: 844 });
    await page.goto('/');
    const node = page.locator('.node:not(.given)').first();
    await node.click();
    await page.keyboard.press('q');
    const redSector = await node.locator('.note-sector.RED').getAttribute('d');
    await page.keyboard.press('e');
    await expect(node).toHaveAttribute('data-notes', '5');
    await expect(node).toHaveAttribute('data-color', '');
    await expect(node.locator('.note-sector.RED')).toHaveAttribute('d', redSector!);
    await page.reload();
    await expect(node).toHaveAttribute('data-notes', '5');
    await node.click();
    await page.keyboard.press('2');
    await expect(node).toHaveAttribute('data-notes', '0');
    await expect(node).toHaveAttribute('data-color', 'GREEN');
    await page.keyboard.press('Control+z');
    await expect(node).toHaveAttribute('data-notes', '5');
    await page.keyboard.press('Delete');
    await expect(node).toHaveAttribute('data-notes', '0');
    await page.getByRole('button', { name: 'Pencil mode', exact: true }).click();
    await page.getByRole('button', { name: 'Toggle Red note', exact: true }).click();
    await page.getByRole('button', { name: 'Toggle Green note', exact: true }).click();
    await page.getByRole('button', { name: 'Toggle Blue note', exact: true }).click();
    await expect(node).toHaveAttribute('data-notes', '7');
    await expect(node.locator('.note-sector')).toHaveCount(3);
    await page.getByRole('button', { name: 'Toggle Green note', exact: true }).click();
    await expect(node).toHaveAttribute('data-notes', '5');
    const given = page.locator('.node.given').first();
    await given.click();
    await page.keyboard.press('q');
    await expect(given).toHaveAttribute('data-notes', '0');
    await expect(page.getByRole('button', { name: 'Toggle Red note', exact: true })).toBeDisabled();
  });
}
