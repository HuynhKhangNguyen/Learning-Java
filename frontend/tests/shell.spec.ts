import { expect, test } from '@playwright/test';

test('main routes load directly, survive refresh, and produce no runtime errors', async ({ page }) => {
  const errors: string[] = [];
  page.on('pageerror', (error) => errors.push(error.message));
  page.on('console', (message) => { if (message.type() === 'error') errors.push(message.text()); });
  const routes = [
    ['/', 'Hiểu backend từ bên trong.'],
    ['/journey/java-backend', 'Java Backend'],
    ['/journey/devops', 'DevOps & vận hành'],
    ['/learn/api-debug-500/intro', 'Xử lý API trả lỗi 500'],
    ['/learn/api-debug-500/challenge', 'Thử thách đang được chuẩn bị'],
    ['/learn/api-debug-500/result', 'Chưa có kết quả học tập'],
  ];
  for (const [url, heading] of routes) {
    await page.goto(url);
    await expect(page.getByRole('heading', { level: 1 })).toHaveText(heading);
    await page.reload();
    await expect(page.getByRole('heading', { level: 1 })).toHaveText(heading);
  }
  expect(errors).toEqual([]);
});

test('navigation and browser Back preserve the active section', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('link', { name: 'Khám phá hành trình' }).click();
  await expect(page).toHaveURL(/\/journey\/java-backend$/);
  await expect(page.getByRole('navigation', { name: 'Điều hướng chính' }).getByRole('link', { name: 'Hành trình' })).toHaveAttribute('aria-current', 'page');
  await page.getByRole('link', { name: 'Xem phòng lab', exact: true }).click();
  await expect(page).toHaveURL(/\/learn\/api-debug-500\/intro$/);
  await page.goBack();
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Java Backend');
});

test('unknown resources have a recovery path; invalid step returns to intro', async ({ page }) => {
  for (const url of ['/missing', '/journey/missing', '/learn/missing/intro']) {
    await page.goto(url);
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Đường dẫn này hiện chưa có nội dung.');
    await page.getByRole('link', { name: 'Về hành trình', exact: true }).click();
    await expect(page).toHaveURL(/\/journey\/java-backend$/);
  }
  await page.goto('/learn/api-debug-500/missing-step');
  await expect(page).toHaveURL(/\/learn\/api-debug-500\/intro$/);
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Xử lý API trả lỗi 500');
});

test('keyboard navigation works with reduced motion', async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await page.goto('/');
  await page.getByRole('link', { name: 'Khám phá hành trình' }).focus();
  await page.keyboard.press('Enter');
  await expect(page).toHaveURL(/\/journey\/java-backend$/);
  await expect(page.getByRole('main')).toBeFocused();
});

test('home and shell reflow on desktop, laptop and mobile', async ({ page }, testInfo) => {
  for (const width of [1440, 1280, 390, 320]) {
    await page.setViewportSize({ width, height: 1000 });
    await page.goto('/');
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth);
    expect(overflow, `overflow at ${width}px`).toBe(false);
    if (width === 1440 || width === 390) {
      await page.screenshot({ path: testInfo.outputPath(`home-${width}.png`), fullPage: true });
    }
  }
  await page.emulateMedia({ colorScheme: 'dark' });
  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.screenshot({ path: testInfo.outputPath('home-dark.png'), fullPage: true });
});
