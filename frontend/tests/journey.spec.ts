import { expect, test } from '@playwright/test';

test('explore branches with keyboard, collapse, and switch tracks', async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await page.goto('/journey/java-backend');
  const topic = page.getByRole('button', { name: 'Internet & DNS', exact: true });
  await topic.focus();
  await page.keyboard.press('Enter');
  await expect(page.getByRole('region', { name: 'Chi tiết Internet & DNS' })).toContainText('domain name được phân giải');
  await expect(page.locator('.topic-detail:visible')).toHaveCSS('animation-name', 'none');
  await page.getByRole('button', { name: 'Client & server', exact: true }).click();
  await expect(page.getByRole('region', { name: 'Chi tiết Internet & DNS' })).toBeHidden();
  await page.getByRole('button', { name: 'Mở tất cả', exact: true }).click();
  await expect(page.locator('.topic-node:visible')).toHaveCount(53);
  await page.getByRole('button', { name: 'Thu gọn tất cả', exact: true }).click();
  await expect(page.locator('.topic-node:visible')).toHaveCount(0);
  await page.getByRole('navigation', { name: 'Chọn lộ trình' }).getByRole('link', { name: 'DevOps', exact: true }).click();
  await expect(page.locator('.stage-node')).toHaveCount(6);
  await expect(page.getByRole('button', { name: 'Process & filesystem', exact: true })).toBeVisible();
});

test('expanded map and topic details fit desktop and mobile in both themes', async ({ page }, testInfo) => {
  await page.goto('/journey/java-backend');
  await page.getByRole('button', { name: 'Mở tất cả', exact: true }).click();
  await page.getByRole('button', { name: 'Internet & DNS', exact: true }).click();
  for (const width of [1440, 900, 390, 320]) {
    await page.setViewportSize({ width, height: 1100 });
    expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), `overflow ${width}`).toBe(false);
    if (width === 1440 || width === 390) await page.screenshot({ path: testInfo.outputPath(`journey-${width}.png`), animations: 'disabled' });
  }
  await page.emulateMedia({ colorScheme: 'dark' });
  await page.setViewportSize({ width: 1440, height: 1100 });
  await page.screenshot({ path: testInfo.outputPath('journey-dark.png'), animations: 'disabled' });
});
