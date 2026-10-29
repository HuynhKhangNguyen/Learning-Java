import { expect, test } from '@playwright/test';

test('global locale follows routes, document metadata and reload', async ({ page }) => {
  await page.goto('/');
  await expect(page.locator('html')).toHaveAttribute('lang', 'vi');
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Hiểu backend từ bên trong.');
  await page.getByRole('radio', { name: 'English', exact: true }).check();
  await expect(page.locator('html')).toHaveAttribute('lang', 'en');
  await expect(page.locator('meta[name="description"]')).toHaveAttribute('content', /self-learning space/);
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Understand backend systems from the inside.');
  await expect(page.getByRole('navigation', { name: 'Primary navigation' })).toBeVisible();
  await expect(page).toHaveTitle('Understand backend systems from the inside. · Backend Lab');

  const routes = [
    ['/journey/devops', 'DevOps & operations'],
    ['/learn/api-debug-500/intro', 'Rescue an API returning 500'],
    ['/learn/api-debug-500/challenge', 'Challenge in preparation'],
    ['/learn/http-foundations', 'HTTP: from a message to a working API'],
    ['/learn/request-lifecycle', 'How does a request travel through a backend?'],
    ['/missing', 'This path has no content yet.'],
  ] as const;
  for (const [url, heading] of routes) {
    await page.goto(url);
    await expect(page.getByRole('heading', { level: 1 })).toHaveText(heading);
    await expect(page.locator('html')).toHaveAttribute('lang', 'en');
    // Tên "Tiếng Việt" ở switch nằm ngoài main; nội dung English không được rò bản dịch Việt.
    expect(await page.getByRole('main').innerText()).not.toMatch(/[\u0102-\u01B0\u1EA0-\u1EF9]/);
  }

  await page.reload();
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('This path has no content yet.');
  await expect(page.getByRole('radio', { name: 'English', exact: true })).toBeChecked();
  await page.getByRole('radio', { name: 'Tiếng Việt', exact: true }).check();
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Đường dẫn này hiện chưa có nội dung.');
});

test('invalid stored locale falls back to Vietnamese; switch fits mobile keyboard flow', async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('backend-lab.locale.v1', 'invalid'));
  await page.setViewportSize({ width: 320, height: 900 });
  await page.goto('/journey/java-backend');
  await expect(page.locator('html')).toHaveAttribute('lang', 'vi');
  await expect(page.getByRole('radio', { name: 'Tiếng Việt', exact: true })).toBeChecked();
  await page.getByRole('button', { name: 'Internet & DNS', exact: true }).click();
  await expect(page.getByRole('region', { name: 'Chi tiết Internet & DNS' })).toBeVisible();
  await page.getByRole('radio', { name: 'English', exact: true }).focus();
  await page.keyboard.press('Space');
  await expect(page.locator('html')).toHaveAttribute('lang', 'en');
  // Locale chỉ đổi copy: topic đang mở và URL context phải giữ nguyên.
  await expect(page.getByRole('region', { name: 'Internet & DNS details' })).toBeVisible();
  await expect(page).toHaveURL(/topic=foundations-0/);
  await expect(page.getByRole('navigation', { name: 'Choose a roadmap' })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await expect.poll(() => page.evaluate(() => localStorage.getItem('backend-lab.locale.v1'))).toBe('en');
});
